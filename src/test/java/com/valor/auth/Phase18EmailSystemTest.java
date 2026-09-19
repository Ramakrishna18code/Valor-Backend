package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:phase18_email;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "email.from=no-reply@valor.example",
        "email.from-name=Valor Ops",
        "email.reply-to=support@valor.example",
        "email.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase18EmailSystemTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users; @Autowired JwtService jwt;
    @Autowired JdbcTemplate db; @Autowired AuthService auth; @Autowired PasswordEncoder encoder;

    private User user(Role role, String email, String phone) {
        User u = new User(); u.setRole(role); u.setEmail(email); u.setPhone(phone); u.setPasswordHash(encoder.encode("oldPass123"));
        return users.saveAndFlush(u);
    }

    @Test void emailTemplateRendersHtmlTextSenderReplyToMockProviderAndIdempotency() throws Exception {
        User admin = user(Role.ADMIN, UUID.randomUUID() + "@admin.test", "+919999100000");
        User recipient = user(Role.CUSTOMER, "phase18@example.com", "+919999100001");
        db.update("insert into communication_templates(event_type,channel,template_key,subject,body,variables) values(?,?,?,?,?,?)",
                "PHASE18_EMAIL", "EMAIL", "phase18-email", "Hello {{name}}", "<p>Hi {{name}}</p>", "name");

        Map<String,Object> body = Map.of("eventType", "PHASE18_EMAIL", "recipientUserId", recipient.getId(),
                "channels", List.of("EMAIL"), "variables", Map.of("name", "Asha"), "idempotencyKey", "phase18-email-1");
        String first = mvc.perform(post("/api/v1/admin/communications/events")
                        .header("Authorization", "Bearer " + jwt.issue(admin))
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode row = json.readTree(first).get("data").get(0);

        mvc.perform(post("/api/v1/admin/communications/events")
                        .header("Authorization", "Bearer " + jwt.issue(admin))
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject("select count(*) from communication_messages where event_id=?", Integer.class, row.get("eventId").asLong()));
        assertEquals("<p>Hi Asha</p>", db.queryForObject("select html_body from communication_messages where id=?", String.class, row.get("id").asLong()));
        assertEquals("Hi Asha", db.queryForObject("select body from communication_messages where id=?", String.class, row.get("id").asLong()));
        assertEquals("no-reply@valor.example", db.queryForObject("select from_address from communication_messages where id=?", String.class, row.get("id").asLong()));
        assertEquals("Valor Ops", db.queryForObject("select from_name from communication_messages where id=?", String.class, row.get("id").asLong()));
        assertEquals("support@valor.example", db.queryForObject("select reply_to from communication_messages where id=?", String.class, row.get("id").asLong()));

        String sent = mvc.perform(post("/api/v1/admin/communications/messages/" + row.get("id").asLong() + "/process")
                        .header("Authorization", "Bearer " + jwt.issue(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("SENT", json.readTree(sent).at("/data/status").asText());
        assertEquals("MOCK_EMAIL", json.readTree(sent).at("/data/provider").asText());
    }

    @Test void setPasswordTokenIsHashedExpiringSingleUseAndDoesNotExposeRawPassword() throws Exception {
        User customer = user(Role.CUSTOMER, "onboard@example.com", "+919999100002");
        String token = auth.createOnboardingToken(customer);
        assertFalse(db.queryForObject("select token_hash from onboarding_tokens order by id desc limit 1", String.class).contains(token));
        mvc.perform(post("/api/v1/auth/set-password").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", token, "password", "NewPassword123"))))
                .andExpect(status().isOk());
        User updated = users.findById(customer.getId()).orElseThrow();
        assertTrue(encoder.matches("NewPassword123", updated.getPasswordHash()));
        mvc.perform(post("/api/v1/auth/set-password").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("token", token, "password", "AnotherPass123"))))
                .andExpect(status().isBadRequest());
    }
}
