package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:installation_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstallationRequestTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JdbcTemplate db;
    @Autowired JwtService jwt;

    private User user(Role role) { User row = new User(); row.setEmail(UUID.randomUUID() + "@example.test"); row.setRole(role); return users.saveAndFlush(row); }
    private User customer() { User row = user(Role.CUSTOMER); CustomerProfile profile = new CustomerProfile(); profile.setUser(row); profile.setFullName("Installation customer"); customers.saveAndFlush(profile); return row; }
    private TechnicianProfile technician() { TechnicianProfile profile = new TechnicianProfile(); profile.user = user(Role.TECHNICIAN); return technicians.saveAndFlush(profile); }
    private JsonNode call(String method, String path, User actor, Object body, int expected) throws Exception {
        var request = switch (method) { case "GET" -> get(path); case "POST" -> post(path); case "PUT" -> put(path); default -> throw new IllegalArgumentException(); };
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        JsonNode envelope = json.readTree(response); assertEquals(expected < 400, envelope.get("success").asBoolean()); assertEquals(expected, envelope.get("status").asInt()); return envelope.get("data");
    }

    @Test void customerWithoutLiftCanCreateAndReadDraft() throws Exception {
        User owner = customer();
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of("requirements", "Passenger lift for a new property", "siteContactName", "Site contact", "siteContactPhone", "+919810000001", "clientRequestKey", "draft-1"), 200);
        assertEquals("DRAFT", draft.get("status").asText());
        assertNull(draft.get("buildingId").isNull() ? null : draft.get("buildingId"));
        call("GET", "/api/v1/customers/me/installations/" + draft.get("id").asLong(), owner, null, 200);
    }

    @Test void structuredSiteAddressRoundTripsThroughCreateReadAndUpdate() throws Exception {
        User owner = customer();
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of(
                "requirements", "New property installation", "siteContactName", "Site contact",
                "siteContactPhone", "+919810000010", "siteAddressLine1", "12 First Street",
                "siteLocality", "Indiranagar", "siteCity", "Bengaluru", "siteState", "Karnataka",
                "sitePostalCode", "560038", "siteCountry", "India",
                "sitePropertyDescription", "Corner plot, north entrance"), 200);
        assertEquals("12 First Street", draft.get("siteAddressLine1").asText());
        assertEquals("Bengaluru", call("GET", "/api/v1/customers/me/installations/" + draft.get("id").asLong(), owner, null, 200).get("siteCity").asText());
        JsonNode updated = call("PUT", "/api/v1/customers/me/installations/" + draft.get("id").asLong(), owner, Map.of(
                "version", draft.get("version").asLong(), "requirements", "New property installation",
                "siteContactName", "Updated contact", "siteContactPhone", "+919810000011",
                "siteAddressLine1", "14 Second Street", "siteCity", "Mysuru", "siteState", "Karnataka",
                "sitePostalCode", "570001", "siteCountry", "India"), 200);
        assertEquals("14 Second Street", updated.get("siteAddressLine1").asText());
        assertEquals("Mysuru", updated.get("siteCity").asText());
    }

    @Test void thirdPartyAuthorizationIsPendingUntilAdminReviewAndSubmissionIsIdempotent() throws Exception {
        User owner = customer(); User admin = user(Role.ADMIN);
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of("requirements", "Install at an authorized property", "siteContactName", "Property contact", "siteContactPhone", "+919810000002", "authorization", Map.of("ownerName", "Property owner", "relationshipType", "Representative")), 200);
        long id = draft.get("id").asLong(); long version = draft.get("version").asLong();
        call("POST", "/api/v1/customers/me/installations/" + id + "/submit", owner, Map.of("submissionKey", "submit-1", "version", version), 409);
        JsonNode auth = draft.get("authorization");
        call("POST", "/api/v1/admin/installations/authorizations/" + auth.get("id").asLong() + "/review", admin, Map.of("status", "APPROVED"), 200);
        JsonNode submitted = call("POST", "/api/v1/customers/me/installations/" + id + "/submit", owner, Map.of("submissionKey", "submit-1", "version", version), 200);
        assertEquals("SUBMITTED", submitted.get("status").asText());
        JsonNode retry = call("POST", "/api/v1/customers/me/installations/" + id + "/submit", owner, Map.of("submissionKey", "submit-1", "version", version), 200);
        assertEquals(id, retry.get("id").asLong());
    }

    @Test void customerCannotReadAnotherCustomersInstallation() throws Exception {
        User owner = customer(); User other = customer();
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of("requirements", "Private installation", "siteContactName", "Contact", "siteContactPhone", "+919810000003"), 200);
        call("GET", "/api/v1/customers/me/installations/" + draft.get("id").asLong(), other, null, 403);
    }

    @Test void proposalRevisionPreservesDeclineAndGatesConstruction() throws Exception {
        User owner = customer(); User other = customer(); User admin = user(Role.ADMIN);
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of("requirements", "Passenger lift", "siteContactName", "Contact", "siteContactPhone", "+919810000030", "authorization", Map.of("ownerName", "Owner", "relationshipType", "Representative")), 200);
        long id = draft.get("id").asLong();
        call("POST", "/api/v1/admin/installations/authorizations/" + draft.at("/authorization/id").asLong() + "/review", admin, Map.of("status", "APPROVED"), 200);
        JsonNode submitted = call("POST", "/api/v1/customers/me/installations/" + id + "/submit", owner, Map.of("submissionKey", "proposal-submit", "version", draft.get("version").asLong()), 200);
        JsonNode reviewed = call("POST", "/api/v1/admin/installations/" + id + "/requirements", admin, Map.of("requirements", "Admin confirmed requirements", "version", submitted.get("version").asLong()), 200);

        JsonNode first = call("POST", "/api/v1/admin/installations/" + id + "/proposals", admin, Map.of("amount", 100000, "currency", "INR", "scope", "Initial scope"), 200);
        first = call("POST", "/api/v1/admin/installations/proposals/" + first.get("id").asLong() + "/present?version=" + first.get("version").asLong(), admin, null, 200);
        long firstId = first.get("id").asLong(); long firstVersion = first.get("version").asLong();
        JsonNode declined = call("POST", "/api/v1/customers/me/installations/proposals/" + firstId + "/decision", owner, Map.of("accepted", false, "notes", "Please revise", "version", firstVersion), 200);
        assertEquals("DECLINED", declined.get("status").asText());
        assertEquals("PROPOSAL_DECLINED", call("GET", "/api/v1/customers/me/installations/" + id, owner, null, 200).get("status").asText());
        call("POST", "/api/v1/admin/installations/" + id + "/start?version=" + submitted.get("version").asLong(), admin, null, 409);

        JsonNode revision = call("POST", "/api/v1/admin/installations/" + id + "/proposals", admin, Map.of("amount", 110000, "currency", "INR", "scope", "Revised scope"), 200);
        assertTrue(revision.get("version").asLong() > firstVersion);
        revision = call("POST", "/api/v1/admin/installations/proposals/" + revision.get("id").asLong() + "/present?version=" + revision.get("version").asLong(), admin, null, 200);
        JsonNode adminHistory = call("GET", "/api/v1/admin/installations/" + id + "/proposals", admin, null, 200);
        assertEquals(2, adminHistory.size());
        JsonNode old = adminHistory.findValue("id").asLong() == firstId ? adminHistory.get(0) : adminHistory.get(1);
        assertEquals("DECLINED", old.get("status").asText()); assertEquals("Please revise", old.get("decisionNotes").asText());
        call("POST", "/api/v1/customers/me/installations/proposals/" + firstId + "/decision", owner, Map.of("accepted", true, "version", firstVersion), 409);
        call("POST", "/api/v1/customers/me/installations/proposals/" + revision.get("id").asLong() + "/decision", other, Map.of("accepted", true, "version", revision.get("version").asLong()), 403);
        JsonNode accepted = call("POST", "/api/v1/customers/me/installations/proposals/" + revision.get("id").asLong() + "/decision", owner, Map.of("accepted", true, "version", revision.get("version").asLong()), 200);
        assertEquals("ACCEPTED", accepted.get("status").asText());
        JsonNode current = call("GET", "/api/v1/customers/me/installations/" + id + "/proposal", owner, null, 200);
        assertEquals(revision.get("id").asLong(), current.get("id").asLong());
        JsonNode request = call("GET", "/api/v1/customers/me/installations/" + id, owner, null, 200);
        call("POST", "/api/v1/admin/installations/" + id + "/start?version=" + request.get("version").asLong(), admin, null, 200);
        call("POST", "/api/v1/customers/me/installations/proposals/" + revision.get("id").asLong() + "/decision", owner, Map.of("accepted", false, "version", revision.get("version").asLong()), 409);
        call("GET", "/api/v1/customers/me/installations/" + id + "/proposals", other, null, 403);
    }

    @Test void inspectionCanBeRequestedScheduledAssignedSubmittedAndReviewedWithoutCreatingAssets() throws Exception {
        User owner = customer(); User admin = user(Role.ADMIN); TechnicianProfile inspector = technician();
        JsonNode draft = call("POST", "/api/v1/customers/me/installations", owner, Map.of("requirements", "Inspection required", "siteContactName", "Contact", "siteContactPhone", "+919810000020", "siteAddressLine1", "44 Inspection Road", "siteCity", "Bengaluru", "siteState", "Karnataka", "sitePostalCode", "560001", "authorization", Map.of("ownerName", "Owner", "relationshipType", "Representative")), 200);
        long id = draft.get("id").asLong(); long authId = draft.at("/authorization/id").asLong();
        call("POST", "/api/v1/admin/installations/authorizations/" + authId + "/review", admin, Map.of("status", "APPROVED"), 200);
        JsonNode submitted = call("POST", "/api/v1/customers/me/installations/" + id + "/submit", owner, Map.of("submissionKey", "inspection-submit", "version", draft.get("version").asLong()), 200);
        JsonNode inspection = call("POST", "/api/v1/admin/installations/" + id + "/inspection", admin,
                Map.of("scheduledDate", "2030-01-02", "technicianProfileId", inspector.getId()), 200);
        inspection = call("PUT", "/api/v1/admin/installations/inspections/" + inspection.get("id").asLong() + "/schedule", admin, Map.of("scheduledAt", "2030-01-02T10:00:00", "scheduledTimezone", "Asia/Kolkata", "version", inspection.get("version").asLong()), 200);
        inspection = call("PUT", "/api/v1/admin/installations/inspections/" + inspection.get("id").asLong() + "/assign", admin, Map.of("technicianProfileId", inspector.getId(), "version", inspection.get("version").asLong()), 200);
        long inspectionId = inspection.get("id").asLong();
        assertEquals(inspectionId, call("GET", "/api/v1/admin/installations/" + id + "/inspection", admin, null, 200).get("id").asLong());
        JsonNode adminQueue = call("GET", "/api/v1/admin/installations/inspections?status=ASSIGNED&page=0&size=20", admin, null, 200); assertEquals(1, adminQueue.get("items").size()); assertEquals("Bengaluru", adminQueue.at("/items/0/siteCity").asText());
        assertEquals(1, call("GET", "/api/v1/technician/me/inspections?status=ASSIGNED&page=0&size=20", inspector.getUser(), null, 200).get("items").size());
        inspection = call("POST", "/api/v1/technician/me/inspections/" + inspectionId + "/accept?version=" + inspection.get("version").asLong(), inspector.getUser(), null, 200);
        assertEquals("ACCEPTED", inspection.get("status").asText());
        call("POST", "/api/v1/technician/me/inspections/" + inspectionId + "/start", inspector.getUser(), null, 200);
        JsonNode assessed = call("PUT", "/api/v1/technician/me/inspections/" + inspectionId + "/assessment", inspector.getUser(), Map.of("conclusion", "READY_WITH_ACTIONS", "findings", "Measured site", "version", call("GET", "/api/v1/technician/me/inspections/" + inspectionId, inspector.getUser(), null, 200).get("version").asLong()), 200);
        JsonNode reviewed = call("POST", "/api/v1/admin/installations/inspections/" + inspectionId + "/review", admin, Map.of("decision", "REWORK_REQUIRED", "notes", "Provide additional measurement", "version", assessed.get("version").asLong()), 200);
        assertEquals("REWORK_REQUIRED", reviewed.get("status").asText());
        JsonNode customerView = call("GET", "/api/v1/customers/me/installations/" + id + "/inspection", owner, null, 200);
        assertEquals("REWORK_REQUIRED", customerView.get("status").asText()); assertTrue(customerView.get("reviewNotes").isNull()); assertTrue(customerView.get("evidence").isEmpty());
        assertEquals(0, dbCount("select count(*) from buildings where customer_id=?", customers.findByUserId(owner.getId()).orElseThrow().getId()));
    }

    private int dbCount(String sql, Object... args) { return db.queryForObject(sql, Integer.class, args); }
}
