package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin-customers;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminCustomerManagementTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users; @Autowired CustomerRepo customers;
    @Autowired JwtService jwt; @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate db;

    User user(Role role) { var u=new User();u.setRole(role);u.setEmail(UUID.randomUUID()+"@example.test");u.setPasswordHash(encoder.encode("Secret123"));return users.saveAndFlush(u); }
    CustomerProfile customer() { var p=new CustomerProfile();p.user=user(Role.CUSTOMER);p.fullName="Existing Customer";p.companyName="Existing Co";p.address="Old address";return customers.saveAndFlush(p); }
    String token(Role role) { return jwt.issue(user(role)); }
    JsonNode data(String body) throws Exception { return json.readTree(body).get("data"); }

    @Test void adminCreatesCustomerWithHashedPasswordAndSafeDetail() throws Exception {
        String email=UUID.randomUUID()+"@customer.test";
        String body=mvc.perform(post("/api/v1/admin/customers").header("Authorization","Bearer "+token(Role.ADMIN))
                .contentType("application/json").content("""
                {"email":" %s ","phone":" +91 98100 00011 ","password":"Secret123","fullName":" Ada Customer ","alternatePhone":"+919810000012","companyName":"Ada Towers","address":"MG Road"}
                """.formatted(email.toUpperCase())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
            .andExpect(jsonPath("$.data.phone").value("+919810000011"))
            .andExpect(jsonPath("$.data.fullName").value("Ada Customer"))
            .andExpect(jsonPath("$.data.active").value(true))
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"));assertFalse(body.contains("Secret123"));assertFalse(body.contains("refreshToken"));
        String hash=db.queryForObject("select password_hash from users where email=?", String.class, email.toLowerCase());
        assertNotEquals("Secret123", hash);assertTrue(encoder.matches("Secret123", hash));
    }

    @Test void duplicateIdentityRejectedAndCustomerCannotUseAdminRoutes() throws Exception {
        var c=customer();
        mvc.perform(post("/api/v1/admin/customers").header("Authorization","Bearer "+token(Role.SUPER_ADMIN))
                .contentType("application/json").content("{\"email\":\""+c.user.getEmail()+"\",\"password\":\"Secret123\",\"fullName\":\"Dup\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Identity already exists"));
        mvc.perform(get("/api/v1/admin/customers/"+c.id).header("Authorization","Bearer "+jwt.issue(c.user)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void adminUpdatesOnlyProfileFieldsAndRejectsIdentityChanges() throws Exception {
        var c=customer();
        mvc.perform(put("/api/v1/admin/customers/"+c.id).header("Authorization","Bearer "+token(Role.ADMIN))
                .contentType("application/json").content("""
                {"fullName":"Updated Name","alternatePhone":"+919810000099","companyName":"Updated Co","address":"New address"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Updated Name"))
            .andExpect(jsonPath("$.data.companyName").value("Updated Co"));
        assertEquals(c.user.getEmail(), users.findById(c.user.getId()).orElseThrow().getEmail());
        mvc.perform(put("/api/v1/admin/customers/"+c.id).header("Authorization","Bearer "+token(Role.ADMIN))
                .contentType("application/json").content("{\"fullName\":\"Bad\",\"email\":\"new@example.test\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test void deactivateAndReactivateAreReversibleAndKeepRelationships() throws Exception {
        var c=customer();
        db.update("insert into buildings(customer_id,building_name,status,is_active) values(?,?,?,true)", c.id, "Retained Building", "ACTIVE");
        Long buildingId=db.queryForObject("select id from buildings where customer_id=?", Long.class, c.id);
        db.update("insert into lifts(building_id,name,current_status,is_active) values(?,?,?,true)", buildingId, "Retained Lift", "ACTIVE");
        Long liftId=db.queryForObject("select id from lifts where building_id=?", Long.class, buildingId);
        db.update("insert into service_requests(customer_id,lift_id,service_id,title,description,priority,status,service_type) values(?,?,?,?,?,?,?,?)",
            c.id, liftId, "SR-"+UUID.randomUUID(), "Retained request", "Description", "HIGH", "PENDING", "BREAKDOWN");
        mvc.perform(post("/api/v1/admin/customers/"+c.id+"/deactivate").header("Authorization","Bearer "+token(Role.ADMIN)).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false)).andExpect(jsonPath("$.data.status").value("INACTIVE"))
            .andExpect(jsonPath("$.data.buildingCount").value(1)).andExpect(jsonPath("$.data.liftCount").value(1)).andExpect(jsonPath("$.data.serviceRequestCount").value(1));
        assertFalse(users.findById(c.user.getId()).orElseThrow().isActive());
        mvc.perform(post("/api/v1/auth/login/customer").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("identity", c.user.getEmail(), "password", "Secret123"))))
            .andExpect(status().isBadRequest());
        assertEquals(1, db.queryForObject("select count(*) from service_requests where customer_id=?", Integer.class, c.id));
        mvc.perform(post("/api/v1/admin/customers/"+c.id+"/reactivate").header("Authorization","Bearer "+token(Role.SUPER_ADMIN)).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true)).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/auth/login/customer").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("identity", c.user.getEmail(), "password", "Secret123"))))
            .andExpect(status().isOk());
    }
}
