package com.valor.commerce;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RazorpayWebhookSecurityTest {
    private static final String PATH = "/api/v1/webhooks/razorpay";
    private static final String SECRET = "offline-test-webhook-secret";

    @Autowired MockMvc mvc;
    @Autowired RazorpayWebhookEventRepository events;

    @Test
    void missingAndIncorrectSignaturesCannotCreateAnEvent() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String payload = payload(eventId);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Razorpay signature"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Razorpay-Signature", "00".repeat(32)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Razorpay signature"));

        assertFalse(events.existsByEventId(eventId));
    }

    @Test
    void signatureForOriginalBytesCannotAuthorizeTamperedPayload() throws Exception {
        String originalId = "evt_" + UUID.randomUUID();
        String tamperedId = "evt_" + UUID.randomUUID();
        String original = payload(originalId);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload(tamperedId))
                        .header("X-Razorpay-Signature", sign(original)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Razorpay signature"));

        assertFalse(events.existsByEventId(originalId));
        assertFalse(events.existsByEventId(tamperedId));
    }

    @Test
    void signedWebhookNeedsNoJwtAndReplayCreatesOnlyOneEvent() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String payload = payload(eventId);
        long before = events.count();

        for (int delivery = 0; delivery < 2; delivery++) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload)
                            .header("X-Razorpay-Signature", sign(payload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        assertTrue(events.existsByEventId(eventId));
        assertEquals(before + 1, events.count());
    }

    @Test
    void webhookSignatureDoesNotGrantAccessToPaymentRoutes() throws Exception {
        String payload = "{}";

        mvc.perform(post("/api/v1/payments/razorpay/checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Razorpay-Signature", sign(payload)))
                .andExpect(status().isUnauthorized());
    }

    private static String payload(String eventId) {
        return "{\"id\":\"" + eventId + "\",\"event\":\"test.ignored\",\"payload\":{}}";
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
