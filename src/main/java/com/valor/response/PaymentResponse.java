package com.valor.response;

import com.valor.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long customerId,
        Long amcId,
        String invoiceNumber,
        BigDecimal amount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String paymentMode,
        PaymentStatus status,
        LocalDateTime paymentDateTime,
        String receiptNumber
) {}
