package com.valor.communication;

import com.valor.auth.Role;
import com.valor.auth.User;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EmailEventService {
    private static final Logger log = LoggerFactory.getLogger(EmailEventService.class);
    private final CommunicationService communication;
    private final CommunicationUserRepository users;
    private final ApplicationEventPublisher events;
    private static final Set<CommunicationChannel> STANDARD = Set.of(CommunicationChannel.EMAIL, CommunicationChannel.SMS, CommunicationChannel.WHATSAPP);

    public EmailEventService(CommunicationService communication, CommunicationUserRepository users, ApplicationEventPublisher events) {
        this.communication = communication; this.users = users; this.events = events;
    }

    public void customerAccountCreated(Long userId, String name, String setPasswordUrl) {
        mandatory("CUSTOMER_ACCOUNT_CREATED", userId, "customer-account-created:" + userId,
                vars("name", name, "setPasswordUrl", setPasswordUrl));
    }
    public void technicianAccountCreated(Long userId, String name, String setPasswordUrl) {
        mandatory("TECHNICIAN_ACCOUNT_CREATED", userId, "technician-account-created:" + userId,
                vars("name", name, "setPasswordUrl", setPasswordUrl));
    }
    public void serviceRequestCreated(Long userId, String name, Long requestId, String serviceId, String title, String status) {
        optional("SERVICE_REQUEST_CREATED", userId, "service-request-created:" + requestId,
                vars("name", name, "serviceId", serviceId, "title", title, "status", status));
        adminBroadcast("ADMIN_CRITICAL_SERVICE_EVENT", "admin-service-request-created:" + requestId,
                vars("title", "Service request created", "message", "Service request " + serviceId + " was created", "serviceId", serviceId, "status", status));
    }
    public void serviceRequestStatusChanged(Long userId, String name, Long requestId, String serviceId, String fromStatus, String status) {
        optional("SERVICE_REQUEST_STATUS_CHANGED", userId, "service-request-status:" + requestId + ":" + status,
                vars("name", name, "serviceId", serviceId, "fromStatus", fromStatus, "status", status));
    }
    public void serviceCompleted(Long userId, String name, Long requestId, String serviceId) {
        optional("SERVICE_COMPLETED", userId, "service-completed:" + requestId,
                vars("name", name, "serviceId", serviceId));
        optional("FEEDBACK_REQUEST", userId, "feedback-request:" + requestId,
                vars("name", name, "serviceId", serviceId));
    }
    public void technicianAssignment(Long userId, String name, Long requestId, String serviceId, String title) {
        optional("TECHNICIAN_ASSIGNMENT", userId, "technician-assignment:" + requestId + ":" + userId,
                vars("name", name, "serviceId", serviceId, "title", title));
    }
    public void technicianJobStatusChanged(Long userId, String name, Long requestId, String serviceId, String fromStatus, String status) {
        optional("TECHNICIAN_JOB_STATUS_CHANGED", userId, "technician-job-status:" + requestId + ":" + userId + ":" + status,
                vars("name", name, "serviceId", serviceId, "fromStatus", fromStatus, "status", status));
    }
    public void appointmentScheduled(Long customerUserId, String customerName, Long technicianUserId, String technicianName,
            Long visitId, Long requestId, String serviceId, String date, String startTime, String endTime) {
        Map<String,String> common = vars("serviceId", serviceId, "visitId", visitId, "date", date, "startTime", startTime, "endTime", endTime);
        optional("APPOINTMENT_SCHEDULED", customerUserId, "appointment-scheduled:" + visitId + ":customer", named(common, customerName));
        optional("TECHNICIAN_VISIT_SCHEDULED", technicianUserId, "technician-visit-scheduled:" + visitId, named(common, technicianName));
    }
    public void appointmentChanged(Long customerUserId, String customerName, Long technicianUserId, String technicianName,
            Long visitId, String serviceId, String date, String startTime, String endTime) {
        Map<String,String> common = vars("serviceId", serviceId, "visitId", visitId, "date", date, "startTime", startTime, "endTime", endTime);
        optional("APPOINTMENT_CHANGED", customerUserId, "appointment-changed:" + visitId + ":" + date + ":" + startTime, named(common, customerName));
        optional("TECHNICIAN_VISIT_RESCHEDULED", technicianUserId, "technician-visit-rescheduled:" + visitId + ":" + date + ":" + startTime, named(common, technicianName));
    }
    public void appointmentCancelled(Long customerUserId, String customerName, Long visitId, String serviceId, String reason) {
        optional("APPOINTMENT_CANCELLED", customerUserId, "appointment-cancelled:" + visitId,
                vars("name", customerName, "serviceId", serviceId, "reason", reason));
    }
    public void changeRequestDecision(Long technicianUserId, String technicianName, Long changeRequestId, String type, String status) {
        optional("TECHNICIAN_CHANGE_REQUEST_DECISION", technicianUserId, "technician-change-decision:" + changeRequestId + ":" + status,
                vars("name", technicianName, "changeRequestId", changeRequestId, "type", type, "status", status));
    }
    public void amcRenewalRequest(Long userId, String name, Long renewalId, Long contractId) {
        optional("AMC_RENEWAL_REQUEST", userId, "amc-renewal-request:" + renewalId,
                vars("name", name, "contractId", contractId));
    }
    public void amcCreated(Long userId, String name, Long contractId, String amcNumber) {
        optional("AMC_CREATED", userId, "amc-created:" + contractId,
                vars("name", name, "contractId", contractId, "amcNumber", amcNumber));
    }
    public void invoiceCreated(Long userId, String name, Long invoiceId, String invoiceNumber, String amount, String currency) {
        optional("INVOICE_CREATED", userId, "invoice-created:" + invoiceId,
                vars("name", name, "invoiceNumber", invoiceNumber, "amount", amount, "currency", currency));
    }
    public void paymentResult(Long userId, String name, Long paymentId, String status) {
        optional("PAYMENT_RESULT", userId, "payment-result:" + paymentId + ":" + status,
                vars("name", name, "paymentId", paymentId, "status", status));
    }
    public void adminAlert(Long adminUserId, String title, String message, String idempotencyKey) {
        optional("ADMIN_SYSTEM_ALERT", adminUserId, idempotencyKey, vars("title", title, "message", message));
    }
    public void reportReady(String reportType, String idempotencyKey) {
        adminBroadcast("ADMIN_REPORT_READY", idempotencyKey, vars("reportType", reportType));
    }

    private void optional(String eventType, Long userId, String key, Map<String,String> variables) {
        if (userId != null) publish(new CommunicationAutomationEvent(eventType, userId, false, false, key, variables, false));
    }
    private void mandatory(String eventType, Long userId, String key, Map<String,String> variables) {
        if (userId != null) publish(new CommunicationAutomationEvent(eventType, userId, false, true, key, variables, true));
    }
    private void adminBroadcast(String eventType, String key, Map<String,String> variables) {
        publish(new CommunicationAutomationEvent(eventType, null, true, false, key, variables, false));
    }
    private void publish(CommunicationAutomationEvent event) {
        events.publishEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAutomationEvent(CommunicationAutomationEvent event) {
        try {
            if (event.adminBroadcast()) {
                for (User admin : users.activeRoles(Set.of(Role.ADMIN, Role.SUPER_ADMIN))) {
                    communication.enqueueApplication(event.eventType(), admin.getId(), STANDARD, event.variables(),
                            event.idempotencyKey() + ":admin:" + admin.getId(), event.mandatory());
                }
            } else if (event.recipientUserId() != null && event.emailOnly()) {
                communication.enqueueApplicationEmail(event.eventType(), event.recipientUserId(), event.variables(), event.idempotencyKey(), true);
            } else if (event.recipientUserId() != null) {
                communication.enqueueApplication(event.eventType(), event.recipientUserId(), STANDARD, event.variables(), event.idempotencyKey(), event.mandatory());
            }
        } catch (RuntimeException ex) {
            log.warn("communication automation failed event={} recipient={} key={} reason={}",
                    event.eventType(), event.recipientUserId(), event.idempotencyKey(), safe(ex.getMessage()));
        }
    }
    private static Map<String,String> named(Map<String,String> values, String name) {
        Map<String,String> copy = new LinkedHashMap<>(values); copy.put("name", safe(name)); return copy;
    }
    private static Map<String,String> vars(Object... values) {
        Map<String,String> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(String.valueOf(values[i]), safe(values[i + 1]));
        return map;
    }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("(?i)(password|token|secret|otp|key)=\\S+", "$1=***"); }
}

record CommunicationAutomationEvent(String eventType, Long recipientUserId, boolean adminBroadcast,
        boolean emailOnly, String idempotencyKey, Map<String,String> variables, boolean mandatory) {}
