package com.valor.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

interface PaymentGateway {
    String keyId();
    boolean configured();
    JsonNode createOrder(BigDecimal amount, String currency, String receipt);
    JsonNode lookupOrder(String razorpayOrderId);
    JsonNode lookupPayment(String razorpayPaymentId);
    JsonNode createRefund(String providerPaymentId, BigDecimal amount, String notes);
    JsonNode lookupRefund(String providerRefundId);
    boolean validWebhookSignature(String payload, String signature);
}
