package com.valor.commerce;

import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.workflow.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import static com.valor.commerce.CommerceDtos.*;

@Service
@Transactional
class CommerceService {
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final AssetIdentityAccess identities;
    private final CommerceCustomerRepository customers;
    private final CommerceBuildingRepository buildings;
    private final CommerceLiftRepository lifts;
    private final CommerceAmcRepository amcs;
    private final CommerceRequestRepository requests;
    private final CommerceAssignmentRepository assignments;
    private final CommerceReportRepository reports;
    private final PaymentRecordRepository payments;
    private final InvoiceRepository invoices;
    private final SupportTicketRepository tickets;
    private final AmcRenewalRequestRepository renewals;
    private final AssetDocumentRepository documents;
    private final PaymentRefundRepository refunds;
    private final RazorpayWebhookEventRepository webhookEvents;
    private final AssetDocumentStorage storage;
    private final PaymentGateway paymentGateway;
    private final AuditService audit;

    CommerceService(AssetIdentityAccess identities, CommerceCustomerRepository customers, CommerceBuildingRepository buildings,
            CommerceLiftRepository lifts, CommerceAmcRepository amcs, CommerceRequestRepository requests,
            CommerceAssignmentRepository assignments, CommerceReportRepository reports, PaymentRecordRepository payments,
            InvoiceRepository invoices, SupportTicketRepository tickets, AmcRenewalRequestRepository renewals,
            AssetDocumentRepository documents, PaymentRefundRepository refunds, RazorpayWebhookEventRepository webhookEvents,
            AssetDocumentStorage storage, PaymentGateway paymentGateway, AuditService audit) {
        this.identities = identities; this.customers = customers; this.buildings = buildings; this.lifts = lifts;
        this.amcs = amcs; this.requests = requests; this.assignments = assignments; this.reports = reports;
        this.payments = payments; this.invoices = invoices; this.tickets = tickets; this.renewals = renewals;
        this.documents = documents; this.refunds = refunds; this.webhookEvents = webhookEvents; this.storage = storage; this.paymentGateway = paymentGateway; this.audit = audit;
    }

    PageView<PaymentView> payments(Pageable page) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Page<PaymentRecord> rows = isCustomer(actor) ? payments.findByCustomerId(customer(actor).getId(), page) : payments.findAll(page);
        return page(rows.map(this::view));
    }
    PaymentView payment(Long id) { return view(authorized(payments.findById(id).orElseThrow(() -> missing("Payment")))); }
    PaymentView createPayment(PaymentCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        CustomerProfile customer = isCustomer(actor) ? customer(actor) : customer(input.customerProfileId());
        PaymentRecord row = new PaymentRecord();
        row.setCustomer(customer); row.setCreatedBy(actor); row.setAmount(input.amount()); row.setCurrency(currency(input.currency()));
        row.setPurpose(input.purpose() == null ? PaymentPurpose.OTHER : input.purpose());
        row.setProviderReference(clean(input.providerReference(), 120));
        row.setServiceRequest(input.serviceRequestId() == null ? null : request(input.serviceRequestId(), customer));
        row.setAmcContract(input.amcContractId() == null ? null : amc(input.amcContractId(), customer));
        if (input.invoiceId() != null) {
            Invoice invoice = authorized(invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice")));
            if (!invoice.getCustomer().getId().equals(customer.getId())) throw denied();
            if (input.amount().compareTo(invoice.getTotalAmount()) != 0 || !currency(input.currency()).equals(invoice.getCurrency())) {
                throw new CommerceException(400, "Payment amount must match invoice total");
            }
            if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                throw new CommerceException(409, "Invoice is not eligible for payment");
            }
            row.setInvoice(invoice);
        }
        return view(payments.saveAndFlush(row));
    }
    PaymentView updatePaymentStatus(Long id, PaymentStatusUpdate input) {
        requireAdmin();
        PaymentRecord row = payments.findById(id).orElseThrow(() -> missing("Payment"));
        row.setStatus(input.status()); row.setFailureReason(clean(input.failureReason(), 500));
        row.setProviderReference(clean(input.providerReference(), 120));
        return view(row);
    }

    RazorpayCheckoutView createRazorpayCheckout(RazorpayCheckoutCreate input) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Invoice invoice = authorized(invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice")));
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new CommerceException(409, "Invoice is not eligible for payment");
        }
        payments.findByInvoiceIdAndStatusIn(invoice.getId(), List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED))
                .ifPresent(p -> { throw new CommerceException(409, "Invoice already has an active payment"); });
        PaymentRecord row = new PaymentRecord();
        row.setCustomer(invoice.getCustomer()); row.setCreatedBy(actor); row.setInvoice(invoice);
        row.setServiceRequest(invoice.getServiceRequest()); row.setAmcContract(invoice.getAmcContract());
        row.setAmount(invoice.getTotalAmount()); row.setCurrency(invoice.getCurrency()); row.setPurpose(PaymentPurpose.INVOICE);
        row.setStatus(PaymentStatus.PROCESSING);
        payments.saveAndFlush(row);
        JsonNode order = paymentGateway.createOrder(row.getAmount(), row.getCurrency(), "valor-payment-" + row.getId());
        row.setRazorpayOrderId(text(order, "id")); row.setGatewayStatus(text(order, "status")); row.setGatewaySyncedAt(java.time.LocalDateTime.now());
        return new RazorpayCheckoutView(row.getId(), invoice.getId(), paymentGateway.keyId(), row.getRazorpayOrderId(), row.getAmount(), row.getCurrency(), row.getStatus().name());
    }

    PaymentView requestRefund(Long paymentId, RefundCreate input) {
        requireAdmin();
        PaymentRecord payment = payments.lockById(paymentId).orElseThrow(() -> missing("Payment"));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) throw new CommerceException(409, "Payment is not refundable");
        if (payment.getRazorpayPaymentId() == null) throw new CommerceException(409, "Payment has no gateway payment id");
        if (refunds.existsByPaymentIdAndStatusIn(paymentId, List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING))) throw new CommerceException(409, "Refund already in progress");
        BigDecimal refunded = refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId).stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.add(input.amount()).compareTo(payment.getAmount()) > 0) throw new CommerceException(409, "Refund exceeds paid amount");
        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment); refund.setRequestedBy(identities.actor()); refund.setAmount(input.amount()); refund.setCurrency(payment.getCurrency());
        refund.setReason(clean(input.reason(), 500)); refund.setStatus(RefundStatus.PROCESSING);
        refunds.saveAndFlush(refund);
        JsonNode node = paymentGateway.createRefund(payment.getRazorpayPaymentId(), refund.getAmount(), refund.getReason());
        refund.setRazorpayRefundId(text(node, "id")); refund.setGatewayStatus(text(node, "status"));
        audit.record("PAYMENT_REFUND_REQUEST", "PAYMENT", payment.getId(), "Requested refund amount=" + refund.getAmount(),
                "status=" + payment.getStatus(), "refundStatus=" + refund.getStatus(), "SUCCESS");
        return view(payment);
    }

    List<RefundView> paymentRefunds(Long paymentId) {
        authorized(payments.findById(paymentId).orElseThrow(() -> missing("Payment")));
        return refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId).stream().map(this::view).toList();
    }

    void razorpayWebhook(String payload, String signature) {
        if (!paymentGateway.validWebhookSignature(payload, signature)) throw new CommerceException(400, "Invalid Razorpay signature");
        try {
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String eventId = text(root, "id"), event = text(root, "event");
            if (eventId == null || event == null) throw new CommerceException(400, "Invalid Razorpay event");
            if (webhookEvents.existsByEventId(eventId)) return;
            JsonNode paymentNode = root.at("/payload/payment/entity");
            JsonNode orderNode = root.at("/payload/order/entity");
            JsonNode refundNode = root.at("/payload/refund/entity");
            String orderId = text(paymentNode, "order_id"); if (orderId == null) orderId = text(orderNode, "id");
            String paymentId = text(paymentNode, "id");
            String refundId = text(refundNode, "id");
            processGatewayEvent(event, paymentNode, refundNode, orderId, paymentId, refundId);
            RazorpayWebhookEvent saved = new RazorpayWebhookEvent();
            saved.setEventId(eventId); saved.setEventType(event); saved.setRazorpayOrderId(orderId);
            saved.setRazorpayPaymentId(paymentId); saved.setRazorpayRefundId(refundId); webhookEvents.save(saved);
        } catch (CommerceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommerceException(400, "Invalid Razorpay webhook payload");
        }
    }

    List<ReconciliationIssue> reconciliation() {
        requireAdmin();
        List<ReconciliationIssue> issues = new ArrayList<>();
        for (PaymentRecord p : payments.findAll()) {
            if (p.getRazorpayOrderId() != null && p.getGatewaySyncedAt() == null) issues.add(issue(p, "LOCAL_NOT_GATEWAY_CONFIRMED", "Gateway order exists but no synchronization timestamp"));
            if (p.getStatus() == PaymentStatus.SUCCEEDED && p.getRazorpayPaymentId() == null) issues.add(issue(p, "MISSING_GATEWAY_PAYMENT", "Successful local payment has no Razorpay payment id"));
            if (p.getInvoice() != null && p.getAmount().compareTo(p.getInvoice().getTotalAmount()) != 0) issues.add(issue(p, "AMOUNT_MISMATCH", "Payment amount differs from invoice total"));
            if (p.getGatewayStatus() != null && p.getStatus() == PaymentStatus.PROCESSING && Set.of("captured", "paid").contains(p.getGatewayStatus())) issues.add(issue(p, "STATUS_MISMATCH", "Gateway status appears paid while local status is processing"));
        }
        return issues;
    }

    PageView<InvoiceView> invoices(Pageable page) {
        User actor = identities.actor();
        if (!isCustomer(actor) && !isAdmin(actor)) throw denied();
        Page<Invoice> rows = isCustomer(actor) ? invoices.findByCustomerId(customer(actor).getId(), page) : invoices.findAll(page);
        return page(rows.map(this::view));
    }
    InvoiceView invoice(Long id) { return view(authorized(invoices.findById(id).orElseThrow(() -> missing("Invoice")))); }
    InvoiceView createInvoice(InvoiceCreate input) {
        requireAdmin();
        CustomerProfile customer = customer(input.customerProfileId());
        Invoice row = new Invoice();
        row.setCustomer(customer); row.setCreatedBy(identities.actor()); row.setDescription(input.description().trim());
        row.setSubtotal(input.subtotal()); row.setTaxAmount(input.taxAmount() == null ? BigDecimal.ZERO : input.taxAmount());
        row.setTotalAmount(row.getSubtotal().add(row.getTaxAmount())); row.setCurrency(currency(input.currency()));
        row.setDueDate(input.dueDate());
        row.setServiceRequest(input.serviceRequestId() == null ? null : request(input.serviceRequestId(), customer));
        row.setAmcContract(input.amcContractId() == null ? null : amc(input.amcContractId(), customer));
        invoices.saveAndFlush(row);
        row.setInvoiceNumber("INV-%06d".formatted(row.getId()));
        return view(row);
    }
    InvoiceView updateInvoiceStatus(Long id, InvoiceStatusUpdate input) {
        requireAdmin();
        Invoice row = invoices.findById(id).orElseThrow(() -> missing("Invoice"));
        row.setStatus(input.status());
        return view(row);
    }

    PageView<SupportTicketView> tickets(Pageable page) {
        User actor = identities.actor();
        Page<SupportTicket> rows = isAdmin(actor) ? tickets.findAll(page) : tickets.findByCreatedById(actor.getId(), page);
        return page(rows.map(this::view));
    }
    SupportTicketView ticket(Long id) { return view(authorized(tickets.findById(id).orElseThrow(() -> missing("Support ticket")))); }
    SupportTicketView createTicket(SupportTicketCreate input) {
        User actor = identities.actor();
        SupportTicket row = new SupportTicket();
        row.setCreatedBy(actor); row.setSubject(input.subject().trim()); row.setDescription(input.description().trim());
        row.setCategory(input.category() == null ? SupportTicketCategory.OTHER : input.category());
        row.setPriority(input.priority() == null ? SupportTicketPriority.MEDIUM : input.priority());
        row.setPreferredContact(clean(input.preferredContact(), 80));
        if (input.serviceRequestId() != null) row.setServiceRequest(authorizedRequestForActor(input.serviceRequestId(), actor));
        tickets.saveAndFlush(row);
        row.setTicketReference("SUP-%06d".formatted(row.getId()));
        return view(row);
    }
    SupportTicketView updateTicket(Long id, SupportTicketStatusUpdate input) {
        requireAdmin();
        SupportTicket row = tickets.findById(id).orElseThrow(() -> missing("Support ticket"));
        row.setStatus(input.status()); row.setAdminNotes(clean(input.adminNotes(), 2000));
        return view(row);
    }

    PageView<RenewalView> renewals(Pageable page) {
        User actor = identities.actor();
        Page<AmcRenewalRequest> rows = isCustomer(actor) ? renewals.findByCustomerId(customer(actor).getId(), page) : renewals.findAll(page);
        return page(rows.map(this::view));
    }
    RenewalView renewal(Long id) { return view(authorized(renewals.findById(id).orElseThrow(() -> missing("AMC renewal request")))); }
    RenewalView createRenewal(Long contractId, RenewalCreate input) {
        User actor = identities.actor();
        CustomerProfile customer = customer(actor);
        AmcContract contract = amc(contractId, customer);
        if (input.requestedEndDate().isBefore(input.requestedStartDate())) throw new CommerceException(400, "Invalid renewal period");
        if (!input.requestedStartDate().isAfter(contract.getEndDate())) throw new CommerceException(400, "Renewal must start after current contract ends");
        if (renewals.existsByContractIdAndStatusIn(contractId, List.of(RenewalRequestStatus.REQUESTED, RenewalRequestStatus.QUOTED, RenewalRequestStatus.PAYMENT_PENDING))) {
            throw new CommerceException(409, "AMC renewal request already active");
        }
        AmcRenewalRequest row = new AmcRenewalRequest();
        row.setContract(contract); row.setCustomer(customer); row.setRequestedBy(actor);
        row.setRequestedStartDate(input.requestedStartDate()); row.setRequestedEndDate(input.requestedEndDate());
        row.setCustomerNotes(clean(input.customerNotes(), 1000));
        renewals.saveAndFlush(row);
        audit.record("AMC_RENEWAL_REQUEST_CREATE", "AMC_RENEWAL_REQUEST", row.getId(), "Created AMC renewal request amc=" + contract.getId(),
                null, renewalSummary(row), "SUCCESS");
        return view(row);
    }
    RenewalView quoteRenewal(Long id, RenewalQuote input) {
        requireAdmin();
        AmcRenewalRequest row = renewals.findById(id).orElseThrow(() -> missing("AMC renewal request"));
        row.setQuotedAmount(input.quotedAmount()); row.setCurrency(currency(input.currency())); row.setAdminNotes(clean(input.adminNotes(), 1000));
        if (input.invoiceId() != null) row.setInvoice(authorized(invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice"))));
        if (input.paymentId() != null) row.setPayment(authorized(payments.findById(input.paymentId()).orElseThrow(() -> missing("Payment"))));
        String before = renewalSummary(row);
        row.setStatus(RenewalRequestStatus.QUOTED);
        audit.record("AMC_RENEWAL_QUOTE", "AMC_RENEWAL_REQUEST", row.getId(), "Quoted AMC renewal request amc=" + row.getContract().getId(),
                before, renewalSummary(row), "SUCCESS");
        return view(row);
    }
    RenewalView updateRenewal(Long id, RenewalStatusUpdate input) {
        requireAdmin();
        AmcRenewalRequest row = renewals.findById(id).orElseThrow(() -> missing("AMC renewal request"));
        String before = renewalSummary(row);
        row.setStatus(input.status()); row.setAdminNotes(clean(input.adminNotes(), 1000));
        if (input.invoiceId() != null) row.setInvoice(authorized(invoices.findById(input.invoiceId()).orElseThrow(() -> missing("Invoice"))));
        if (input.paymentId() != null) row.setPayment(authorized(payments.findById(input.paymentId()).orElseThrow(() -> missing("Payment"))));
        if (input.status() == RenewalRequestStatus.RENEWED) applyRenewal(row);
        audit.record("AMC_RENEWAL_STATUS", "AMC_RENEWAL_REQUEST", row.getId(), "Changed AMC renewal status amc=" + row.getContract().getId(),
                before, renewalSummary(row), "SUCCESS");
        return view(row);
    }

    List<AssetDocumentView> listDocuments(AssetDocumentOwnerType type, Long ownerId) {
        authorizeAsset(type, ownerId);
        return documents.findByOwnerTypeAndOwnerIdOrderByCreatedAtAscIdAsc(type, ownerId).stream().map(this::view).toList();
    }
    AssetDocumentView uploadDocument(AssetDocumentOwnerType type, Long ownerId, MultipartFile file) {
        User actor = identities.actor();
        authorizeAsset(type, ownerId);
        if (file == null || file.isEmpty()) throw new CommerceException(400, "Document file is required");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!TYPES.contains(contentType)) throw new CommerceException(400, "Unsupported document type");
        if (file.getSize() <= 0 || file.getSize() > MAX_BYTES) throw new CommerceException(400, "Document exceeds size limit");
        try {
            byte[] bytes = file.getBytes();
            if (!looksLike(contentType, bytes)) throw new CommerceException(400, "Document content does not match supported file type");
            var stored = storage.store(extension(contentType), new ByteArrayInputStream(bytes));
            AssetDocument row = new AssetDocument();
            row.setOwnerType(type); row.setOwnerId(ownerId); row.setUploadedBy(actor);
            row.setOriginalFilename(filename(file.getOriginalFilename())); row.setContentType(contentType);
            row.setFileSize(file.getSize()); row.setStorageKey(stored.key());
            return view(documents.saveAndFlush(row));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    Download downloadDocument(AssetDocumentOwnerType type, Long ownerId, Long documentId) {
        authorizeAsset(type, ownerId);
        AssetDocument row = documents.findById(documentId).orElseThrow(() -> missing("Document"));
        if (row.getOwnerType() != type || !row.getOwnerId().equals(ownerId)) throw missing("Document");
        return new Download(row.getOriginalFilename(), row.getContentType(), row.getFileSize(), storage.load(row.getStorageKey()));
    }
    void deleteDocument(AssetDocumentOwnerType type, Long ownerId, Long documentId) {
        authorizeAsset(type, ownerId);
        AssetDocument row = documents.findById(documentId).orElseThrow(() -> missing("Document"));
        if (row.getOwnerType() != type || !row.getOwnerId().equals(ownerId)) throw missing("Document");
        documents.delete(row);
        try { storage.delete(row.getStorageKey()); } catch (IOException ignored) { }
        audit.record("DOCUMENT_DELETE", "DOCUMENT", row.getId(), "Deleted " + type + " document parent=" + ownerId,
                "document=" + row.getId() + ",ownerType=" + type + ",ownerId=" + ownerId + ",contentType=" + row.getContentType() + ",size=" + row.getFileSize(),
                "deleted=true", "SUCCESS");
    }

    Download serviceReportPdf(Long requestId) {
        ServiceRequest request = authorizedRequestForActor(requestId, identities.actor());
        ServiceReport report = reports.findByRequestId(requestId).orElseThrow(() -> missing("Service report"));
        String text = "Valor Service Report\nRequest: " + request.getServiceId() + "\nStatus: " + request.getStatus()
                + "\nDiagnosis: " + report.getDiagnosis() + "\nWork Performed: " + report.getWorkPerformed()
                + "\nTesting Result: " + report.getTestingResult();
        byte[] bytes = minimalPdf(text);
        return new Download("service-report-" + request.getServiceId() + ".pdf", MediaType.APPLICATION_PDF_VALUE, (long) bytes.length,
                new org.springframework.core.io.ByteArrayResource(bytes));
    }

    private void applyRenewal(AmcRenewalRequest row) {
        PaymentRecord payment = row.getPayment();
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) throw new CommerceException(409, "Successful payment is required");
        AmcContract contract = row.getContract();
        contract.setStartDate(row.getRequestedStartDate()); contract.setEndDate(row.getRequestedEndDate());
        contract.setRenewalCount(contract.getRenewalCount() + 1); contract.setStatus(AmcStatus.ACTIVE);
    }
    private void processGatewayEvent(String event, JsonNode paymentNode, JsonNode refundNode, String orderId, String paymentId, String refundId) {
        if (event.startsWith("payment.")) {
            PaymentRecord payment = orderId == null ? payments.findByRazorpayPaymentId(paymentId).orElse(null) : payments.findByRazorpayOrderId(orderId).orElse(null);
            if (payment == null) return;
            payment.setRazorpayPaymentId(paymentId); payment.setGatewayStatus(text(paymentNode, "status")); payment.setGatewaySyncedAt(java.time.LocalDateTime.now());
            switch (event) {
                case "payment.captured" -> { payment.setStatus(PaymentStatus.SUCCEEDED); if (payment.getInvoice() != null) payment.getInvoice().setStatus(InvoiceStatus.PAID); }
                case "payment.failed" -> { payment.setStatus(PaymentStatus.FAILED); payment.setFailureReason(text(paymentNode, "error_description")); }
                case "payment.authorized" -> payment.setStatus(PaymentStatus.PROCESSING);
                default -> { }
            }
        } else if (event.startsWith("refund.") && refundId != null) {
            PaymentRefund refund = refunds.findByRazorpayRefundId(refundId).orElse(null);
            if (refund == null) return;
            refund.setGatewayStatus(text(refundNode, "status"));
            if (event.equals("refund.processed")) refund.setStatus(RefundStatus.SUCCEEDED);
            if (event.equals("refund.failed")) refund.setStatus(RefundStatus.FAILED);
            updateRefundedPayment(refund.getPayment());
        }
    }
    private void updateRefundedPayment(PaymentRecord payment) {
        BigDecimal refunded = refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(payment.getId()).stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.compareTo(BigDecimal.ZERO) > 0) payment.setStatus(refunded.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
    }
    private ReconciliationIssue issue(PaymentRecord p, String type, String detail) { return new ReconciliationIssue(p.getId(), id(p.getInvoice()), type, detail); }
    private static String text(JsonNode node, String field) { JsonNode v = node == null ? null : node.get(field); return v == null || v.isNull() ? null : v.asText(); }
    private ServiceRequest authorizedRequestForActor(Long requestId, User actor) {
        ServiceRequest r = requests.withOwner(requestId).orElseThrow(() -> missing("Service request"));
        if (actor.getRole() == Role.CUSTOMER && !r.getCustomer().getUser().getId().equals(actor.getId())) throw denied();
        if (actor.getRole() == Role.TECHNICIAN && !assignments.existsByRequestIdAndTechnicianUserId(requestId, actor.getId())) throw denied();
        if (!isAdmin(actor) && actor.getRole() != Role.CUSTOMER && actor.getRole() != Role.TECHNICIAN) throw denied();
        return r;
    }
    private void authorizeAsset(AssetDocumentOwnerType type, Long ownerId) {
        User actor = identities.actor();
        if (isAdmin(actor)) {
            if (type == AssetDocumentOwnerType.BUILDING) building(ownerId); else lift(ownerId);
            return;
        }
        if (!isCustomer(actor)) throw denied();
        CustomerProfile customer = customer(actor);
        if (type == AssetDocumentOwnerType.BUILDING) {
            Building b = building(ownerId);
            if (!b.getCustomer().getId().equals(customer.getId())) throw missing("Document owner");
        } else {
            Lift l = lift(ownerId);
            if (!l.getBuilding().getCustomer().getId().equals(customer.getId())) throw missing("Document owner");
        }
    }
    private CustomerProfile customer(Long id) { return identities.activeCustomer(id); }
    private CustomerProfile customer(User actor) { return customers.findByUserId(actor.getId()).map(c -> identities.activeCustomer(c.getId())).orElseThrow(() -> denied()); }
    private ServiceRequest request(Long id, CustomerProfile customer) {
        ServiceRequest row = requests.withOwner(id).orElseThrow(() -> missing("Service request"));
        if (!row.getCustomer().getId().equals(customer.getId())) throw denied();
        return row;
    }
    private AmcContract amc(Long id, CustomerProfile customer) {
        AmcContract row = amcs.withOwner(id).orElseThrow(() -> missing("AMC contract"));
        if (!row.getLift().getBuilding().getCustomer().getId().equals(customer.getId())) throw denied();
        return row;
    }
    private Building building(Long id) { return buildings.withOwner(id).orElseThrow(() -> missing("Building")); }
    private Lift lift(Long id) { return lifts.withOwner(id).orElseThrow(() -> missing("Lift")); }
    private <T extends PaymentRecord> T authorized(T row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private <T extends Invoice> T authorized(T row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private AmcRenewalRequest authorized(AmcRenewalRequest row) { if (owns(row.getCustomer())) return row; throw denied(); }
    private SupportTicket authorized(SupportTicket row) {
        User actor = identities.actor();
        if (isAdmin(actor) || row.getCreatedBy().getId().equals(actor.getId())) return row;
        throw denied();
    }
    private boolean owns(CustomerProfile customer) {
        User actor = identities.actor();
        return isAdmin(actor) || isCustomer(actor) && customer.getUser().getId().equals(actor.getId());
    }
    private void requireAdmin() { identities.requireAdmin(); }
    private boolean isAdmin(User u) { return u.getRole() == Role.ADMIN || u.getRole() == Role.SUPER_ADMIN; }
    private boolean isCustomer(User u) { return u.getRole() == Role.CUSTOMER; }
    private String currency(String value) { String v = value == null ? "INR" : value.trim().toUpperCase(Locale.ROOT); if (!v.matches("[A-Z]{3}")) throw new CommerceException(400, "Invalid currency"); return v; }
    private String clean(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); return v.length() > max ? v.substring(0, max) : v; }
    private String filename(String value) { String name = value == null || value.isBlank() ? "document" : value.replaceAll("[\\\\/\\r\\n]", "_").trim(); return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    private String extension(String type) { return switch (type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; }; }
    private boolean looksLike(String type, byte[] bytes) {
        if (bytes.length < 4) return false;
        if (type.equals("application/pdf")) return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (type.equals("image/png")) return bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        if (type.equals("image/jpeg")) return bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8;
        return type.equals("image/webp") && bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
    private byte[] minimalPdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", "").replace("\n", ") Tj T* (");
        String stream = "BT /F1 11 Tf 50 760 Td (" + safe + ") Tj ET";
        String pdf = "%PDF-1.4\n1 0 obj<<>>endobj\n2 0 obj<</Type/Catalog/Pages 3 0 R>>endobj\n3 0 obj<</Type/Pages/Count 1/Kids[4 0 R]>>endobj\n4 0 obj<</Type/Page/Parent 3 0 R/MediaBox[0 0 612 792]/Resources<</Font<</F1 5 0 R>>>>/Contents 6 0 R>>endobj\n5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n6 0 obj<</Length " + stream.length() + ">>stream\n" + stream + "\nendstream\nendobj\ntrailer<</Root 2 0 R>>\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }
    private <T> PageView<T> page(Page<T> p) { return new PageView<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize()); }
    private PaymentView view(PaymentRecord p) { return new PaymentView(p.getId(), p.getCustomer().getId(), id(p.getServiceRequest()), id(p.getAmcContract()), id(p.getInvoice()), p.getAmount(), p.getCurrency(), p.getPurpose(), p.getStatus(), p.getProviderReference(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(), p.getRazorpayPaymentLinkId(), p.getGatewayStatus(), p.getGatewaySyncedAt(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt()); }
    private RefundView view(PaymentRefund r) { return new RefundView(r.getId(), r.getPayment().getId(), r.getAmount(), r.getCurrency(), r.getStatus(), r.getRazorpayRefundId(), r.getGatewayStatus(), r.getReason(), r.getCreatedAt(), r.getUpdatedAt()); }
    private InvoiceView view(Invoice i) { return new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getCustomer().getId(), id(i.getServiceRequest()), id(i.getAmcContract()), i.getDescription(), i.getSubtotal(), i.getTaxAmount(), i.getTotalAmount(), i.getCurrency(), i.getStatus(), i.getIssuedDate(), i.getDueDate(), i.getCreatedAt(), i.getUpdatedAt()); }
    private SupportTicketView view(SupportTicket t) { return new SupportTicketView(t.getId(), t.getTicketReference(), t.getCreatedBy().getId(), t.getCreatedBy().getRole().name(), id(t.getServiceRequest()), t.getCategory(), t.getPriority(), t.getStatus(), t.getSubject(), t.getDescription(), t.getPreferredContact(), isAdmin(identities.actor()) ? t.getAdminNotes() : null, t.getCreatedAt(), t.getUpdatedAt()); }
    private RenewalView view(AmcRenewalRequest r) { return new RenewalView(r.getId(), r.getContract().getId(), r.getCustomer().getId(), r.getStatus(), r.getRequestedStartDate(), r.getRequestedEndDate(), r.getQuotedAmount(), r.getCurrency(), id(r.getInvoice()), id(r.getPayment()), r.getCustomerNotes(), isAdmin(identities.actor()) ? r.getAdminNotes() : null, r.getCreatedAt(), r.getUpdatedAt()); }
    private AssetDocumentView view(AssetDocument d) { return new AssetDocumentView(d.getId(), d.getOwnerType(), d.getOwnerId(), d.getOriginalFilename(), d.getContentType(), d.getFileSize(), d.getUploadedBy().getId(), d.getCreatedAt()); }
    private Long id(Object entity) {
        if (entity == null) return null;
        if (entity instanceof ServiceRequest r) return r.getId();
        if (entity instanceof AmcContract a) return a.getId();
        if (entity instanceof Invoice i) return i.getId();
        if (entity instanceof PaymentRecord p) return p.getId();
        return null;
    }
    private String renewalSummary(AmcRenewalRequest r) {
        return "amc=" + r.getContract().getId()
                + ",customer=" + r.getCustomer().getId()
                + ",status=" + r.getStatus()
                + ",requestedStartDate=" + r.getRequestedStartDate()
                + ",requestedEndDate=" + r.getRequestedEndDate()
                + ",quotedAmount=" + r.getQuotedAmount()
                + ",invoice=" + id(r.getInvoice())
                + ",payment=" + id(r.getPayment());
    }
    private static CommerceException missing(String name) { return new CommerceException(404, name + " not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    record Download(String filename, String contentType, Long size, Resource resource) {}
}
