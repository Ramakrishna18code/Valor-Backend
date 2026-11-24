package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dev_customer_login_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "dev.customer-test.enabled=true",
        "dev.customer-test.email=testcustomer@valor.local",
        "dev.customer-test.password=ValorTest@123"
})
class DevCustomerLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test
    void seededCustomerLogsInAndLoadsCustomerProfile() throws Exception {
        Integer count = db.queryForObject(
                "select count(*) from users u join customer_profiles p on p.user_id=u.id "
                        + "where u.email=? and u.role='CUSTOMER' and u.is_active=true "
                        + "and u.is_locked=false and p.status='ACTIVE' and p.is_active=true",
                Integer.class, "testcustomer@valor.local");
        assertEquals(1, count);

        String login = mvc.perform(post("/api/v1/auth/login/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identity", "testcustomer@valor.local",
                                "password", "ValorTest@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        String accessToken = json.readTree(login).at("/data/accessToken").asText();
        mvc.perform(get("/api/v1/customers/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("testcustomer@valor.local"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void seedingIsIdempotent() throws Exception {
        mvc.perform(post("/api/v1/auth/login/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identity", "testcustomer@valor.local",
                                "password", "ValorTest@123"))))
                .andExpect(status().isOk());

        Integer count = db.queryForObject(
                "select count(*) from users where email=?",
                Integer.class, "testcustomer@valor.local");
        assertEquals(1, count);
    }
}
