package com.valor.commerce;

import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.communication.EmailEventService;
import com.valor.pricing.PricingService;
import com.valor.workflow.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "email.enabled=false",
        "smtp.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("mysql-integration")
class WebhookMysqlSpringLifecycleTest {
    @Autowired CommerceService service;
    @Autowired JdbcTemplate jdbc;

    @MockBean AssetIdentityAccess identities;
    @MockBean CommerceCustomerRepository customers;
    @MockBean CommerceBuildingRepository buildings;
    @MockBean CommerceLiftRepository lifts;
    @MockBean CommerceAmcRepository amcs;
    @MockBean CommerceRequestRepository requests;
    @MockBean CommerceAssignmentRepository assignments;
    @MockBean CommerceReportRepository reports;
    @MockBean PaymentRecordRepository payments;
    @MockBean InvoiceRepository invoices;
    @MockBean CashPaymentOtpRepository cashOtps;
    @MockBean SupportTicketRepository tickets;
    @MockBean AmcRenewalRequestRepository renewals;
    @MockBean AssetDocumentRepository documents;
    @MockBean PaymentRefundRepository refunds;
    @MockBean AssetDocumentStorage storage;
    @MockBean PaymentGateway paymentGateway;
    @MockBean AuditService audit;
    @MockBean EmailEventService emails;
    @MockBean PricingService pricing;

    @Test
    void realSpringTransactionCommitsOneEventAndDuplicateIsNoOp() {
        when(paymentGateway.validWebhookSignature(anyString(), anyString())).thenReturn(true);
        String eventId = "db19-" + UUID.randomUUID();
        service.razorpayWebhook(payment(eventId), "sig");
        service.razorpayWebhook(payment(eventId), "sig");
        assertEquals(1, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, eventId));
    }

    @Test
    void realSpringRollbackRemovesClaimAndLaterDeliveryCanRetry() {
        when(paymentGateway.validWebhookSignature(anyString(), anyString())).thenReturn(true);
        String eventId = "db19-rollback-" + UUID.randomUUID();
        when(payments.findByRazorpayOrderId("order-db19")).thenThrow(new IllegalStateException("synthetic processing failure"));
        try {
            service.razorpayWebhook(payment(eventId), "sig");
        } catch (CommerceException expected) {
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, eventId));
        reset(payments);
        when(payments.findByRazorpayOrderId("order-db19")).thenReturn(Optional.empty());
        service.razorpayWebhook(payment(eventId), "sig");
        assertEquals(1, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, eventId));
    }

    @RepeatedTest(3)
    void concurrentSameEventIsProcessedOnceAndSecondWaitsForCompletion() throws Exception {
        String eventId = "db19-concurrent-" + UUID.randomUUID();
        CountDownLatch signaturesReady = new CountDownLatch(2);
        CountDownLatch releaseSignatures = new CountDownLatch(1);
        CountDownLatch firstProcessingEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstProcessing = new CountDownLatch(1);
        AtomicInteger processingCalls = new AtomicInteger();

        when(paymentGateway.validWebhookSignature(anyString(), anyString())).thenAnswer(invocation -> {
            signaturesReady.countDown();
            if (!releaseSignatures.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("signature barrier timed out");
            }
            return true;
        });
        when(payments.findByRazorpayOrderId("order-db19")).thenAnswer(invocation -> {
            if (processingCalls.incrementAndGet() == 1) {
                firstProcessingEntered.countDown();
                if (!releaseFirstProcessing.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("processing barrier timed out");
                }
            }
            return Optional.empty();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.razorpayWebhook(payment(eventId), "sig-1"));
            Future<?> second = executor.submit(() -> service.razorpayWebhook(payment(eventId), "sig-2"));
            assertEquals(true, signaturesReady.await(5, TimeUnit.SECONDS));
            releaseSignatures.countDown();
            assertEquals(true, firstProcessingEntered.await(5, TimeUnit.SECONDS));
            assertEquals(1, processingCalls.get());
            releaseFirstProcessing.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseSignatures.countDown();
            releaseFirstProcessing.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, processingCalls.get());
        assertEquals(1, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, eventId));
    }

    @Test
    void distinctEventsCommitIndependently() {
        when(paymentGateway.validWebhookSignature(anyString(), anyString())).thenReturn(true);
        String first = "db19-distinct-a-" + UUID.randomUUID();
        String second = "db19-distinct-b-" + UUID.randomUUID();
        service.razorpayWebhook(payment(first), "sig-a");
        service.razorpayWebhook(payment(second), "sig-b");
        assertEquals(1, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, first));
        assertEquals(1, jdbc.queryForObject("select count(*) from razorpay_webhook_events where event_id=?", Integer.class, second));
    }

    private String payment(String eventId) {
        return "{\"id\":\"" + eventId + "\",\"event\":\"payment.authorized\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay-db19\",\"order_id\":\"order-db19\",\"status\":\"authorized\"}}}}";
    }
}
