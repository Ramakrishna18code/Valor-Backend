package com.valor.workflow;

import com.valor.auth.*;
import com.valor.response.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.workflow.ServiceVisitDtos.*;

@Service
@Transactional
public class ServiceVisitService {
    private static final EnumSet<VisitStatus> ACTIVE = EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.IN_PROGRESS);
    private final ServiceVisitRepository visits;
    private final VisitChangeRequestRepository changeRequests;
    private final VisitHistoryRepository history;
    private final VisitTechnicianRepository technicians;
    private final RequestRepository requests;
    private final AssignmentRepository assignments;
    private final WorkflowIdentityAccess profiles;
    private final AssetIdentityAccess identities;

    public ServiceVisitService(ServiceVisitRepository visits, VisitChangeRequestRepository changeRequests,
            VisitHistoryRepository history, VisitTechnicianRepository technicians, RequestRepository requests,
            AssignmentRepository assignments, WorkflowIdentityAccess profiles, AssetIdentityAccess identities) {
        this.visits = visits; this.changeRequests = changeRequests; this.history = history;
        this.technicians = technicians; this.requests = requests; this.assignments = assignments;
        this.profiles = profiles; this.identities = identities;
    }

    @Transactional(readOnly = true)
    public PageView<VisitView> adminList(LocalDate fromDate, LocalDate toDate, Long technicianId,
            Long requestId, VisitStatus status, int page, int size) {
        User actor = identities.actor(); admin(actor);
        Page<ServiceVisit> result = visits.search(fromDate, toDate, technicianId, requestId, status, paging(page, size));
        return page(result, actor);
    }

    @Transactional(readOnly = true)
    public VisitView adminDetail(Long id) {
        User actor = identities.actor(); admin(actor);
        return view(detailEntity(id), actor);
    }

    public VisitView create(VisitCreateRequest input) {
        User actor = identities.actor(); admin(actor);
        ServiceRequest request = lockedRequest(input.serviceRequestId());
        ensureRequestSchedulable(request);
        TechnicianProfile technician = lockTechnician(input.technicianProfileId());
        ensureAssigned(request, technician);
        validateWindow(input.scheduledDate(), input.startTime(), input.endTime());
        ensureAvailable(request.getId(), technician.getId(), input.scheduledDate(), input.startTime(), input.endTime(), null);
        if (!visits.activeForRequest(request.getId(), ACTIVE).isEmpty()) throw conflict("Service request already has an active visit");
        ServiceVisit visit = new ServiceVisit();
        visit.setRequest(request); visit.setTechnician(technician); visit.setScheduledDate(input.scheduledDate());
        visit.setStartTime(input.startTime()); visit.setEndTime(input.endTime()); visit.setNotes(input.notes());
        visits.saveAndFlush(visit); record(visit, actor, "CREATED", null);
        return view(visit, actor);
    }

    public VisitView update(Long id, VisitUpdateRequest input) {
        User actor = identities.actor(); admin(actor);
        ServiceVisit visit = lockedVisit(id);
        if (visit.getStatus() != VisitStatus.SCHEDULED) throw conflict("Only scheduled visits can be rescheduled");
        ServiceRequest request = lockedRequest(visit.getRequest().getId());
        ensureRequestSchedulable(request);
        TechnicianProfile newTechnician = lockTechnician(input.technicianProfileId());
        ensureAssigned(request, newTechnician);
        validateWindow(input.scheduledDate(), input.startTime(), input.endTime());
        ensureAvailable(request.getId(), newTechnician.getId(), input.scheduledDate(), input.startTime(), input.endTime(), visit.getId());
        TechnicianProfile previousTechnician = visit.getTechnician();
        boolean technicianChanged = !previousTechnician.getId().equals(newTechnician.getId());
        visit.setTechnician(newTechnician); visit.setScheduledDate(input.scheduledDate());
        visit.setStartTime(input.startTime()); visit.setEndTime(input.endTime()); visit.setNotes(input.notes());
        visits.flush();
        VisitHistory change = record(visit, actor, technicianChanged ? "TECHNICIAN_CHANGED" : "RESCHEDULED", input.notes());
        change.setFromTechnician(previousTechnician);
        return view(visit, actor);
    }

    public VisitView cancel(Long id, String reason, User actor) {
        ServiceVisit visit = lockedVisit(id);
        if (actor.getRole() == Role.TECHNICIAN) {
            TechnicianProfile technician = profiles.technician(actor);
            if (!visit.getTechnician().getId().equals(technician.getId())) throw denied();
        } else admin(actor);
        requireReason(reason);
        if (visit.getStatus() == VisitStatus.COMPLETED || visit.getStatus() == VisitStatus.CANCELLED) throw conflict("Visit is terminal");
        VisitStatus from = visit.getStatus(); visit.setStatus(VisitStatus.CANCELLED);
        VisitHistory event = record(visit, actor, "CANCELLED", reason); event.setFromStatus(from); event.setToStatus(VisitStatus.CANCELLED);
        visits.flush();
        return view(visit, actor);
    }

    @Transactional(readOnly = true)
    public PageView<VisitView> technicianList(LocalDate fromDate, LocalDate toDate, VisitStatus status, int page, int size) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        return page(visits.technicianVisits(technician.getId(), fromDate, toDate, status, paging(page, size)), actor);
    }

    @Transactional(readOnly = true)
    public VisitView technicianDetail(Long id) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        ServiceVisit visit = detailEntity(id);
        if (!visit.getTechnician().getId().equals(technician.getId())) throw denied();
        return view(visit, actor);
    }

    public VisitView progress(Long id, VisitProgressRequest input) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        ServiceVisit visit = lockedVisit(id);
        if (!visit.getTechnician().getId().equals(technician.getId())) throw denied();
        if (input.status() != VisitStatus.IN_PROGRESS && input.status() != VisitStatus.COMPLETED) throw conflict("Invalid visit progress");
        if (visit.getStatus() == VisitStatus.SCHEDULED && input.status() != VisitStatus.IN_PROGRESS
                || visit.getStatus() == VisitStatus.IN_PROGRESS && input.status() != VisitStatus.COMPLETED
                || visit.getStatus() != VisitStatus.SCHEDULED && visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw conflict("Invalid visit progress");
        }
        VisitStatus from = visit.getStatus(); visit.setStatus(input.status()); visit.setNotes(input.notes());
        VisitHistory event = record(visit, actor, "STATUS_CHANGED", input.notes()); event.setFromStatus(from); event.setToStatus(input.status());
        visits.flush();
        return view(visit, actor);
    }

    public VisitChangeRequestView requestReschedule(Long id, ChangeRequestCreate input) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        ServiceVisit visit = lockedVisit(id);
        if (!visit.getTechnician().getId().equals(technician.getId())) throw denied();
        if (visit.getStatus() != VisitStatus.SCHEDULED) throw conflict("Only scheduled visits can be rescheduled");
        validateWindow(input.requestedDate(), input.requestedStartTime(), input.requestedEndTime());
        if (pendingForVisit(id, VisitChangeRequestType.RESCHEDULE)) throw conflict("A reschedule request is already pending");
        VisitChangeRequest request = newChange(visit, actor, technician, VisitChangeRequestType.RESCHEDULE, input);
        changeRequests.saveAndFlush(request); record(visit, actor, "RESCHEDULE_REQUESTED", input.reason());
        return changeView(request);
    }

    public VisitChangeRequestView requestAdditional(Long id, ChangeRequestCreate input) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        ServiceVisit visit = lockedVisit(id);
        if (!visit.getTechnician().getId().equals(technician.getId())) throw denied();
        if (visit.getStatus() == VisitStatus.CANCELLED) throw conflict("Cancelled visit cannot request additional work");
        ServiceRequest request = lockedRequest(visit.getRequest().getId()); ensureRequestSchedulable(request);
        validateWindow(input.requestedDate(), input.requestedStartTime(), input.requestedEndTime());
        if (pendingForVisit(id, VisitChangeRequestType.ADDITIONAL_VISIT)) throw conflict("An additional visit request is already pending");
        VisitChangeRequest change = newChange(visit, actor, technician, VisitChangeRequestType.ADDITIONAL_VISIT, input);
        changeRequests.saveAndFlush(change); record(visit, actor, "ADDITIONAL_VISIT_REQUESTED", input.reason());
        return changeView(change);
    }

    @Transactional(readOnly = true)
    public PageView<VisitChangeRequestView> changeList(VisitChangeRequestType type, VisitChangeRequestStatus status, int page, int size) {
        User actor = identities.actor(); admin(actor);
        Page<VisitChangeRequest> result = changeRequests.search(type, status, changeRequestPaging(page, size));
        return new PageView<>(result.getContent().stream().map(this::changeView).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public VisitView approve(Long id, ChangeRequestDecision input) {
        User actor = identities.actor(); admin(actor);
        VisitChangeRequest change = changeRequests.detail(id).orElseThrow(() -> missing("Visit change request not found"));
        if (change.getStatus() != VisitChangeRequestStatus.PENDING) throw conflict("Change request is already decided");
        ServiceRequest request = lockedRequest(change.getRequest().getId()); ensureRequestSchedulable(request);
        LocalDate date = input.scheduledDate() == null ? change.getRequestedDate() : input.scheduledDate();
        LocalTime start = input.startTime() == null ? change.getRequestedStartTime() : input.startTime();
        LocalTime end = input.endTime() == null ? change.getRequestedEndTime() : input.endTime();
        Long technicianId = input.technicianProfileId() == null ? change.getRequestedTechnician().getId() : input.technicianProfileId();
        TechnicianProfile technician = lockTechnician(technicianId); ensureAssigned(request, technician);
        validateWindow(date, start, end);
        if (change.getType() == VisitChangeRequestType.RESCHEDULE) {
            ServiceVisit visit = lockedVisit(change.getVisit().getId());
            if (visit.getStatus() != VisitStatus.SCHEDULED) throw conflict("Visit is no longer schedulable");
            ensureAvailable(request.getId(), technician.getId(), date, start, end, visit.getId());
            TechnicianProfile previousTechnician = visit.getTechnician();
            boolean technicianChanged = !previousTechnician.getId().equals(technician.getId());
            visit.setTechnician(technician); visit.setScheduledDate(date); visit.setStartTime(start); visit.setEndTime(end);
            visits.flush();
            VisitHistory changeEvent = record(visit, actor, technicianChanged ? "TECHNICIAN_CHANGED" : "RESCHEDULED", input.reviewNotes());
            changeEvent.setFromTechnician(previousTechnician);
            change.setStatus(VisitChangeRequestStatus.APPROVED); review(change, actor, input.reviewNotes());
            changeRequests.flush(); return view(visit, actor);
        }
        if (!visits.activeForRequest(request.getId(), ACTIVE).isEmpty()) throw conflict("Service request already has an active visit");
        ensureAvailable(request.getId(), technician.getId(), date, start, end, null);
        ServiceVisit visit = new ServiceVisit(); visit.setRequest(request); visit.setTechnician(technician);
        visit.setScheduledDate(date); visit.setStartTime(start); visit.setEndTime(end); visit.setNotes(input.reviewNotes());
        visits.saveAndFlush(visit); record(visit, actor, "CREATED", input.reviewNotes());
        change.setStatus(VisitChangeRequestStatus.APPROVED); review(change, actor, input.reviewNotes()); changeRequests.flush();
        return view(visit, actor);
    }

    public VisitChangeRequestView reject(Long id, String reason) {
        User actor = identities.actor(); admin(actor); requireReason(reason);
        VisitChangeRequest change = changeRequests.detail(id).orElseThrow(() -> missing("Visit change request not found"));
        if (change.getStatus() != VisitChangeRequestStatus.PENDING) throw conflict("Change request is already decided");
        change.setStatus(VisitChangeRequestStatus.REJECTED); review(change, actor, reason);
        if (change.getVisit() != null) record(change.getVisit(), actor, "REQUEST_REJECTED", reason);
        changeRequests.flush(); return changeView(change);
    }

    @Transactional(readOnly = true)
    public PageView<VisitView> customerList(Long requestId, VisitStatus status, int page, int size) {
        User actor = identities.actor(); CustomerProfile customer = profiles.customer(actor);
        Page<ServiceVisit> result = visits.customerVisits(customer.getId(), requestId, status, paging(page, size));
        return page(result, actor);
    }

    void cancelActiveForRequest(ServiceRequest request, User actor, String reason) {
        for (ServiceVisit visit : visits.activeForRequest(request.getId(), ACTIVE)) {
            VisitStatus from = visit.getStatus(); visit.setStatus(VisitStatus.CANCELLED);
            VisitHistory event = record(visit, actor, "REQUEST_CANCELLED", reason);
            event.setFromStatus(from); event.setToStatus(VisitStatus.CANCELLED);
        }
        visits.flush();
    }

    private VisitChangeRequest newChange(ServiceVisit visit, User actor, TechnicianProfile technician,
            VisitChangeRequestType type, ChangeRequestCreate input) {
        VisitChangeRequest request = new VisitChangeRequest(); request.setRequest(visit.getRequest()); request.setVisit(visit);
        request.setRequestedBy(actor); request.setRequestedTechnician(technician); request.setType(type); request.setReason(input.reason());
        request.setRequestedDate(input.requestedDate()); request.setRequestedStartTime(input.requestedStartTime()); request.setRequestedEndTime(input.requestedEndTime());
        return request;
    }
    private boolean pendingForVisit(Long id, VisitChangeRequestType type) {
        return changeRequests.existsByVisitIdAndTypeAndStatus(id, type, VisitChangeRequestStatus.PENDING);
    }
    private void review(VisitChangeRequest change, User actor, String notes) { change.setReviewedBy(actor); change.setReviewedAt(LocalDateTime.now()); change.setReviewNotes(notes); }
    private ServiceRequest lockedRequest(Long id) { return requests.lockById(id).orElseThrow(() -> missing("Service request not found")); }
    private ServiceVisit lockedVisit(Long id) { return visits.lockById(id).orElseThrow(() -> missing("Visit not found")); }
    private ServiceVisit detailEntity(Long id) { return visits.detail(id).orElseThrow(() -> missing("Visit not found")); }
    private TechnicianProfile lockTechnician(Long id) { return technicians.lockById(id).orElseThrow(() -> missing("Technician not found")); }
    private void ensureAssigned(ServiceRequest request, TechnicianProfile technician) {
        TechnicianAssignment assignment = assignments.active(request.getId()).orElseThrow(() -> conflict("Technician assignment is required"));
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw conflict("Technician is not assigned to this request");
    }
    private void ensureRequestSchedulable(ServiceRequest request) {
        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELLED) throw conflict("Request is terminal");
    }
    private void ensureAvailable(Long requestId, Long technicianId, LocalDate date, LocalTime start, LocalTime end, Long excludeId) {
        if (!visits.overlaps(technicianId, date, start, end, ACTIVE, excludeId).isEmpty()) throw conflict("Technician has an overlapping visit");
    }
    private static void validateWindow(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null || !start.isBefore(end)) throw new WorkflowException(400, "Invalid visit time range");
    }
    private VisitHistory record(ServiceVisit visit, User actor, String type, String reason) {
        VisitHistory event = new VisitHistory(visit, actor, type, reason); event.setScheduledDate(visit.getScheduledDate());
        event.setStartTime(visit.getStartTime()); event.setEndTime(visit.getEndTime()); event.setToStatus(visit.getStatus());
        event.setToTechnician(visit.getTechnician()); return history.save(event);
    }
    private VisitView view(ServiceVisit visit, User actor) {
        boolean customer = actor.getRole() == Role.CUSTOMER;
        List<VisitHistoryView> events = customer ? List.of() : history.findByVisitIdOrderByChangedAtAscIdAsc(visit.getId()).stream().map(this::historyView).toList();
        return new VisitView(visit.getId(), visit.getRequest().getId(), visit.getRequest().getServiceId(), visit.getRequest().getCustomer().getId(),
                visit.getRequest().getLift().getId(), visit.getRequest().getTitle(), customer ? null : visit.getTechnician().getId(),
                customer ? null : visit.getTechnician().getEmployeeId(), customer ? null : visit.getTechnician().getSpecialization(),
                visit.getScheduledDate(), visit.getStartTime(), visit.getEndTime(), visit.getStatus(), customer ? null : visit.getNotes(),
                events, visit.getCreatedAt(), visit.getUpdatedAt());
    }
    private VisitHistoryView historyView(VisitHistory event) {
        return new VisitHistoryView(event.getId(), event.getEventType(), event.getFromStatus(), event.getToStatus(), event.getChangedBy().getId(),
                event.getFromTechnician() == null ? null : event.getFromTechnician().getId(), event.getToTechnician() == null ? null : event.getToTechnician().getId(),
                event.getScheduledDate(), event.getStartTime(), event.getEndTime(), event.getReason(), event.getChangedAt());
    }
    private VisitChangeRequestView changeView(VisitChangeRequest request) {
        return new VisitChangeRequestView(request.getId(), request.getRequest().getId(), request.getVisit() == null ? null : request.getVisit().getId(),
                request.getType(), request.getStatus(), request.getRequestedBy().getId(), request.getRequestedTechnician().getId(), request.getRequestedDate(),
                request.getRequestedStartTime(), request.getRequestedEndTime(), request.getReason(), request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewNotes(), request.getCreatedAt(), request.getUpdatedAt());
    }
    private PageView<VisitView> page(Page<ServiceVisit> result, User actor) {
        return new PageView<>(result.getContent().stream().map(v -> view(v, actor)).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    private Pageable paging(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new WorkflowException(400, "Invalid page"); return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "scheduledDate", "startTime", "id")); }
    private Pageable changeRequestPaging(int page, int size) { if (page < 0 || size < 1 || size > 100) throw new WorkflowException(400, "Invalid page"); return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")); }
    private static void requireReason(String reason) { if (reason == null || reason.isBlank()) throw new WorkflowException(400, "Reason is required"); }
    private static void admin(User actor) { if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied(); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    private static WorkflowException conflict(String message) { return new WorkflowException(409, message); }
    private static WorkflowException missing(String message) { return new WorkflowException(404, message); }
}
