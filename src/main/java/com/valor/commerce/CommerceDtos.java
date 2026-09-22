package com.valor.commerce;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

final class CommerceDtos {
    private CommerceDtos() {}
    interface StrictInput {
        @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported commerce field"); }
    }
    record PageView<T>(List<T> items, long totalElements, int totalPages, int page, int size) {}
    record PaymentCreate(@NotNull @DecimalMin("0.01") BigDecimal amount, @Size(min=3,max=3) String currency,
        PaymentPurpose purpose, Long customerProfileId, Long serviceRequestId, Long amcContractId, Long invoiceId,
        @Size(max=120) String providerReference) implements StrictInput {}
    record PaymentStatusUpdate(@NotNull PaymentStatus status, @Size(max=500) String failureReason,
        @Size(max=120) String providerReference) implements StrictInput {}
    record RazorpayCheckoutCreate(@NotNull @Positive Long invoiceId) implements StrictInput {}
    record RazorpayCheckoutView(Long paymentId, Long invoiceId, String razorpayKeyId, String razorpayOrderId,
        BigDecimal amount, String currency, String status) {}
    record ServiceInvoiceCreate(LocalDate dueDate) implements StrictInput {}
    record CashPaymentOtpRequest(@NotNull @Positive Long invoiceId) implements StrictInput {}
    record CashPaymentOtpVerify(@NotNull @Positive Long paymentId, @NotNull @Positive Long otpId,
        @NotBlank @Size(min=4,max=16) String otp) implements StrictInput {}
    record CashPaymentOtpView(Long id, Long paymentId, Long invoiceId, Long serviceRequestId, String status,
        LocalDateTime expiresAt, short attemptsRemaining, LocalDateTime lockedUntil, LocalDateTime verifiedAt, String code) {}
    record TechnicianServicePaymentView(Long serviceRequestId, InvoiceView invoice, PaymentView payment, CashPaymentOtpView cashOtp) {}
    record RefundCreate(@NotNull @DecimalMin("0.01") BigDecimal amount, @Size(max=500) String reason) implements StrictInput {}
    record RefundView(Long id, Long paymentId, BigDecimal amount, String currency, RefundStatus status,
        String razorpayRefundId, String gatewayStatus, String reason, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record ReconciliationIssue(Long paymentId, Long invoiceId, String type, String detail) {}
    record PaymentView(Long id, Long customerProfileId, Long serviceRequestId, Long amcContractId, Long invoiceId,
        BigDecimal amount, String currency, PaymentPurpose purpose, PaymentStatus status, String providerReference,
        String razorpayOrderId, String razorpayPaymentId, String razorpayPaymentLinkId, String gatewayStatus,
        LocalDateTime gatewaySyncedAt, String failureReason, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record InvoiceCreate(@NotNull @Positive Long customerProfileId, Long serviceRequestId, Long amcContractId,
        @NotBlank @Size(max=500) String description, @NotNull @DecimalMin("0.00") BigDecimal subtotal,
        @DecimalMin("0.00") BigDecimal taxAmount, @Size(min=3,max=3) String currency, LocalDate dueDate) implements StrictInput {}
    record InvoiceStatusUpdate(@NotNull InvoiceStatus status) implements StrictInput {}
    record InvoiceView(Long id, String invoiceNumber, Long customerProfileId, Long serviceRequestId, Long amcContractId,
        String description, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount, String currency,
        InvoiceStatus status, LocalDate issuedDate, LocalDate dueDate, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record SupportTicketCreate(Long serviceRequestId, SupportTicketCategory category, SupportTicketPriority priority,
        @NotBlank @Size(max=200) String subject, @NotBlank @Size(max=5000) String description,
        @Size(max=80) String preferredContact) implements StrictInput {}
    record SupportTicketStatusUpdate(@NotNull SupportTicketStatus status, @Size(max=2000) String adminNotes) implements StrictInput {}
    record SupportTicketView(Long id, String ticketReference, Long createdByUserId, String createdByRole, Long serviceRequestId,
        SupportTicketCategory category, SupportTicketPriority priority, SupportTicketStatus status, String subject,
        String description, String preferredContact, String adminNotes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record RenewalCreate(@NotNull LocalDate requestedStartDate, @NotNull LocalDate requestedEndDate,
        @Size(max=1000) String customerNotes) implements StrictInput {}
    record RenewalQuote(@NotNull @DecimalMin("0.01") BigDecimal quotedAmount, @Size(min=3,max=3) String currency,
        @Size(max=1000) String adminNotes, Long invoiceId, Long paymentId) implements StrictInput {}
    record RenewalStatusUpdate(@NotNull RenewalRequestStatus status, @Size(max=1000) String adminNotes,
        Long invoiceId, Long paymentId) implements StrictInput {}
    record RenewalView(Long id, Long amcContractId, Long customerProfileId, RenewalRequestStatus status,
        LocalDate requestedStartDate, LocalDate requestedEndDate, BigDecimal quotedAmount, String currency,
        Long invoiceId, Long paymentId, String customerNotes, String adminNotes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record AssetDocumentView(Long id, AssetDocumentOwnerType ownerType, Long ownerId, String originalFilename,
        String contentType, Long fileSize, Long uploadedByUserId, LocalDateTime createdAt) {}
}
