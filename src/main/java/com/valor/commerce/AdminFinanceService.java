package com.valor.commerce;

import com.valor.auth.AssetIdentityAccess;
import com.valor.communication.EmailEventService;
import jakarta.persistence.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.commerce.AdminFinanceDtos.*;

@Service
@Transactional(readOnly = true)
class AdminFinanceService {
    private static final Set<String> TYPES = Set.of("PAYMENT", "REFUND");
    private static final Set<String> PAYMENT_STATUSES = Arrays.stream(PaymentStatus.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
    private static final Set<String> REFUND_STATUSES = Arrays.stream(RefundStatus.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
    private static final Set<String> INVOICE_STATUSES = Arrays.stream(InvoiceStatus.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
    private static final Map<String, String> SORTS = Map.of(
            "createdAt,asc", "created_at asc, numeric_id asc",
            "createdAt,desc", "created_at desc, numeric_id desc",
            "amount,asc", "amount asc, created_at desc, numeric_id desc",
            "amount,desc", "amount desc, created_at desc, numeric_id desc",
            "status,asc", "status asc, created_at desc, numeric_id desc",
            "status,desc", "status desc, created_at desc, numeric_id desc");
    private final EntityManager em;
    private final AssetIdentityAccess identities;
    private final EmailEventService emails;

    AdminFinanceService(EntityManager em, AssetIdentityAccess identities, EmailEventService emails) { this.em = em; this.identities = identities; this.emails = emails; }

    PageView<TransactionView> transactions(int page, int size, String sort, String status, Long customerId, String type,
            LocalDate from, LocalDate to, String q) {
        requireAdmin(); Bounds b = bounds(page, size); Filters f = filters(status, customerId, type, from, to, q);
        long total = ((Number) query("select count(*) from (" + unionSql(f, false) + ") tx", f).getSingleResult()).longValue();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query("select * from (" + unionSql(f, false) + ") tx order by " + sort(sort) + " limit :limit offset :offset", f)
                .setParameter("limit", b.size).setParameter("offset", (long) b.page * b.size).getResultList();
        return new PageView<>(rows.stream().map(this::transaction).toList(), total, (int)Math.ceil(total / (double)b.size), b.page, b.size);
    }

    TransactionView transaction(String id) {
        requireAdmin();
        String[] parts = id == null ? new String[0] : id.trim().toUpperCase(Locale.ROOT).split("-", 2);
        if (parts.length != 2 || !TYPES.contains(parts[0])) throw new CommerceException(400, "Invalid transaction id");
        long numeric;
        try { numeric = Long.parseLong(parts[1]); } catch (NumberFormatException ex) { throw new CommerceException(400, "Invalid transaction id"); }
        Filters f = new Filters(null, null, parts[0], null, null, null, numeric);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query(unionSql(f, true), f).getResultList();
        if (rows.isEmpty()) throw new CommerceException(404, "Transaction not found");
        return transaction(rows.get(0));
    }

    byte[] transactionsCsv(String sort, String status, Long customerId, String type, LocalDate from, LocalDate to, String q) {
        requireAdmin(); Filters f = filters(status, customerId, type, from, to, q);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query("select * from (" + unionSql(f, false) + ") tx order by " + sort(sort) + " limit 1000", f).getResultList();
        List<List<?>> csv = new ArrayList<>();
        csv.add(List.of("id","type","status","amount","currency","customerProfileId","invoiceId","paymentId","refundId","gatewayStatus","createdAt"));
        rows.stream().map(this::transaction).forEach(t -> csv.add(List.of(t.id(), t.type(), t.status(), t.amount(), t.currency(), t.customerProfileId(), nullSafe(t.invoiceId()), nullSafe(t.paymentId()), nullSafe(t.refundId()), nullSafe(t.gatewayStatus()), t.createdAt())));
        return csv(csv);
    }

    ReportView report(String type, LocalDate from, LocalDate to, String status, Long customerId, Long technicianId) {
        requireAdmin(); Range r = range(from, to); String t = type(type);
        return switch (t) {
            case "REVENUE" -> revenue(r, customerId);
            case "PAYMENTS" -> payments(r, status, customerId);
            case "INVOICES" -> invoices(r, status, customerId);
            case "SERVICES" -> services(r, status, customerId, technicianId);
            case "CUSTOMERS" -> customers(r);
            case "TECHNICIANS" -> technicians(r, technicianId);
            default -> throw new CommerceException(400, "Unsupported report type");
        };
    }

    byte[] reportCsv(String type, LocalDate from, LocalDate to, String status, Long customerId, Long technicianId) {
        ReportView report = report(type, from, to, status, customerId, technicianId);
        emails.reportReady(report.type(), "admin-report-ready:" + report.type() + ":" + nullSafe(from) + ":" + nullSafe(to) + ":" + nullSafe(status) + ":" + nullSafe(customerId) + ":" + nullSafe(technicianId));
        List<List<?>> rows = new ArrayList<>();
        if (report.rows().isEmpty()) {
            rows.add(List.of("metric", "value"));
            report.summary().forEach((k, v) -> rows.add(List.of(k, v)));
        } else {
            List<String> headers = new ArrayList<>(report.rows().get(0).keySet());
            rows.add(new ArrayList<>(headers));
            for (Map<String, Object> row : report.rows()) rows.add(headers.stream().map(row::get).toList());
        }
        return csv(rows);
    }

    private ReportView revenue(Range r, Long customerId) {
        BigDecimal gross = money("select coalesce(sum(p.amount),0) from PaymentRecord p where str(p.status) in ('SUCCEEDED','REFUNDED','PARTIALLY_REFUNDED')" + paymentWhere(r, customerId), r, customerId);
        BigDecimal refunded = money("select coalesce(sum(ref.amount),0) from PaymentRefund ref join ref.payment p where str(ref.status)='SUCCEEDED'" + paymentWhere(r, customerId), r, customerId);
        long count = count("select count(p) from PaymentRecord p where str(p.status) in ('SUCCEEDED','REFUNDED','PARTIALLY_REFUNDED')" + paymentWhere(r, customerId), r, customerId, null);
        return new ReportView("REVENUE", ordered("grossAmount", gross, "refundedAmount", refunded, "netAmount", gross.subtract(refunded), "transactionCount", count),
                List.of(ordered("metric", "grossAmount", "amount", gross), ordered("metric", "refundedAmount", "amount", refunded), ordered("metric", "netAmount", "amount", gross.subtract(refunded))));
    }

    private ReportView payments(Range r, String status, Long customerId) {
        if (status != null && !PAYMENT_STATUSES.contains(status)) throw new CommerceException(400, "Invalid payment status");
        String where = paymentWhere(r, customerId) + (status == null ? "" : " and str(p.status)=:status");
        List<Map<String,Object>> rows = enumRows("select str(p.status), count(p), coalesce(sum(p.amount),0) from PaymentRecord p where 1=1" + where + " group by p.status", r, customerId, status);
        return new ReportView("PAYMENTS", ordered("totalPayments", rows.stream().mapToLong(x -> ((Number)x.get("count")).longValue()).sum()), rows);
    }

    private ReportView invoices(Range r, String status, Long customerId) {
        if (status != null && !INVOICE_STATUSES.contains(status)) throw new CommerceException(400, "Invalid invoice status");
        String where = invoiceWhere(r, customerId) + (status == null ? "" : " and str(i.status)=:status");
        List<Map<String,Object>> rows = enumRows("select str(i.status), count(i), coalesce(sum(i.totalAmount),0) from Invoice i where 1=1" + where + " group by i.status", r, customerId, status);
        BigDecimal total = money("select coalesce(sum(i.totalAmount),0) from Invoice i where 1=1" + invoiceWhere(r, customerId), r, customerId);
        BigDecimal paid = money("select coalesce(sum(i.totalAmount),0) from Invoice i where str(i.status)='PAID'" + invoiceWhere(r, customerId), r, customerId);
        return new ReportView("INVOICES", ordered("invoiceCount", count("select count(i) from Invoice i where 1=1" + invoiceWhere(r, customerId), r, customerId, null), "totalInvoicedAmount", total, "totalPaidAmount", paid), rows);
    }

    private ReportView services(Range r, String status, Long customerId, Long technicianId) {
        String where = requestWhere(r, customerId, technicianId) + (status == null ? "" : " and str(r.status)=:status");
        List<Map<String,Object>> rows = enumRows("select str(r.status), count(distinct r), 0 from ServiceRequest r left join TechnicianAssignment a on a.request=r where 1=1" + where + " group by r.status", r, customerId, status, technicianId);
        long visits = count("select count(v) from ServiceVisit v where 1=1" + dateWhere("v.createdAt", r), r, null, null);
        return new ReportView("SERVICES", ordered("totalRequests", rows.stream().mapToLong(x -> ((Number)x.get("count")).longValue()).sum(), "visitCount", visits), rows);
    }

    private ReportView customers(Range r) {
        long total = count("select count(c) from CustomerProfile c", r, null, null);
        long active = count("select count(c) from CustomerProfile c where c.active=true", r, null, null);
        long amcs = count("select count(a) from AmcContract a", r, null, null);
        return new ReportView("CUSTOMERS", ordered("customerCount", total, "activeCustomers", active, "inactiveCustomers", total - active, "amcCount", amcs), List.of());
    }

    private ReportView technicians(Range r, Long technicianId) {
        String filter = technicianId == null ? "" : " where t.id=:technicianId";
        long total = ((Number) params(em.createQuery("select count(t) from TechnicianProfile t" + filter), null, null, null, technicianId).getSingleResult()).longValue();
        List<Map<String,Object>> rows = enumRows("select t.availabilityStatus, count(t), 0 from TechnicianProfile t" + filter + " group by t.availabilityStatus", r, null, null, technicianId);
        return new ReportView("TECHNICIANS", ordered("technicianCount", total), rows);
    }

    private Query query(String sql, Filters f) {
        Query q = em.createNativeQuery(sql);
        if (f.status != null) q.setParameter("status", f.status);
        if (f.customerId != null) q.setParameter("customerId", f.customerId);
        if (f.from != null) q.setParameter("from", Timestamp.valueOf(f.from.atStartOfDay()));
        if (f.to != null) q.setParameter("to", Timestamp.valueOf(f.to.plusDays(1).atStartOfDay()));
        if (f.q != null) q.setParameter("q", "%" + f.q.toLowerCase(Locale.ROOT) + "%");
        if (f.numericId != null) q.setParameter("numericId", f.numericId);
        return q;
    }

    private String unionSql(Filters f, boolean detail) {
        String payment = "select concat('PAYMENT-',p.id) id,'PAYMENT' type,p.status status,p.amount amount,p.currency currency,p.customer_id customer_profile_id,p.invoice_id invoice_id,i.invoice_number invoice_number,p.id payment_id,null refund_id,p.service_request_id service_request_id,p.amc_contract_id amc_contract_id,p.purpose purpose,p.provider_reference provider_reference,p.razorpay_order_id razorpay_order_id,p.razorpay_payment_id razorpay_payment_id,null razorpay_refund_id,p.gateway_status gateway_status,p.gateway_synced_at gateway_synced_at,p.created_at created_at,p.updated_at updated_at,p.id numeric_id from payment_records p left join invoices i on i.id=p.invoice_id where 1=1" + nativeWhere("p", f, "PAYMENT", detail);
        String refund = "select concat('REFUND-',r.id) id,'REFUND' type,r.status status,r.amount amount,r.currency currency,p.customer_id customer_profile_id,p.invoice_id invoice_id,i.invoice_number invoice_number,p.id payment_id,r.id refund_id,p.service_request_id service_request_id,p.amc_contract_id amc_contract_id,p.purpose purpose,null provider_reference,p.razorpay_order_id razorpay_order_id,p.razorpay_payment_id razorpay_payment_id,r.razorpay_refund_id razorpay_refund_id,r.gateway_status gateway_status,null gateway_synced_at,r.created_at created_at,r.updated_at updated_at,r.id numeric_id from payment_refunds r join payment_records p on p.id=r.payment_id left join invoices i on i.id=p.invoice_id where 1=1" + nativeWhere("r", f, "REFUND", detail);
        if ("PAYMENT".equals(f.type)) return payment;
        if ("REFUND".equals(f.type)) return refund;
        return payment + " union all " + refund;
    }

    private String nativeWhere(String alias, Filters f, String rowType, boolean detail) {
        StringBuilder sql = new StringBuilder();
        if (f.status != null) sql.append(" and ").append(alias).append(".status=:status");
        if (f.customerId != null) sql.append(" and p.customer_id=:customerId");
        if (f.from != null) sql.append(" and ").append(alias).append(".created_at>=:from");
        if (f.to != null) sql.append(" and ").append(alias).append(".created_at<:to");
        if (f.q != null) sql.append(" and (lower(coalesce(i.invoice_number,'')) like :q or lower(coalesce(p.razorpay_order_id,'')) like :q or lower(coalesce(p.razorpay_payment_id,'')) like :q");
        if (f.q != null && rowType.equals("REFUND")) sql.append(" or lower(coalesce(r.razorpay_refund_id,'')) like :q");
        if (f.q != null) sql.append(")");
        if (detail) sql.append(" and ").append(alias).append(".id=:numericId");
        return sql.toString();
    }

    private TransactionView transaction(Object[] r) {
        return new TransactionView(str(r[0]), str(r[1]), str(r[2]), decimal(r[3]), str(r[4]), lng(r[5]), lng(r[6]), str(r[7]), lng(r[8]), lng(r[9]), lng(r[10]), lng(r[11]), str(r[12]), str(r[13]), str(r[14]), str(r[15]), str(r[16]), str(r[17]), time(r[18]), time(r[19]), time(r[20]));
    }

    private Filters filters(String status, Long customerId, String type, LocalDate from, LocalDate to, String q) {
        Range r = range(from, to);
        String t = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        if (t != null && !TYPES.contains(t)) throw new CommerceException(400, "Invalid transaction type");
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (s != null && !(PAYMENT_STATUSES.contains(s) || REFUND_STATUSES.contains(s))) throw new CommerceException(400, "Invalid transaction status");
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        if (query != null && query.length() > 120) throw new CommerceException(400, "Invalid search");
        return new Filters(s, customerId, t, r.from, r.to, query, null);
    }
    private Bounds bounds(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new CommerceException(400, "Invalid pagination"); return new Bounds(page, size); }
    private Range range(LocalDate from, LocalDate to) { if (from != null && to != null && to.isBefore(from)) throw new CommerceException(400, "Invalid date range"); return new Range(from, to); }
    private String sort(String value) {
        String key = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String sql = SORTS.get(key);
        if (sql == null) throw new CommerceException(400, "Invalid sort");
        return sql;
    }
    private String type(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String paymentWhere(Range r, Long customerId) { return dateWhere("p.createdAt", r) + (customerId == null ? "" : " and p.customer.id=:customerId"); }
    private String invoiceWhere(Range r, Long customerId) { return dateWhere("i.createdAt", r) + (customerId == null ? "" : " and i.customer.id=:customerId"); }
    private String requestWhere(Range r, Long customerId, Long technicianId) { return dateWhere("r.createdAt", r) + (customerId == null ? "" : " and r.customer.id=:customerId") + (technicianId == null ? "" : " and a.technician.id=:technicianId"); }
    private String dateWhere(String field, Range r) { return (r.from == null ? "" : " and " + field + ">=:from") + (r.to == null ? "" : " and " + field + "<:to"); }
    private long count(String jpql, Range r, Long customerId, String status) { return ((Number) params(em.createQuery(jpql), r, customerId, status, null).getSingleResult()).longValue(); }
    private BigDecimal money(String jpql, Range r, Long customerId) { return (BigDecimal) params(em.createQuery(jpql), r, customerId, null, null).getSingleResult(); }
    private List<Map<String,Object>> enumRows(String jpql, Range r, Long customerId, String status) { return enumRows(jpql, r, customerId, status, null); }
    private List<Map<String,Object>> enumRows(String jpql, Range r, Long customerId, String status, Long technicianId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = params(em.createQuery(jpql), r, customerId, status, technicianId).getResultList();
        return rows.stream().map(row -> ordered("status", str(row[0]), "count", ((Number)row[1]).longValue(), "amount", row[2] instanceof BigDecimal b ? b : BigDecimal.ZERO)).toList();
    }
    private Query params(Query q, Range r, Long customerId, String status, Long technicianId) {
        if (r != null && r.from != null) q.setParameter("from", r.from.atStartOfDay());
        if (r != null && r.to != null) q.setParameter("to", r.to.plusDays(1).atStartOfDay());
        if (customerId != null) q.setParameter("customerId", customerId);
        if (status != null) q.setParameter("status", status);
        if (technicianId != null) q.setParameter("technicianId", technicianId);
        return q;
    }
    private void requireAdmin() { identities.requireAdmin(); }
    private static Map<String,Object> ordered(Object... values) { Map<String,Object> m = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) m.put(String.valueOf(values[i]), values[i + 1]); return m; }
    private static byte[] csv(List<List<?>> rows) { return rows.stream().map(row -> row.stream().map(AdminFinanceService::cell).collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8); }
    private static String cell(Object value) { String v = value == null ? "" : String.valueOf(value); if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v; return "\"" + v.replace("\"", "\"\"") + "\""; }
    private static Object nullSafe(Object value) { return value == null ? "" : value; }
    private static String str(Object value) { return value == null ? null : String.valueOf(value); }
    private static Long lng(Object value) { return value == null ? null : ((Number)value).longValue(); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); }
    private static LocalDateTime time(Object value) { return value instanceof Timestamp t ? t.toLocalDateTime() : value instanceof LocalDateTime t ? t : null; }
    record Bounds(int page, int size) {}
    record Range(LocalDate from, LocalDate to) {}
    record Filters(String status, Long customerId, String type, LocalDate from, LocalDate to, String q, Long numericId) {}
}
