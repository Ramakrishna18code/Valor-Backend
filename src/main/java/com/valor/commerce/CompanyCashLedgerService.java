package com.valor.commerce;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.Role;
import com.valor.auth.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompanyCashLedgerService {
    record Summary(BigDecimal collected, BigDecimal remitted, BigDecimal pendingVerification,
                   BigDecimal outstanding, String currency) {}
    record Receipt(long id, long paymentId, long invoiceId, long serviceRequestId,
                   String receiptNumber, BigDecimal amount, LocalDateTime receivedAt) {}
    record Remittance(long id, long technicianProfileId, BigDecimal amount, String method, String handoverReference,
                      String status, LocalDateTime submittedAt, LocalDateTime verifiedAt,
                      String verificationReason) {}
    record Submit(String idempotencyKey, BigDecimal amount, String method, String handoverReference) {}
    record Review(boolean approve, String reason) {}

    private final JdbcTemplate jdbc;
    private final AssetIdentityAccess identities;

    CompanyCashLedgerService(JdbcTemplate jdbc, AssetIdentityAccess identities) {
        this.jdbc = jdbc;
        this.identities = identities;
    }

    @Transactional
    void recordVerifiedCash(PaymentRecord payment, User collector) {
        if (collector.getRole() != Role.TECHNICIAN || payment.getStatus() != PaymentStatus.SUCCEEDED
                || payment.getInvoice() == null || payment.getServiceRequest() == null
                || payment.getProviderReference() == null || !payment.getProviderReference().startsWith("CASH-")) return;
        long technicianId = technicianId(collector);
        jdbc.update("INSERT INTO company_cash_receipts (payment_record_id,invoice_id,service_request_id,technician_profile_id,receipt_number,amount,currency,received_at) VALUES (?,?,?,?,?,?,?,?)",
                payment.getId(), payment.getInvoice().getId(), payment.getServiceRequest().getId(), technicianId,
                "VAL-CASH-" + payment.getId(), payment.getAmount(), payment.getCurrency(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    Summary summary() {
        long technicianId = technicianId(identities.actor());
        BigDecimal collected = sum("SELECT COALESCE(SUM(amount),0) FROM company_cash_receipts WHERE technician_profile_id=?", technicianId);
        BigDecimal remitted = sum("SELECT COALESCE(SUM(claimed_amount),0) FROM company_cash_remittances WHERE technician_profile_id=? AND status='VERIFIED'", technicianId);
        BigDecimal pending = sum("SELECT COALESCE(SUM(claimed_amount),0) FROM company_cash_remittances WHERE technician_profile_id=? AND status='PENDING_VERIFICATION'", technicianId);
        return new Summary(collected, remitted, pending, collected.subtract(remitted), "INR");
    }

    @Transactional(readOnly = true)
    List<Receipt> receipts() {
        long technicianId = technicianId(identities.actor());
        return jdbc.query("SELECT id,payment_record_id,invoice_id,service_request_id,receipt_number,amount,received_at FROM company_cash_receipts WHERE technician_profile_id=? ORDER BY id DESC",
                (rs, row) -> new Receipt(rs.getLong("id"), rs.getLong("payment_record_id"), rs.getLong("invoice_id"),
                        rs.getLong("service_request_id"), rs.getString("receipt_number"), rs.getBigDecimal("amount"),
                        rs.getTimestamp("received_at").toLocalDateTime()), technicianId);
    }

    @Transactional(readOnly = true)
    List<Remittance> remittances() {
        long technicianId = technicianId(identities.actor());
        return jdbc.query("SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances WHERE technician_profile_id=? ORDER BY id DESC",
                (rs, row) -> remittance(rs), technicianId);
    }

    @Transactional
    Remittance submit(Submit input) {
        long technicianId = technicianId(identities.actor());
        if (input == null || input.idempotencyKey() == null || !input.idempotencyKey().matches("[A-Za-z0-9_-]{8,80}")
                || input.amount() == null || input.amount().signum() <= 0 || input.amount().scale() > 2
                || input.method() == null || !List.of("BANK_DEPOSIT", "OFFICE_HANDOVER").contains(input.method())
                || input.handoverReference() == null || input.handoverReference().isBlank()
                || input.handoverReference().length() > 120) throw new CommerceException(400, "Invalid cash remittance");
        jdbc.queryForObject("SELECT id FROM technician_profiles WHERE id=? FOR UPDATE", Long.class, technicianId);
        List<Remittance> previous = jdbc.query("SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances WHERE technician_profile_id=? AND idempotency_key=?",
                (rs, row) -> remittance(rs), technicianId, input.idempotencyKey());
        if (!previous.isEmpty()) {
            Remittance existing = previous.get(0);
            if (existing.amount().compareTo(input.amount()) != 0 || !existing.method().equals(input.method())
                    || !existing.handoverReference().equals(input.handoverReference().trim()))
                throw new CommerceException(409, "Remittance key was already used for different details");
            return existing;
        }
        List<AvailableReceipt> available = jdbc.query("SELECT r.id, r.amount-COALESCE((SELECT SUM(i.amount) FROM company_cash_remittance_items i JOIN company_cash_remittances m ON m.id=i.remittance_id WHERE i.receipt_id=r.id AND m.status IN ('PENDING_VERIFICATION','VERIFIED')),0) AS available FROM company_cash_receipts r WHERE r.technician_profile_id=? ORDER BY r.id FOR UPDATE",
                (rs, row) -> new AvailableReceipt(rs.getLong("id"), rs.getBigDecimal("available")), technicianId);
        BigDecimal balance = available.stream().map(AvailableReceipt::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (input.amount().compareTo(balance) > 0) throw new CommerceException(409, "Remittance exceeds cash available for handover");
        jdbc.update("INSERT INTO company_cash_remittances (technician_profile_id,idempotency_key,claimed_amount,currency,method,handover_reference) VALUES (?,?,?,'INR',?,?)",
                technicianId, input.idempotencyKey(), input.amount(), input.method(), input.handoverReference().trim());
        long id = jdbc.queryForObject("SELECT id FROM company_cash_remittances WHERE technician_profile_id=? AND idempotency_key=?", Long.class, technicianId, input.idempotencyKey());
        BigDecimal remaining = input.amount();
        for (AvailableReceipt receipt : available) {
            if (remaining.signum() == 0) break;
            if (receipt.amount().signum() <= 0) continue;
            BigDecimal allocated = remaining.min(receipt.amount());
            jdbc.update("INSERT INTO company_cash_remittance_items (remittance_id,receipt_id,amount) VALUES (?,?,?)", id, receipt.id(), allocated);
            remaining = remaining.subtract(allocated);
        }
        return jdbc.queryForObject("SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances WHERE id=?",
                (rs, row) -> remittance(rs), id);
    }

    @Transactional(readOnly = true)
    List<Remittance> adminQueue(String status) {
        identities.requireAdmin();
        if (status != null && !List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "DISPUTED").contains(status))
            throw new CommerceException(400, "Invalid remittance status");
        String sql = "SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances"
                + (status == null ? "" : " WHERE status=?") + " ORDER BY id DESC LIMIT 200";
        return status == null ? jdbc.query(sql, (rs, row) -> remittance(rs))
                : jdbc.query(sql, (rs, row) -> remittance(rs), status);
    }

    @Transactional
    Remittance review(long id, Review input) {
        identities.requireAdmin();
        if (input == null || input.reason() == null || input.reason().isBlank() || input.reason().length() > 1000)
            throw new CommerceException(400, "A verification reason is required");
        List<Remittance> locked = jdbc.query("SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances WHERE id=? FOR UPDATE",
                (rs, row) -> remittance(rs), id);
        if (locked.isEmpty()) throw new CommerceException(404, "Remittance not found");
        if (!"PENDING_VERIFICATION".equals(locked.get(0).status())) throw new CommerceException(409, "Remittance was already reviewed");
        jdbc.update("UPDATE company_cash_remittances SET status=?,verified_at=NOW(6),verified_by_user_id=?,verification_reason=? WHERE id=?",
                input.approve() ? "VERIFIED" : "REJECTED", identities.actor().getId(), input.reason().trim(), id);
        return jdbc.queryForObject("SELECT id,technician_profile_id,claimed_amount,method,handover_reference,status,submitted_at,verified_at,verification_reason FROM company_cash_remittances WHERE id=?",
                (rs, row) -> remittance(rs), id);
    }

    private long technicianId(User actor) {
        if (actor.getRole() != Role.TECHNICIAN) throw new AccessDeniedException("Technician access required");
        List<Long> ids = jdbc.query("SELECT id FROM technician_profiles WHERE user_id=? AND is_active=TRUE", (rs, row) -> rs.getLong(1), actor.getId());
        if (ids.isEmpty()) throw new AccessDeniedException("Technician profile unavailable");
        return ids.get(0);
    }

    private BigDecimal sum(String sql, long id) { return jdbc.queryForObject(sql, BigDecimal.class, id); }
    private record AvailableReceipt(long id, BigDecimal amount) {}
    private Remittance remittance(java.sql.ResultSet rs) throws java.sql.SQLException {
        var verified = rs.getTimestamp("verified_at");
        return new Remittance(rs.getLong("id"), rs.getLong("technician_profile_id"), rs.getBigDecimal("claimed_amount"), rs.getString("method"),
                rs.getString("handover_reference"), rs.getString("status"), rs.getTimestamp("submitted_at").toLocalDateTime(),
                verified == null ? null : verified.toLocalDateTime(), rs.getString("verification_reason"));
    }
}
