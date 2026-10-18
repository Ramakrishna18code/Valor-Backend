package com.valor.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.communication.EmailEventService;
import com.valor.pricing.PricingService;
import com.valor.workflow.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercePaymentIntegrityTest {
    @Mock AssetIdentityAccess identities;
    @Mock CommerceCustomerRepository customers;
    @Mock CommerceBuildingRepository buildings;
    @Mock CommerceLiftRepository lifts;
    @Mock CommerceAmcRepository amcs;
    @Mock CommerceRequestRepository requests;
    @Mock CommerceAssignmentRepository assignments;
    @Mock CommerceReportRepository reports;
    @Mock PaymentRecordRepository payments;
    @Mock InvoiceRepository invoices;
    @Mock CashPaymentOtpRepository cashOtps;
    @Mock SupportTicketRepository tickets;
    @Mock AmcRenewalRequestRepository renewals;
    @Mock AssetDocumentRepository documents;
    @Mock PaymentRefundRepository refunds;
    @Mock RazorpayWebhookEventRepository webhookEvents;
    @Mock AssetDocumentStorage storage;
    @Mock PaymentGateway paymentGateway;
    @Mock AuditService audit;
    @Mock EmailEventService emails;
    @Mock PricingService pricing;
    @Mock PasswordEncoder encoder;

    private CommerceService service;
    private User admin;
    private CustomerProfile customer;
    private PaymentRecord payment;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new CommerceService(identities, customers, buildings, lifts, amcs, requests, assignments, reports,
                payments, invoices, cashOtps, tickets, renewals, documents, refunds, webhookEvents, storage,
                paymentGateway, audit, emails, pricing, encoder,
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));
        admin = mock(User.class);
        lenient().when(admin.getRole()).thenReturn(Role.ADMIN);
        lenient().when(identities.actor()).thenReturn(admin);
        lenient().doNothing().when(identities).requireAdmin();

        customer = mock(CustomerProfile.class);
        User customerUser = mock(User.class);
        lenient().when(customer.getId()).thenReturn(7L);
        lenient().when(customer.getUser()).thenReturn(customerUser);
        lenient().when(customer.getFullName()).thenReturn("Payment Customer");
        lenient().when(customerUser.getId()).thenReturn(8L);

        payment = new PaymentRecord();
        payment.setId(11L);
        payment.setCustomer(customer);
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setCurrency("INR");
        payment.setPurpose(PaymentPurpose.INVOICE);
        payment.setStatus(PaymentStatus.PROCESSING);
        lenient().when(payments.lockById(11L)).thenReturn(Optional.of(payment));

        invoice = new Invoice();
        invoice.setId(21L);
        invoice.setCustomer(customer);
        invoice.setTotalAmount(new BigDecimal("1000.00"));
        invoice.setCurrency("INR");
        invoice.setStatus(InvoiceStatus.ISSUED);
        payment.setInvoice(invoice);
    }

    @Test
    void adminCannotManuallyCompleteGatewayPaymentOrSetRefundStates() {
        payment.setRazorpayOrderId("order_test");
        when(payments.findById(11L)).thenReturn(Optional.of(payment));

        assertThrows(CommerceException.class, () -> service.updatePaymentStatus(11L,
                new CommerceDtos.PaymentStatusUpdate(PaymentStatus.SUCCEEDED, null, null)));
        assertThrows(CommerceException.class, () -> service.updatePaymentStatus(11L,
                new CommerceDtos.PaymentStatusUpdate(PaymentStatus.REFUNDED, null, null)));
        assertThrows(CommerceException.class, () -> service.updatePaymentStatus(11L,
                new CommerceDtos.PaymentStatusUpdate(PaymentStatus.PARTIALLY_REFUNDED, null, null)));
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        verifyNoInteractions(emails);
    }

    @Test
    void nonGatewayManualFailureTransitionRemainsSupported() {
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        CommerceDtos.PaymentView result = service.updatePaymentStatus(11L,
                new CommerceDtos.PaymentStatusUpdate(PaymentStatus.FAILED, "manual failure", "MANUAL"));
        assertEquals(PaymentStatus.FAILED, result.status());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        verify(emails).paymentResult(8L, "Payment Customer", 11L, "FAILED");
    }

    @Test
    void financialViewsExposeAuthoritativePersistedAmountsAndStatuses() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmount(new BigDecimal("1000.00"));
        invoice.setSubtotal(new BigDecimal("1000.00"));
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("1000.00"));
        invoice.setInvoiceNumber("INV-000021");
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        when(invoices.findById(21L)).thenReturn(Optional.of(invoice));

        CommerceDtos.PaymentView paymentView = service.payment(11L);
        CommerceDtos.InvoiceView invoiceView = service.invoice(21L);

        assertEquals(PaymentStatus.SUCCEEDED, paymentView.status());
        assertEquals(new BigDecimal("1000.00"), paymentView.amount());
        assertEquals(InvoiceStatus.ISSUED, invoiceView.status());
        assertEquals(new BigDecimal("1000.00"), invoiceView.totalAmount());
        assertEquals("INR", invoiceView.currency());
    }

    @Test
    void pendingAndFailedPaymentViewsDoNotImplySuccessfulSettlement() {
        when(payments.findById(11L)).thenReturn(Optional.of(payment));

        payment.setStatus(PaymentStatus.PROCESSING);
        assertEquals(PaymentStatus.PROCESSING, service.payment(11L).status());
        payment.setStatus(PaymentStatus.FAILED);
        assertEquals(PaymentStatus.FAILED, service.payment(11L).status());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
    }

    @Test
    void customerCannotReadAnotherCustomersPaymentInvoiceOrRefundHistory() {
        User customerActor = mock(User.class);
        when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        when(customerActor.getId()).thenReturn(88L);
        when(identities.actor()).thenReturn(customerActor);
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        when(invoices.findById(21L)).thenReturn(Optional.of(invoice));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.payment(11L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.invoice(21L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.paymentRefunds(11L));
        verify(refunds, never()).findByPaymentIdOrderByCreatedAtAscIdAsc(anyLong());
    }

    @Test
    void ownerCanDownloadReceiptWithAuthoritativeSuccessfulPaymentData() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        invoice.setInvoiceNumber("INV-000021");
        payment.setGatewaySyncedAt(java.time.LocalDateTime.parse("2026-10-18T10:15:30"));
        when(payments.findById(11L)).thenReturn(Optional.of(payment));

        CommerceService.Download receipt = service.paymentReceiptPdf(11L);

        assertEquals("receipt-11.pdf", receipt.filename());
        assertEquals("application/pdf", receipt.contentType());
        assertTrue(receipt.size() > 0);
        try {
            String pdf = new String(receipt.resource().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(pdf.startsWith("%PDF-1.4"));
            assertTrue(pdf.contains("RECEIPT-PAYMENT-11"));
            assertTrue(pdf.contains("INV-000021"));
            assertTrue(pdf.contains("1000.00 INR"));
        } catch (java.io.IOException ex) {
            fail(ex);
        }
    }

    @Test
    void receiptIsRejectedForUnsettledPaymentsAndCrossCustomerAccess() {
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        assertThrows(CommerceException.class, () -> service.paymentReceiptPdf(11L));

        User customerActor = mock(User.class);
        when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        when(customerActor.getId()).thenReturn(88L);
        when(identities.actor()).thenReturn(customerActor);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.paymentReceiptPdf(11L));
    }

    @Test
    void receiptRejectsMissingOrMismatchedInvoiceRelationship() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        payment.setInvoice(null);
        assertThrows(CommerceException.class, () -> service.paymentReceiptPdf(11L));

        payment.setInvoice(invoice);
        CustomerProfile otherCustomer = mock(CustomerProfile.class);
        when(otherCustomer.getId()).thenReturn(99L);
        invoice.setCustomer(otherCustomer);
        assertThrows(CommerceException.class, () -> service.paymentReceiptPdf(11L));
    }

    @Test
    void consolidatedRefundDocumentCountsOnlySuccessfulRefundsByDefault() throws Exception {
        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        invoice.setInvoiceNumber("INV-000021");
        PaymentRefund successful = refund(31L, new BigDecimal("400.00"), RefundStatus.SUCCEEDED,
                java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
        PaymentRefund pending = refund(32L, new BigDecimal("100.00"), RefundStatus.PROCESSING,
                java.time.LocalDateTime.parse("2026-10-02T10:00:00"));
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of(successful, pending));

        CommerceService.Download document = service.refundDocument(11L, "pdf", false);
        String pdf = new String(document.resource().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("refunds-11.pdf", document.filename());
        assertEquals("application/pdf", document.contentType());
        assertTrue(pdf.contains("Successful refunded total: 400.00 INR"));
        assertTrue(pdf.contains("Net amount remaining: 600.00 INR"));
        assertTrue(pdf.contains("Refund 31"));
        assertFalse(pdf.contains("Refund 32"));
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());

        CommerceService.Download withUnsettled = service.refundDocument(11L, "pdf", true);
        String allPdf = new String(withUnsettled.resource().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(allPdf.contains("Refund 32"));
        assertTrue(allPdf.contains("status=PROCESSING"));
    }

    @Test
    void customerRefundDocumentUsesTwelveMonthHistoryAndRejectsOtherFormatsOrOwners() {
        payment.setStatus(PaymentStatus.REFUNDED);
        invoice.setInvoiceNumber("INV-000021");
        PaymentRefund old = refund(31L, new BigDecimal("100.00"), RefundStatus.SUCCEEDED,
                java.time.LocalDateTime.parse("2025-10-17T10:00:00"));
        PaymentRefund recent = refund(32L, new BigDecimal("200.00"), RefundStatus.SUCCEEDED,
                java.time.LocalDateTime.parse("2026-10-17T10:00:00"));
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of(old, recent));

        assertThrows(CommerceException.class, () -> service.refundDocument(11L, "csv", false));
        assertDoesNotThrow(() -> service.refundDocument(11L, "pdf", false));

        User otherCustomer = mock(User.class);
        when(otherCustomer.getRole()).thenReturn(Role.CUSTOMER);
        when(otherCustomer.getId()).thenReturn(88L);
        when(identities.actor()).thenReturn(otherCustomer);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.refundDocument(11L, "pdf", false));
    }

    @Test
    void customerRefundRequestIsOwnedAndDoesNotCallGateway() {
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setRazorpayPaymentId("pay_test");
        User customerActor = mock(User.class); when(customerActor.getRole()).thenReturn(Role.CUSTOMER); when(customerActor.getId()).thenReturn(8L);
        when(identities.actor()).thenReturn(customerActor); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.existsByPaymentIdAndStatusIn(eq(11L), any())).thenReturn(false); when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of());
        when(refunds.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));

        service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(new BigDecimal("100.00"), "not needed"));

        verify(refunds).saveAndFlush(argThat(r -> r.getStatus() == RefundStatus.REQUESTED && r.getAmount().compareTo(new BigDecimal("100.00")) == 0));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void invalidAndExcessiveRefundAmountsAreRejectedBeforePersistence() {
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setRazorpayPaymentId("pay_test");
        User customerActor = mock(User.class); when(customerActor.getRole()).thenReturn(Role.CUSTOMER); when(customerActor.getId()).thenReturn(8L);
        when(identities.actor()).thenReturn(customerActor); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.existsByPaymentIdAndStatusIn(eq(11L), any())).thenReturn(false); when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of());
        assertThrows(CommerceException.class, () -> service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(BigDecimal.ZERO, "zero")));
        assertThrows(CommerceException.class, () -> service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(new BigDecimal("100.001"), "precision")));
        assertThrows(CommerceException.class, () -> service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(new BigDecimal("1000.01"), "excess")));
        verify(refunds, never()).saveAndFlush(any());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void nonInrRefundIsRejectedFromCanonicalPaymentCurrency() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setCurrency("USD");
        payment.setRazorpayPaymentId("pay_test");
        User customerActor = mock(User.class);
        when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        when(customerActor.getId()).thenReturn(8L);
        when(identities.actor()).thenReturn(customerActor);
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));

        CommerceException error = assertThrows(CommerceException.class, () -> service.customerRequestRefund(11L,
                new CommerceDtos.RefundCreate(new BigDecimal("10.00"), "unsupported currency")));

        assertEquals(400, error.status);
        verify(refunds, never()).saveAndFlush(any());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void activeRefundRequestBlocksDuplicateRequestSafely() {
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setRazorpayPaymentId("pay_test");
        User customerActor = mock(User.class); when(customerActor.getRole()).thenReturn(Role.CUSTOMER); when(customerActor.getId()).thenReturn(8L);
        when(identities.actor()).thenReturn(customerActor); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.existsByPaymentIdAndStatusIn(eq(11L), any())).thenReturn(true);
        assertThrows(CommerceException.class, () -> service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(new BigDecimal("10.00"), "duplicate")));
        verify(refunds, never()).saveAndFlush(any());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void crossCustomerRefundRequestIsDenied() {
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setRazorpayPaymentId("pay_test");
        User other = mock(User.class); when(other.getRole()).thenReturn(Role.CUSTOMER); when(other.getId()).thenReturn(99L);
        when(identities.actor()).thenReturn(other); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.customerRequestRefund(11L, new CommerceDtos.RefundCreate(new BigDecimal("100.00"), "other")));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void missingThresholdFailsClosedBeforeGatewayInitiation() throws Exception {
        setThreshold(""); PaymentRefund refund = pendingRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund)); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        assertThrows(CommerceException.class, () -> service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "approve")));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void malformedNegativeAndExcessiveDecimalThresholdsFailClosed() throws Exception {
        for (String threshold : List.of("not-a-number", "-1.00", "5000.001")) {
            setThreshold(threshold);
            PaymentRefund refund = pendingRefund();
            when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
            when(payments.lockById(11L)).thenReturn(Optional.of(payment));

            assertThrows(CommerceException.class, () -> service.approveRefund(31L,
                    new CommerceDtos.RefundApproval(31L, "invalid threshold")));
            verifyNoInteractions(paymentGateway);
        }
    }

    @Test
    void refundAtOrBelowProposedBoundaryUsesOneAdminApproval() throws Exception {
        setThreshold("5000.00");
        for (String amount : List.of("4999.99", "5000.00")) {
            PaymentRefund refund = pendingRefund();
            refund.setAmount(new BigDecimal(amount));
            when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
            when(payments.lockById(11L)).thenReturn(Optional.of(payment));
            when(refunds.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentGateway.createRefund(eq("pay_test"), eq(new BigDecimal(amount)), any()))
                    .thenReturn(node("id", "rfnd_" + amount.replace('.', '_'), "status", "pending"));

            service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "within threshold"));
        }
        verify(paymentGateway, times(2)).createRefund(any(), any(), any());
    }

    @Test
    void amountAboveProposedBoundaryRequiresDistinctSecondAdmin() throws Exception {
        setThreshold("5000.00");
        PaymentRefund refund = pendingRefund();
        refund.setAmount(new BigDecimal("5000.01"));
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "first"));
        assertThrows(CommerceException.class, () -> service.approveRefund(31L,
                new CommerceDtos.RefundApproval(31L, "same admin")));

        User secondAdmin = mock(User.class);
        when(secondAdmin.getId()).thenReturn(99L);
        when(identities.actor()).thenReturn(secondAdmin);
        when(paymentGateway.createRefund(eq("pay_test"), eq(new BigDecimal("5000.01")), any()))
                .thenReturn(node("id", "rfnd_above", "status", "pending"));

        service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "second"));
        verify(paymentGateway).createRefund(eq("pay_test"), eq(new BigDecimal("5000.01")), any());
    }

    @Test
    void highValueRefundRequiresDistinctSecondAdmin() throws Exception {
        setThreshold("100.00"); PaymentRefund refund = pendingRefund();
        refund.setAmount(new BigDecimal("200.00"));
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund)); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "first"));
        verifyNoInteractions(paymentGateway);
        assertThrows(CommerceException.class, () -> service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "same")));
    }

    @Test
    void approvalPathCallsGatewayOnlyAfterWithinThresholdApproval() throws Exception {
        setThreshold("1000.00"); PaymentRefund refund = pendingRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund)); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.createRefund(eq("pay_test"), eq(new BigDecimal("100.00")), any())).thenReturn(node("id", "rfnd_test", "status", "pending"));
        service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "approve"));
        verify(paymentGateway).createRefund(eq("pay_test"), eq(new BigDecimal("100.00")), any());
    }

    @Test
    void unknownRefundOutcomeCannotBeBlindlyRetried() throws Exception {
        setThreshold("1000.00"); PaymentRefund refund = pendingRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund)); when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.createRefund(eq("pay_test"), eq(new BigDecimal("100.00")), any())).thenThrow(new RuntimeException("timeout"));
        assertThrows(CommerceException.class, () -> service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "approve")));
        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
        assertEquals("outcome_unknown", refund.getGatewayStatus());
        assertThrows(CommerceException.class, () -> service.approveRefund(31L, new CommerceDtos.RefundApproval(31L, "retry")));
        verify(paymentGateway, times(1)).createRefund(any(), any(), any());
    }

    private PaymentRefund pendingRefund() {
        payment.setStatus(PaymentStatus.SUCCEEDED); payment.setRazorpayPaymentId("pay_test");
        PaymentRefund refund = new PaymentRefund(); refund.setId(31L); refund.setPayment(payment); refund.setAmount(new BigDecimal("100.00"));
        refund.setCurrency("INR"); refund.setStatus(RefundStatus.REQUESTED); refund.setRequestedBy(admin); return refund;
    }

    private void setThreshold(String value) throws Exception {
        Field field = CommerceService.class.getDeclaredField("sameAdminRefundThreshold"); field.setAccessible(true); field.set(service, value);
    }

    @Test
    void staleAuthorizationAndFailureCannotRegressSucceededOrRefundedPayment() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");

        payment.setStatus(PaymentStatus.SUCCEEDED);
        service.razorpayWebhook(event("evt_auth", "payment.authorized", "order_test", "pay_test", null), "sig");
        service.razorpayWebhook(event("evt_failed", "payment.failed", "order_test", "pay_test", null), "sig");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());

        payment.setStatus(PaymentStatus.REFUNDED);
        service.razorpayWebhook(event("evt_auth_refund", "payment.authorized", "order_test", "pay_test", null), "sig");
        service.razorpayWebhook(event("evt_failed_refund", "payment.failed", "order_test", "pay_test", null), "sig");
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());

        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        service.razorpayWebhook(event("evt_auth_partial", "payment.authorized", "order_test", "pay_test", null), "sig");
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
    }

    @Test
    void capturedEventCannotRestoreSucceededAfterRefund() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");
        payment.setStatus(PaymentStatus.REFUNDED);

        service.razorpayWebhook(event("evt_capture_after_refund", "payment.captured", "order_test", "pay_test", "100000INR"), "sig");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertTrue(payment.getFailureReason().contains("after a refund state"));
    }

    @Test
    void successfulPartialAndFullRefundEventsUpdateAccountingWithoutPayingInvoice() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        PaymentRefund first = new PaymentRefund(); first.setId(41L); first.setPayment(payment); first.setAmount(new BigDecimal("400.00")); first.setStatus(RefundStatus.PROCESSING); first.setRazorpayRefundId("rfnd_1"); first.setCurrency("INR");
        PaymentRefund second = new PaymentRefund(); second.setId(42L); second.setPayment(payment); second.setAmount(new BigDecimal("600.00")); second.setStatus(RefundStatus.PROCESSING); second.setRazorpayRefundId("rfnd_2"); second.setCurrency("INR");
        when(refunds.findByRazorpayRefundId(anyString())).thenAnswer(inv -> "rfnd_1".equals(inv.getArgument(0)) ? Optional.of(first) : Optional.of(second));
        when(refunds.lockById(41L)).thenReturn(Optional.of(first));
        when(refunds.lockById(42L)).thenReturn(Optional.of(second));
        when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of(first, second));

        service.razorpayWebhook(refundEvent("evt_refund_1", "refund.processed", "rfnd_1"), "sig");
        assertEquals(RefundStatus.SUCCEEDED, first.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());

        service.razorpayWebhook(refundEvent("evt_refund_2", "refund.processed", "rfnd_2"), "sig");
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());

        when(webhookEvents.existsByEventId("evt_refund_2")).thenReturn(true);
        service.razorpayWebhook(refundEvent("evt_refund_2", "refund.failed", "rfnd_2"), "sig");
        assertEquals(RefundStatus.SUCCEEDED, second.getStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void captureAmountMismatchDoesNotPayInvoiceButMissingFieldsRemainCompatible() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");

        service.razorpayWebhook(event("evt_bad_capture", "payment.captured", "order_test", "pay_bad", "99900INR"), "sig");
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertTrue(payment.getFailureReason().contains("does not match"));

        payment.setFailureReason(null);
        payment.setRazorpayPaymentId(null);
        service.razorpayWebhook(event("evt_compatible_capture", "payment.captured", "order_test", "pay_good", null), "sig");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void duplicateCapturedEventDoesNotApplyDifferentInvoiceEffect() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId("evt_duplicate")).thenReturn(true);
        payment.setRazorpayOrderId("order_test");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        invoice.setStatus(InvoiceStatus.PAID);

        service.razorpayWebhook(event("evt_duplicate", "payment.captured", "order_test", "pay_test", null), "sig");

        verify(webhookEvents, never()).save(any());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void webhookSettlementLocksPaymentBeforeApplyingState() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        when(webhookEvents.saveAndFlush(any(RazorpayWebhookEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");

        service.razorpayWebhook(event("evt_locked_capture", "payment.captured", "order_test", "pay_locked", null), "sig");

        verify(payments).lockById(11L);
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void uniqueConstraintConflictDuringClaimMakesConcurrentDuplicateANoOp() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId("evt_race")).thenReturn(false);
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");
        AtomicInteger claims = new AtomicInteger();
        doAnswer(invocation -> {
            if (claims.incrementAndGet() == 2) throw new org.springframework.dao.DataIntegrityViolationException("duplicate event_id");
            return invocation.getArgument(0);
        }).when(webhookEvents).saveAndFlush(any(RazorpayWebhookEvent.class));

        service.razorpayWebhook(event("evt_race", "payment.captured", "order_test", "pay_race", null), "sig");
        service.razorpayWebhook(event("evt_race", "payment.captured", "order_test", "pay_race", null), "sig");

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(2, claims.get());
    }

    @Test
    void mismatchedGatewayPaymentReferenceCannotPayInvoice() {
        when(paymentGateway.validWebhookSignature(anyString(), any())).thenReturn(true);
        when(webhookEvents.existsByEventId(anyString())).thenReturn(false);
        when(payments.findByRazorpayOrderId("order_test")).thenReturn(Optional.of(payment));
        payment.setRazorpayOrderId("order_test");
        payment.setRazorpayPaymentId("pay_expected");

        service.razorpayWebhook(event("evt_wrong_reference", "payment.captured", "order_test", "pay_other", null), "sig");

        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
    }

    @Test
    void reconciliationLeavesUnknownAttemptUnresolvedWhenOrderReferenceIsMissing() {
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));

        CommerceDtos.ReconciliationResult result = service.reconcilePayment(11L);

        assertFalse(result.gatewayEvidence());
        assertEquals("NO_ACTION", result.action());
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void authorizedGatewayPaymentDoesNotPayInvoice() {
        prepareReconciliation();
        when(paymentGateway.lookupOrder("order_test")).thenReturn(node("id", "order_test", "status", "created"));
        when(paymentGateway.lookupPayment("pay_test")).thenReturn(paymentNode("pay_test", "order_test", "authorized", 100000, "INR"));

        CommerceDtos.ReconciliationResult result = service.reconcilePayment(11L);

        assertEquals("NO_ACTION", result.action());
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
    }

    @Test
    void verifiedCaptureReconcilesPaymentAndInvoiceIdempotently() {
        prepareReconciliation();
        when(paymentGateway.lookupOrder("order_test")).thenReturn(node("id", "order_test", "status", "created"));
        when(paymentGateway.lookupPayment("pay_test")).thenReturn(paymentNode("pay_test", "order_test", "captured", 100000, "INR"));

        CommerceDtos.ReconciliationResult first = service.reconcilePayment(11L);
        CommerceDtos.ReconciliationResult second = service.reconcilePayment(11L);

        assertEquals("CAPTURE_CONFIRMED", first.action());
        assertEquals("CAPTURE_CONFIRMED", second.action());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void verifiedReconciliationActivatesLinkedAmcRenewalOnce() {
        prepareReconciliation();
        AmcContract contract = mock(AmcContract.class); lenient().when(contract.getId()).thenReturn(5L); lenient().when(contract.getRenewalCount()).thenReturn(0);
        final boolean[] activated = {false};
        AmcRenewalRequest renewal = mock(AmcRenewalRequest.class); when(renewal.getId()).thenReturn(77L); when(renewal.getContract()).thenReturn(contract); when(renewal.getCustomer()).thenReturn(customer);
        when(renewal.getRequestedStartDate()).thenReturn(java.time.LocalDate.of(2027, 1, 1)); when(renewal.getRequestedEndDate()).thenReturn(java.time.LocalDate.of(2027, 12, 31));
        when(renewal.getStatus()).thenAnswer(inv -> activated[0] ? RenewalRequestStatus.RENEWED : RenewalRequestStatus.PAYMENT_PENDING); when(renewal.getPayment()).thenReturn(payment); payment.setAmcContract(contract);
        doAnswer(inv -> { activated[0] = true; return null; }).when(renewal).setStatus(RenewalRequestStatus.RENEWED);
        when(renewals.findFirstByPaymentIdAndStatusIn(eq(11L), any())).thenReturn(Optional.of(renewal));
        when(renewals.lockById(anyLong())).thenReturn(Optional.of(renewal));
        when(paymentGateway.lookupOrder("order_test")).thenReturn(node("id", "order_test", "status", "created"));
        when(paymentGateway.lookupPayment("pay_test")).thenReturn(paymentNode("pay_test", "order_test", "captured", 100000, "INR"));

        service.reconcilePayment(11L);
        service.reconcilePayment(11L);

        verify(renewal).setStatus(RenewalRequestStatus.RENEWED);
        verify(contract).setRenewalCount(1);
        verify(contract).setEndDate(java.time.LocalDate.of(2027, 12, 31));
    }

    @Test
    void mismatchOrGatewayUnavailableLeavesPaymentSafe() {
        prepareReconciliation();
        when(paymentGateway.lookupOrder("order_test")).thenReturn(node("id", "order_test", "status", "created"));
        when(paymentGateway.lookupPayment("pay_test")).thenReturn(paymentNode("pay_other", "order_test", "captured", 99900, "INR"));
        CommerceDtos.ReconciliationResult mismatch = service.reconcilePayment(11L);
        assertEquals("NO_ACTION", mismatch.action());
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());

        when(paymentGateway.lookupOrder("order_test")).thenThrow(new RuntimeException("gateway unavailable"));
        CommerceDtos.ReconciliationResult unavailable = service.reconcilePayment(11L);
        assertFalse(unavailable.gatewayEvidence());
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
    }

    @Test
    void refundReconciliationAppliesVerifiedProcessedStatusWithoutCreatingRefund() {
        PaymentRefund refund = reconciliationRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(refunds.findByPaymentIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of(refund));
        when(paymentGateway.lookupRefund("rfnd_test")).thenReturn(refundNode("rfnd_test", "processed", "pay_test", 10000, "INR"));

        CommerceDtos.RefundReconciliationResult result = service.reconcileRefund(31L);
        CommerceDtos.RefundReconciliationResult repeated = service.reconcileRefund(31L);

        assertEquals("REFUND_CONFIRMED", result.action());
        assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals("NO_ACTION", repeated.action());
        verify(paymentGateway, times(1)).lookupRefund("rfnd_test");
        verify(paymentGateway, never()).createRefund(any(), any(), any());
    }

    @Test
    void refundReconciliationAppliesVerifiedFailedStatus() {
        PaymentRefund refund = reconciliationRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(paymentGateway.lookupRefund("rfnd_test")).thenReturn(refundNode("rfnd_test", "failed", "pay_test", 10000, "INR"));

        CommerceDtos.RefundReconciliationResult result = service.reconcileRefund(31L);

        assertEquals("FAILURE_CONFIRMED", result.action());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void pendingUnavailableOrMissingRefundEvidenceStaysUnresolved() {
        PaymentRefund missing = reconciliationRefund();
        missing.setRazorpayRefundId(null);
        when(refunds.lockById(31L)).thenReturn(Optional.of(missing));
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        CommerceDtos.RefundReconciliationResult noId = service.reconcileRefund(31L);
        assertEquals("NO_ACTION", noId.action());
        assertEquals(RefundStatus.PROCESSING, missing.getStatus());
        verify(paymentGateway, never()).lookupRefund(any());

        PaymentRefund pending = reconciliationRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(pending));
        when(paymentGateway.lookupRefund("rfnd_test")).thenReturn(refundNode("rfnd_test", "pending", "pay_test", 10000, "INR"));
        CommerceDtos.RefundReconciliationResult pendingResult = service.reconcileRefund(31L);
        assertEquals("NO_ACTION", pendingResult.action());
        assertEquals(RefundStatus.PROCESSING, pending.getStatus());

        when(paymentGateway.lookupRefund("rfnd_test")).thenThrow(new RuntimeException("unavailable"));
        CommerceDtos.RefundReconciliationResult unavailable = service.reconcileRefund(31L);
        assertFalse(unavailable.providerEvidence());
        assertEquals(RefundStatus.PROCESSING, pending.getStatus());
    }

    @Test
    void refundReconciliationRejectsMismatchedProviderEvidence() {
        PaymentRefund refund = reconciliationRefund();
        when(refunds.lockById(31L)).thenReturn(Optional.of(refund));
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(paymentGateway.lookupRefund("rfnd_test")).thenReturn(refundNode("rfnd_other", "processed", "pay_test", 10000, "INR"));

        CommerceDtos.RefundReconciliationResult result = service.reconcileRefund(31L);

        assertEquals("NO_ACTION", result.action());
        assertFalse(result.referencesMatch());
        assertEquals(RefundStatus.PROCESSING, refund.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void refundReconciliationRequiresAdminAuthorization() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(identities).requireAdmin();

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.reconcileRefund(31L));
        verifyNoInteractions(refunds, paymentGateway);
    }

    private PaymentRefund reconciliationRefund() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setRazorpayPaymentId("pay_test");
        PaymentRefund refund = pendingRefund();
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setRazorpayRefundId("rfnd_test");
        refund.setCurrency("INR");
        refund.setAmount(new BigDecimal("100.00"));
        return refund;
    }

    @Test
    void verifiedFailureMarksPaymentFailedWithoutPayingInvoice() {
        prepareReconciliation();
        when(paymentGateway.lookupOrder("order_test")).thenReturn(node("id", "order_test", "status", "created"));
        when(paymentGateway.lookupPayment("pay_test")).thenReturn(paymentNode("pay_test", "order_test", "failed", 100000, "INR"));

        CommerceDtos.ReconciliationResult result = service.reconcilePayment(11L);

        assertEquals("FAILURE_CONFIRMED", result.action());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
    }

    private void prepareReconciliation() {
        payment.setRazorpayOrderId("order_test");
        payment.setRazorpayPaymentId("pay_test");
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
    }

    private JsonNode node(String idField, String id, String statusField, String status) {
        return new ObjectMapper().createObjectNode().put(idField, id).put(statusField, status);
    }

    private JsonNode paymentNode(String id, String orderId, String status, int amount, String currency) {
        return new ObjectMapper().createObjectNode().put("id", id).put("order_id", orderId)
                .put("status", status).put("amount", amount).put("currency", currency);
    }

    private JsonNode refundNode(String id, String status, String paymentId, int amount, String currency) {
        return new ObjectMapper().createObjectNode().put("id", id).put("status", status)
                .put("payment_id", paymentId).put("amount", amount).put("currency", currency);
    }

    @Test
    void authorizedCashOtpVerificationStillSucceedsAndPaysInvoice() {
        User technician = mock(User.class);
        when(technician.getRole()).thenReturn(Role.TECHNICIAN);
        when(technician.getId()).thenReturn(44L);
        when(identities.actor()).thenReturn(technician);
        ServiceRequest request = mock(ServiceRequest.class);
        when(request.getId()).thenReturn(31L);
        payment.setServiceRequest(request);
        CashPaymentOtp otp = new CashPaymentOtp();
        otp.id = 55L; otp.payment = payment; otp.invoice = invoice; otp.customer = customer;
        otp.otpHash = "hash"; otp.attemptsRemaining = 3; otp.expiresAt = java.time.LocalDateTime.now().plusMinutes(5);
        when(payments.lockById(11L)).thenReturn(Optional.of(payment));
        when(cashOtps.lockById(55L)).thenReturn(Optional.of(otp));
        when(assignments.existsByRequestIdAndTechnicianUserId(31L, 44L)).thenReturn(true);
        when(encoder.matches("123456", "hash")).thenReturn(true);

        service.verifyCashPaymentOtp(new CommerceDtos.CashPaymentOtpVerify(11L, 55L, "123456"));

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("cash_verified", payment.getGatewayStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(technician, otp.verifiedBy);
    }

    @Test
    void repeatedCheckoutReusesConfirmedOrderAndSuccessfulPaymentBlocksNewCheckout() {
        prepareCheckout();
        PaymentRecord existing = payment;
        existing.setRazorpayOrderId("order_existing");
        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.of(existing));

        CommerceDtos.RazorpayCheckoutView reused = service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L));
        assertEquals(11L, reused.paymentId());
        assertEquals("order_existing", reused.razorpayOrderId());
        verify(paymentGateway, never()).createOrder(any(), any(), any());

        existing.setStatus(PaymentStatus.SUCCEEDED);
        assertThrows(CommerceException.class, () -> service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L)));
    }

    @Test
    void uncertainOrderAttemptRemainsProtectedAndDefinitiveRejectionCanRetry() {
        prepareCheckout();
        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(PaymentRecord.class))).thenAnswer(invocation -> {
            PaymentRecord saved = invocation.getArgument(0);
            saved.setId(12L);
            payment = saved;
            return saved;
        });
        when(paymentGateway.createOrder(any(), any(), any())).thenThrow(new RuntimeException("network timeout"));

        assertThrows(CommerceException.class, () -> service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L)));
        verify(paymentGateway).createOrder(new BigDecimal("1000.00"), "INR", "valor-payment-12");

        PaymentRecord uncertain = payment;
        uncertain.setId(12L);
        uncertain.setStatus(PaymentStatus.PROCESSING);
        uncertain.setRazorpayOrderId(null);
        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.of(uncertain));
        assertThrows(CommerceException.class, () -> service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L)));
        verify(paymentGateway, times(1)).createOrder(any(), any(), any());
    }

    @Test
    void definitiveGatewayRejectionMarksAttemptFailedAndAllowsControlledRetry() throws Exception {
        prepareCheckout();
        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(PaymentRecord.class))).thenAnswer(invocation -> {
            PaymentRecord saved = invocation.getArgument(0);
            saved.setId(13L);
            payment = saved;
            return saved;
        });
        HttpClientErrorException rejection = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Rejected", null, null, null);
        when(paymentGateway.createOrder(any(), any(), any())).thenThrow(rejection);

        assertThrows(HttpClientErrorException.class, () -> service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L)));
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertTrue(payment.getFailureReason().contains("Rejected"));

        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.empty());
        doReturn(new ObjectMapper().readTree("{\"id\":\"order_retry\",\"status\":\"created\"}")).when(paymentGateway).createOrder(any(), any(), any());
        CommerceDtos.RazorpayCheckoutView retry = service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L));
        assertEquals("order_retry", retry.razorpayOrderId());
        verify(paymentGateway, times(2)).createOrder(any(), any(), any());
    }

    @Test
    void checkoutUsesInvoiceAmountAndCurrency() {
        prepareCheckout();
        when(payments.findByInvoiceIdAndStatusIn(21L, List.of(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(PaymentRecord.class))).thenAnswer(invocation -> {
            PaymentRecord saved = invocation.getArgument(0);
            saved.setId(14L);
            payment = saved;
            return saved;
        });
        when(paymentGateway.createOrder(any(), any(), any())).thenReturn(new ObjectMapper().createObjectNode().put("id", "order_authoritative"));

        CommerceDtos.RazorpayCheckoutView result = service.createRazorpayCheckout(new CommerceDtos.RazorpayCheckoutCreate(21L));

        assertEquals(new BigDecimal("1000.00"), result.amount());
        assertEquals("INR", result.currency());
        verify(paymentGateway).createOrder(new BigDecimal("1000.00"), "INR", "valor-payment-14");
    }

    @Test
    void serviceInvoiceRequiresCompletedRequestAndUsesAuthoritativePricing() {
        ServiceRequest request = serviceRequest(RequestStatus.PENDING);
        when(requests.lockWithOwner(44L)).thenReturn(Optional.of(request));
        assertThrows(CommerceException.class, () -> service.createServiceInvoice(44L, null));

        request.setStatus(RequestStatus.COMPLETED);
        when(pricing.servicePrice(request)).thenReturn(new BigDecimal("1499.00"));
        when(invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(44L)).thenReturn(Optional.empty());
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> { Invoice saved = inv.getArgument(0); saved.setId(44L); return saved; });

        CommerceDtos.InvoiceView result = service.createServiceInvoice(44L, null);
        assertEquals(new BigDecimal("1499.00"), result.totalAmount());
        assertEquals("INR", result.currency());
        verify(pricing).servicePrice(request);
        verify(requests, times(2)).lockWithOwner(44L);
    }

    @Test
    void serviceInvoiceReturnsExistingEligibleInvoiceAndLocksRequest() {
        ServiceRequest request = serviceRequest(RequestStatus.COMPLETED);
        Invoice existing = new Invoice(); existing.setId(55L); existing.setCustomer(customer); existing.setServiceRequest(request);
        existing.setSubtotal(new BigDecimal("999.00")); existing.setTaxAmount(BigDecimal.ZERO); existing.setTotalAmount(new BigDecimal("999.00"));
        existing.setCurrency("INR"); existing.setStatus(InvoiceStatus.ISSUED);
        when(requests.lockWithOwner(44L)).thenReturn(Optional.of(request));
        when(invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(44L)).thenReturn(Optional.of(existing));

        CommerceDtos.InvoiceView result = service.createServiceInvoice(44L, null);
        assertEquals(55L, result.id());
        verify(invoices, never()).saveAndFlush(any(Invoice.class));
        verify(requests).lockWithOwner(44L);
    }

    @Test
    void voidedOrCancelledHistoryDoesNotBlockNewInvoiceUnderCurrentPolicy() {
        ServiceRequest request = serviceRequest(RequestStatus.COMPLETED);
        Invoice voided = new Invoice(); voided.setId(56L); voided.setCustomer(customer); voided.setServiceRequest(request); voided.setStatus(InvoiceStatus.VOID);
        when(requests.lockWithOwner(44L)).thenReturn(Optional.of(request));
        when(invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(44L)).thenReturn(Optional.of(voided));
        when(pricing.servicePrice(request)).thenReturn(new BigDecimal("999.00"));
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> { Invoice saved = inv.getArgument(0); saved.setId(57L); return saved; });

        CommerceDtos.InvoiceView result = service.createServiceInvoice(44L, null);
        assertEquals(57L, result.id());
        verify(invoices).saveAndFlush(any(Invoice.class));
    }

    @Test
    void serviceInvoiceCreationUsesPessimisticRequestLockForConcurrencyBoundary() {
        ServiceRequest request = serviceRequest(RequestStatus.COMPLETED);
        when(requests.lockWithOwner(44L)).thenReturn(Optional.of(request));
        Invoice existing = new Invoice(); existing.setId(58L); existing.setCustomer(customer); existing.setServiceRequest(request); existing.setStatus(InvoiceStatus.ISSUED);
        when(invoices.findFirstByServiceRequestIdOrderByCreatedAtDesc(44L)).thenReturn(Optional.of(existing));
        service.createServiceInvoice(44L, null);
        verify(requests).lockWithOwner(44L);
    }

    @Test
    void successfulAmcRenewalActivatesExactlyOnce() {
        AmcContract contract = mock(AmcContract.class); when(contract.getId()).thenReturn(5L); when(contract.getRenewalCount()).thenReturn(0);
        AmcRenewalRequest renewal = new AmcRenewalRequest(); renewal.setContract(contract); renewal.setCustomer(customer);
        renewal.setRequestedStartDate(java.time.LocalDate.of(2027, 1, 1)); renewal.setRequestedEndDate(java.time.LocalDate.of(2027, 12, 31));
        renewal.setStatus(RenewalRequestStatus.PAYMENT_PENDING);
        PaymentRecord paid = new PaymentRecord(); paid.setStatus(PaymentStatus.SUCCEEDED); paid.setCustomer(customer); paid.setAmcContract(contract);
        when(renewals.lockById(77L)).thenReturn(Optional.of(renewal)); when(payments.findById(91L)).thenReturn(Optional.of(paid));

        service.updateRenewal(77L, new CommerceDtos.RenewalStatusUpdate(RenewalRequestStatus.RENEWED, null, null, 91L));
        service.updateRenewal(77L, new CommerceDtos.RenewalStatusUpdate(RenewalRequestStatus.RENEWED, null, null, 91L));

        assertEquals(RenewalRequestStatus.RENEWED, renewal.getStatus());
        verify(contract).setRenewalCount(1); verify(contract).setEndDate(java.time.LocalDate.of(2027, 12, 31));
    }

    private PaymentRefund refund(Long id, BigDecimal amount, RefundStatus status, java.time.LocalDateTime createdAt) {
        PaymentRefund row = new PaymentRefund();
        row.setId(id); row.setPayment(payment); row.setAmount(amount); row.setCurrency("INR");
        row.setStatus(status); row.setCreatedAt(createdAt); row.setUpdatedAt(createdAt);
        return row;
    }

    private ServiceRequest serviceRequest(RequestStatus status) {
        ServiceRequest request = new ServiceRequest(); request.setCustomer(customer); request.setStatus(status);
        request.setServiceId("BREAKDOWN"); request.setTitle("Lift service"); request.setDescription("Repair");
        return request;
    }

    private void prepareCheckout() {
        User customerUser = customer.getUser();
        User customerActor = mock(User.class);
        lenient().when(customerActor.getRole()).thenReturn(Role.CUSTOMER);
        Long customerUserId = customerUser.getId();
        when(customerActor.getId()).thenReturn(customerUserId);
        when(identities.actor()).thenReturn(customerActor);
        when(invoices.lockById(21L)).thenReturn(Optional.of(invoice));
        lenient().when(paymentGateway.configured()).thenReturn(true);
        lenient().when(paymentGateway.keyId()).thenReturn("test_key");
    }

    private String event(String id, String type, String orderId, String paymentId, String amountCurrency) {
        String fields = amountCurrency == null ? "" : ",\"amount\":" + amountCurrency.replace("INR", "\"currency\":\"INR\",\"amount\":");
        if (amountCurrency != null) {
            String[] parts = amountCurrency.split("INR");
            fields = ",\"amount\":" + parts[0] + ",\"currency\":\"INR\"";
        }
        return "{\"id\":\"" + id + "\",\"event\":\"" + type + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + orderId + "\",\"status\":\"captured\"" + fields + "}}}}";
    }

    private String refundEvent(String id, String type, String refundId) {
        return "{\"id\":\"" + id + "\",\"event\":\"" + type + "\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"" + refundId + "\",\"status\":\"processed\"}}}}";
    }
}
