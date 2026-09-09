package com.valor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.notifications.Notification;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notifications_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Stage4NotificationsTest.TimeConfig.class)
class Stage4NotificationsTest {
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>();
        void set(String time) { now.set(Instant.parse(time)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        public Instant instant() { return now.get(); }
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock notificationTestClock() {
            MutableClock clock = new MutableClock(); clock.set("2032-01-01T12:00:00Z"); return clock;
        }
    }
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired Flyway flyway;
    @Autowired Environment environment;
    @Autowired RequestMappingHandlerMapping mappings;
    @BeforeEach void resetTime() { clock.set("2032-01-01T12:00:00Z"); }

    private User user(Role role) {
        User user = new User(); user.setRole(role); user.setEmail(UUID.randomUUID() + "@example.test");
        return users.saveAndFlush(user);
    }
    private Map<String,Object> input(User recipient) {
        return Map.of("recipientUserId", recipient.getId(), "title", "Service update", "message", "Your update is available");
    }
    private JsonNode call(MockHttpServletRequestBuilder request, User actor, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + jwt.issue(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.timestamp").exists()).andReturn().getResponse().getContentAsString();
        for (String secret : List.of("passwordHash", "otpHash", "tokenHash", "accessToken", "refreshToken", "stackTrace", "com.valor", "org.hibernate")) {
            assertFalse(text.contains(secret));
        }
        JsonNode response = json.readTree(text); assertEquals(expected < 400, response.get("success").asBoolean());
        return response.get("data");
    }
    private JsonNode create(User admin, User recipient) throws Exception { return call(post("/api/v1/notifications"), admin, input(recipient), 200); }
    private JsonNode inbox(User recipient) throws Exception { return call(get("/api/v1/notifications"), recipient, null, 200); }
    private JsonNode read(User recipient, long id, int status) throws Exception { return call(put("/api/v1/notifications/" + id + "/read"), recipient, null, status); }

    @Test void v4AndHibernateValidateUseOnlyCanonicalNotificationEntity() {
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertEquals(4, flyway.info().applied().length);
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(13, em.getMetamodel().getEntities().size());
        assertEquals(Long.class, em.getMetamodel().entity(Notification.class).getIdType().getJavaType());
        assertEquals(1, em.getMetamodel().getEntities().stream().filter(e -> e.getName().equals("Notification")).count());
        assertTrue(em.getMetamodel().getEntities().stream().noneMatch(e -> e.getJavaType().getPackageName().equals("com.valor.entity")));
    }

    @Test void schemaHasSignedKeysRestrictiveFkNamedChecksAndExactIndexes() {
        assertEquals(2, db.queryForObject("select count(*) from information_schema.columns where table_schema='PUBLIC' and table_name='NOTIFICATIONS' and column_name in ('ID','RECIPIENT_USER_ID') and data_type='BIGINT'", Integer.class));
        assertEquals("RESTRICT", db.queryForObject("select delete_rule from information_schema.referential_constraints where constraint_schema='PUBLIC' and constraint_name='FK_NOTIFICATIONS_RECIPIENT'", String.class));
        assertEquals(List.of("RECIPIENT_USER_ID","STATUS","CREATED_AT"), db.queryForList("select column_name from information_schema.index_columns where index_schema='PUBLIC' and index_name='IDX_NOTIFICATIONS_RECIPIENT_STATE' order by ordinal_position", String.class));
        assertEquals(List.of("STATUS","SCHEDULED_AT"), db.queryForList("select column_name from information_schema.index_columns where index_schema='PUBLIC' and index_name='IDX_NOTIFICATIONS_SCHEDULED' order by ordinal_position", String.class));
        assertEquals(2, db.queryForObject("select count(*) from information_schema.check_constraints where constraint_schema='PUBLIC' and constraint_name in ('CK_NOTIFICATIONS_CHANNEL','CK_NOTIFICATIONS_STATUS')", Integer.class));
        User recipient = user(Role.CUSTOMER);
        db.update("insert into notifications(recipient_user_id,title,message) values(?,?,?)", recipient.getId(), "Default", "Default");
        assertEquals("IN_APP", db.queryForObject("select channel from notifications where recipient_user_id=?", String.class, recipient.getId()));
        assertEquals("PENDING", db.queryForObject("select status from notifications where recipient_user_id=?", String.class, recipient.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("insert into notifications(recipient_user_id,title,message,channel) values(?,?,?,'UNKNOWN')", recipient.getId(), "Invalid", "Invalid"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("insert into notifications(recipient_user_id,title,message,status) values(?,?,?,'UNKNOWN')", recipient.getId(), "Invalid", "Invalid"));
        assertThrows(DataIntegrityViolationException.class, () -> db.update("insert into notifications(recipient_user_id,title,message) values(?,?,?)", Long.MAX_VALUE, "Invalid", "Invalid"));
    }

    @Test void adminAndSuperAdminCreatePendingInAppForEveryUserRole() throws Exception {
        for (Role adminRole : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
            User admin = user(adminRole);
            for (Role role : Role.values()) {
                User recipient = user(role); JsonNode notification = create(admin, recipient);
                assertEquals(recipient.getId(), notification.get("recipientUserId").asLong());
                assertEquals("IN_APP", notification.get("channel").asText());
                assertEquals("PENDING", notification.get("status").asText());
                assertTrue(notification.get("sentAt").isNull()); assertTrue(notification.get("readAt").isNull());
                assertEquals(1, inbox(recipient).get("totalElements").asInt());
            }
        }
    }

    @Test void customerAndTechnicianCannotCreateNotifications() throws Exception {
        User recipient = user(Role.CUSTOMER);
        call(post("/api/v1/notifications"), user(Role.CUSTOMER), input(recipient), 403);
        call(post("/api/v1/notifications"), user(Role.TECHNICIAN), input(recipient), 403);
        assertEquals(0, inbox(recipient).get("totalElements").asInt());
    }

    @Test void externalChannelsAreRejectedWithoutCreatingDeliveryRecords() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER);
        for (String channel : List.of("EMAIL","SMS","PUSH")) {
            Map<String,Object> input = new HashMap<>(input(recipient)); input.put("channel", channel);
            call(post("/api/v1/notifications"), admin, input, 400);
        }
        assertEquals(0, db.queryForObject("select count(*) from notifications where recipient_user_id=?", Integer.class, recipient.getId()));
    }

    @Test void recipientMustExistAndBeActive() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER); recipient.setActive(false); users.saveAndFlush(recipient);
        call(post("/api/v1/notifications"), admin, input(recipient), 400);
        call(post("/api/v1/notifications"), admin, Map.of("recipientUserId", Long.MAX_VALUE, "title", "Invalid", "message", "Invalid"), 404);
        assertEquals(0, db.queryForObject("select count(*) from notifications where recipient_user_id=?", Integer.class, recipient.getId()));
    }

    @Test void recipientCannotOverrideJwtScopeOrReadAnotherUsersNotification() throws Exception {
        User admin = user(Role.ADMIN), first = user(Role.CUSTOMER), second = user(Role.TECHNICIAN);
        long own = create(admin, first).get("id").asLong(), other = create(admin, second).get("id").asLong();
        JsonNode result = call(get("/api/v1/notifications").param("recipientUserId", second.getId().toString()), first, null, 200);
        assertEquals(1, result.get("totalElements").asInt()); assertEquals(own, result.at("/items/0/id").asLong());
        read(first, other, 403); read(admin, own, 403);
        assertEquals("PENDING", db.queryForObject("select status from notifications where id=?", String.class, other));
    }

    @Test void scheduleIsHiddenUntilExactDueTimeAndCannotBeReadEarly() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER);
        Map<String,Object> input = new HashMap<>(input(recipient)); input.put("scheduledAt", "2032-01-01T13:00:00");
        long id = call(post("/api/v1/notifications"), admin, input, 200).get("id").asLong();
        assertEquals(0, inbox(recipient).get("totalElements").asInt()); read(recipient, id, 404);
        assertNull(db.queryForObject("select read_at from notifications where id=?", java.sql.Timestamp.class, id));
        clock.set("2032-01-01T13:00:00Z");
        assertEquals(id, inbox(recipient).at("/items/0/id").asLong()); read(recipient, id, 200);
    }

    @Test void pastSchedulesAndUnscheduledRecordsAreImmediatelyVisible() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER);
        create(admin, recipient);
        Map<String,Object> input = new HashMap<>(input(recipient)); input.put("scheduledAt", "2031-12-31T23:59:59");
        call(post("/api/v1/notifications"), admin, input, 200);
        assertEquals(2, inbox(recipient).get("totalElements").asInt());
    }

    @Test void readingIsIdempotentAndNeverMarksExternalDelivery() throws Exception {
        User recipient = user(Role.CUSTOMER); long id = create(user(Role.ADMIN), recipient).get("id").asLong();
        JsonNode first = read(recipient, id, 200);
        assertEquals("READ", first.get("status").asText()); assertEquals("2032-01-01T12:00:00", first.get("readAt").asText());
        clock.set("2032-01-02T12:00:00Z"); JsonNode second = read(recipient, id, 200);
        assertEquals(first.get("readAt"), second.get("readAt")); assertTrue(second.get("sentAt").isNull());
        assertEquals(1, db.queryForObject("select count(*) from notifications where id=?", Integer.class, id));
    }

    @Test void pagingAndStatusFiltersAreBoundedAndRecipientScoped() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER);
        long readId = create(admin, recipient).get("id").asLong(); create(admin, recipient); create(admin, user(Role.CUSTOMER)); read(recipient, readId, 200);
        JsonNode read = call(get("/api/v1/notifications").param("status", "READ").param("size", "1"), recipient, null, 200);
        assertEquals(1, read.get("totalElements").asInt()); assertEquals(readId, read.at("/items/0/id").asLong());
        assertEquals(1, call(get("/api/v1/notifications").param("status", "PENDING"), recipient, null, 200).get("totalElements").asInt());
        call(get("/api/v1/notifications").param("page", "-1"), recipient, null, 400);
        call(get("/api/v1/notifications").param("size", "101"), recipient, null, 400);
        call(get("/api/v1/notifications").param("status", "INVALID"), recipient, null, 400);
    }

    @Test void invalidPayloadsAnd401404ErrorsUseSafeEnvelopes() throws Exception {
        User admin = user(Role.ADMIN), recipient = user(Role.CUSTOMER);
        call(get("/api/v1/notifications"), null, null, 401);
        call(post("/api/v1/notifications"), null, input(recipient), 401);
        call(put("/api/v1/notifications/1/read"), null, null, 401);
        read(recipient, Long.MAX_VALUE, 404);
        for (String field : List.of("status", "sentAt", "readAt")) {
            Map<String,Object> input = new HashMap<>(input(recipient)); input.put(field, "SENT");
            call(post("/api/v1/notifications"), admin, input, 400);
        }
        call(post("/api/v1/notifications"), admin, Map.of("recipientUserId", recipient.getId(), "title", " ", "message", "Valid"), 400);
        call(post("/api/v1/notifications"), admin, Map.of("recipientUserId", recipient.getId(), "title", "x".repeat(201), "message", "Valid"), 400);
    }

    @Test void concurrentReadsPreserveOneOriginalReadTimestamp() throws Exception {
        User recipient = user(Role.CUSTOMER); long id = create(user(Role.ADMIN), recipient).get("id").asLong();
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<JsonNode> read = () -> { start.await(); return read(recipient, id, 200); };
            Future<JsonNode> first = executor.submit(read), second = executor.submit(read); start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS).get("readAt"), second.get(10, TimeUnit.SECONDS).get("readAt"));
            assertEquals("READ", db.queryForObject("select status from notifications where id=?", String.class, id));
        } finally { executor.shutdownNow(); }
    }

    @Test void noUnversionedNotificationAliasOrDeletionEndpointIsMapped() {
        var notificationMappings = mappings.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getValue().getBeanType().getPackageName().equals("com.valor.notifications")).toList();
        assertEquals(3, notificationMappings.size());
        assertTrue(notificationMappings.stream().allMatch(e -> e.getKey().getPatternValues().stream().allMatch(p -> p.startsWith("/api/v1/notifications"))));
        assertTrue(notificationMappings.stream().noneMatch(e -> e.getKey().getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.DELETE)));
    }
}
