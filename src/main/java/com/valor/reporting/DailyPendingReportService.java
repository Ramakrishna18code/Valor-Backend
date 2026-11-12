package com.valor.reporting;

import com.valor.auth.AssetIdentityAccess;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Current operational snapshot. No historical reconstruction, writes, delivery, or scheduler. */
@Service
public class DailyPendingReportService {
    private static final String OPEN = "r.status NOT IN ('COMPLETED','CANCELLED')";
    private static final String JOB = "SELECT 'SERVICE_REQUEST' AS source,r.id,r.service_id AS reference,r.status,"
            + "r.preferred_visit_date AS due_date,NULL AS technician_id FROM service_requests r WHERE ";
    private static final String ACTIVE_AMC = "EXISTS(SELECT 1 FROM amc_contracts c WHERE c.lift_id=l.id "
            + "AND c.status IN ('ACTIVE','RENEWED') AND c.start_date<=? AND c.end_date>=?)";
    private final JdbcTemplate db;
    private final AssetIdentityAccess identities;
    private final Clock clock;
    private final ZoneId fallbackZone;
    private final int itemLimit;

    public DailyPendingReportService(JdbcTemplate db, AssetIdentityAccess identities, Clock clock,
            @Value("${valor.reports.timezone:Asia/Kolkata}") String timezone,
            @Value("${valor.reports.item-limit:200}") int itemLimit) {
        this.db = db;
        this.identities = identities;
        this.clock = clock;
        this.fallbackZone = ZoneId.of(timezone);
        if (itemLimit < 1 || itemLimit > 1000) throw new IllegalArgumentException("Report item limit must be between 1 and 1000");
        this.itemLimit = itemLimit;
    }

    public record Item(String source, long id, String reference, String status, LocalDate dueDate, Long technicianProfileId) {}
    public record Section(long total, List<Item> items, boolean truncated) {}
    public record Report(String reportKey, LocalDate reportDate, String timezone, Instant generatedAt,
            Instant dayStart, Instant nextDayStart, int itemLimit, Map<String, Section> sections, List<String> notes) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report current() {
        identities.requireAdmin();
        var zones = db.queryForList("SELECT timezone FROM admin_settings WHERE id=1", String.class);
        ZoneId zone = zones.isEmpty() ? fallbackZone : ZoneId.of(zones.get(0));
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        var sections = new LinkedHashMap<String, Section>();
        sections.put("pendingBookings", section(JOB + "r.status='PENDING'"));
        sections.put("unassignedJobs", section(JOB + OPEN + " AND NOT EXISTS(SELECT 1 FROM technician_assignments a "
                + "WHERE a.service_request_id=r.id AND a.status IN ('ASSIGNED','ACCEPTED'))"));
        sections.put("overdueJobs", section("SELECT 'SERVICE_REQUEST' AS source,r.id,r.service_id AS reference,r.status,"
                + "v.scheduled_date AS due_date,v.technician_profile_id AS technician_id FROM service_requests r "
                + "JOIN service_visits v ON v.service_request_id=r.id WHERE " + OPEN
                + " AND v.status IN ('SCHEDULED','IN_PROGRESS') AND v.scheduled_date<?", today));
        String lifts = "SELECT 'LIFT' AS source,l.id,l.lift_number AS reference,l.current_status AS status,"
                + "l.next_maintenance_date AS due_date,NULL AS technician_id FROM lifts l JOIN buildings b ON b.id=l.building_id "
                + "WHERE l.is_active=true AND b.is_active=true AND " + ACTIVE_AMC;
        sections.put("amcServicesDue", section(lifts + " AND l.next_maintenance_date<=?", today, today, today));
        sections.put("amcScheduleMissing", section(lifts + " AND l.next_maintenance_date IS NULL", today, today));
        sections.put("technicianWorkPending", section(
                "SELECT 'TECHNICIAN_JOB' AS source,r.id,r.service_id AS reference,r.status,r.preferred_visit_date AS due_date,"
                + "a.technician_id FROM service_requests r JOIN technician_assignments a ON a.service_request_id=r.id WHERE "
                + OPEN + " AND a.status IN ('ASSIGNED','ACCEPTED') UNION "
                + "SELECT 'TECHNICIAN_JOB' AS source,r.id,r.service_id AS reference,r.status,r.preferred_visit_date AS due_date,"
                + "t.helper_technician_profile_id AS technician_id FROM service_requests r JOIN service_team_members t "
                + "ON t.service_request_id=r.id WHERE " + OPEN + " AND t.status='APPROVED'"));
        sections.put("failedProcesses", section(
                failureQuery("communication_messages", "COMMUNICATION", "status='FAILED'") + " UNION ALL "
                + failureQuery("payment_records", "PAYMENT", "status='FAILED'") + " UNION ALL "
                + failureQuery("payment_refunds", "REFUND", "status='FAILED'") + " UNION ALL "
                + failureQuery("technician_payout_batches", "PAYOUT", "status IN ('FAILED','RECONCILIATION_REQUIRED')") + " UNION ALL "
                + failureQuery("finance_notification_outbox", "FINANCE_OUTBOX", "status='PENDING' AND attempts>0")));
        return new Report("daily-pending:" + today + ":" + zone.getId(), today, zone.getId(), now,
                today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(), itemLimit,
                Collections.unmodifiableMap(sections), List.of(
                "Current state at generation time; this is not a historical end-of-day snapshot. Repeated reads have no side effects.",
                "Overdue means an unfinished job with an active visit scheduled before the report date. Preferred dates are not SLA deadlines.",
                "AMC due uses recorded lift maintenance dates and currently valid ACTIVE/RENEWED contracts; coverage and payment eligibility remain separate checks.",
                "AMC lifts without a maintenance date are reported separately; no recurrence is invented.",
                "Technician work counts unique job/technician pairs, including approved helpers; categories overlap and must not be summed.",
                "Failed processes count records by source, not unique incidents. No retry or delivery is triggered.",
                "Totals include every matching row; each section includes at most itemLimit rows, ordered by due date then source/id/technician."));
    }

    private Section section(String query, Object... args) {
        long total = Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM (" + query + ") pending_rows", Long.class, args));
        var items = db.query("SELECT * FROM (" + query + ") pending_rows ORDER BY CASE WHEN due_date IS NULL THEN 1 ELSE 0 END,"
                        + "due_date,source,id,technician_id LIMIT " + itemLimit,
                (rs, index) -> new Item(rs.getString("source"), rs.getLong("id"), rs.getString("reference"),
                        rs.getString("status"), rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
                        rs.getObject("technician_id") == null ? null : ((Number) rs.getObject("technician_id")).longValue()), args);
        return new Section(total, List.copyOf(items), total > items.size());
    }

    // All arguments are internal constants, never request input. No provider payloads or recipient details are returned.
    private static String failureQuery(String table, String source, String predicate) {
        return "SELECT '" + source + "' AS source,id,NULL AS reference,status,NULL AS due_date,NULL AS technician_id FROM " + table + " WHERE " + predicate;
    }
}
