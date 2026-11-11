package com.valor.commerce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

final class AdminFinanceDtos {
    private AdminFinanceDtos() {}

    record PageView<T>(List<T> items, long totalElements, int totalPages, int page, int size) {}

    record TransactionView(String id, String type, String status, BigDecimal amount, String currency,
            Long customerProfileId, Long invoiceId, String invoiceNumber, Long paymentId, Long refundId,
            Long serviceRequestId, Long amcContractId, String purpose, String providerReference,
            String razorpayOrderId, String razorpayPaymentId, String razorpayRefundId, String gatewayStatus,
            LocalDateTime gatewaySyncedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    record ReportView(String type, Map<String, Object> summary, List<Map<String, Object>> rows) {}

    record InvoicePaymentReference(Long id, String status, BigDecimal amount, String currency, String providerReference,
            String razorpayOrderId, String razorpayPaymentId, String gatewayStatus, LocalDateTime createdAt) {}
    record InvoiceRefundReference(Long id, Long paymentId, String status, BigDecimal amount, String currency,
            String razorpayRefundId, String gatewayStatus, String reason, LocalDateTime createdAt) {}
    record InvoiceReconciliationView(Long invoiceId, String invoiceNumber, Long customerProfileId,
            String description, String invoiceStatus, BigDecimal invoiceAmount, BigDecimal successfulPayments,
            BigDecimal confirmedRefunds, BigDecimal outstandingBalance, String currency, LocalDate issuedDate,
            List<InvoicePaymentReference> payments, List<InvoiceRefundReference> refunds, List<String> discrepancies) {}
}
