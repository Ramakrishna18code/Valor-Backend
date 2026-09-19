package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phase19_21;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase19To21CommunicationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users; @Autowired CustomerRepo customers;
    @Autowired OtpRepo otps; @Autowired JwtService jwt; @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate db;

    private User user(Role role, String email, String phone) {
        User u = new User(); u.setRole(role); u.setEmail(email); u.setPhone(phone); u.setPasswordHash(encoder.encode("Pass12345"));
        users.saveAndFlush(u);
        if (role == Role.CUSTOMER) {
            CustomerProfile p = new CustomerProfile(); p.setUser(u); p.setFullName("Test Customer"); customers.saveAndFlush(p);
        }
        return u;
    }
    private JsonNode call(MockHttpServletRequestBuilder req, User actor, Object body, int status) throws Exception {
        if (actor != null) req.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        assertFalse(text.contains("MSG91_AUTH_KEY")); assertFalse(text.contains("authKey=")); assertFalse(text.contains("otpHash"));
        return json.readTree(text).get("data");
    }

    @Test void otpSendVerifyResendCooldownProviderFailureAndMsg91MissingConfigWork() throws Exception {
        User customer = user(Role.CUSTOMER, UUID.randomUUID() + "@customer.test", "+919888120001");
        JsonNode sent = call(post("/api/v1/auth/otp/send"), null, Map.of("phone", customer.getPhone()), 200);
        call(post("/api/v1/auth/otp/resend"), null, Map.of("phone", customer.getPhone(), "requestId", sent.get("requestId").asLong()), 400);
        OtpVerification row = otps.findById(sent.get("requestId").asLong()).orElseThrow();
        row.lastSentAt = LocalDateTime.now().minusSeconds(20); otps.saveAndFlush(row);
        JsonNode resent = call(post("/api/v1/auth/otp/resend"), null, Map.of("phone", customer.getPhone(), "requestId", sent.get("requestId").asLong()), 200);
        assertEquals(sent.get("requestId").asLong(), resent.get("requestId").asLong());
        call(post("/api/v1/auth/otp/verify"), null, Map.of("phone", customer.getPhone(), "requestId", sent.get("requestId").asLong(), "otp", resent.get("otp").asText()), 200);

        call(post("/api/v1/auth/otp/send"), null, Map.of("phone", "+919888120000"), 400);
        Msg91OtpProvider msg91 = new Msg91OtpProvider(new Msg91OtpProperties("", "", "", 300, "msg91", false));
        OtpProviderResult result = msg91.send(new OtpProviderRequest("+919888120001", "123456", 1L, "test"));
        assertFalse(result.success());
        assertEquals("MSG91_OTP", result.provider());
        assertTrue(result.failureReason().contains("configuration missing"));
    }

    @Test void whatsappMockDeliveryRetryAndCategoryPreferencesUseExistingCommunicationModel() throws Exception {
        User admin = user(Role.ADMIN, UUID.randomUUID() + "@admin.test", "+919888130001");
        User recipient = user(Role.CUSTOMER, "pref-" + UUID.randomUUID() + "@example.test", "+919888130002");

        call(put("/api/v1/admin/communications/preferences/" + recipient.getId()), admin,
                Map.of("emailEnabled", true, "smsEnabled", true, "whatsappEnabled", true, "inAppEnabled", true,
                        "serviceNotificationsEnabled", false, "billingNotificationsEnabled", true, "otpSmsEnabled", true, "otpWhatsappEnabled", false), 200);

        JsonNode suppressed = call(post("/api/v1/admin/communications/events"), admin,
                Map.of("eventType", "SERVICE_REQUEST_STATUS_CHANGED", "recipientUserId", recipient.getId(),
                        "channels", List.of("SMS", "WHATSAPP"), "variables", Map.of("serviceId", "SR-1", "status", "ASSIGNED"),
                        "idempotencyKey", "service-pref-suppressed"), 200);
        assertEquals(0, suppressed.size());

        JsonNode billing = call(post("/api/v1/admin/communications/events"), admin,
                Map.of("eventType", "PAYMENT_RESULT", "recipientUserId", recipient.getId(),
                        "channels", List.of("WHATSAPP"), "variables", Map.of("paymentId", "55", "status", "SUCCEEDED"),
                        "idempotencyKey", "payment-whatsapp-1"), 200);
        assertEquals("WHATSAPP", billing.get(0).get("channel").asText());
        JsonNode sent = call(post("/api/v1/admin/communications/messages/" + billing.get(0).get("id").asLong() + "/process"), admin, null, 200);
        assertEquals("SENT", sent.get("status").asText());
        assertEquals("MOCK_WHATSAPP", sent.get("provider").asText());

        JsonNode failedRows = call(post("/api/v1/admin/communications/events"), admin,
                Map.of("eventType", "PAYMENT_RESULT", "recipientUserId", recipient.getId(),
                        "channels", List.of("WHATSAPP"), "variables", Map.of("paymentId", "56", "status", "FORCE_PROVIDER_FAILURE"),
                        "idempotencyKey", "payment-whatsapp-failure"), 200);
        JsonNode failed = call(post("/api/v1/admin/communications/messages/" + failedRows.get(0).get("id").asLong() + "/process"), admin, null, 200);
        assertEquals("FAILED", failed.get("status").asText());
        assertEquals(1, failed.get("retryCount").asInt());
        assertTrue(failed.get("failureReason").asText().contains("token=***"));
    }
}
