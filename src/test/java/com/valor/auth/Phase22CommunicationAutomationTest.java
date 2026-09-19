package com.valor.auth;

import com.fasterxml.jackson.databind.*;
import com.valor.communication.EmailEventService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phase22_automation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase22CommunicationAutomationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users; @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians; @Autowired JwtService jwt; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder;
    @Autowired EmailEventService automation;

    private User user(Role role, String phone) {
        User u = new User(); u.setRole(role); u.setEmail(UUID.randomUUID() + "@example.test"); u.setPhone(phone); u.setPasswordHash(encoder.encode("Pass12345"));
        return users.saveAndFlush(u);
    }
    private JsonNode call(MockHttpServletRequestBuilder req, User actor, Object body) throws Exception {
        req.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200)).andReturn().getResponse().getContentAsString();
        assertFalse(text.contains("passwordHash")); assertFalse(text.contains("otpHash")); assertFalse(text.contains("tokenHash"));
        return json.readTree(text).get("data");
    }

    @Test void serviceRequestAutomationCreatesCustomerAndAdminDeliveryRecordsAndHonorsPreferences() throws Exception {
        User admin = user(Role.ADMIN, "+919888220001");
        User customer = user(Role.CUSTOMER, "+919888220002");
        CustomerProfile profile = new CustomerProfile(); profile.setUser(customer); profile.setFullName("Automation Customer"); customers.saveAndFlush(profile);

        call(put("/api/v1/admin/communications/preferences/" + customer.getId()), admin,
                Map.of("emailEnabled", true, "smsEnabled", true, "whatsappEnabled", true, "inAppEnabled", true,
                        "serviceNotificationsEnabled", false));
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", profile.getId(), "buildingName", "Automation Tower")).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Automation Lift")).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), customer,
                Map.of("liftId", lift, "title", "Automation request", "description", "Issue", "serviceType", "BREAKDOWN"))
                .at("/request/id").asLong();

        assertEquals(0, db.queryForObject("""
                select count(*) from communication_messages m join communication_events e on e.id=m.event_id
                where e.event_type='SERVICE_REQUEST_CREATED' and m.recipient_user_id=?
                """, Integer.class, customer.getId()));
        assertTrue(db.queryForObject("""
                select count(*) from communication_messages m join communication_events e on e.id=m.event_id
                where e.event_type='ADMIN_CRITICAL_SERVICE_EVENT' and m.recipient_user_id=?
                """, Integer.class, admin.getId()) > 0);

        long technicianProfile = technician().getId();
        call(post("/api/v1/service-requests/" + request + "/assignments"), admin,
                Map.of("technicianProfileId", technicianProfile, "notes", "Assign"));
        assertTrue(db.queryForObject("""
                select count(*) from communication_messages m join communication_events e on e.id=m.event_id
                where e.event_type='TECHNICIAN_ASSIGNMENT'
                """, Integer.class) > 0);
    }

    @Test void automationIsIdempotentProviderIndependentAndRetryableOnFailure() {
        User customer = user(Role.CUSTOMER, "+919888220003");
        automation.paymentResult(customer.getId(), "Failure Customer", 7701L, "FORCE_PROVIDER_FAILURE");
        automation.paymentResult(customer.getId(), "Failure Customer", 7701L, "FORCE_PROVIDER_FAILURE");
        Long id = db.queryForObject("""
                select min(m.id) from communication_messages m join communication_events e on e.id=m.event_id
                where e.idempotency_key='payment-result:7701:FORCE_PROVIDER_FAILURE' and m.channel='WHATSAPP'
                """, Long.class);
        assertNotNull(id);
        assertEquals(3, db.queryForObject("""
                select count(*) from communication_messages m join communication_events e on e.id=m.event_id
                where e.idempotency_key='payment-result:7701:FORCE_PROVIDER_FAILURE'
                """, Integer.class));
        callProcess(id);
        assertEquals("FAILED", db.queryForObject("select status from communication_messages where id=?", String.class, id));
        assertEquals(1, db.queryForObject("select retry_count from communication_messages where id=?", Integer.class, id));
    }

    private TechnicianProfile technician() {
        TechnicianProfile tech = new TechnicianProfile(); tech.user = user(Role.TECHNICIAN, "+919888220004"); tech.employeeId = "T-" + UUID.randomUUID();
        return technicians.saveAndFlush(tech);
    }
    private void callProcess(Long id) {
        // Use the HTTP API so provider processing stays under the public communication contract.
        try {
            User admin = user(Role.ADMIN, "+919888220005");
            call(post("/api/v1/admin/communications/messages/" + id + "/process"), admin, null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
