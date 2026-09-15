package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-settings;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminSettingsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;

    User user(Role role) {
        User value = new User();
        value.setEmail(UUID.randomUUID() + "@example.test");
        value.setPasswordHash(encoder.encode("Secret123"));
        value.setRole(role);
        return users.saveAndFlush(value);
    }

    String token(Role role) { return jwt.issue(user(role)); }

    JsonNode data(String response) throws Exception { return json.readTree(response).get("data"); }

    Map<String, Object> valid() {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("companyName", "Valor Local");
        value.put("supportEmail", "support@valor.local");
        value.put("supportPhone", "+919810000000");
        value.put("timezone", "Asia/Kolkata");
        value.put("currency", "INR");
        value.put("dateFormat", "YYYY-MM-DD");
        value.put("defaultVisitDurationMinutes", 90);
        value.put("maintenanceReminderDays", 45);
        value.put("emergencyResponseTargetMinutes", 30);
        value.put("emailNotificationsEnabled", true);
        value.put("smsNotificationsEnabled", false);
        value.put("autoAssignRequestsEnabled", false);
        return value;
    }

    @Test void adminCanReadAndPersistSafeSettings() throws Exception {
        String body = mvc.perform(get("/api/v1/admin/settings").header("Authorization", "Bearer " + token(Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.companyName").value("Valor Lift Services"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("password"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("token"));

        mvc.perform(put("/api/v1/admin/settings").header("Authorization", "Bearer " + token(Role.SUPER_ADMIN))
                .contentType("application/json").content(json.writeValueAsString(valid())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.companyName").value("Valor Local"))
            .andExpect(jsonPath("$.data.defaultVisitDurationMinutes").value(90))
            .andExpect(jsonPath("$.data.emailNotificationsEnabled").value(true));

        mvc.perform(get("/api/v1/admin/settings").header("Authorization", "Bearer " + token(Role.ADMIN)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.companyName").value("Valor Local"));
    }

    @Test void settingsRejectInvalidAndUnknownFields() throws Exception {
        var invalid = new java.util.HashMap<>(valid());
        invalid.put("timezone", "Not/AZone");
        mvc.perform(put("/api/v1/admin/settings").header("Authorization", "Bearer " + token(Role.ADMIN))
                .contentType("application/json").content(json.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/admin/settings").header("Authorization", "Bearer " + token(Role.ADMIN))
                .contentType("application/json").content("""
                {"companyName":"Valor","timezone":"Asia/Kolkata","currency":"INR","dateFormat":"YYYY-MM-DD","defaultVisitDurationMinutes":60,"maintenanceReminderDays":30,"emergencyResponseTargetMinutes":60,"jwtSecret":"nope"}
                """))
            .andExpect(status().isBadRequest());
    }

    @Test void customerCannotReadOrUpdateSettings() throws Exception {
        User customer = user(Role.CUSTOMER);
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(customer);
        profile.setFullName("Customer");
        customers.saveAndFlush(profile);
        mvc.perform(get("/api/v1/admin/settings").header("Authorization", "Bearer " + jwt.issue(customer)))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/settings").header("Authorization", "Bearer " + jwt.issue(customer))
                .contentType("application/json").content(json.writeValueAsString(valid())))
            .andExpect(status().isForbidden());
    }
}
