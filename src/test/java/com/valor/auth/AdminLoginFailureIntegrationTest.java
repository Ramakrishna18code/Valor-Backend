package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminLoginFailureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void missingUserGetsTheGenericAuthenticationFailure() throws Exception {
        assertRejected("missing-" + UUID.randomUUID() + "@example.test", "test-only-password");
    }

    @Test
    void invalidPasswordGetsTheGenericAuthenticationFailure() throws Exception {
        String email = createUser("ADMIN");
        assertRejected(email, "incorrect-test-password");
    }

    @Test
    void customerRoleCannotUseAdministratorLogin() throws Exception {
        String email = createUser("CUSTOMER");
        assertRejected(email, "test-only-password");
    }

    private String createUser(String role) {
        String email = UUID.randomUUID() + "@example.test";
        database.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) "
                        + "values(?,?,?,true,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                email, passwordEncoder.encode("test-only-password"), role);
        return email;
    }

    private void assertRejected(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }
}
