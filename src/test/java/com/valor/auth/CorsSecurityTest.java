package com.valor.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test void environmentNameBindsToExistingApplicationProperty() {
        assertEquals("http://localhost:5173,http://127.0.0.1:5173", environment.getProperty("app.cors.allowed-origins"));
    }

    @Test void bothApprovedLoginPreflightsSucceedWithoutAuthentication() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            mvc.perform(options("/api/v1/auth/login/admin").header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
    }

    @Test void unapprovedOriginIsRejectedForPreflightAndActualRequests() throws Exception {
        mvc.perform(options("/api/v1/auth/login/admin").header("Origin", "http://unapproved.invalid")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/v1/health").header("Origin", "http://unapproved.invalid"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void approvedOriginReceivesCorsOnActualValidationResponse() throws Exception {
        mvc.perform(post("/api/v1/auth/login/admin").header("Origin", "http://localhost:5173")
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void protectedRoutePreflightDoesNotPermitUnauthenticatedActualAccess() throws Exception {
        mvc.perform(options("/api/v1/admin/dashboard/summary").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Headers", "authorization"));
        mvc.perform(get("/api/v1/admin/dashboard/summary").header("Origin", "http://localhost:5173"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/v1/admin/dashboard/summary"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }
}
