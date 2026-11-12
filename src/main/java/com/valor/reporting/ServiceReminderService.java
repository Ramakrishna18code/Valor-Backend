package com.valor.reporting;

import com.valor.auth.AssetIdentityAccess;
import com.valor.communication.CommunicationService;
import com.valor.notifications.NotificationService;
import com.valor.realtime.RealtimeEventService;
import com.valor.realtime.RealtimeEvent;
import java.sql.Date;
import java.sql.Time;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceReminderService {
    private final JdbcTemplate db;
    private final AssetIdentityAccess identities;
    private final NotificationService notifications;
    private final CommunicationService communications;
    private final RealtimeEventService realtime;
    private final Clock clock;
    private final ZoneId fallbackZone;
    private final TransactionTemplate eventScan;
    private volatile Instant lastScan = Instant.EPOCH;

    public ServiceReminderService(JdbcTemplate db, AssetIdentityAccess identities, NotificationService notifications, CommunicationService communications,
            RealtimeEventService realtime, Clock clock, PlatformTransactionManager transactionManager,
            @Value("${valor.reminders.timezone:Asia/Kolkata}") String timezone) {
        this.db = db; this.identities = identities; this.notifications = notifications; this.communications = communications; this.realtime = realtime;
        this.clock = clock; this.fallbackZone = ZoneId.of(timezone);
        this.eventScan = new TransactionTemplate(transactionManager);
    }

    public record Settings(int mediumDays, int upcomingDays, int scanIntervalMinutes, boolean reportEmailEnabled) {}
    public record Item(String sourceType, long sourceId, Long serviceRequestId, String serviceId, String customer,
            String building, String lift, Long liftId, String serviceType, LocalDate scheduledDate, LocalTime scheduledTime,
            String technician, String priority, String status, String stage, String amcContractNumber,
            Integer remainingAmcServices, LocalDate lastCompletedVisitDate, String requestPriority) {}
    public record Page(List<Item> items, int page, int size, long totalElements, int totalPages) {}
    public record Summary(LocalDate asOf, String timezone, long todayScheduled, long tomorrowScheduled,
            long upcoming, long overdue, long pendingAssignments, long amcDueSoon, long customerFollowUps,
            long emergencies, long pendingAcceptance, long inProgress, long totalPending) {}
    public record Alert(long id, long notificationId, String title, String message, String sourceType,
            long sourceId, String alertKind, LocalDate dueDate, boolean read, boolean resolved, LocalDateTime createdAt) {}
    public record AlertPage(List<Alert> items, int page, int size, long totalElements, int totalPages, long unreadCount) {}
    public record DailyReport(long id, LocalDate reportDate, String timezone, long totalPending, long todayScheduled,
            long overdue, long unassigned, long pendingAcceptance, long inProgress, long upcomingAmc,
            long customerApprovals, LocalDateTime generatedAt) {}

    @Transactional(readOnly = true)
    public Settings settings() { identities.requireAdmin(); return internalSettings(); }

    public Settings updateSettings(Settings input) {
        identities.requireAdmin();
        if (input == null || input.mediumDays() < 1 || input.mediumDays() > 6 || input.upcomingDays() < 4
                || input.upcomingDays() > 30 || input.mediumDays() >= input.upcomingDays()
                || input.scanIntervalMinutes() < 1 || input.scanIntervalMinutes() > 1440)
            throw new IllegalArgumentException("Choose valid reminder thresholds and scan interval.");
        db.update("UPDATE service_reminder_settings SET medium_days=?,upcoming_days=?,scan_interval_minutes=?,report_email_enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=1",
                input.mediumDays(), input.upcomingDays(), input.scanIntervalMinutes(), input.reportEmailEnabled());
        return internalSettings();
    }

    @Transactional(readOnly = true)
    public Summary summary() { identities.requireAdmin(); return snapshot(); }

    @Transactional(readOnly = true)
    public Page list(String tab, String search, LocalDate from, LocalDate to, String status, String sort,
            String direction, int page, int size) {
        return list(tab, search, from, to, status, sort, direction, null, page, size);
    }

    @Transactional(readOnly = true)
    public Page list(String tab, String search, LocalDate from, LocalDate to, String status, String sort,
            String direction, String bucket, int page, int size) {
        identities.requireAdmin();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page number.");
        if (search != null && search.length() > 254) throw new IllegalArgumentException("Search is too long.");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Date range is invalid.");
        if (!Set.of("ALL","TODAY","UPCOMING","OVERDUE","AMC","NON_AMC","COMPLETED").contains(tab))
            throw new IllegalArgumentException("Invalid reminder tab.");
        if (!Set.of("date","priority","serviceId","customer","status").contains(sort)) throw new IllegalArgumentException("Invalid sort field.");
        if (!Set.of("asc","desc").contains(direction)) throw new IllegalArgumentException("Invalid sort direction.");
        if (bucket != null && !Set.of("todayScheduled","tomorrowScheduled","upcoming","overdue","pendingAssignments","amcDueSoon","customerFollowUps","emergencies").contains(bucket))
            throw new IllegalArgumentException("Invalid reminder category.");
        var today = today(); var settings = internalSettings();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var filtered = all(today, settings).stream().filter(row -> switch (tab) {
            case "TODAY" -> today.equals(row.scheduledDate()) && !terminal(row);
            case "UPCOMING" -> row.scheduledDate() != null && row.scheduledDate().isAfter(today)
                    && !row.scheduledDate().isAfter(today.plusDays(settings.upcomingDays())) && !terminal(row);
            case "OVERDUE" -> overdue(row, today);
            case "AMC" -> "AMC".equals(row.serviceType());
            case "NON_AMC" -> "NON_AMC".equals(row.serviceType());
            case "COMPLETED" -> "COMPLETED".equals(row.status());
            default -> true;
        }).filter(row -> bucket == null || switch (bucket) {
            case "todayScheduled" -> "SERVICE_REQUEST".equals(row.sourceType()) && today.equals(row.scheduledDate()) && !terminal(row);
            case "tomorrowScheduled" -> "SERVICE_REQUEST".equals(row.sourceType()) && today.plusDays(1).equals(row.scheduledDate()) && !terminal(row);
            case "upcoming" -> row.scheduledDate() != null && row.scheduledDate().isAfter(today) && !row.scheduledDate().isAfter(today.plusDays(settings.upcomingDays())) && !terminal(row);
            case "overdue" -> overdue(row, today);
            case "pendingAssignments" -> "ASSIGNMENT_PENDING".equals(row.stage()) || "EMERGENCY".equals(row.stage());
            case "amcDueSoon" -> "AMC".equals(row.serviceType()) && !terminal(row) && row.scheduledDate() != null && !row.scheduledDate().isAfter(today.plusDays(settings.upcomingDays()));
            case "customerFollowUps" -> "CUSTOMER_APPROVAL".equals(row.stage()) || "PAYMENT_FOLLOW_UP".equals(row.stage());
            case "emergencies" -> "SERVICE_REQUEST".equals(row.sourceType()) && "EMERGENCY".equals(row.requestPriority()) && !terminal(row);
            default -> true;
        }).filter(row -> from == null || row.scheduledDate() != null && !row.scheduledDate().isBefore(from))
          .filter(row -> to == null || row.scheduledDate() != null && !row.scheduledDate().isAfter(to))
          .filter(row -> status == null || status.isBlank() || status.equals(row.status()) || status.equals(row.stage()))
          .filter(row -> query.isEmpty() || String.join(" ", row.serviceId(), row.customer(), row.building(), row.lift(),
                  String.valueOf(row.amcContractNumber()), String.valueOf(row.technician())).toLowerCase(Locale.ROOT).contains(query))
          .toList();
        Comparator<Item> comparator = switch (sort) {
            case "priority" -> Comparator.comparingInt(row -> switch (row.priority()) { case "CRITICAL" -> 0; case "HIGH" -> 1; case "MEDIUM" -> 2; default -> 3; });
            case "serviceId" -> Comparator.comparing(Item::serviceId);
            case "customer" -> Comparator.comparing(Item::customer);
            case "status" -> Comparator.comparing(Item::status);
            default -> Comparator.comparing(Item::scheduledDate, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if ("desc".equals(direction)) comparator = comparator.reversed();
        var sorted = filtered.stream().sorted(comparator.thenComparing(Item::sourceId)).toList();
        int start = Math.min(sorted.size(), page * size), end = Math.min(sorted.size(), start + size);
        return new Page(sorted.subList(start, end), page, size, sorted.size(), (sorted.size() + size - 1) / size);
    }

    private Summary snapshot() {
        LocalDate today = today(); Settings settings = internalSettings(); List<Item> rows = all(today, settings);
        return new Summary(today, zone().getId(), count(rows, r -> "SERVICE_REQUEST".equals(r.sourceType()) && today.equals(r.scheduledDate()) && !terminal(r)),
                count(rows, r -> "SERVICE_REQUEST".equals(r.sourceType()) && today.plusDays(1).equals(r.scheduledDate()) && !terminal(r)),
                count(rows, r -> r.scheduledDate() != null && r.scheduledDate().isAfter(today)
                        && !r.scheduledDate().isAfter(today.plusDays(settings.upcomingDays())) && !terminal(r)),
                count(rows, r -> overdue(r, today)),
                count(rows, r -> "ASSIGNMENT_PENDING".equals(r.stage()) || "EMERGENCY".equals(r.stage())),
                count(rows, r -> "AMC".equals(r.serviceType()) && !terminal(r) && r.scheduledDate() != null
                        && !r.scheduledDate().isAfter(today.plusDays(settings.upcomingDays()))),
                count(rows, r -> "CUSTOMER_APPROVAL".equals(r.stage()) || "PAYMENT_FOLLOW_UP".equals(r.stage())),
                count(rows, r -> "SERVICE_REQUEST".equals(r.sourceType()) && "EMERGENCY".equals(r.requestPriority()) && !terminal(r)),
                count(rows, r -> "ACCEPTANCE_PENDING".equals(r.stage())),
                count(rows, r -> "IN_PROGRESS".equals(r.stage())),
                count(rows, r -> !terminal(r)));
    }
    private static long count(List<Item> rows, java.util.function.Predicate<Item> match) { return rows.stream().filter(match).count(); }
    private static boolean terminal(Item row) { return "COMPLETED".equals(row.status()) || "CANCELLED".equals(row.status()); }
    private static boolean overdue(Item row, LocalDate today) { return !terminal(row) && row.scheduledDate() != null && row.scheduledDate().isBefore(today); }
    private ZoneId zone() {
        var configured = db.queryForList("SELECT timezone FROM admin_settings WHERE id=1", String.class);
        return configured.isEmpty() ? fallbackZone : ZoneId.of(configured.getFirst());
    }
    private LocalDate today() { return clock.instant().atZone(zone()).toLocalDate(); }
    private Settings internalSettings() {
        return db.queryForObject("SELECT medium_days,upcoming_days,scan_interval_minutes,report_email_enabled FROM service_reminder_settings WHERE id=1",
                (rs, ignored) -> new Settings(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getBoolean(4)));
    }
    private static LocalDate date(java.sql.ResultSet rs, String key) throws java.sql.SQLException { Date value = rs.getDate(key); return value == null ? null : value.toLocalDate(); }
    private static LocalTime time(java.sql.ResultSet rs, String key) throws java.sql.SQLException { Time value = rs.getTime(key); return value == null ? null : value.toLocalTime(); }
    private static Integer integer(java.sql.ResultSet rs, String key) throws java.sql.SQLException { Object value = rs.getObject(key); return value == null ? null : ((Number) value).intValue(); }
    private static Long longValue(java.sql.ResultSet rs, String key) throws java.sql.SQLException { Object value = rs.getObject(key); return value == null ? null : ((Number) value).longValue(); }

    private List<Item> all(LocalDate today, Settings settings) {
        String requests = "SELECT r.id,r.service_id,r.created_at AS request_created_at,c.full_name,COALESCE(b.building_name,'Not linked') AS building_name,"
                + "COALESCE(l.lift_number,l.name,'Not linked') AS lift_name,l.id AS lift_id,"
                + "CASE WHEN r.status='COMPLETED' THEN (SELECT MAX(cv.scheduled_date) FROM service_visits cv WHERE cv.service_request_id=r.id AND cv.status='COMPLETED') ELSE v.scheduled_date END AS scheduled_date,"
                + "v.start_time,COALESCE(t.public_name,t.employee_id,u.email) AS technician_name,r.status,r.priority,"
                + "r.service_type AS request_type,q.status AS quotation_status,q.valid_until AS followup_due,i.status AS invoice_status,ta.status AS assignment_status,"
                + "ac.amc_number,e.included_visit_limit,CASE WHEN d.outcome='COVERED' OR (d.id IS NULL AND e.covered=TRUE) THEN TRUE ELSE FALSE END AS amc_covered,"
                + "(SELECT COUNT(DISTINCT cv.id) FROM service_visits cv JOIN service_requests cr ON cr.id=cv.service_request_id "
                + "JOIN service_request_coverage_decisions cd ON cd.service_request_id=cr.id AND cd.scope='VISIT' "
                + "WHERE cr.lift_id=l.id AND cv.status='COMPLETED' AND cd.contract_id=ac.id AND cd.outcome='COVERED' "
                + "AND cd.id=(SELECT MAX(cd2.id) FROM service_request_coverage_decisions cd2 WHERE cd2.service_request_id=cr.id AND cd2.scope='VISIT')) AS completed_count,"
                + "(SELECT MAX(cv.scheduled_date) FROM service_visits cv JOIN service_requests cr ON cr.id=cv.service_request_id "
                + "WHERE cr.lift_id=l.id AND cv.status='COMPLETED') AS last_completed "
                + "FROM service_requests r JOIN customer_profiles c ON c.id=r.customer_id LEFT JOIN lifts l ON l.id=r.lift_id "
                + "LEFT JOIN buildings b ON b.id=l.building_id LEFT JOIN service_visits v ON v.service_request_id=r.id AND v.status IN ('SCHEDULED','IN_PROGRESS') "
                + "LEFT JOIN technician_assignments ta ON ta.service_request_id=r.id AND ta.status IN ('ASSIGNED','ACCEPTED') "
                + "LEFT JOIN technician_profiles t ON t.id=COALESCE(v.technician_profile_id,ta.technician_id) LEFT JOIN users u ON u.id=t.user_id "
                + "LEFT JOIN service_quotations q ON q.id=(SELECT MAX(q2.id) FROM service_quotations q2 WHERE q2.service_request_id=r.id) "
                + "LEFT JOIN invoices i ON i.id=q.invoice_id "
                + "LEFT JOIN service_request_coverage_decisions d ON d.id=(SELECT MAX(d2.id) FROM service_request_coverage_decisions d2 WHERE d2.service_request_id=r.id AND d2.scope='VISIT') "
                + "LEFT JOIN amc_contracts ac ON ac.id=COALESCE(d.contract_id,(SELECT MAX(ac2.id) FROM amc_contracts ac2 WHERE ac2.lift_id=l.id "
                + "AND ac2.status IN ('ACTIVE','RENEWED') AND ac2.start_date<=COALESCE(v.scheduled_date,?) AND ac2.end_date>=COALESCE(v.scheduled_date,?))) "
                + "LEFT JOIN amc_service_entitlements e ON e.contract_id=ac.id AND e.scope='VISIT' AND e.service_type=r.service_type "
                + "AND e.effective_from<=COALESCE(v.scheduled_date,?) AND e.effective_until>=COALESCE(v.scheduled_date,?)";
        List<Item> result = new ArrayList<>(db.query(requests, (rs, index) -> {
            long id = rs.getLong("id"); LocalDate due = date(rs, "scheduled_date"); String status = rs.getString("status");
            String assignment = rs.getString("assignment_status"), quote = rs.getString("quotation_status"), invoice = rs.getString("invoice_status");
            String stage = stage(status, rs.getString("priority"), due, assignment, quote, invoice);
            if ("NEW_REQUEST".equals(stage) && rs.getTimestamp("request_created_at").toLocalDateTime().toLocalDate().isBefore(today))
                stage = "QUOTATION_PENDING";
            String reminderPriority = priority(status, rs.getString("priority"), due, today, settings);
            LocalDate followupDue = date(rs, "followup_due");
            if (!"CRITICAL".equals(reminderPriority) && "CUSTOMER_APPROVAL".equals(stage) && followupDue != null
                    && !followupDue.isAfter(today)) reminderPriority = "HIGH";
            boolean amc = rs.getBoolean("amc_covered");
            Integer limit = integer(rs, "included_visit_limit");
            Integer remaining = !amc || limit == null ? null : Math.max(0, limit - rs.getInt("completed_count"));
            return new Item("SERVICE_REQUEST", id, id, rs.getString("service_id"), rs.getString("full_name"),
                    rs.getString("building_name"), rs.getString("lift_name"), longValue(rs, "lift_id"), amc ? "AMC" : "NON_AMC", due,
                    time(rs, "start_time"), rs.getString("technician_name"), reminderPriority,
                    status, stage, amc ? rs.getString("amc_number") : null, remaining, date(rs, "last_completed"), rs.getString("priority"));
        }, today, today, today, today));
        String amcDue = "SELECT l.id,c.full_name,b.building_name,COALESCE(l.lift_number,l.name) AS lift_name,l.next_maintenance_date,"
                + "ac.amc_number,e.included_visit_limit,"
                + "(SELECT COUNT(DISTINCT cv.id) FROM service_visits cv JOIN service_requests cr ON cr.id=cv.service_request_id "
                + "JOIN service_request_coverage_decisions cd ON cd.service_request_id=cr.id AND cd.scope='VISIT' "
                + "WHERE cr.lift_id=l.id AND cv.status='COMPLETED' AND cd.contract_id=ac.id AND cd.outcome='COVERED' "
                + "AND cd.id=(SELECT MAX(cd2.id) FROM service_request_coverage_decisions cd2 WHERE cd2.service_request_id=cr.id AND cd2.scope='VISIT')) AS completed_count,"
                + "(SELECT MAX(cv.scheduled_date) FROM service_visits cv JOIN service_requests cr ON cr.id=cv.service_request_id "
                + "WHERE cr.lift_id=l.id AND cv.status='COMPLETED') AS last_completed "
                + "FROM lifts l JOIN buildings b ON b.id=l.building_id JOIN customer_profiles c ON c.id=b.customer_id "
                + "JOIN amc_contracts ac ON ac.id=(SELECT MAX(ac2.id) FROM amc_contracts ac2 WHERE ac2.lift_id=l.id AND ac2.status IN ('ACTIVE','RENEWED') "
                + "AND ac2.start_date<=l.next_maintenance_date AND ac2.end_date>=l.next_maintenance_date) "
                + "JOIN amc_service_entitlements e ON e.contract_id=ac.id AND e.scope='VISIT' AND e.service_type='ROUTINE_MAINTENANCE' "
                + "AND e.covered=TRUE AND e.effective_from<=l.next_maintenance_date AND e.effective_until>=l.next_maintenance_date "
                + "WHERE l.is_active=TRUE AND b.is_active=TRUE AND l.next_maintenance_date IS NOT NULL "
                + "AND NOT EXISTS(SELECT 1 FROM service_requests sr JOIN service_visits sv ON sv.service_request_id=sr.id "
                + "WHERE sr.lift_id=l.id AND sr.status NOT IN ('COMPLETED','CANCELLED') AND sv.status IN ('SCHEDULED','IN_PROGRESS'))";
        result.addAll(db.query(amcDue, (rs, index) -> {
            LocalDate due = date(rs, "next_maintenance_date"); int completed = rs.getInt("completed_count"); Integer limit = integer(rs, "included_visit_limit");
            Integer remaining = limit == null ? null : Math.max(0, limit - completed);
            return new Item("AMC_DUE", rs.getLong("id"), null, rs.getString("amc_number"), rs.getString("full_name"),
                    rs.getString("building_name"), rs.getString("lift_name"), rs.getLong("id"), "AMC", due, null, null,
                    priority("DUE", null, due, today, settings), "DUE", "AMC_DUE", rs.getString("amc_number"), remaining,
                    date(rs, "last_completed"), null);
        }).stream().filter(row -> row.remainingAmcServices() == null || row.remainingAmcServices() > 0).toList());
        return result;
    }

    static String stage(String status, String requestPriority, LocalDate due, String assignment, String quote, String invoice) {
        if ("COMPLETED".equals(status)) return "ISSUED".equals(invoice) ? "PAYMENT_FOLLOW_UP" : "COMPLETED";
        if ("CANCELLED".equals(status)) return "CANCELLED";
        if ("EMERGENCY".equals(requestPriority) && assignment == null) return "EMERGENCY";
        if ("ISSUED".equals(quote)) return "CUSTOMER_APPROVAL";
        if (assignment == null && "ACCEPTED".equals(quote)) return "ASSIGNMENT_PENDING";
        if (assignment == null && "PENDING".equals(status)) return quote == null ? "NEW_REQUEST" : "ASSIGNMENT_PENDING";
        if ("TESTING".equals(status)) return "COMPLETION_PENDING";
        if ("ASSIGNED".equals(assignment)) return "ACCEPTANCE_PENDING";
        if (due != null && "ASSIGNED".equals(status)) return "SCHEDULED";
        return "IN_PROGRESS";
    }
    static String priority(String status, String requestPriority, LocalDate due, LocalDate today, Settings settings) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) return "NORMAL";
        if ("EMERGENCY".equals(requestPriority) || due != null && due.isBefore(today)) return "CRITICAL";
        if (today.equals(due)) return "HIGH";
        if (due != null && !due.isAfter(today.plusDays(settings.mediumDays()))) return "MEDIUM";
        return "NORMAL";
    }

    @Scheduled(fixedDelayString = "${valor.reminders.tick-ms:60000}", initialDelayString = "${valor.reminders.initial-delay-ms:60000}")
    @Transactional
    public void scheduledTick() {
        Instant now = clock.instant(); Settings settings = internalSettings();
        if (Duration.between(lastScan, now).toMinutes() >= settings.scanIntervalMinutes()) {
            lastScan = now; scan();
        }
        ZonedDateTime businessTime = now.atZone(zone());
        if (businessTime.getHour() >= 8) generateDailyReport();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowChange(RealtimeEvent event) {
        if ("SERVICE_VISIT".equals(event.entityType()) && "SERVICE_VISIT_CHANGED".equals(event.type())) {
            eventScan.executeWithoutResult(status -> scan());
            return;
        }
        if (!"SERVICE_REQUEST".equals(event.entityType()) || !Set.of("SERVICE_REQUEST_CREATED","JOB_ASSIGNMENT_CHANGED","SERVICE_REQUEST_STATUS_CHANGED").contains(event.type())) return;
        if ("SERVICE_REQUEST_CREATED".equals(event.type())) {
            var priorities = db.queryForList("SELECT priority FROM service_requests WHERE id=?", String.class, event.entityId());
            if (priorities.isEmpty() || !"EMERGENCY".equals(priorities.getFirst())) return;
        }
        eventScan.executeWithoutResult(status -> scan());
    }

    @Transactional
    public void scan() {
        LocalDate today = today(); Settings settings = internalSettings(); List<Item> rows = all(today, settings);
        var admins = db.queryForList("SELECT id FROM users WHERE role IN ('ADMIN','SUPER_ADMIN') AND is_active=TRUE", Long.class);
        var activeKeys = new HashSet<String>();
        for (Item row : rows) {
            if (terminal(row) && !"PAYMENT_FOLLOW_UP".equals(row.stage())) continue;
            String kind = alertKind(row, today, settings.upcomingDays());
            if (kind == null) continue;
            for (Long admin : admins) { activeKeys.add(alertKey(admin, row, kind)); createAlert(admin, row, kind); }
        }
        for (var alert : db.queryForList("SELECT id,event_key FROM service_reminder_alerts WHERE status='OPEN'"))
            if (!activeKeys.contains(alert.get("event_key")))
                db.update("UPDATE service_reminder_alerts SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=? AND status='OPEN'", alert.get("id"));
    }
    static String alertKind(Item row, LocalDate today, int upcomingDays) {
        if ("PAYMENT_FOLLOW_UP".equals(row.stage())) return "PAYMENT_FOLLOW_UP";
        if ("EMERGENCY".equals(row.stage())) return "EMERGENCY";
        if ("ASSIGNMENT_PENDING".equals(row.stage())) return "ASSIGNMENT_PENDING";
        if ("CUSTOMER_APPROVAL".equals(row.stage())) return "CUSTOMER_APPROVAL";
        if (row.scheduledDate() == null) return null;
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, row.scheduledDate());
        if (days < 0) return "OVERDUE";
        if (days == 0) return "DUE_TODAY";
        if (days == 1) return "DUE_1";
        if (days <= 3) return "DUE_3";
        return days <= upcomingDays ? "DUE_SOON" : null;
    }
    private void createAlert(Long admin, Item row, String kind) {
        String key = alertKey(admin, row, kind);
        if (db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE event_key=?", Long.class, key) > 0) return;
        String title = switch (kind) { case "OVERDUE" -> "Service overdue"; case "EMERGENCY" -> "Emergency service needs assignment";
            case "ASSIGNMENT_PENDING" -> "Technician assignment pending"; case "CUSTOMER_APPROVAL" -> "Customer approval pending";
            case "PAYMENT_FOLLOW_UP" -> "Payment follow-up pending";
            default -> "Service due soon"; };
        String message = row.serviceId() + " for " + row.customer() + " (" + row.lift() + ")"
                + (row.scheduledDate() == null ? " needs attention." : " is scheduled for " + row.scheduledDate() + ".");
        var notification = notifications.createSystemOnce(admin, key, title, message);
        try {
            db.update("INSERT INTO service_reminder_alerts(recipient_user_id,notification_id,source_type,source_id,alert_kind,due_date,event_key) VALUES(?,?,?,?,?,?,?)",
                    admin, notification.id(), row.sourceType(), row.sourceId(), kind, row.scheduledDate(), key);
            realtime.publish("SERVICE_REMINDER_CREATED", row.sourceType(), row.sourceId(), kind, Set.of(admin));
        } catch (DuplicateKeyException concurrentScan) { /* Database uniqueness owns deduplication. */ }
    }
    private static String alertKey(long admin, Item row, String kind) {
        return "service-reminder:" + admin + ":" + row.sourceType() + ":" + row.sourceId() + ":" + kind
                + ":" + (row.scheduledDate() == null ? "none" : row.scheduledDate());
    }

    @Transactional(readOnly = true)
    public AlertPage alerts(int page, int size) {
        identities.requireAdmin(); long user = identities.actor().getId();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid page number.");
        long total = db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts WHERE recipient_user_id=?", Long.class, user);
        long unread = db.queryForObject("SELECT COUNT(*) FROM service_reminder_alerts a JOIN notifications n ON n.id=a.notification_id "
                + "WHERE a.recipient_user_id=? AND a.status='OPEN' AND n.read_at IS NULL", Long.class, user);
        var items = db.query("SELECT a.*,n.title,n.message,n.read_at FROM service_reminder_alerts a JOIN notifications n ON n.id=a.notification_id "
                + "WHERE a.recipient_user_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?", (rs, index) ->
                new Alert(rs.getLong("id"), rs.getLong("notification_id"), rs.getString("title"), rs.getString("message"),
                        rs.getString("source_type"), rs.getLong("source_id"), rs.getString("alert_kind"), date(rs, "due_date"),
                        rs.getTimestamp("read_at") != null, "RESOLVED".equals(rs.getString("status")),
                        rs.getTimestamp("created_at").toLocalDateTime()), user, size, page * size);
        return new AlertPage(items, page, size, total, (int) ((total + size - 1) / size), unread);
    }
    public Alert readAlert(long id) {
        identities.requireAdmin(); long user = identities.actor().getId(); var row = alertOwned(id, user);
        notifications.read(((Number) row.get("notification_id")).longValue());
        return alertById(id, user);
    }
    public Alert resolveAlert(long id) {
        identities.requireAdmin(); long user = identities.actor().getId(); alertOwned(id, user);
        db.update("UPDATE service_reminder_alerts SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=?", id);
        return alertById(id, user);
    }
    private Map<String,Object> alertOwned(long id, long user) {
        var rows = db.queryForList("SELECT notification_id FROM service_reminder_alerts WHERE id=? AND recipient_user_id=?", id, user);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }
    private Alert alertById(long id, long user) {
        return db.queryForObject("SELECT a.*,n.title,n.message,n.read_at FROM service_reminder_alerts a JOIN notifications n ON n.id=a.notification_id "
                + "WHERE a.id=? AND a.recipient_user_id=?", (rs, index) -> new Alert(rs.getLong("id"),rs.getLong("notification_id"),
                rs.getString("title"),rs.getString("message"),rs.getString("source_type"),rs.getLong("source_id"),
                rs.getString("alert_kind"),date(rs,"due_date"),rs.getTimestamp("read_at")!=null,
                "RESOLVED".equals(rs.getString("status")),rs.getTimestamp("created_at").toLocalDateTime()), id, user);
    }

    public DailyReport generateDailyReport() {
        LocalDate today = today(); String timezone = zone().getId(); Summary s = snapshot();
        int created = db.update("INSERT INTO daily_service_reminder_reports(report_date,timezone,total_pending,today_scheduled,overdue,unassigned,pending_acceptance,in_progress,upcoming_amc,customer_approvals,generated_at) "
                + "SELECT ?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM daily_service_reminder_reports WHERE report_date=? AND timezone=?)",
                today, timezone, s.totalPending(), s.todayScheduled(), s.overdue(), s.pendingAssignments(), s.pendingAcceptance(),
                s.inProgress(), s.amcDueSoon(), s.customerFollowUps(), LocalDateTime.ofInstant(clock.instant(), zone()), today, timezone);
        if (created > 0 && internalSettings().reportEmailEnabled()) {
            var variables = Map.of("reportDate", today.toString(), "timezone", timezone,
                    "totalPending", String.valueOf(s.totalPending()), "todayScheduled", String.valueOf(s.todayScheduled()),
                    "overdue", String.valueOf(s.overdue()), "unassigned", String.valueOf(s.pendingAssignments()),
                    "pendingAcceptance", String.valueOf(s.pendingAcceptance()), "inProgress", String.valueOf(s.inProgress()),
                    "upcomingAmc", String.valueOf(s.amcDueSoon()), "customerApprovals", String.valueOf(s.customerFollowUps()));
            for (Long admin : db.queryForList("SELECT id FROM users WHERE role IN ('ADMIN','SUPER_ADMIN') AND is_active=TRUE", Long.class))
                communications.enqueueDailyServiceReport(admin, variables, "daily-service-report:" + today + ":" + timezone + ":" + admin);
        }
        return report(today, timezone);
    }
    @Transactional(readOnly = true)
    public List<DailyReport> reports() {
        identities.requireAdmin();
        return db.query("SELECT * FROM daily_service_reminder_reports ORDER BY report_date DESC,id DESC LIMIT 90", this::mapReport);
    }
    @Transactional(readOnly = true)
    public String reportCsv() {
        identities.requireAdmin();
        StringBuilder csv = new StringBuilder("Date,Timezone,Total pending,Today scheduled,Overdue,Unassigned,Pending acceptance,In progress,Upcoming AMC,Customer approvals,Generated at\r\n");
        for (DailyReport r : reports()) csv.append(r.reportDate()).append(',').append(r.timezone()).append(',')
                .append(r.totalPending()).append(',').append(r.todayScheduled()).append(',').append(r.overdue()).append(',')
                .append(r.unassigned()).append(',').append(r.pendingAcceptance()).append(',').append(r.inProgress()).append(',')
                .append(r.upcomingAmc()).append(',').append(r.customerApprovals()).append(',').append(r.generatedAt()).append("\r\n");
        return csv.toString();
    }
    private DailyReport report(LocalDate date, String timezone) {
        return db.queryForObject("SELECT * FROM daily_service_reminder_reports WHERE report_date=? AND timezone=?", this::mapReport, date, timezone);
    }
    private DailyReport mapReport(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        return new DailyReport(rs.getLong("id"),date(rs,"report_date"),rs.getString("timezone"),rs.getLong("total_pending"),
                rs.getLong("today_scheduled"),rs.getLong("overdue"),rs.getLong("unassigned"),rs.getLong("pending_acceptance"),
                rs.getLong("in_progress"),rs.getLong("upcoming_amc"),rs.getLong("customer_approvals"),
                rs.getTimestamp("generated_at").toLocalDateTime());
    }
}
