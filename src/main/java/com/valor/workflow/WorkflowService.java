package com.valor.workflow;

import com.valor.auth.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.workflow.WorkflowDtos.*;

@Service
@Transactional
public class WorkflowService {
    private final RequestRepository requests;
    private final AssignmentRepository assignments;
    private final HistoryRepository history;
    private final ReportRepository reports;
    private final WorkflowLiftRepository lifts;
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;

    public WorkflowService(RequestRepository requests, AssignmentRepository assignments, HistoryRepository history,
            ReportRepository reports, WorkflowLiftRepository lifts, AssetIdentityAccess identities, WorkflowIdentityAccess profiles) {
        this.requests = requests; this.assignments = assignments; this.history = history;
        this.reports = reports; this.lifts = lifts; this.identities = identities; this.profiles = profiles;
    }

    public Detail create(CreateRequest input) {
        User actor = identities.actor();
        CustomerProfile owner;
        if (actor.getRole() == Role.CUSTOMER) {
            if (input.customerProfileId() != null || input.internalAdminNotes() != null || input.estimatedCompletionMinutes() != null) {
                throw new WorkflowException(400, "Customer ownership and admin fields cannot be submitted");
            }
            owner = profiles.customer(actor);
        } else {
            admin(actor);
            if (input.customerProfileId() == null) throw new WorkflowException(400, "Customer profile is required");
            owner = identities.activeCustomer(input.customerProfileId());
        }
        var lift = lifts.forIntake(input.liftId()).orElseThrow(() -> missing());
        if (!lift.getBuilding().getCustomer().getId().equals(owner.getId())) throw denied();
        if (!lift.isActive() || !lift.getBuilding().isActive()) throw new WorkflowException(409, "Asset is inactive");
        ServiceRequest request = new ServiceRequest();
        request.setCustomer(owner); request.setLift(lift); request.setServiceId("SR-" + UUID.randomUUID());
        request.setTitle(input.title()); request.setDescription(input.description()); request.setIssueCategory(input.issueCategory());
        request.setPriority(input.priority() == null ? RequestPriority.MEDIUM : input.priority());
        request.setServiceType(input.serviceType()); request.setCustomerRemarks(input.customerRemarks());
        request.setPreferredVisitDate(input.preferredVisitDate()); request.setPreferredTimeSlot(input.preferredTimeSlot());
        request.setInternalAdminNotes(input.internalAdminNotes()); request.setEstimatedCompletionMinutes(input.estimatedCompletionMinutes());
        requests.saveAndFlush(request);
        event(request, null, actor, null);
        return detail(request, actor);
    }

    @Transactional(readOnly = true)
    public Detail read(Long id, boolean technicianOnly) {
        User actor = identities.actor();
        if (technicianOnly && actor.getRole() != Role.TECHNICIAN) throw denied();
        ServiceRequest request = requests.findById(id).orElseThrow(WorkflowService::missing);
        authorizeRead(request, actor);
        return detail(request, actor);
    }

    @Transactional(readOnly = true)
    public PageView<RequestView> list(RequestStatus status, RequestPriority priority, int page, int size) {
        User actor = identities.actor(); admin(actor);
        return page(requests.search(status, priority, paging(page, size)), actor);
    }

    @Transactional(readOnly = true)
    public PageView<RequestView> jobs(RequestStatus status, int page, int size) {
        User actor = identities.actor();
        var technician = profiles.technician(actor);
        return page(requests.jobs(technician.getId(), status, paging(page, size)), actor);
    }

    public Detail assign(Long id, AssignRequest input) {
        User actor = identities.actor(); admin(actor);
        ServiceRequest request = locked(id); nonterminal(request);
        var technician = profiles.activeTechnician(input.technicianProfileId());
        TechnicianAssignment previous = assignments.active(id).orElse(null);
        if (previous == null && request.getStatus() != RequestStatus.PENDING) throw conflict();
        if (previous != null) {
            previous.setStatus(AssignmentStatus.RELEASED); previous.setReleasedAt(LocalDateTime.now());
            assignments.flush(); // Free the generated unique key before inserting the new assignment.
        }
        TechnicianAssignment assignment = new TechnicianAssignment();
        assignment.setRequest(request); assignment.setTechnician(technician); assignment.setAssignedBy(actor);
        assignment.setNotes(input.notes()); assignments.saveAndFlush(assignment);
        RequestStatus from = request.getStatus();
        if (from == RequestStatus.PENDING) request.setStatus(RequestStatus.ASSIGNED);
        // Reassignment is an operational event: preserve advanced lifecycle state, never regress it.
        event(request, from, actor, input.notes());
        requests.flush();
        return detail(request, actor);
    }

    public Detail accept(Long id, Long assignmentId) {
        User actor = identities.actor();
        ServiceRequest request = locked(id); nonterminal(request);
        TechnicianAssignment assignment = ownedActive(request, actor);
        if (!assignment.getId().equals(assignmentId)) throw denied();
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) throw conflict();
        RequestStatus from = request.getStatus();
        assignment.setStatus(AssignmentStatus.ACCEPTED); assignment.setAcceptedAt(LocalDateTime.now());
        if (from == RequestStatus.ASSIGNED) request.setStatus(RequestStatus.ACCEPTED);
        event(request, from, actor, null);
        requests.flush();
        return detail(request, actor);
    }

    public Detail status(Long id, StatusRequest input) {
        User actor = identities.actor();
        ServiceRequest request = locked(id); nonterminal(request);
        TechnicianAssignment assignment;
        if (actor.getRole() == Role.TECHNICIAN) assignment = ownedActive(request, actor);
        else { admin(actor); assignment = assignments.active(id).orElse(null); }
        RequestStatus from = request.getStatus(), to = input.toStatus();
        if (!next(from).contains(to)) throw new WorkflowException(409, "Invalid status transition");
        if (to == RequestStatus.ASSIGNED) throw new WorkflowException(409, "Use the assignment endpoint");
        if ((to == RequestStatus.CANCELLED || to == RequestStatus.WAITING_FOR_PARTS) && blank(input.notes())) {
            throw new WorkflowException(400, "Transition notes are required");
        }
        if (to != RequestStatus.CANCELLED && assignment == null) throw conflict();
        if (to == RequestStatus.ACCEPTED) {
            if (assignment.getStatus() != AssignmentStatus.ASSIGNED) throw conflict();
            assignment.setStatus(AssignmentStatus.ACCEPTED); assignment.setAcceptedAt(LocalDateTime.now());
        } else if (to != RequestStatus.CANCELLED && assignment.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new WorkflowException(409, "Assignment must be accepted first");
        }
        if (to == RequestStatus.COMPLETED) {
            ServiceReport report = reports.findByRequestId(id).orElseThrow(() -> new WorkflowException(409, "Valid report required"));
            if (!validReport(report, request, assignment)) throw new WorkflowException(409, "Valid report required");
            request.setCompletedAt(LocalDateTime.now()); assignment.setStatus(AssignmentStatus.COMPLETED);
        }
        if (to == RequestStatus.CANCELLED && assignment != null) {
            assignment.setStatus(AssignmentStatus.RELEASED); assignment.setReleasedAt(LocalDateTime.now());
        }
        request.setStatus(to); event(request, from, actor, input.notes());
        requests.flush();
        return detail(request, actor);
    }

    public ReportView report(Long id, ReportRequest input) {
        User actor = identities.actor();
        ServiceRequest request = locked(id); nonterminal(request);
        TechnicianAssignment assignment = ownedActive(request, actor);
        if (blank(input.diagnosis()) || blank(input.workPerformed()) || blank(input.testingResult())) {
            throw new WorkflowException(400, "Report fields are required");
        }
        ServiceReport report = reports.findByRequestId(id).orElseGet(ServiceReport::new);
        report.setRequest(request); report.setAssignment(assignment); report.setReportedBy(actor);
        report.setDiagnosis(input.diagnosis()); report.setWorkPerformed(input.workPerformed());
        report.setTestingResult(input.testingResult()); report.setCompletionNotes(input.completionNotes());
        reports.saveAndFlush(report);
        return reportView(report);
    }

    private boolean validReport(ServiceReport report, ServiceRequest request, TechnicianAssignment assignment) {
        return report.getRequest().getId().equals(request.getId()) && report.getAssignment().getId().equals(assignment.getId())
                && report.getReportedBy().getId().equals(assignment.getTechnician().getUser().getId())
                && !blank(report.getDiagnosis()) && !blank(report.getWorkPerformed()) && !blank(report.getTestingResult());
    }
    private ServiceRequest locked(Long id) { return requests.lockById(id).orElseThrow(WorkflowService::missing); }
    private void nonterminal(ServiceRequest request) {
        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELLED) {
            throw new WorkflowException(409, "Request is terminal");
        }
    }
    private TechnicianAssignment ownedActive(ServiceRequest request, User actor) {
        var technician = profiles.technician(actor);
        TechnicianAssignment assignment = assignments.active(request.getId()).orElseThrow(WorkflowService::denied);
        if (!assignment.getTechnician().getId().equals(technician.getId())) throw denied();
        return assignment;
    }
    private void authorizeRead(ServiceRequest request, User actor) {
        if (actor.getRole() == Role.CUSTOMER) {
            var customer = profiles.customer(actor);
            if (!request.getCustomer().getId().equals(customer.getId())) throw denied();
        } else if (actor.getRole() == Role.TECHNICIAN) ownedActive(request, actor);
        else admin(actor);
    }
    private void event(ServiceRequest request, RequestStatus from, User actor, String notes) {
        history.save(new ServiceStatusHistory(request, from, request.getStatus(), actor, notes));
    }
    private static void admin(User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
    private static WorkflowException missing() { return new WorkflowException(404, "Request or asset not found"); }
    private static WorkflowException conflict() { return new WorkflowException(409, "Workflow conflict"); }
    private Pageable paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new WorkflowException(400, "Invalid page");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
    private PageView<RequestView> page(Page<ServiceRequest> page, User actor) {
        return new PageView<>(page.getContent().stream().map(r -> view(r, actor)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    static Set<RequestStatus> next(RequestStatus status) {
        return switch (status) {
            case PENDING -> Set.of(RequestStatus.ASSIGNED, RequestStatus.CANCELLED);
            case ASSIGNED -> Set.of(RequestStatus.ACCEPTED, RequestStatus.CANCELLED);
            case ACCEPTED -> Set.of(RequestStatus.ON_THE_WAY, RequestStatus.CANCELLED);
            case ON_THE_WAY -> Set.of(RequestStatus.REACHED_SITE, RequestStatus.CANCELLED);
            case REACHED_SITE -> Set.of(RequestStatus.DIAGNOSIS, RequestStatus.CANCELLED);
            case DIAGNOSIS -> Set.of(RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.WAITING_FOR_PARTS, RequestStatus.CANCELLED);
            case REPAIR_IN_PROGRESS -> Set.of(RequestStatus.WAITING_FOR_PARTS, RequestStatus.TESTING, RequestStatus.CANCELLED);
            case WAITING_FOR_PARTS -> Set.of(RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.CANCELLED);
            case TESTING -> Set.of(RequestStatus.COMPLETED, RequestStatus.REPAIR_IN_PROGRESS, RequestStatus.CANCELLED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    private RequestView view(ServiceRequest r, User actor) {
        boolean admin = actor.getRole() == Role.ADMIN || actor.getRole() == Role.SUPER_ADMIN;
        return new RequestView(r.getId(), r.getServiceId(), r.getCustomer().getId(), r.getLift().getId(), r.getTitle(),
                r.getDescription(), r.getIssueCategory(), r.getPriority(), r.getStatus(), r.getServiceType(), r.getCustomerRemarks(),
                actor.getRole() == Role.CUSTOMER ? null : r.getTechnicianRemarks(), r.getServiceRequestedAt(), r.getPreferredVisitDate(),
                r.getPreferredTimeSlot(), admin ? r.getInternalAdminNotes() : null, r.getCompletedAt(), r.getEstimatedCompletionMinutes(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
    private Detail detail(ServiceRequest request, User actor) {
        var assignment = assignments.active(request.getId()).map(a -> new AssignmentView(a.getId(), request.getId(),
                a.getTechnician().getId(), a.getStatus(), a.getAssignedBy().getId(), a.getAssignedAt(), a.getAcceptedAt(), a.getReleasedAt(),
                actor.getRole() == Role.CUSTOMER ? null : a.getNotes())).orElse(null);
        var events = history.findByRequestIdOrderByChangedAtAscIdAsc(request.getId()).stream()
                .map(e -> new HistoryView(e.getId(), e.getFromStatus(), e.getToStatus(), e.getChangedBy().getId(),
                        actor.getRole() == Role.CUSTOMER ? null : e.getNotes(), e.getChangedAt())).toList();
        var report = actor.getRole() == Role.CUSTOMER ? null : reports.findByRequestId(request.getId()).map(this::reportView).orElse(null);
        return new Detail(view(request, actor), assignment, events, report);
    }
    private ReportView reportView(ServiceReport r) {
        return new ReportView(r.getId(), r.getRequest().getId(), r.getAssignment().getId(), r.getDiagnosis(), r.getWorkPerformed(),
                r.getTestingResult(), r.getCompletionNotes(), r.getReportedBy().getId(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
