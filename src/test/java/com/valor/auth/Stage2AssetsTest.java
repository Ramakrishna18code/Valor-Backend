package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.assets.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Stage2AssetsTest.Dates.class)
@Transactional
class Stage2AssetsTest {
    @TestConfiguration
    static class Dates {
        @Bean @Primary Clock fixedAssetClock() { return Clock.fixed(Instant.parse("2030-06-15T00:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired JwtService jwt;
    @Autowired Flyway flyway;
    @Autowired Environment environment;
    @Autowired RequestMappingHandlerMapping mappings;

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setRole(role);
        return users.saveAndFlush(user);
    }
    private CustomerProfile customer() {
        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user(Role.CUSTOMER));
        profile.setFullName("Asset test customer");
        return customers.saveAndFlush(profile);
    }
    private JsonNode request(MockHttpServletRequestBuilder request, User actor, Object body, int status) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String response = mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.timestamp").exists()).andReturn().getResponse().getContentAsString();
        for (String field : List.of("passwordHash", "tokenHash", "otpHash", "stackTrace", "com.valor", "org.hibernate")) {
            assertFalse(response.contains(field));
        }
        JsonNode result = json.readTree(response);
        assertEquals(status < 400, result.get("success").asBoolean());
        return result.get("data");
    }
    private long building(User admin, CustomerProfile owner) throws Exception {
        return request(post("/api/v1/buildings"), admin,
                Map.of("customerProfileId", owner.getId(), "buildingName", "Tower"), 200).get("id").asLong();
    }
    private long lift(User admin, long building) throws Exception {
        return request(post("/api/v1/lifts"), admin, Map.of("buildingId", building, "name", "Lift"), 200).get("id").asLong();
    }
    private Map<String, Object> contract(long lift, String start, String end) {
        return Map.of("liftId", lift, "amcNumber", UUID.randomUUID().toString(), "plan", "Standard", "startDate", start, "endDate", end);
    }
    private long amc(User admin, long lift) throws Exception {
        return request(post("/api/v1/amc-contracts"), admin, contract(lift, "2030-01-01", "2030-12-31"), 200).get("id").asLong();
    }
    private JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.get("id").asLong() == id) return row;
        fail("Expected asset in list");
        return null;
    }
    private void sql(String sql, Object... args) { em.flush(); db.update(sql, args); em.clear(); }

    @Test void v2RunsWithHibernateValidateAndOnlyCanonicalAssetMappings() {
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        assertEquals(2, flyway.info().applied().length);
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("never", environment.getProperty("spring.sql.init.mode", "never"));
        Set<Class<?>> entities = em.getMetamodel().getEntities().stream().map(e -> e.getJavaType()).collect(Collectors.toSet());
        assertEquals(8, entities.size());
        assertTrue(entities.containsAll(Set.of(Building.class, Lift.class, AmcContract.class)));
        assertTrue(entities.stream().noneMatch(c -> c.getPackageName().equals("com.valor.entity")));
        assertEquals(3, db.queryForObject("select count(*) from information_schema.tables where table_schema='PUBLIC' and table_name in ('BUILDINGS','LIFTS','AMC_CONTRACTS')", Integer.class));
    }

    @Test void schemaHasRestrictiveKeysIndexesAndNoDerivedColumns() {
        List<String> columns = db.queryForList("select column_name from information_schema.columns where table_schema='PUBLIC' and table_name='LIFTS'", String.class);
        assertTrue(columns.contains("BUILDING_ID"));
        assertFalse(columns.contains("CUSTOMER_ID"));
        assertFalse(columns.contains("AMC_STATUS"));
        assertFalse(columns.contains("TOTAL_BREAKDOWNS"));
        assertEquals(0, db.queryForObject("select count(*) from information_schema.columns where table_schema='PUBLIC' and table_name='BUILDINGS' and column_name='NUMBER_OF_LIFTS'", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from information_schema.referential_constraints where constraint_schema='PUBLIC' and constraint_name in ('FK_BUILDINGS_CUSTOMER','FK_LIFTS_BUILDING','FK_AMC_LIFT') and delete_rule='RESTRICT'", Integer.class));
        assertEquals(3, db.queryForObject("select count(*) from information_schema.indexes where index_schema='PUBLIC' and index_name in ('IDX_BUILDINGS_CUSTOMER_STATUS','IDX_LIFTS_BUILDING_STATUS','IDX_AMC_LIFT_STATUS_END')", Integer.class));
    }

    @Test void adminAndSuperAdminCanManageAllAssetEndpoints() throws Exception {
        for (Role role : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
            User admin = user(role);
            CustomerProfile owner = customer();
            long b = building(admin, owner);
            request(put("/api/v1/buildings/" + b), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Updated"), 200);
            assertEquals("Updated", find(request(get("/api/v1/buildings"), admin, null, 200), b).get("buildingName").asText());
            long l = lift(admin, b);
            request(put("/api/v1/lifts/" + l), admin, Map.of("buildingId", b, "name", "Updated lift", "currentStatus", "MAINTENANCE"), 200);
            assertEquals("MAINTENANCE", find(request(get("/api/v1/lifts"), admin, null, 200), l).get("currentStatus").asText());
            long a = amc(admin, l);
            request(put("/api/v1/amc-contracts/" + a + "/renew"), admin, Map.of("plan", "Renewed", "startDate", "2031-01-01", "endDate", "2031-12-31"), 200);
            assertEquals(1, find(request(get("/api/v1/amc-contracts"), admin, null, 200), a).get("renewalCount").asInt());
            request(delete("/api/v1/lifts/" + l), admin, null, 200);
            request(delete("/api/v1/buildings/" + b), admin, null, 200);
        }
    }

    @Test void customerAndTechnicianCannotUseAdminAssetOperations() throws Exception {
        for (Role role : List.of(Role.CUSTOMER, Role.TECHNICIAN)) {
            User actor = user(role);
            for (String path : List.of("/api/v1/buildings", "/api/v1/lifts")) {
                request(get(path), actor, null, 403);
                request(post(path), actor, Map.of(), 403);
                request(put(path + "/1"), actor, Map.of(), 403);
                request(delete(path + "/1"), actor, null, 403);
            }
            request(post("/api/v1/amc-contracts"), actor, Map.of(), 403);
            request(put("/api/v1/amc-contracts/1/renew"), actor, Map.of(), 403);
        }
        request(get("/api/v1/amc-contracts"), user(Role.TECHNICIAN), null, 403);
        request(get("/api/v1/buildings"), null, null, 401);
    }

    @Test void customerAmcReadsAreScopedToJwtOwnerEvenWithSubmittedFilter() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile first = customer(), second = customer();
        long a = amc(admin, lift(admin, building(admin, first)));
        long other = amc(admin, lift(admin, building(admin, second)));
        JsonNode rows = request(get("/api/v1/amc-contracts").param("customerProfileId", second.getId().toString()), first.getUser(), null, 200);
        assertEquals(1, rows.size());
        assertEquals(a, rows.get(0).get("id").asLong());
        assertNotEquals(other, rows.get(0).get("id").asLong());
        assertEquals(0, request(get("/api/v1/amc-contracts"), customer().getUser(), null, 200).size());
    }

    @Test void buildingOwnerIsRequiredValidAndImmutable() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer(), other = customer();
        request(post("/api/v1/buildings"), admin, Map.of("customerProfileId", Long.MAX_VALUE, "buildingName", "Missing"), 400);
        request(post("/api/v1/buildings"), admin, Map.of("buildingName", "Missing owner"), 400);
        long b = building(admin, owner);
        request(put("/api/v1/buildings/" + b), admin, Map.of("customerProfileId", other.getId(), "buildingName", "Moved"), 400);
        assertEquals(owner.getId(), db.queryForObject("select customer_id from buildings where id=?", Long.class, b));
    }

    @Test void inactiveProfileOrAccountBlocksNewAssetsAndCustomerRead() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        User customerUser = owner.getUser();
        long b = building(admin, owner), l = lift(admin, b);
        sql("update customer_profiles set is_active=false where id=?", owner.getId());
        request(post("/api/v1/buildings"), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Blocked"), 400);
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Blocked"), 400);
        request(post("/api/v1/amc-contracts"), admin, contract(l, "2030-01-01", "2030-12-31"), 400);
        request(get("/api/v1/amc-contracts"), customerUser, null, 403);
        sql("update customer_profiles set is_active=true where id=?", owner.getId());
        sql("update users set is_active=false where id=?", customerUser.getId());
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Blocked"), 400);
        request(get("/api/v1/amc-contracts"), customerUser, null, 401);
    }

    @Test void suspendedProfileAndLockedOwnerBlockNewOperations() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        long b = building(admin, owner);
        sql("update customer_profiles set status='SUSPENDED' where id=?", owner.getId());
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Blocked"), 400);
        sql("update customer_profiles set status='ACTIVE' where id=?", owner.getId());
        sql("update users set is_locked=true where id=?", owner.getUser().getId());
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Blocked"), 400);
    }

    @Test void liftRejectsCustomerIdReparentingAndDerivedFields() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        long b = building(admin, owner), other = building(admin, customer());
        for (String forbidden : List.of("customerId", "amcStatus", "totalBreakdowns", "isActive")) {
            request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Rejected", forbidden, 1), 400);
        }
        long l = lift(admin, b);
        request(put("/api/v1/lifts/" + l), admin, Map.of("buildingId", other, "name", "Moved"), 400);
        assertEquals(b, db.queryForObject("select building_id from lifts where id=?", Long.class, l));
    }

    @Test void buildingDeactivationRetainsDescendantsAndBlocksWrites() throws Exception {
        User admin = user(Role.ADMIN);
        CustomerProfile owner = customer();
        long b = building(admin, owner), l = lift(admin, b), a = amc(admin, l);
        request(delete("/api/v1/buildings/" + b), admin, null, 200);
        request(delete("/api/v1/buildings/" + b), admin, null, 200);
        assertFalse(db.queryForObject("select is_active from buildings where id=?", Boolean.class, b));
        assertEquals(1, db.queryForObject("select count(*) from lifts where id=?", Integer.class, l));
        assertEquals(1, db.queryForObject("select count(*) from amc_contracts where id=?", Integer.class, a));
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Blocked"), 409);
        request(put("/api/v1/buildings/" + b), admin, Map.of("customerProfileId", owner.getId(), "buildingName", "Blocked"), 409);
        request(put("/api/v1/lifts/" + l), admin, Map.of("buildingId", b, "name", "Blocked"), 409);
        request(post("/api/v1/amc-contracts"), admin, contract(l, "2031-01-01", "2031-12-31"), 409);
        assertEquals(a, request(get("/api/v1/amc-contracts"), owner.getUser(), null, 200).get(0).get("id").asLong());
    }

    @Test void liftDeactivationRetainsContractsAndUpdatesDerivedLiftCount() throws Exception {
        User admin = user(Role.ADMIN);
        long b = building(admin, customer());
        assertEquals(0, find(request(get("/api/v1/buildings"), admin, null, 200), b).get("activeLiftCount").asInt());
        long l = lift(admin, b), a = amc(admin, l);
        assertEquals(1, find(request(get("/api/v1/buildings"), admin, null, 200), b).get("activeLiftCount").asInt());
        request(delete("/api/v1/lifts/" + l), admin, null, 200);
        assertEquals(0, find(request(get("/api/v1/buildings"), admin, null, 200), b).get("activeLiftCount").asInt());
        assertFalse(db.queryForObject("select is_active from lifts where id=?", Boolean.class, l));
        assertEquals(1, db.queryForObject("select count(*) from amc_contracts where id=?", Integer.class, a));
        request(post("/api/v1/amc-contracts"), admin, contract(l, "2031-01-01", "2031-12-31"), 409);
        request(put("/api/v1/amc-contracts/" + a + "/renew"), admin, Map.of("plan", "Blocked", "startDate", "2031-01-01", "endDate", "2031-12-31"), 409);
    }

    @Test void renewalIsAtomicAndDateRangesAreValidated() throws Exception {
        User admin = user(Role.ADMIN);
        long l = lift(admin, building(admin, customer()));
        request(post("/api/v1/amc-contracts"), admin, contract(l, "2030-12-31", "2030-01-01"), 400);
        long a = amc(admin, l);
        request(put("/api/v1/amc-contracts/" + a + "/renew"), admin, Map.of("plan", "Invalid", "startDate", "2030-06-01", "endDate", "2031-12-31"), 400);
        assertEquals(0, db.queryForObject("select renewal_count from amc_contracts where id=?", Integer.class, a));
        JsonNode renewed = request(put("/api/v1/amc-contracts/" + a + "/renew"), admin,
                Map.of("plan", "Premium", "startDate", "2031-01-01", "endDate", "2031-12-31", "coverageDetails", "Updated"), 200);
        assertEquals(1, renewed.get("renewalCount").asInt());
        assertEquals("ACTIVE", renewed.get("status").asText());
        assertFalse(renewed.get("covered").asBoolean());
        assertEquals("2030-06-15", renewed.get("asOfDate").asText());
    }

    @Test void coverageUsesOneBusinessDateAndInclusiveBoundariesWithoutPersistingIt() throws Exception {
        User admin = user(Role.ADMIN);
        long b = building(admin, customer());
        long l = lift(admin, b);
        JsonNode contract = request(post("/api/v1/amc-contracts"), admin, contract(l, "2030-06-15", "2030-06-15"), 200);
        assertTrue(contract.get("covered").asBoolean());
        JsonNode row = find(request(get("/api/v1/lifts"), admin, null, 200), l);
        assertEquals("ACTIVE", row.get("amcCoverage").asText());
        assertEquals("2030-06-15", row.get("asOfDate").asText());
        sql("update amc_contracts set status='CANCELLED' where id=?", contract.get("id").asLong());
        assertEquals("NON_AMC", find(request(get("/api/v1/lifts"), admin, null, 200), l).get("amcCoverage").asText());
        long future = lift(admin, b);
        request(post("/api/v1/amc-contracts"), admin, contract(future, "2030-06-16", "2030-12-31"), 200);
        assertEquals("NON_AMC", find(request(get("/api/v1/lifts"), admin, null, 200), future).get("amcCoverage").asText());
        long expired = lift(admin, b);
        request(post("/api/v1/amc-contracts"), admin, contract(expired, "2030-01-01", "2030-06-14"), 200);
        assertEquals("NON_AMC", find(request(get("/api/v1/lifts"), admin, null, 200), expired).get("amcCoverage").asText());
    }

    @Test void invalidPayloadsReturnSafeEnvelopeWithoutMutatingAssets() throws Exception {
        User admin = user(Role.ADMIN);
        long b = building(admin, customer());
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Invalid", "capacity", -1), 400);
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Invalid", "healthScore", 101), 400);
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Invalid", "currentStatus", "UNKNOWN"), 400);
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", b, "name", "Invalid", "warrantyStartDate", "2030-02-01", "warrantyEndDate", "2030-01-01"), 400);
        request(post("/api/v1/lifts"), admin, Map.of("buildingId", Long.MAX_VALUE, "name", "Missing"), 404);
        assertEquals(0, db.queryForObject("select count(*) from lifts where building_id=?", Integer.class, b));
    }

    @Test void canonicalAssetRoutesHaveNoUnversionedAliases() {
        var routes = mappings.getHandlerMethods().keySet().stream().flatMap(m -> m.getPatternValues().stream()).toList();
        assertTrue(routes.containsAll(List.of("/api/v1/buildings", "/api/v1/lifts", "/api/v1/amc-contracts")));
        assertFalse(routes.stream().anyMatch(p -> p.startsWith("/api/") && !p.startsWith("/api/v1/")));
    }
}
