package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:first_admin_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "first-admin.provisioning.enabled=true",
        "first-admin.provisioning.secret=operator-secret-that-is-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FirstAdminProvisioningTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired PasswordEncoder encoder;

    @Test
    void privatelyAuthorizedProvisioningCreatesOnlyTheInitialSuperAdmin() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/first-admin")
                        .header("X-Valor-First-Admin-Secret", "operator-secret-that-is-at-least-32-bytes-long")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "email", " Operator@Valor.test ",
                                "password", "operator-test-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("operator@valor.test"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("operator-test-password"));
        User admin = users.findByEmail("operator@valor.test").orElseThrow();
        assertEquals(Role.SUPER_ADMIN, admin.getRole());
        assertTrue(encoder.matches("operator-test-password", admin.getPasswordHash()));

        mvc.perform(post("/api/v1/auth/first-admin")
                        .header("X-Valor-First-Admin-Secret", "operator-secret-that-is-at-least-32-bytes-long")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "email", "second@valor.test",
                                "password", "another-test-password"))))
                .andExpect(status().isConflict());
    }

    @Test
    void missingOrIncorrectSecretCannotProvisionAnAdministrator() throws Exception {
        for (String secret : new String[] { null, "wrong-secret" }) {
            var request = post("/api/v1/auth/first-admin")
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of(
                            "email", "operator@valor.test",
                            "password", "operator-test-password")));
            if (secret != null) request.header("X-Valor-First-Admin-Secret", secret);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        assertEquals(0, users.countByRole(Role.ADMIN) + users.countByRole(Role.SUPER_ADMIN));
    }

    @Test
    void provisionedAdministratorUsesNormalLoginAndCannotBeReusedAsCustomer() throws Exception {
        provision();
        JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/login/admin")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "email", "operator@valor.test",
                                "password", "operator-test-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"))
                .andReturn().getResponse().getContentAsString());
        assertTrue(login.at("/data/accessToken").isTextual());

        mvc.perform(post("/api/v1/auth/login/customer")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "identity", "operator@valor.test",
                                "password", "operator-test-password"))))
                .andExpect(status().isBadRequest());
    }

    private void provision() throws Exception {
        mvc.perform(post("/api/v1/auth/first-admin")
                        .header("X-Valor-First-Admin-Secret", "operator-secret-that-is-at-least-32-bytes-long")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "email", "operator@valor.test",
                                "password", "operator-test-password"))))
                .andExpect(status().isOk());
    }
}
