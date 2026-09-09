package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RuntimeSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @Autowired UserDetailsService details;
    @Autowired UserRepo users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setRole(role);
        user.setPasswordHash(encoder.encode("Test-only-runtime-password"));
        return users.saveAndFlush(user);
    }

    @Test void healthIsPublicAndGeneric() throws Exception {
        String body = mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Healthy"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, json.readTree(body).get("data").size());
        assertSafe(body);
    }

    @Test void openApiIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openapi").exists());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    @Test void swaggerEntryAndUiArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test void protectedPathsAndInvalidJwtUse401Envelope() throws Exception {
        for (String path : new String[]{"/api/v1/me", "/api/v1/future", "/api/v1/auth/future"}) {
            assertEnvelope(mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString(), 401);
        }
        assertEnvelope(mvc.perform(get("/api/v1/me").header("Authorization", "Bearer invalid-test-value"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString(), 401);
        assertEnvelope(mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString(), 401);
    }

    @Test void customerCannotAccessAdminRoutes() throws Exception {
        String token = jwt.issue(user(Role.CUSTOMER));
        String body = mvc.perform(get("/api/v1/admin/future").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertEnvelope(body, 403);
        assertFalse(body.contains(token));
    }

    @Test void noFallbackUserDetailsManagerExists() {
        assertTrue(context.getBeansOfType(InMemoryUserDetailsManager.class).isEmpty());
        assertFalse(context.containsBean("inMemoryUserDetailsManager"));
        assertEquals(1, context.getBeansOfType(UserDetailsService.class).size());
        assertInstanceOf(CanonicalUserDetailsService.class, details);
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername("user"));
    }

    @Test void canonicalEmailLookupNormalizesAndMapsEveryRoleWithBcrypt() {
        for (Role role : Role.values()) {
            User user = user(role);
            var loaded = details.loadUserByUsername("  " + user.getEmail().toUpperCase(java.util.Locale.ROOT) + "  ");
            assertEquals(user.getId().toString(), loaded.getUsername());
            assertTrue(encoder.matches("Test-only-runtime-password", loaded.getPassword()));
            assertEquals(user.getPasswordHash(), loaded.getPassword());
            assertEquals(java.util.Set.of("ROLE_" + role), loaded.getAuthorities().stream()
                    .map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test void normalizedPhoneLookupIsCustomerOnly() {
        User customer = user(Role.CUSTOMER);
        customer.setPhone("+442079460123");
        users.saveAndFlush(customer);
        assertEquals(customer.getId().toString(), details.loadUserByUsername(" +44 (20) 7946-0123 ").getUsername());
        customer.setRole(Role.TECHNICIAN);
        users.saveAndFlush(customer);
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername(customer.getPhone()));
    }

    @Test void unknownInactiveLockedAndPasswordlessUsersAreRejectedGenerically() {
        User user = user(Role.CUSTOMER);
        assertRejected("missing@example.test");
        user.setActive(false);
        users.saveAndFlush(user);
        assertRejected(user.getEmail());
        user.setActive(true);
        user.setLocked(true);
        users.saveAndFlush(user);
        assertRejected(user.getEmail());
        user.setLocked(false);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        users.saveAndFlush(user);
        assertRejected(user.getEmail());
        user.setLockedUntil(null);
        user.setPasswordHash(null);
        users.saveAndFlush(user);
        assertRejected(user.getEmail());
    }

    private void assertRejected(String identity) {
        assertEquals("Authentication failed", assertThrows(UsernameNotFoundException.class,
                () -> details.loadUserByUsername(identity)).getMessage());
    }

    private void assertEnvelope(String body, int status) throws Exception {
        var response = json.readTree(body);
        assertFalse(response.get("success").asBoolean());
        assertEquals(status, response.get("status").asInt());
        assertTrue(response.get("data").isNull());
        assertTrue(response.hasNonNull("timestamp"));
        assertSafe(body);
    }

    private void assertSafe(String body) {
        for (String forbidden : new String[]{"password", "token", "otp", "hash", "stackTrace", "com.valor", "jdbc:", "environment"}) {
            assertFalse(body.contains(forbidden));
        }
    }
}
