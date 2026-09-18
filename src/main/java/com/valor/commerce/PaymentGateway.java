package com.valor.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

interface PaymentGateway {
    String keyId();
    boolean configured();
    JsonNode createOrder(BigDecimal amount, String currency, String receipt);
    JsonNode createRefund(String providerPaymentId, BigDecimal amount, String notes);
    boolean validWebhookSignature(String payload, String signature);
}
