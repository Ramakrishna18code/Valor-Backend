package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:phase13b1_audit;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.asset-documents.local-root=target/test-phase13b1-asset-documents",
        "app.attachments.local-root=target/test-phase13b1-request-attachments",
        "razorpay.offline-test-mode=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase13B1AuditCoverageTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;

    record Fixture(User admin, User customer, User technician, long customerId, long technicianId,
            long buildingId, long liftId, long amcId, long requestId) {}

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private CustomerProfile customer() {
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user(Role.CUSTOMER));
        profile.setFullName("Audit customer");
        return customers.saveAndFlush(profile);
    }

    private TechnicianProfile technician() {
        TechnicianProfile profile = new TechnicianProfile();
        profile.user = user(Role.TECHNICIAN);
        return technicians.saveAndFlush(profile);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andReturn().getResponse().getContentAsString();
        assertSafe(text);
        return json.readTree(text).get("data");
    }

    private Fixture fixture() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        TechnicianProfile tech = technician();
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Audit Tower"), 200).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Audit Lift"), 200).get("id").asLong();
        long amc = call(post("/api/v1/amc-contracts"), admin, Map.of("liftId", lift, "plan", "Standard", "startDate", "2030-01-01", "endDate", "2030-12-31"), 200).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), owner.getUser(), Map.of("liftId", lift, "title", "Audit service", "description", "Issue", "serviceType", "BREAKDOWN"), 200)
                .at("/request/id").asLong();
        return new Fixture(admin, owner.getUser(), tech.getUser(), owner.getId(), tech.getId(), building, lift, amc, request);
    }

    @Test void customerBuildingLiftAndAmcMutationsCreateSafeAuditRows() throws Exception {
        User admin = user(Role.ADMIN);
        String email = UUID.randomUUID() + "@customer.test";
        long customer = call(post("/api/v1/admin/customers"), admin,
                Map.of("email", email, "password", "Secret123", "fullName", "Audit Managed Customer"), 200).get("id").asLong();
        call(put("/api/v1/admin/customers/" + customer), admin, Map.of("fullName", "Updated Audit Customer"), 200);
        call(post("/api/v1/admin/customers/" + customer + "/deactivate"), admin, Map.of(), 200);
        call(post("/api/v1/admin/customers/" + customer + "/reactivate"), admin, Map.of(), 200);
        assertAudit("CUSTOMER_CREATE", "CUSTOMER", customer, admin, "Created customer");
        assertAudit("CUSTOMER_UPDATE", "CUSTOMER", customer, admin, "Updated customer");
        assertAudit("CUSTOMER_DEACTIVATE", "CUSTOMER", customer, admin, "active=true");
        assertAudit("CUSTOMER_REACTIVATE", "CUSTOMER", customer, admin, "active=false");

        CustomerProfile owner = customer();
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "B1"), 200).get("id").asLong();
        call(put("/api/v1/buildings/" + building), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "B2"), 200);
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "L1"), 200).get("id").asLong();
        call(put("/api/v1/lifts/" + lift), admin, Map.of("buildingId", building, "name", "L2", "currentStatus", "MAINTENANCE"), 200);
        long amc = call(post("/api/v1/amc-contracts"), admin, Map.of("liftId", lift, "plan", "Basic", "startDate", "2030-01-01", "endDate", "2030-12-31"), 200).get("id").asLong();
        call(put("/api/v1/amc-contracts/" + amc + "/renew"), admin, Map.of("plan", "Premium", "startDate", "2031-01-01", "endDate", "2031-12-31"), 200);
        call(delete("/api/v1/lifts/" + lift), admin, null, 200);
        call(delete("/api/v1/buildings/" + building), admin, null, 200);
        assertAudit("BUILDING_CREATE", "BUILDING", building, admin, "customer=" + owner.getId());
        assertAudit("BUILDING_UPDATE", "BUILDING", building, admin, "Updated building");
        assertAudit("BUILDING_DEACTIVATE", "BUILDING", building, admin, "Deactivated building");
        assertAudit("LIFT_CREATE", "LIFT", lift, admin, "building=" + building);
        assertAudit("LIFT_UPDATE", "LIFT", lift, admin, "Updated lift");
        assertAudit("LIFT_DEACTIVATE", "LIFT", lift, admin, "Deactivated lift");
        assertAudit("AMC_CREATE", "AMC", amc, admin, "lift=" + lift);
        assertAudit("AMC_RENEW", "AMC", amc, admin, "Renewed AMC");
    }

    @Test void workflowVisitChangeRequestNotificationAndDocumentsCreateSafeAuditRows() throws Exception {
        Fixture f = fixture();
        long assignment = call(post("/api/v1/service-requests/" + f.requestId() + "/assignments"), f.admin(), Map.of("technicianProfileId", f.technicianId()), 200)
                .at("/activeAssignment/id").asLong();
        call(post("/api/v1/service-requests/" + f.requestId() + "/assignments/" + assignment + "/accept"), f.technician(), null, 200);
        call(post("/api/v1/service-requests/" + f.requestId() + "/status"), f.technician(), Map.of("toStatus", "ON_THE_WAY", "notes", "Travel"), 200);
        assertAudit("SERVICE_REQUEST_CREATE", "SERVICE_REQUEST", f.requestId(), f.customer(), "lift=" + f.liftId());
        assertAudit("SERVICE_REQUEST_ASSIGN", "SERVICE_REQUEST", f.requestId(), f.admin(), "technician=" + f.technicianId());
        assertAudit("SERVICE_REQUEST_STATUS", "SERVICE_REQUEST", f.requestId(), f.technician(), "ON_THE_WAY");

        long visit = call(post("/api/v1/admin/service-visits"), f.admin(), Map.of("serviceRequestId", f.requestId(), "technicianProfileId", f.technicianId(),
                "scheduledDate", "2030-02-01", "startTime", "10:00:00", "endTime", "11:00:00"), 200).get("id").asLong();
        call(put("/api/v1/admin/service-visits/" + visit), f.admin(), Map.of("technicianProfileId", f.technicianId(),
                "scheduledDate", "2030-02-02", "startTime", "12:00:00", "endTime", "13:00:00"), 200);
        long change = call(post("/api/v1/technician/me/visits/" + visit + "/reschedule-requests"), f.technician(),
                Map.of("reason", "Later", "requestedDate", "2030-02-03", "requestedStartTime", "14:00:00", "requestedEndTime", "15:00:00"), 200).get("id").asLong();
        call(post("/api/v1/admin/visit-change-requests/" + change + "/reject"), f.admin(), Map.of("reason", "No capacity"), 200);
        call(post("/api/v1/admin/service-visits/" + visit + "/cancel"), f.admin(), Map.of("reason", "Customer unavailable"), 200);
        assertAudit("SERVICE_VISIT_CREATE", "SERVICE_VISIT", visit, f.admin(), "request=" + f.requestId());
        assertAudit("SERVICE_VISIT_UPDATE", "SERVICE_VISIT", visit, f.admin(), "2030-02-02");
        assertAudit("VISIT_CHANGE_REQUEST_CREATE", "VISIT_CHANGE_REQUEST", change, f.technician(), "2030-02-03");
        assertAudit("VISIT_CHANGE_REQUEST_REJECT", "VISIT_CHANGE_REQUEST", change, f.admin(), "REJECTED");
        assertAudit("SERVICE_VISIT_CANCEL", "SERVICE_VISIT", visit, f.admin(), "CANCELLED");

        long notification = call(post("/api/v1/notifications"), f.admin(), Map.of("recipientUserId", f.customer().getId(), "title", "Audit", "message", "Ready"), 200).get("id").asLong();
        assertAudit("NOTIFICATION_CREATE", "NOTIFICATION", notification, f.admin(), "recipient=" + f.customer().getId());

        MockMultipartFile pdf = new MockMultipartFile("file", "manual.pdf", "application/pdf", "%PDF test".getBytes());
        long doc = json.readTree(mvc.perform(multipart("/api/v1/buildings/" + f.buildingId() + "/documents").file(pdf)
                .header("Authorization", "Bearer " + jwt.issue(f.admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/id").asLong();
        call(delete("/api/v1/buildings/" + f.buildingId() + "/documents/" + doc), f.admin(), null, 200);
        assertAudit("DOCUMENT_DELETE", "DOCUMENT", doc, f.admin(), "parent=" + f.buildingId());

        MockMultipartFile image = new MockMultipartFile("file", "issue.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        long attachment = json.readTree(mvc.perform(multipart("/api/v1/service-requests/" + f.requestId() + "/attachments").file(image)
                .header("Authorization", "Bearer " + jwt.issue(f.customer())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/id").asLong();
        call(delete("/api/v1/service-requests/" + f.requestId() + "/attachments/" + attachment), f.customer(), null, 200);
        assertAudit("DOCUMENT_DELETE", "SERVICE_REQUEST_ATTACHMENT", attachment, f.customer(), "request=" + f.requestId());
    }

    @Test void amcRenewalRequestAndStateChangesCreateSafeAuditRows() throws Exception {
        Fixture f = fixture();
        long renewal = call(post("/api/v1/customers/me/amc-contracts/" + f.amcId() + "/renewal-requests"), f.customer(),
                Map.of("requestedStartDate", "2031-01-01", "requestedEndDate", "2031-12-31", "customerNotes", "Renew"), 200).get("id").asLong();
        call(put("/api/v1/amc-renewal-requests/" + renewal + "/quote"), f.admin(), Map.of("quotedAmount", 5000, "currency", "INR"), 200);
        call(put("/api/v1/amc-renewal-requests/" + renewal + "/status"), f.admin(), Map.of("status", "CANCELLED"), 200);
        assertAudit("AMC_RENEWAL_REQUEST_CREATE", "AMC_RENEWAL_REQUEST", renewal, f.customer(), "amc=" + f.amcId());
        assertAudit("AMC_RENEWAL_QUOTE", "AMC_RENEWAL_REQUEST", renewal, f.admin(), "QUOTED");
        assertAudit("AMC_RENEWAL_STATUS", "AMC_RENEWAL_REQUEST", renewal, f.admin(), "CANCELLED");
    }

    private void assertAudit(String action, String entityType, long entityId, User actor, String expectedText) {
        Map<String, Object> row = db.queryForMap("""
                select actor_user_id, actor_role, action, entity_type, entity_id, coalesce(summary,'') || ' ' || coalesce(before_summary,'') || ' ' || coalesce(after_summary,'') as text
                from audit_logs where action=? and entity_type=? and entity_id=? order by id desc limit 1
                """, action, entityType, String.valueOf(entityId));
        assertEquals(actor.getId(), ((Number) row.get("ACTOR_USER_ID")).longValue());
        assertEquals(actor.getRole().name(), row.get("ACTOR_ROLE"));
        assertEquals(action, row.get("ACTION"));
        assertTrue(String.valueOf(row.get("TEXT")).contains(expectedText), action);
        assertSafe(String.valueOf(row));
    }

    private void assertSafe(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("secret123", "passwordhash", "tokenhash", "accesstoken", "refreshtoken", "otp", "cvv")) {
            assertFalse(lower.contains(forbidden), forbidden);
        }
    }
}
