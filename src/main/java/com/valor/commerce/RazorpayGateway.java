package com.valor.commerce;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RazorpayGateway implements PaymentGateway {
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final boolean offlineTestMode;
    private final RestClient rest;
    private final ObjectMapper mapper;

    RazorpayGateway(@Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret,
            @Value("${razorpay.offline-test-mode:false}") boolean offlineTestMode,
            RestClient.Builder builder, ObjectMapper mapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.offlineTestMode = offlineTestMode;
        this.rest = builder.baseUrl("https://api.razorpay.com/v1").build();
        this.mapper = mapper;
    }

    public String keyId() { return keyId; }
    public boolean configured() { return !keyId.isBlank() && !keySecret.isBlank(); }

    public JsonNode createOrder(BigDecimal amount, String currency, String receipt) {
        requireConfigured();
        if (offlineTestMode) return mapper.valueToTree(Map.of("id", "order_test_" + receipt, "status", "created"));
        Map<String,Object> body = Map.of("amount", paise(amount), "currency", currency, "receipt", receipt, "payment_capture", 1);
        return post("/orders", body);
    }

    public JsonNode createRefund(String razorpayPaymentId, BigDecimal amount, String notes) {
        requireConfigured();
        if (offlineTestMode) return mapper.valueToTree(Map.of("id", "rfnd_test_" + razorpayPaymentId + "_" + paise(amount), "status", "pending"));
        Map<String,Object> body = notes == null || notes.isBlank()
                ? Map.of("amount", paise(amount))
                : Map.of("amount", paise(amount), "notes", Map.of("reason", notes));
        return post("/payments/" + razorpayPaymentId + "/refund", body);
    }

    public boolean validWebhookSignature(String payload, String signature) {
        if (webhookSecret.isBlank() || signature == null || signature.isBlank()) return false;
        return constantEquals(hmac(payload, webhookSecret), signature);
    }

    private JsonNode post(String path, Map<String,Object> body) {
        return rest.post().uri(path).headers(h -> h.setBasicAuth(keyId, keySecret, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
    }
    private long paise(BigDecimal rupees) { return rupees.movePointRight(2).setScale(0).longValueExact(); }
    private void requireConfigured() { if (!configured()) throw new CommerceException(503, "Razorpay test credentials are not configured"); }
    private String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append("%02x".formatted(b));
            return out.toString();
        } catch (Exception ex) {
            throw new CommerceException(500, "Webhook signature validation failed");
        }
    }
    private boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
