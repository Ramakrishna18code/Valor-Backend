package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phase17_comm;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase17CommunicationFoundationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users; @Autowired JwtService jwt; @Autowired JdbcTemplate db;

    private User user(Role role, String email, String phone) {
        User u = new User(); u.setRole(role); u.setEmail(email); u.setPhone(phone); return users.saveAndFlush(u);
    }
    private JsonNode call(MockHttpServletRequestBuilder req, User actor, Object body, int status) throws Exception {
        req.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(req).andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status)).andReturn().getResponse().getContentAsString();
        assertFalse(text.contains("hidden")); assertFalse(text.contains("1234567890"));
        return json.readTree(text).get("data");
    }
    private JsonNode byChannel(JsonNode rows, String channel) {
        for (JsonNode row : rows) if (channel.equals(row.get("channel").asText())) return row;
        fail("Missing communication channel " + channel);
        return null;
    }

    @Test void eventTemplatePreferencesIdempotencyDeliveryFailureRetryAndMaskedRecipientWork() throws Exception {
        User admin = user(Role.ADMIN, UUID.randomUUID() + "@admin.test", "+919999000000");
        User recipient = user(Role.CUSTOMER, "geeta@example.com", "+919876543210");
        db.update("insert into communication_templates(event_type,channel,template_key,subject,body,variables) values(?,?,?,?,?,?)",
                "SERVICE_REQUEST_CREATED", "EMAIL", "sr-created-email", "Request {{serviceId}}", "Hello {{name}}", "serviceId,name");
        db.update("insert into communication_templates(event_type,channel,template_key,subject,body,variables) values(?,?,?,?,?,?)",
                "OTP_REQUESTED", "SMS", "otp-requested-sms", "OTP requested", "{{failureToken}}", "failureToken");

        var event = Map.of("eventType", "SERVICE_REQUEST_CREATED", "recipientUserId", recipient.getId(),
                "channels", List.of("EMAIL", "SMS", "IN_APP"), "variables", Map.of("serviceId", "SR-1", "name", "Geeta"),
                "idempotencyKey", "sr-1-created");
        JsonNode first = call(post("/api/v1/admin/communications/events"), admin, event, 200);
        JsonNode second = call(post("/api/v1/admin/communications/events"), admin, event, 200);
        assertEquals(3, first.size()); assertEquals(first.get(0).get("eventId").asLong(), second.get(0).get("eventId").asLong());
        assertEquals(1, db.queryForObject("select count(*) from communication_events where idempotency_key='sr-1-created'", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from communication_messages", Integer.class));
        JsonNode emailRow = byChannel(first, "EMAIL");
        assertEquals("g***@example.com", emailRow.get("recipientMasked").asText());

        JsonNode sent = call(post("/api/v1/admin/communications/messages/" + emailRow.get("id").asLong() + "/process"), admin, null, 200);
        assertEquals("SENT", sent.get("status").asText());
        assertEquals("MOCK_EMAIL", sent.get("provider").asText());

        call(put("/api/v1/admin/communications/preferences/" + recipient.getId()), admin,
                Map.of("emailEnabled", true, "smsEnabled", false, "whatsappEnabled", true, "inAppEnabled", true), 200);
        JsonNode pref = call(get("/api/v1/admin/communications/preferences/" + recipient.getId()), admin, null, 200);
        assertFalse(pref.get("smsEnabled").asBoolean());
        var smsSuppressed = Map.of("eventType", "OTP_REQUESTED", "recipientUserId", recipient.getId(),
                "channels", List.of("SMS"), "variables", Map.of("failureToken", "FORCE_PROVIDER_FAILURE"), "idempotencyKey", "otp-suppressed");
        assertEquals(0, call(post("/api/v1/admin/communications/events"), admin, smsSuppressed, 200).size());

        call(put("/api/v1/admin/communications/preferences/" + recipient.getId()), admin,
                Map.of("emailEnabled", true, "smsEnabled", true, "whatsappEnabled", true, "inAppEnabled", true), 200);
        JsonNode failedRows = call(post("/api/v1/admin/communications/events"), admin,
                Map.of("eventType", "OTP_REQUESTED", "recipientUserId", recipient.getId(), "channels", List.of("SMS"),
                        "variables", Map.of("failureToken", "FORCE_PROVIDER_FAILURE"), "idempotencyKey", "otp-failure"), 200);
        JsonNode failed = call(post("/api/v1/admin/communications/messages/" + failedRows.get(0).get("id").asLong() + "/process"), admin, null, 200);
        assertEquals("FAILED", failed.get("status").asText());
        assertTrue(failed.get("failureReason").asText().contains("token=***"));
        assertEquals(1, failed.get("retryCount").asInt());

        JsonNode page = call(get("/api/v1/admin/communications/messages").param("status", "FAILED"), admin, null, 200);
        assertTrue(page.get("totalElements").asInt() >= 1);
        assertEquals(0, call(post("/api/v1/admin/communications/retries/process").param("limit", "10"), admin, null, 200).asInt());
    }
}
