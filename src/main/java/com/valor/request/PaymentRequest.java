package com.valor.request;

import com.valor.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequest(
        @NotNull Long customerId,
        Long amcId,
        BigDecimal amount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String paymentMode,
        PaymentStatus status,
        LocalDateTime paymentDateTime,
        String invoiceNumber,
        String receiptNumber
) {}
