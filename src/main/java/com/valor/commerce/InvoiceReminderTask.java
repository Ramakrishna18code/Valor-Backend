package com.valor.commerce;

import com.valor.communication.CommunicationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Enqueues collectible-invoice reminders; delivery is handled by the communication retry worker. */
@Component
class InvoiceReminderTask {
    private static final Set<InvoiceStatus> STOPPED = EnumSet.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED, InvoiceStatus.VOID);
    private static final int[] STAGES = {-3, 0, 3, 7, 14};
    private final InvoiceRepository invoices;
    private final PaymentRecordRepository payments;
    private final PaymentRefundRepository refunds;
    private final CommunicationService communications;
    private final Clock clock;
    private final int batchSize;
    private final ZoneId zone;

    InvoiceReminderTask(InvoiceRepository invoices, PaymentRecordRepository payments, PaymentRefundRepository refunds, CommunicationService communications, Clock clock,
            @Value("${valor.payment.reminders.batch-size:100}") int batchSize,
            @Value("${valor.payment.reminders.timezone:Asia/Kolkata}") String timezone) {
        this.invoices = invoices; this.payments = payments; this.refunds = refunds; this.communications = communications; this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, 500)); this.zone = ZoneId.of(timezone);
    }

    @Scheduled(cron = "${valor.payment.reminders.cron:0 15 9 * * *}", zone = "${valor.payment.reminders.timezone:Asia/Kolkata}")
    @Transactional
    public void enqueueDueReminders() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        for (int offset : STAGES) {
            LocalDate target = today.plusDays(offset);
            for (Invoice invoice : invoices.findCollectibleDueBetween(target, target, STOPPED, PageRequest.of(0, batchSize))) {
                String stage = offset < 0 ? "BEFORE_3_DAYS" : offset == 0 ? "DUE_TODAY" : "OVERDUE_" + offset + "_DAYS";
                enqueue(invoice, stage, offset);
            }
        }
    }

    private void enqueue(Invoice invoice, String stage, int offset) {
        Long recipient = invoice.getCustomer().getUser().getId();
        String key = "PAYMENT_REMINDER:" + invoice.getId() + ":" + stage + ":CUSTOMER";
        java.math.BigDecimal collected = payments.sumCollectedForInvoice(invoice.getId(), Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED));
        java.math.BigDecimal refunded = refunds.sumForInvoice(invoice.getId(), RefundStatus.SUCCEEDED);
        collected = (collected == null ? java.math.BigDecimal.ZERO : collected).subtract(refunded == null ? java.math.BigDecimal.ZERO : refunded);
        String balance = (invoice.getTotalAmount() == null ? java.math.BigDecimal.ZERO : invoice.getTotalAmount()).subtract(collected == null ? java.math.BigDecimal.ZERO : collected).max(java.math.BigDecimal.ZERO).toPlainString();
        String message = offset < 0
                ? "Invoice " + invoice.getInvoiceNumber() + " is due on " + invoice.getDueDate() + ". Remaining balance: " + balance + " " + invoice.getCurrency() + "."
                : offset == 0
                ? "Invoice " + invoice.getInvoiceNumber() + " is due today. Remaining balance: " + balance + " " + invoice.getCurrency() + "."
                : "Invoice " + invoice.getInvoiceNumber() + " is " + offset + " days overdue. Remaining balance: " + balance + " " + invoice.getCurrency() + ".";
        communications.enqueuePaymentReminder(recipient,
                Map.of("invoiceNumber", String.valueOf(invoice.getInvoiceNumber()), "dueDate", String.valueOf(invoice.getDueDate()), "balance", balance, "currency", String.valueOf(invoice.getCurrency()), "stage", stage), key);
    }
}
