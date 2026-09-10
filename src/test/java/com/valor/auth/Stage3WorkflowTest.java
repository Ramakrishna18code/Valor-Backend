package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.workflow.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Each HTTP transaction really commits to this isolated in-memory database, including concurrent tests.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:workflow_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage3WorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired Flyway flyway;
    @Autowired Environment environment;
    @Autowired RequestMappingHandlerMapping mappings;

    record Fixture(User admin, User customer, User technician, Long profileId, Long technicianId, long liftId, long requestId) {}
    private User user(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.test"); user.setRole(role);
        return users.saveAndFlush(user);
    }
    private TechnicianProfile technician() {
        TechnicianProfile profile = new TechnicianProfile(); profile.user = user(Role.TECHNICIAN);
        return technicians.saveAndFlush(profile);
    }
    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.timestamp").exists()).andReturn().getResponse().getContentAsString();
        for (String secret : List.of("passwordHash", "otpHash", "tokenHash", "accessToken", "refreshToken", "stackTrace", "com.valor", "org.hibernate")) {
            assertFalse(text.contains(secret));
        }
        JsonNode envelope = json.readTree(text); assertEquals(expected < 400, envelope.get("success").asBoolean());
        return envelope.get("data");
    }
    private Map<String,Object> creation(long lift) {
        return Map.of("liftId", lift, "title", "Service needed", "description", "Test issue", "serviceType", "BREAKDOWN");
    }
    private Fixture fixture() throws Exception {
        User admin = user(Role.ADMIN), customer = user(Role.CUSTOMER);
        CustomerProfile owner = new CustomerProfile(); owner.setUser(customer); owner.setFullName("Workflow customer");
        customers.saveAndFlush(owner);
        TechnicianProfile tech = technician();
        long building = call(post("/api/v1/buildings"), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Tower"), 200).get("id").asLong();
        long lift = call(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Lift"), 200).get("id").asLong();
        long request = call(post("/api/v1/service-requests"), customer, creation(lift), 200).at("/request/id").asLong();
        return new Fixture(admin, customer, tech.getUser(), owner.getId(), tech.getId(), lift, request);
    }
    private String path(Fixture f) { return "/api/v1/service-requests/" + f.requestId(); }
    private long assign(Fixture f) throws Exception { return assign(f, f.technicianId()); }
    private long assign(Fixture f, long technicianId) throws Exception {
        return call(post(path(f) + "/assignments"), f.admin(), Map.of("technicianProfileId", technicianId, "notes", "Assignment note"), 200).at("/activeAssignment/id").asLong();
    }
    private void accept(Fixture f, long assignment, User technician) throws Exception {
        call(post(path(f) + "/assignments/" + assignment + "/accept"), technician, null, 200);
    }
    private JsonNode transition(Fixture f, User actor, String to, String notes, int status) throws Exception {
        return call(post(path(f) + "/status"), actor, Map.of("toStatus", to, "notes", notes), status);
    }
    private int events(Fixture f) { return db.queryForObject("select count(*) from service_status_history where service_request_id=?", Integer.class, f.requestId()); }
    private String state(Fixture f) { return db.queryForObject("select status from service_requests where id=?", String.class, f.requestId()); }
    private JsonNode report(Fixture f, User tech, int status) throws Exception {
        return call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), tech,
                Map.of("diagnosis", "Diagnosed", "workPerformed", "Repaired", "testingResult", "Passed"), status);
    }
    private void reach(Fixture f, String target) throws Exception {
        if (target.equals("PENDING")) return;
        long a = assign(f);
        if (target.equals("ASSIGNED")) return;
        accept(f, a, f.technician());
        if (target.equals("ACCEPTED")) return;
        for (String next : List.of("ON_THE_WAY", "REACHED_SITE", "DIAGNOSIS")) {
            transition(f, f.technician(), next, "Progress", 200); if (target.equals(next)) return;
        }
        if (target.equals("WAITING_FOR_PARTS")) { transition(f, f.technician(), target, "Waiting", 200); return; }
        transition(f, f.technician(), "REPAIR_IN_PROGRESS", "Repair", 200);
        if (target.equals("REPAIR_IN_PROGRESS")) return;
        transition(f, f.technician(), "TESTING", "Test", 200);
        if (target.equals("TESTING")) return;
        if (target.equals("COMPLETED")) report(f, f.technician(), 200);
        transition(f, f.admin(), target, "Terminal reason", 200);
    }

    @Test void v3AndHibernateValidateUseSignedCanonicalMappingsAndExactMysqlSql() throws Exception {
        assertTrue(Arrays.stream(flyway.info().applied()).anyMatch(m -> "3".equals(m.getVersion().getVersion())));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(em.getMetamodel().getEntities().size() >= 12); // Later canonical stages add entities.
        assertTrue(em.getMetamodel().getEntities().stream().noneMatch(e -> e.getJavaType().getPackageName().equals("com.valor.entity")));
        for (Class<?> type : List.of(ServiceRequest.class, TechnicianAssignment.class, ServiceReport.class, ServiceStatusHistory.class)) {
            assertEquals(Long.class, em.getMetamodel().entity(type).getIdType().getJavaType());
        }
        assertEquals(0, db.queryForObject("select count(*) from information_schema.columns where table_schema='PUBLIC' and table_name='SERVICE_REQUESTS' and column_name in ('TECHNICIAN_ID','ASSIGNED_TECHNICIAN_ID')", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from information_schema.columns where table_schema='PUBLIC' and table_name in ('SERVICE_REQUESTS','TECHNICIAN_ASSIGNMENTS','SERVICE_REPORTS','SERVICE_STATUS_HISTORY') and (column_name='ID' or column_name like '%\\_ID' escape '\\') and column_name <> 'SERVICE_ID' and data_type <> 'BIGINT'", Integer.class));
        assertEquals("ALWAYS", db.queryForObject("select is_generated from information_schema.columns where table_schema='PUBLIC' and table_name='TECHNICIAN_ASSIGNMENTS' and column_name='ACTIVE_REQUEST_ID'", String.class));
        assertTrue(Arrays.stream(TechnicianAssignment.class.getDeclaredFields()).noneMatch(f -> f.getName().equals("activeRequestId")));
        try (var stream = getClass().getResourceAsStream("/db/migration/V3__create_service_workflow.sql")) {
            String sql = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains(") STORED;")); assertTrue(sql.contains("CREATE UNIQUE INDEX uk_assignment_active_request"));
            assertFalse(sql.contains("BIGINT UNSIGNED")); assertFalse(sql.contains("UNIQUE (service_request_id, technician_id)"));
        }
        assertTrue(ServiceStatusHistory.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class));
    }

    @Test void customerCreationStartsPendingWithOneImmutableInitialEvent() throws Exception {
        Fixture f = fixture();
        JsonNode detail = call(get(path(f)), f.customer(), null, 200);
        assertEquals("PENDING", detail.at("/request/status").asText());
        assertEquals(f.profileId(), detail.at("/request/customerProfileId").asLong());
        assertEquals(1, detail.get("history").size()); assertTrue(detail.at("/history/0/fromStatus").isNull());
        assertEquals("PENDING", detail.at("/history/0/toStatus").asText());
        assertEquals(f.customer().getId(), detail.at("/history/0/changedByUserId").asLong());
    }

    @Test void ownershipAndUnknownIdsAreRejectedBeforeCreatingAnyRequest() throws Exception {
        Fixture f = fixture(), other = fixture();
        int before = db.queryForObject("select count(*) from service_requests", Integer.class);
        Map<String,Object> input = new HashMap<>(creation(f.liftId())); input.put("customerId", f.profileId());
        call(post("/api/v1/service-requests"), f.customer(), input, 400);
        input.remove("customerId"); input.put("customerProfileId", f.profileId());
        call(post("/api/v1/service-requests"), f.customer(), input, 400);
        call(post("/api/v1/service-requests"), f.customer(), creation(other.liftId()), 403);
        assertEquals(before, db.queryForObject("select count(*) from service_requests", Integer.class));
    }

    @Test void adminCreationRequiresMatchingOwnerAndHidesInternalNotes() throws Exception {
        Fixture f = fixture();
        call(post("/api/v1/service-requests"), f.admin(), creation(f.liftId()), 400);
        Map<String,Object> input = new HashMap<>(creation(f.liftId())); input.put("customerProfileId", f.profileId());
        input.put("internalAdminNotes", "Private admin instruction");
        JsonNode result = call(post("/api/v1/service-requests"), user(Role.SUPER_ADMIN), input, 200);
        long id = result.at("/request/id").asLong();
        assertEquals("Private admin instruction", result.at("/request/internalAdminNotes").asText());
        assertTrue(call(get("/api/v1/service-requests/" + id), f.customer(), null, 200).at("/request/internalAdminNotes").isNull());
    }

    @Test void inactiveAssetsAndTechnicianProfilesBlockNewOperations() throws Exception {
        Fixture f = fixture();
        call(delete("/api/v1/lifts/" + f.liftId()), f.admin(), null, 200);
        call(post("/api/v1/service-requests"), f.customer(), creation(f.liftId()), 409);
        db.update("update technician_profiles set is_active=false where id=?", f.technicianId());
        call(post(path(f) + "/assignments"), f.admin(), Map.of("technicianProfileId", f.technicianId()), 400);
        assertEquals("PENDING", state(f)); assertEquals(1, events(f));
    }

    @Test void assignmentAcceptanceAndJobReadsEnforceTechnicianOwnership() throws Exception {
        Fixture f = fixture(); TechnicianProfile other = technician();
        long a = assign(f);
        assertEquals("ASSIGNED", state(f)); assertEquals(2, events(f));
        call(get("/api/v1/technician/me/jobs/" + f.requestId()), other.getUser(), null, 403);
        call(post(path(f) + "/assignments/" + a + "/accept"), other.getUser(), null, 403);
        transition(f, other.getUser(), "ACCEPTED", "", 403);
        assertEquals(0, call(get("/api/v1/technician/me/jobs"), other.getUser(), null, 200).get("totalElements").asLong());
        assertEquals(f.requestId(), call(get("/api/v1/technician/me/jobs").param("status", "ASSIGNED"), f.technician(), null, 200).at("/items/0/id").asLong());
        accept(f, a, f.technician());
        assertEquals("ACCEPTED", state(f)); assertEquals(3, events(f));
        call(post(path(f) + "/assignments/" + a + "/accept"), f.technician(), null, 409);
        assertEquals(3, events(f));
    }

    @Test void reassignmentsKeepRowsAndAllowSameTechnicianAgain() throws Exception {
        Fixture f = fixture(); TechnicianProfile other = technician();
        long first = assign(f), second = assign(f, other.getId()), third = assign(f);
        assertNotEquals(first, third); assertNotEquals(second, third);
        assertEquals(3, db.queryForObject("select count(*) from technician_assignments where service_request_id=?", Integer.class, f.requestId()));
        assertEquals(2, db.queryForObject("select count(*) from technician_assignments where service_request_id=? and status='RELEASED' and released_at is not null", Integer.class, f.requestId()));
        assertEquals(1, db.queryForObject("select count(*) from technician_assignments where active_request_id=?", Integer.class, f.requestId()));
        assertEquals(2, events(f)); assertEquals("ASSIGNED", state(f));
        call(post(path(f) + "/assignments/" + first + "/accept"), f.technician(), null, 403);
        accept(f, third, f.technician());
    }

    @Test void advancedReassignmentPreservesLifecycleAndRequiresFreshReport() throws Exception {
        Fixture f = fixture(); reach(f, "TESTING");
        JsonNode old = report(f, f.technician(), 200);
        TechnicianProfile replacement = technician(); long next = assign(f, replacement.getId());
        assertEquals("TESTING", state(f));
        int beforeAcceptance = events(f);
        transition(f, f.admin(), "COMPLETED", "", 409);
        accept(f, next, replacement.getUser());
        assertEquals(beforeAcceptance, events(f));
        transition(f, f.admin(), "COMPLETED", "", 409);
        report(f, f.technician(), 403);
        JsonNode updated = report(f, replacement.getUser(), 200);
        assertEquals(old.get("id"), updated.get("id")); assertEquals(next, updated.get("assignmentId").asLong());
        transition(f, f.admin(), "COMPLETED", "", 200);
    }

    private Map<String,List<String>> graph() {
        return Map.ofEntries(Map.entry("PENDING", List.of("ASSIGNED","CANCELLED")),
                Map.entry("ASSIGNED", List.of("ACCEPTED","CANCELLED")), Map.entry("ACCEPTED", List.of("ON_THE_WAY","CANCELLED")),
                Map.entry("ON_THE_WAY", List.of("REACHED_SITE","CANCELLED")), Map.entry("REACHED_SITE", List.of("DIAGNOSIS","CANCELLED")),
                Map.entry("DIAGNOSIS", List.of("REPAIR_IN_PROGRESS","WAITING_FOR_PARTS","CANCELLED")),
                Map.entry("REPAIR_IN_PROGRESS", List.of("WAITING_FOR_PARTS","TESTING","CANCELLED")),
                Map.entry("WAITING_FOR_PARTS", List.of("REPAIR_IN_PROGRESS","CANCELLED")),
                Map.entry("TESTING", List.of("COMPLETED","REPAIR_IN_PROGRESS","CANCELLED")),
                Map.entry("COMPLETED", List.of()), Map.entry("CANCELLED", List.of()));
    }

    @Test void everyAllowedTransitionAddsExactlyOneCorrectEvent() throws Exception {
        for (var entry : graph().entrySet()) for (String next : entry.getValue()) {
            Fixture f = fixture(); reach(f, entry.getKey());
            if (next.equals("COMPLETED")) report(f, f.technician(), 200);
            int before = events(f);
            if (next.equals("ASSIGNED")) assign(f);
            else transition(f, entry.getKey().equals("PENDING") ? f.admin() : f.technician(), next, "Required notes", 200);
            assertEquals(next, state(f)); assertEquals(before + 1, events(f));
            assertEquals(entry.getKey(), db.queryForObject("select from_status from service_status_history where service_request_id=? order by id desc limit 1", String.class, f.requestId()));
            assertEquals(next, db.queryForObject("select to_status from service_status_history where service_request_id=? order by id desc limit 1", String.class, f.requestId()));
            assertEquals(next.equals("ASSIGNED") ? "Assignment note" : "Required notes", db.queryForObject("select notes from service_status_history where service_request_id=? order by id desc limit 1", String.class, f.requestId()));
        }
    }

    @Test void allDisallowedTransitionsLeaveStatusHistoryAndAssignmentUnchanged() throws Exception {
        for (var entry : graph().entrySet()) {
            Fixture f = fixture(); reach(f, entry.getKey());
            int before = events(f);
            List<Map<String,Object>> assignments = db.queryForList("select id,status,accepted_at,released_at from technician_assignments where service_request_id=? order by id", f.requestId());
            for (RequestStatus next : RequestStatus.values()) if (!entry.getValue().contains(next.name())) {
                transition(f, f.admin(), next.name(), "Reason", 409);
                assertEquals(entry.getKey(), state(f)); assertEquals(before, events(f));
                assertEquals(assignments, db.queryForList("select id,status,accepted_at,released_at from technician_assignments where service_request_id=? order by id", f.requestId()));
            }
        }
    }

    @Test void cancellationAndWaitingRequireNotesAndCancellationReleasesAssignment() throws Exception {
        Fixture f = fixture(); reach(f, "DIAGNOSIS"); int before = events(f);
        transition(f, f.technician(), "WAITING_FOR_PARTS", "   ", 400);
        transition(f, f.admin(), "CANCELLED", "   ", 400);
        assertEquals(before, events(f));
        transition(f, f.admin(), "CANCELLED", "Customer cancelled", 200);
        assertEquals(before + 1, events(f));
        assertEquals(1, db.queryForObject("select count(*) from technician_assignments where service_request_id=? and status='RELEASED' and released_at is not null and active_request_id is null", Integer.class, f.requestId()));
        assertNull(db.queryForObject("select completed_at from service_requests where id=?", Timestamp.class, f.requestId()));
    }

    @Test void reportsEnforceOwnershipRequiredFieldsUniqueRequestAndServerResolvedIds() throws Exception {
        Fixture f = fixture(); assign(f); TechnicianProfile other = technician();
        report(f, other.getUser(), 403); report(f, f.admin(), 403);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), f.technician(),
                Map.of("diagnosis", " ", "workPerformed", "Work", "testingResult", "Passed"), 400);
        call(post("/api/v1/technician/me/jobs/" + f.requestId() + "/report"), f.technician(),
                Map.of("diagnosis", "D", "workPerformed", "W", "testingResult", "T", "assignmentId", 1), 400);
        int before = events(f);
        JsonNode first = report(f, f.technician(), 200), second = report(f, f.technician(), 200);
        assertEquals(first.get("id"), second.get("id")); assertEquals(before, events(f)); assertEquals("ASSIGNED", state(f));
        assertEquals(1, db.queryForObject("select count(*) from service_reports where service_request_id=?", Integer.class, f.requestId()));
        assertEquals(first.get("id"), call(get(path(f)), f.customer(), null, 200).at("/report/id"));
    }

    @Test void completionRequiresReportForAdminsAndCommitsAllStateAtomically() throws Exception {
        Fixture f = fixture(); reach(f, "TESTING"); int before = events(f);
        transition(f, f.admin(), "COMPLETED", "", 409);
        transition(f, user(Role.SUPER_ADMIN), "COMPLETED", "", 409);
        transition(f, f.technician(), "COMPLETED", "", 409);
        assertEquals("TESTING", state(f)); assertEquals(before, events(f));
        report(f, f.technician(), 200);
        transition(f, f.technician(), "COMPLETED", "", 200);
        assertEquals(before + 1, events(f)); assertNotNull(db.queryForObject("select completed_at from service_requests where id=?", Timestamp.class, f.requestId()));
        assertEquals(1, db.queryForObject("select count(*) from technician_assignments where service_request_id=? and status='COMPLETED' and active_request_id is null", Integer.class, f.requestId()));
        report(f, f.technician(), 409);
        transition(f, f.admin(), "COMPLETED", "", 409);
        assertEquals(before + 1, events(f));
    }

    @Test void completionRejectsReportWithWrongReporterWithoutMutation() throws Exception {
        Fixture f = fixture(); reach(f, "TESTING"); report(f, f.technician(), 200); int before = events(f);
        db.update("update service_reports set reported_by_user_id=? where service_request_id=?", f.admin().getId(), f.requestId());
        transition(f, f.admin(), "COMPLETED", "", 409);
        assertEquals("TESTING", state(f)); assertEquals(before, events(f));
        assertNull(db.queryForObject("select completed_at from service_requests where id=?", Timestamp.class, f.requestId()));
    }

    @Test void authorizationAndPagingUseSafe401403404Responses() throws Exception {
        Fixture f = fixture(), other = fixture();
        call(get(path(f)), null, null, 401); call(get(path(f)), other.customer(), null, 403);
        call(get("/api/v1/service-requests/" + Long.MAX_VALUE), f.admin(), null, 404);
        call(get("/api/v1/service-requests"), f.customer(), null, 403);
        call(post("/api/v1/service-requests"), f.technician(), creation(f.liftId()), 403);
        call(get("/api/v1/service-requests").param("size", "101"), f.admin(), null, 400);
        call(get("/api/v1/service-requests").param("status", "INVALID"), f.admin(), null, 400);
        JsonNode page = call(get("/api/v1/service-requests").param("status", "PENDING").param("size", "2"), f.admin(), null, 200);
        assertTrue(page.get("items").size() <= 2); assertEquals(2, page.get("size").asInt());
        for (JsonNode item : page.get("items")) assertEquals("PENDING", item.get("status").asText());
        var paths = mappings.getHandlerMethods().keySet().stream().flatMap(m -> m.getPatternValues().stream()).toList();
        assertFalse(paths.stream().anyMatch(p -> p.startsWith("/api/") && !p.startsWith("/api/v1/")));
    }

    @Test void generatedUniqueKeyRejectsConcurrentDuplicateActiveAssignments() throws Exception {
        Fixture f = fixture();
        String sql = "insert into technician_assignments(service_request_id,technician_id,status,assigned_by_user_id) values(?,?,'ASSIGNED',?)";
        try (Connection first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            try (PreparedStatement insert = first.prepareStatement(sql)) {
                insert.setLong(1, f.requestId()); insert.setLong(2, f.technicianId()); insert.setLong(3, f.admin().getId()); insert.executeUpdate();
            }
            ExecutorService executor = Executors.newSingleThreadExecutor(); CountDownLatch started = new CountDownLatch(1);
            try {
                Future<String> second = executor.submit(() -> {
                    try (Connection connection = dataSource.getConnection(); PreparedStatement insert = connection.prepareStatement(sql)) {
                        insert.setLong(1, f.requestId()); insert.setLong(2, f.technicianId()); insert.setLong(3, f.admin().getId());
                        started.countDown(); insert.executeUpdate(); return "unexpected success";
                    } catch (SQLException error) { return error.getSQLState(); }
                });
                assertTrue(started.await(5, TimeUnit.SECONDS)); first.commit();
                assertEquals("23505", second.get(10, TimeUnit.SECONDS));
                assertEquals(1, db.queryForObject("select count(*) from technician_assignments where active_request_id=?", Integer.class, f.requestId()));
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void concurrentCompletionProducesOneSuccessAndOneHistoryEvent() throws Exception {
        Fixture f = fixture(); reach(f, "TESTING"); report(f, f.technician(), 200); int before = events(f);
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Integer> completion = () -> {
                go.await(); return mvc.perform(post(path(f) + "/status").header("Authorization", "Bearer " + jwt.issue(f.admin()))
                        .contentType("application/json").content("{\"toStatus\":\"COMPLETED\"}")).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(completion), second = executor.submit(completion); go.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200,409), statuses); assertEquals(before + 1, events(f));
            assertEquals("COMPLETED", state(f));
        } finally { executor.shutdownNow(); }
    }
    @Test void terminalReportsRemainVisibleToAuthorizedHistoricalViewers() throws Exception {
        for (String terminal : List.of("COMPLETED", "CANCELLED")) {
            Fixture f = fixture(); reach(f, "TESTING");
            JsonNode created = report(f, f.technician(), 200);
            JsonNode saved = call(get(path(f)), f.technician(), null, 200).get("report");
            assertEquals(created.get("id"), saved.get("id"));
            transition(f, f.technician(), terminal, "Terminal explanation", 200);
            for (User actor : List.of(f.customer(), f.admin(), f.technician())) {
                JsonNode detail = call(get(path(f)), actor, null, 200);
                assertTrue(detail.get("activeAssignment").isNull());
                assertEquals(saved, detail.get("report"));
                assertEquals("Diagnosed", detail.at("/report/diagnosis").asText());
                assertEquals("Repaired", detail.at("/report/workPerformed").asText());
                assertEquals("Passed", detail.at("/report/testingResult").asText());
            }
            assertEquals(saved, call(get("/api/v1/technician/me/jobs/" + f.requestId()), f.technician(), null, 200).get("report"));
            call(get(path(f)), technician().getUser(), null, 403);
            call(get(path(f)), fixture().customer(), null, 403);
            report(f, f.technician(), 409);
        }
    }

    @Test void transitionNotesSurvivePersistenceAndAllAuthorizedProjections() throws Exception {
        Fixture f = fixture(); reach(f, "DIAGNOSIS");
        transition(f, f.technician(), "WAITING_FOR_PARTS", "Awaiting replacement board", 200);
        transition(f, f.admin(), "CANCELLED", "Customer requested cancellation", 200);
        for (User actor : List.of(f.customer(), f.admin(), f.technician())) {
            JsonNode rows = call(get(path(f)), actor, null, 200).get("history");
            assertEquals("Awaiting replacement board", rows.get(rows.size()-2).get("notes").asText());
            assertEquals("Customer requested cancellation", rows.get(rows.size()-1).get("notes").asText());
        }
        assertEquals("Awaiting replacement board", db.queryForObject("select notes from service_status_history where service_request_id=? and to_status='WAITING_FOR_PARTS'", String.class, f.requestId()));
        assertEquals("Customer requested cancellation", db.queryForObject("select notes from service_status_history where service_request_id=? and to_status='CANCELLED'", String.class, f.requestId()));
    }

    @Test void historyRejectsEqualStatesAndReassignmentNeverAddsThem() throws Exception {
        Fixture f = fixture(); long first = assign(f); int before = events(f);
        long second = assign(f); assertNotEquals(first, second); assertEquals(before, events(f));
        accept(f, second, f.technician());
        long third = assign(f); before = events(f); accept(f, third, f.technician());
        assertEquals(before, events(f));
        assertEquals(0, db.queryForObject("select count(*) from service_status_history where service_request_id=? and from_status=to_status", Integer.class, f.requestId()));
        for (RequestStatus status : RequestStatus.values()) {
            assertThrows(IllegalArgumentException.class, () -> new ServiceStatusHistory(null, status, status, f.admin(), "Invalid"));
        }
    }

}
