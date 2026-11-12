package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.reporting.DailyPendingReportService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:daily_report;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "valor.reports.item-limit=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DailyPendingReportTest {
    @Autowired JdbcTemplate db;
    @Autowired DailyPendingReportService reports;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired TechRepo technicians;
    @Autowired JwtService jwt;
    @MockitoBean Clock clock;
    private User admin;
    private long customer, technician, helper, building;
    private final LocalDate today = LocalDate.of(2026, 11, 12);

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-11T18:45:00Z"));
        admin = user(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin.getId(), null, List.of()));
        CustomerProfile owner = new CustomerProfile(); owner.user = user(Role.CUSTOMER); owner.fullName = "Report fixture";
        customer = customers.saveAndFlush(owner).id;
        technician = technician(); helper = technician();
        building = insert("INSERT INTO buildings(customer_id,building_name) VALUES(?,?)", customer, "Report fixture");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void categoriesUseRecordedDatesExcludeTerminalJobsAndIncludeHelpers() {
        long lift = lift(today), missing = lift(null), future = lift(today.plusDays(1));
        contract(lift); contract(missing); contract(future);
        job(lift, "PENDING");
        long overdue = job(lift, "ACCEPTED"), dueToday = job(lift, "ASSIGNED"), completed = job(lift, "COMPLETED");
        job(lift, "CANCELLED");
        assign(overdue); assign(dueToday); assign(completed);
        visit(overdue, today.minusDays(1)); visit(dueToday, today); visit(completed, today.minusDays(2));
        insert("INSERT INTO service_team_members(service_request_id,lead_technician_profile_id,helper_technician_profile_id,status,request_reason,requested_by_user_id) VALUES(?,?,?,'APPROVED','Fixture',?)", overdue, technician, helper, admin.getId());
        var result = reports.current();
        assertEquals(today, result.reportDate());
        assertEquals(1, result.sections().get("pendingBookings").total());
        assertEquals(1, result.sections().get("unassignedJobs").total());
        assertEquals(1, result.sections().get("overdueJobs").total());
        assertEquals(overdue, result.sections().get("overdueJobs").items().getFirst().id());
        assertEquals(1, result.sections().get("amcServicesDue").total());
        assertEquals(1, result.sections().get("amcScheduleMissing").total());
        assertEquals(3, result.sections().get("technicianWorkPending").total());
        assertTrue(result.sections().get("technicianWorkPending").truncated());
    }

    @Test void repeatReadsAreIdempotentAndLimitsNeverHideTotals() {
        long lift = lift(null);
        for (int i = 0; i < 4; i++) job(lift, "PENDING");
        long records = db.queryForObject("SELECT COUNT(*) FROM communication_events", Long.class);
        var first = reports.current();
        assertEquals(first, reports.current());
        assertEquals(4, first.sections().get("pendingBookings").total());
        assertEquals(2, first.sections().get("pendingBookings").items().size());
        assertTrue(first.sections().get("pendingBookings").truncated());
        assertEquals(records, db.queryForObject("SELECT COUNT(*) FROM communication_events", Long.class));
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM service_requests", Integer.class));
    }

    @Test void failedProcessesAreReportedWithoutSensitivePayloadsOrRetries() throws Exception {
        long event = insert("INSERT INTO communication_events(event_type,recipient_user_id,idempotency_key,status) VALUES('TEST',?,?,'FAILED')", admin.getId(), UUID.randomUUID().toString());
        insert("INSERT INTO communication_messages(event_id,recipient_user_id,channel,status,body,failure_reason) VALUES(?,?,'EMAIL','FAILED','PRIVATE_PAYLOAD','PRIVATE_REASON')", event, admin.getId());
        insert("INSERT INTO payment_records(customer_id,created_by_user_id,amount,status) VALUES(?,?,1,'FAILED')", customer, admin.getId());
        var result = reports.current();
        assertEquals(2, result.sections().get("failedProcesses").total());
        assertFalse(json.writeValueAsString(result).contains("PRIVATE_"));
        assertEquals(0, db.queryForObject("SELECT retry_count FROM communication_messages WHERE event_id=?", Integer.class, event));
    }

    @Test void adminTimezoneControlsMidnightAndDaylightSavingBoundaries() {
        assertEquals("2026-11-11T18:30:00Z", reports.current().dayStart().toString());
        db.update("UPDATE admin_settings SET timezone='America/New_York' WHERE id=1");
        when(clock.instant()).thenReturn(Instant.parse("2026-03-08T12:00:00Z"));
        var result = reports.current();
        assertEquals(LocalDate.of(2026, 3, 8), result.reportDate());
        assertEquals(23, Duration.between(result.dayStart(), result.nextDayStart()).toHours());
        assertEquals("daily-pending:2026-03-08:America/New_York", result.reportKey());
    }

    @Test void emptySectionsAreExplicitAndEndpointRequiresAnAdministrator() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/admin/reports/daily-pending")).andExpect(status().isUnauthorized());
        for (Role role : Role.values()) {
            User actor = user(role);
            var request = mvc.perform(get("/api/v1/admin/reports/daily-pending").header("Authorization", "Bearer " + jwt.issue(actor)));
            if (role == Role.ADMIN || role == Role.SUPER_ADMIN) {
                request.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.sections.pendingBookings.total").value(0))
                        .andExpect(jsonPath("$.data.sections.pendingBookings.items").isEmpty());
            } else request.andExpect(status().isForbidden());
        }
    }

    private User user(Role role) { User u = new User(); u.setEmail(UUID.randomUUID() + "@example.test"); u.setRole(role); return users.saveAndFlush(u); }
    private long technician() { TechnicianProfile t = new TechnicianProfile(); t.user = user(Role.TECHNICIAN); return technicians.saveAndFlush(t).id; }
    private long lift(LocalDate due) { return insert("INSERT INTO lifts(building_id,name,next_maintenance_date) VALUES(?,'Fixture',?)", building, due); }
    private void contract(long lift) { insert("INSERT INTO amc_contracts(lift_id,amc_number,plan,start_date,end_date) VALUES(?,?,'Standard',?,?)", lift, UUID.randomUUID().toString(), today.minusDays(20), today.plusDays(20)); }
    private long job(long lift, String status) { return insert("INSERT INTO service_requests(customer_id,lift_id,service_id,title,description,status,service_type,preferred_visit_date) VALUES(?,?,?,'Fixture','Fixture',?,'BREAKDOWN',?)", customer, lift, UUID.randomUUID().toString(), status, today.minusDays(2)); }
    private void assign(long job) { insert("INSERT INTO technician_assignments(service_request_id,technician_id,assigned_by_user_id) VALUES(?,?,?)", job, technician, admin.getId()); }
    private void visit(long job, LocalDate date) { insert("INSERT INTO service_visits(service_request_id,technician_profile_id,scheduled_date,start_time,end_time) VALUES(?,?,?,'09:00:00','10:00:00')", job, technician, date); }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        db.update(connection -> { var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, key);
        return ((Number) Objects.requireNonNull(key.getKeys()).get("ID")).longValue();
    }
}
