package com.valor.commerce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class ServiceQuotationDtos {
    private ServiceQuotationDtos() {}

    record Create(
            @Size(max = 80) String pricingCode,
            @Size(max = 500) String description,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal subtotal,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal taxAmount,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @FutureOrPresent LocalDate validUntil,
            @Size(min = 1, max = 50) List<@Valid LineItemCreate> items
    ) implements CommerceDtos.StrictInput {}

    record LineItemCreate(
            @NotBlank @Size(max = 80) String pricingCode,
            @Size(max = 500) String description,
            @NotNull @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Max(1000) Integer quantity,
            @Digits(integer = 10, fraction = 2) BigDecimal unitAmount,
            @Pattern(regexp = "VISIT|LABOUR|PARTS") String coverageScope
    ) implements CommerceDtos.StrictInput {
        LineItemCreate(String pricingCode,String description,Integer quantity,BigDecimal unitAmount){this(pricingCode,description,quantity,unitAmount,null);}
    }

    record LineItemView(String pricingCode, String description, int quantity, BigDecimal unitAmount, BigDecimal lineAmount, String coverageScope) {}

    record Decision(@NotNull ServiceQuotationDecision decision) implements CommerceDtos.StrictInput {}
    record Revision(@NotNull @Valid Create quotation,@NotBlank @Size(max=1000) String reason) implements CommerceDtos.StrictInput {}
    record Milestone(@NotBlank @Size(max=120) String label,@NotBlank String dueCondition,LocalDate dueDate,
                     @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
    record Plan(@NotNull @Size(min=1,max=12) List<@Valid Milestone> milestones) {}

    enum ServiceQuotationDecision {
        ACCEPT,
        DECLINE
    }

    record View(
            Long id,
            String quotationNumber,
            Long serviceRequestId,
            String serviceName,
            String pricingCode,
            String description,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            ServiceQuotationStatus status,
            LocalDate validUntil,
            LocalDateTime respondedAt,
            Long invoiceId,
            List<LineItemView> items,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            int versionNumber,Long supersedesQuotationId,String revisionKind,String releaseStatus,String revisionReason,String approvedTermsHash
    ) {}
}
