package com.valor.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

interface PaymentGateway {
    String keyId();
    boolean configured();
    JsonNode createOrder(BigDecimal amount, String currency, String receipt);
    JsonNode lookupOrder(String razorpayOrderId);
    JsonNode listOrders(String receipt, long from, long to, int count, int skip);
    JsonNode lookupPayment(String razorpayPaymentId);
    JsonNode createRefund(String providerPaymentId, BigDecimal amount, String notes);
    default JsonNode createRefund(String providerPaymentId, BigDecimal amount, String notes, String idempotencyKey) {
        return createRefund(providerPaymentId, amount, notes);
    }
    JsonNode lookupRefund(String providerRefundId);
    default JsonNode listRefunds(String providerPaymentId) { return null; }
    boolean validWebhookSignature(String payload, String signature);
}
