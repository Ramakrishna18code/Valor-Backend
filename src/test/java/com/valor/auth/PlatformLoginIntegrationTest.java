package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CUSTOMER", "TECHNICIAN"})
    void loginMeRefreshLogoutAndCrossRoleAuthorization(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString();
        db.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) values(?,?,?,true,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", email, encoder.encode(password), role);
        String body = payload(role, email, password);
        String result = mvc.perform(post(loginPath(role)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value(role))
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(result).get("data");
        String access = data.get("accessToken").asText();
        String refresh = data.get("refreshToken").asText();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value(role));
        for (String other : new String[]{"ADMIN", "CUSTOMER", "TECHNICIAN"}) {
            if (other.equals(role)) continue;
            mvc.perform(post(loginPath(other)).contentType(MediaType.APPLICATION_JSON).content(payload(other, email, password)))
                    .andExpect(status().isBadRequest());
            String protectedPath = other.equals("ADMIN") ? "/api/v1/admin/dashboard/summary"
                    : other.equals("CUSTOMER") ? "/api/v1/customers/me" : "/api/v1/technician/me/dashboard";
            mvc.perform(get(protectedPath).header("Authorization", "Bearer " + access))
                    .andExpect(status().isForbidden());
        }
        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var next = json.readTree(rotated).get("data");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + next.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value(role));
        String logout = json.writeValueAsString(Map.of("refreshToken", next.get("refreshToken").asText()));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + next.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content(logout)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(logout))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CUSTOMER", "TECHNICIAN"})
    void wrongPasswordAndMissingUserAreRejected(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        db.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) values(?,?,?,true,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", email, encoder.encode(UUID.randomUUID().toString()), role);
        for (String identity : new String[]{email, UUID.randomUUID() + "@example.test"}) {
            mvc.perform(post(loginPath(role)).contentType(MediaType.APPLICATION_JSON).content(payload(role, identity, "incorrect-test-password")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Authentication failed"));
        }
    }

    private String loginPath(String role) { return "/api/v1/auth/login/" + role.toLowerCase(java.util.Locale.ROOT); }
    private String payload(String role, String email, String password) throws Exception {
        return json.writeValueAsString(Map.of(role.equals("CUSTOMER") ? "identity" : "email", email, "password", password));
    }
}
