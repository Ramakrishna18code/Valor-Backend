package com.valor.commerce;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.valor.auth.CustomerProfile;
import com.valor.auth.User;
import com.valor.communication.CommunicationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class InvoiceReminderTaskTest {
    @Mock InvoiceRepository invoices;
    @Mock PaymentRecordRepository payments;
    @Mock PaymentRefundRepository refunds;
    @Mock CommunicationService communications;

    @Test
    void enqueuesEachConfiguredStageWithRemainingBalanceAndBoundedQueries() {
        Clock clock = Clock.fixed(Instant.parse("2030-01-10T03:00:00Z"), ZoneId.of("UTC"));
        InvoiceReminderTask task = task(clock, 2, "Asia/Kolkata");
        Invoice invoice = invoice(42L, LocalDate.of(2030, 1, 13), InvoiceStatus.ISSUED, new BigDecimal("1000.00"));
        when(invoices.findCollectibleDueBetween(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(invoice));
        when(payments.sumCollectedForInvoice(eq(42L), any())).thenReturn(new BigDecimal("700.00"));
        when(refunds.sumForInvoice(42L, RefundStatus.SUCCEEDED)).thenReturn(new BigDecimal("100.00"));

        task.enqueueDueReminders();

        verify(invoices, times(5)).findCollectibleDueBetween(any(), any(), eq(Set.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED, InvoiceStatus.VOID)), argThat(p -> p.getPageSize() == 2));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<java.util.Map<String,String>> vars = ArgumentCaptor.forClass(java.util.Map.class);
        verify(communications, times(5)).enqueuePaymentReminder(isNull(), vars.capture(), key.capture());
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:42:BEFORE_3_DAYS:CUSTOMER"));
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:42:DUE_TODAY:CUSTOMER"));
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:42:OVERDUE_3_DAYS:CUSTOMER"));
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:42:OVERDUE_7_DAYS:CUSTOMER"));
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:42:OVERDUE_14_DAYS:CUSTOMER"));
        assertEquals("400.00", vars.getAllValues().get(0).get("balance"));
    }

    @Test
    void usesConfiguredBusinessTimezoneForStageSelection() {
        Clock clock = Clock.fixed(Instant.parse("2030-01-09T20:00:00Z"), ZoneId.of("UTC"));
        InvoiceReminderTask task = task(clock, 100, "Asia/Kolkata");
        Invoice invoice = invoice(7L, LocalDate.of(2030, 1, 13), InvoiceStatus.ISSUED, new BigDecimal("10.00"));
        when(invoices.findCollectibleDueBetween(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(invoice));
        when(payments.sumCollectedForInvoice(eq(7L), any())).thenReturn(BigDecimal.ZERO);
        when(refunds.sumForInvoice(7L, RefundStatus.SUCCEEDED)).thenReturn(BigDecimal.ZERO);

        task.enqueueDueReminders();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(communications, times(5)).enqueuePaymentReminder(isNull(), any(), key.capture());
        assertTrue(key.getAllValues().contains("PAYMENT_REMINDER:7:DUE_TODAY:CUSTOMER"));
    }

    @Test
    void repositoryExclusionIsUsedForPaidCancelledAndVoidInvoices() {
        InvoiceReminderTask task = task(Clock.systemUTC(), 100, "Asia/Kolkata");
        when(invoices.findCollectibleDueBetween(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());

        task.enqueueDueReminders();

        verify(invoices, times(5)).findCollectibleDueBetween(any(), any(), eq(Set.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED, InvoiceStatus.VOID)), any(Pageable.class));
        verifyNoInteractions(payments, refunds, communications);
    }

    private InvoiceReminderTask task(Clock clock, int batchSize, String zone) {
        return new InvoiceReminderTask(invoices, payments, refunds, communications, clock, batchSize, zone);
    }

    private Invoice invoice(Long id, LocalDate due, InvoiceStatus status, BigDecimal total) {
        User user = new User(); user.setRole(com.valor.auth.Role.CUSTOMER);
        CustomerProfile customer = new CustomerProfile(); customer.setUser(user);
        Invoice invoice = new Invoice(); invoice.setId(id); invoice.setInvoiceNumber("INV-" + id); invoice.setCustomer(customer);
        invoice.setDueDate(due); invoice.setStatus(status); invoice.setTotalAmount(total); invoice.setCurrency("INR");
        return invoice;
    }
}
