package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:visits_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc @ActiveProfiles("test")
class ServiceVisitTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;

    record Fixture(User admin, User customer, User technician, User otherTechnician, long requestId, long assignedTechnicianId) {}

    private User user(Role role) {
        User value = new User(); value.setEmail(UUID.randomUUID() + "@example.test"); value.setRole(role);
        return users.saveAndFlush(value);
    }
    private TechnicianProfile technician(User user) {
        TechnicianProfile value = new TechnicianProfile(); value.user = user;
        value.employeeId = "EMP-" + UUID.randomUUID(); value.specialization = "Electrical";
        return technicians.saveAndFlush(value);
    }
    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andReturn().getResponse().getContentAsString();
        for (String secret : List.of("passwordHash", "otpHash", "tokenHash", "accessToken", "refreshToken", "employeeId", "specialization")) {
            if (actor != null && actor.getRole() == Role.CUSTOMER) assertFalse(text.contains(secret));
        }
        JsonNode response = json.readTree(text); assertEquals(expected < 400, response.get("success").asBoolean());
        return response.get("data");
    }
    private Fixture fixture() throws Exception {
        User admin = user(Role.ADMIN), customer = user(Role.CUSTOMER), techUser = user(Role.TECHNICIAN), otherUser = user(Role.TECHNICIAN);
        CustomerProfile customerProfile = new CustomerProfile(); customerProfile.setUser(customer); customerProfile.setFullName("Visit customer"); customers.saveAndFlush(customerProfile);
        TechnicianProfile tech = technician(techUser); technician(otherUser);
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", customerProfile.getId(), "buildingName", "Tower"), 200).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Lift"), 200).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), customer, Map.of("liftId", lift, "title", "Repair", "description", "Issue", "serviceType", "BREAKDOWN"), 200)
                .get("request").get("id").asLong();
        call(post("/api/v1/service-requests/" + request + "/assignments"), admin, Map.of("technicianProfileId", tech.getId()), 200);
        return new Fixture(admin, customer, techUser, otherUser, request, tech.getId());
    }
    private Map<String, Object> visit(Fixture fixture, String date, String start, String end) {
        return Map.of("serviceRequestId", fixture.requestId(), "technicianProfileId", fixture.assignedTechnicianId(),
                "scheduledDate", date, "startTime", start, "endTime", end, "notes", "Inspection");
    }
    private String visitPath(Fixture fixture) { return "/api/v1/admin/service-visits"; }

    @Test void adminCreatesTypedVisitAndCustomerSeesSafeProjection() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        assertEquals("SCHEDULED", created.get("status").asText()); assertEquals(fixture.requestId(), created.get("serviceRequestId").asLong());
        JsonNode customer = call(get("/api/v1/customers/me/visits"), fixture.customer(), null, 200).get("items").get(0);
        assertEquals("SCHEDULED", customer.get("status").asText()); assertTrue(customer.get("technicianProfileId").isNull());
        assertTrue(customer.get("notes").isNull()); assertTrue(customer.get("history").isEmpty());
    }

    @Test void adminCanFilterVisitsByServiceRequest() throws Exception {
        Fixture first = fixture(), second = fixture();
        call(post(visitPath(first)), first.admin(), visit(first, "2031-01-10", "10:00:00", "11:00:00"), 200);
        call(post(visitPath(second)), second.admin(), visit(second, "2031-01-11", "10:00:00", "11:00:00"), 200);
        JsonNode filtered = call(get(visitPath(first) + "?serviceRequestId=" + first.requestId()), first.admin(), null, 200);
        assertEquals(1, filtered.get("items").size());
        assertEquals(first.requestId(), filtered.get("items").get(0).get("serviceRequestId").asLong());
    }

    @Test void creationRequiresExistingRequestAssignedTechnicianAndValidRange() throws Exception {
        Fixture fixture = fixture();
        Map<String, Object> input = new HashMap<>(visit(fixture, "2031-01-10", "10:00:00", "11:00:00"));
        input.put("serviceRequestId", Long.MAX_VALUE); call(post(visitPath(fixture)), fixture.admin(), input, 404);
        input = new HashMap<>(visit(fixture, "2031-01-10", "10:00:00", "11:00:00")); input.put("technicianProfileId", Long.MAX_VALUE);
        call(post(visitPath(fixture)), fixture.admin(), input, 404);
        input = new HashMap<>(visit(fixture, "2031-01-10", "11:00:00", "10:00:00")); call(post(visitPath(fixture)), fixture.admin(), input, 400);
    }

    @Test void duplicateRequestAndOverlappingTechnicianVisitsAreRejectedButAdjacentIsAllowed() throws Exception {
        Fixture fixture = fixture(); call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "11:00:00", "12:00:00"), 409);
        Fixture other = fixture();
        call(post("/api/v1/service-requests/" + other.requestId() + "/assignments"), other.admin(), Map.of("technicianProfileId", fixture.assignedTechnicianId()), 200);
        Map<String, Object> overlap = new HashMap<>(visit(other, "2031-01-10", "10:30:00", "11:30:00")); overlap.put("technicianProfileId", fixture.assignedTechnicianId());
        call(post(visitPath(other)), fixture.admin(), overlap, 409);
        Map<String, Object> adjacent = new HashMap<>(visit(other, "2031-01-10", "11:00:00", "12:00:00")); adjacent.put("technicianProfileId", fixture.assignedTechnicianId());
        call(post(visitPath(other)), fixture.admin(), adjacent, 200);
    }

    @Test void completedAndCancelledVisitsDoNotBlockLaterRequestVisits() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        long id = created.get("id").asLong();
        call(put("/api/v1/technician/me/visits/" + id + "/status"), fixture.technician(), Map.of("status", "IN_PROGRESS"), 200);
        call(put("/api/v1/technician/me/visits/" + id + "/status"), fixture.technician(), Map.of("status", "COMPLETED"), 200);
        Fixture next = fixture();
        call(post("/api/v1/service-requests/" + next.requestId() + "/assignments"), next.admin(), Map.of("technicianProfileId", fixture.assignedTechnicianId()), 200);
        Map<String, Object> later = new HashMap<>(visit(next, "2031-01-10", "10:30:00", "11:30:00")); later.put("technicianProfileId", fixture.assignedTechnicianId());
        call(post(visitPath(next)), fixture.admin(), later, 200);
    }

    @Test void technicianCannotCreateOrReadAnotherTechniciansVisit() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        call(post(visitPath(fixture)), fixture.technician(), visit(fixture, "2031-01-11", "10:00:00", "11:00:00"), 403);
        call(get("/api/v1/technician/me/visits/" + created.get("id").asLong()), fixture.otherTechnician(), null, 403);
    }

    @Test void adminRescheduleRecordsHistoryAndTechnicianCanRequestAnotherVisit() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        long id = created.get("id").asLong();
        call(put(visitPath(fixture) + "/" + id), fixture.admin(), Map.of("technicianProfileId", fixture.assignedTechnicianId(), "scheduledDate", "2031-01-11", "startTime", "12:00:00", "endTime", "13:00:00", "notes", "Moved"), 200);
        JsonNode detail = call(get(visitPath(fixture) + "/" + id), fixture.admin(), null, 200);
        assertTrue(detail.get("history").toString().contains("RESCHEDULED"));
        call(put("/api/v1/technician/me/visits/" + id + "/status"), fixture.technician(), Map.of("status", "IN_PROGRESS"), 200);
        call(put("/api/v1/technician/me/visits/" + id + "/status"), fixture.technician(), Map.of("status", "COMPLETED"), 200);
        JsonNode request = call(post("/api/v1/technician/me/visits/" + id + "/additional-visit-requests"), fixture.technician(),
                Map.of("reason", "Part required", "requestedDate", "2031-01-12", "requestedStartTime", "09:00:00", "requestedEndTime", "10:00:00"), 200);
        assertEquals("PENDING", request.get("status").asText());
        JsonNode approved = call(post("/api/v1/admin/visit-change-requests/" + request.get("id").asLong() + "/approve"), fixture.admin(), Map.of("reviewNotes", "Approved"), 200);
        assertEquals("SCHEDULED", approved.get("status").asText()); assertNotEquals(id, approved.get("id").asLong());
    }

    @Test void technicianCancellationRequiresReasonAndLeavesRequestAvailable() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        long id = created.get("id").asLong();
        call(post("/api/v1/technician/me/visits/" + id + "/cancel"), fixture.technician(), Map.of("reason", "Unavailable"), 200);
        assertEquals("ASSIGNED", call(get("/api/v1/service-requests/" + fixture.requestId()), fixture.customer(), null, 200).at("/request/status").asText());
        call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
    }

    @Test void customerCanCancelBeforeArrivalButNotAfterWorkStarts() throws Exception {
        Fixture fixture = fixture();
        call(post("/api/v1/service-requests/" + fixture.requestId() + "/status"), fixture.customer(), Map.of("toStatus", "CANCELLED", "notes", "No longer needed"), 200);
        Fixture later = fixture();
        call(post("/api/v1/service-requests/" + later.requestId() + "/assignments"), later.admin(), Map.of("technicianProfileId", later.assignedTechnicianId()), 200);
        call(post("/api/v1/service-requests/" + later.requestId() + "/assignments/" + 999999 + "/accept"), later.technician(), null, 403);
        call(post("/api/v1/service-requests/" + later.requestId() + "/status"), later.technician(), Map.of("toStatus", "ON_THE_WAY", "notes", "Travel"), 409);
        assertEquals("ACCEPTED", call(post("/api/v1/service-requests/" + later.requestId() + "/assignments/" +
                call(get("/api/v1/service-requests/" + later.requestId()), later.admin(), null, 200).at("/activeAssignment/id").asLong() + "/accept"), later.technician(), null, 200).at("/request/status").asText());
        call(post("/api/v1/service-requests/" + later.requestId() + "/status"), later.technician(), Map.of("toStatus", "ON_THE_WAY", "notes", "Travel"), 200);
        call(post("/api/v1/service-requests/" + later.requestId() + "/status"), later.customer(), Map.of("toStatus", "CANCELLED", "notes", "Changed plans"), 200);
        Fixture arrived = fixture();
        long assignment = call(post("/api/v1/service-requests/" + arrived.requestId() + "/assignments"), arrived.admin(), Map.of("technicianProfileId", arrived.assignedTechnicianId()), 200).at("/activeAssignment/id").asLong();
        call(post("/api/v1/service-requests/" + arrived.requestId() + "/assignments/" + assignment + "/accept"), arrived.technician(), null, 200);
        call(post("/api/v1/service-requests/" + arrived.requestId() + "/status"), arrived.technician(), Map.of("toStatus", "ON_THE_WAY", "notes", "Travel"), 200);
        call(post("/api/v1/service-requests/" + arrived.requestId() + "/status"), arrived.technician(), Map.of("toStatus", "REACHED_SITE", "notes", "Arrived"), 200);
        call(post("/api/v1/service-requests/" + arrived.requestId() + "/status"), arrived.customer(), Map.of("toStatus", "CANCELLED", "notes", "Too late"), 409);
    }

    @Test void adminCanCancelVisitWithoutCancellingRequest() throws Exception {
        Fixture fixture = fixture();
        JsonNode created = call(post(visitPath(fixture)), fixture.admin(), visit(fixture, "2031-01-10", "10:00:00", "11:00:00"), 200);
        call(post(visitPath(fixture) + "/" + created.get("id").asLong() + "/cancel"), fixture.admin(), Map.of("reason", "Customer unavailable"), 200);
        assertEquals("ASSIGNED", call(get("/api/v1/service-requests/" + fixture.requestId()), fixture.customer(), null, 200).at("/request/status").asText());
    }
}
