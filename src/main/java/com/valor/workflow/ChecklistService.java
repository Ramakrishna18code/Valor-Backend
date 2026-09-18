package com.valor.workflow;

import com.valor.auth.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.valor.workflow.ChecklistDtos.*;

@Service
@Transactional
class ChecklistService {
    private final ChecklistTemplateRepository templates;
    private final ChecklistItemRepository items;
    private final JobChecklistRepository jobs;
    private final ChecklistResponseRepository responses;
    private final RequestRepository requests;
    private final AssignmentRepository assignments;
    private final ServiceVisitRepository visits;
    private final AssetIdentityAccess identities;
    private final WorkflowIdentityAccess profiles;

    ChecklistService(ChecklistTemplateRepository templates, ChecklistItemRepository items, JobChecklistRepository jobs,
            ChecklistResponseRepository responses, RequestRepository requests, AssignmentRepository assignments,
            ServiceVisitRepository visits, AssetIdentityAccess identities, WorkflowIdentityAccess profiles) {
        this.templates = templates; this.items = items; this.jobs = jobs; this.responses = responses; this.requests = requests;
        this.assignments = assignments; this.visits = visits; this.identities = identities; this.profiles = profiles;
    }

    List<TemplateView> templates() { admin(); return templates.findAll().stream().sorted(Comparator.comparing(t -> t.id)).map(this::view).toList(); }
    TemplateView createTemplate(TemplateWrite input) {
        admin(); ChecklistTemplate t = new ChecklistTemplate(); apply(t, input); return view(templates.saveAndFlush(t));
    }
    TemplateView updateTemplate(Long id, TemplateWrite input) {
        admin(); ChecklistTemplate t = templates.findById(id).orElseThrow(ChecklistService::missing); apply(t, input); t.version++; return view(t);
    }
    ItemView addItem(Long templateId, ItemWrite input) {
        admin(); ChecklistTemplate t = templates.findById(templateId).orElseThrow(ChecklistService::missing);
        ChecklistItem item = new ChecklistItem(); item.template = t; apply(item, input); t.version++; return itemView(items.saveAndFlush(item));
    }
    ItemView updateItem(Long templateId, Long itemId, ItemWrite input) {
        admin(); ChecklistItem item = item(templateId, itemId); apply(item, input); item.template.version++; return itemView(item);
    }
    void deleteItem(Long templateId, Long itemId) {
        admin(); ChecklistItem item = item(templateId, itemId); item.template.version++; items.delete(item);
    }
    JobChecklistView technicianChecklist(Long requestId) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        ServiceRequest request = technicianRequest(requestId, technician);
        return jobs.findByRequestId(requestId).or(() -> createJobChecklist(request)).map(this::jobView).orElse(null);
    }
    JobChecklistView saveResponses(Long requestId, ResponseBatch input) {
        User actor = identities.actor(); TechnicianProfile technician = profiles.technician(actor);
        technicianRequest(requestId, technician);
        JobChecklist job = jobs.findByRequestId(requestId).orElseThrow(ChecklistService::missing);
        Map<Long, ChecklistItem> allowed = new HashMap<>();
        for (ChecklistItem item : items.findByTemplateIdOrderBySortOrderAscIdAsc(job.template.id)) allowed.put(item.id, item);
        for (ResponseWrite write : Optional.ofNullable(input.responses()).orElse(List.of())) {
            ChecklistItem item = allowed.get(write.itemId());
            if (item == null) throw new WorkflowException(400, "Invalid checklist item");
            ChecklistResponse response = responses.findByChecklistIdAndItemId(job.id, item.id).orElseGet(ChecklistResponse::new);
            response.checklist = job; response.item = item; response.technician = technician;
            response.checked = write.checked(); response.valueText = clean(write.valueText(), 2000); response.respondedAt = LocalDateTime.now();
            responses.save(response);
        }
        refreshStatus(job);
        return jobView(job);
    }
    void requireComplete(Long requestId) {
        jobs.findByRequestId(requestId).ifPresent(job -> {
            refreshStatus(job);
            if (job.status != JobChecklistStatus.COMPLETED) throw new WorkflowException(409, "Required checklist items must be completed");
        });
    }

    private Optional<JobChecklist> createJobChecklist(ServiceRequest request) {
        List<ChecklistTemplate> matches = templates.activeFor(request.getServiceType(), PageRequest.of(0, 1));
        if (matches.isEmpty()) return Optional.empty();
        JobChecklist job = new JobChecklist(); job.request = request; job.template = matches.get(0); job.templateVersion = job.template.version;
        visits.activeForRequest(request.getId(), List.of(VisitStatus.SCHEDULED, VisitStatus.IN_PROGRESS)).stream().findFirst().ifPresent(v -> job.visit = v);
        return Optional.of(jobs.saveAndFlush(job));
    }
    private void refreshStatus(JobChecklist job) {
        List<ChecklistItem> required = items.findByTemplateIdOrderBySortOrderAscIdAsc(job.template.id).stream().filter(i -> i.required).toList();
        Map<Long, ChecklistResponse> byItem = new HashMap<>();
        for (ChecklistResponse r : responses.findByChecklistId(job.id)) byItem.put(r.item.id, r);
        boolean complete = required.stream().allMatch(i -> {
            ChecklistResponse r = byItem.get(i.id);
            return r != null && (Boolean.TRUE.equals(r.checked) || r.valueText != null && !r.valueText.isBlank());
        });
        job.status = complete ? JobChecklistStatus.COMPLETED : JobChecklistStatus.IN_PROGRESS;
    }
    private ServiceRequest technicianRequest(Long requestId, TechnicianProfile technician) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(ChecklistService::missing);
        if (!assignments.existsByRequestIdAndTechnicianId(requestId, technician.getId())) throw denied();
        return request;
    }
    private void apply(ChecklistTemplate t, TemplateWrite input) {
        t.name = input.name().trim(); t.description = clean(input.description(), 1000);
        if (input.active() != null) t.active = input.active();
        t.serviceTypes.clear(); if (input.serviceTypes() != null) t.serviceTypes.addAll(input.serviceTypes());
    }
    private void apply(ChecklistItem item, ItemWrite input) {
        item.label = input.label().trim(); item.description = clean(input.description(), 1000);
        item.required = input.required() == null || input.required(); item.sortOrder = input.sortOrder() == null ? 0 : input.sortOrder();
        item.inputType = input.inputType() == null ? ChecklistInputType.CHECKBOX : input.inputType();
    }
    private ChecklistItem item(Long templateId, Long itemId) {
        ChecklistItem item = items.findById(itemId).orElseThrow(ChecklistService::missing);
        if (!item.template.id.equals(templateId)) throw missing(); return item;
    }
    private TemplateView view(ChecklistTemplate t) { return new TemplateView(t.id, t.name, t.description, t.active, t.serviceTypes, t.version, items.findByTemplateIdOrderBySortOrderAscIdAsc(t.id).stream().map(this::itemView).toList(), t.createdAt, t.updatedAt); }
    private ItemView itemView(ChecklistItem i) { return new ItemView(i.id, i.template.id, i.label, i.description, i.required, i.sortOrder, i.inputType); }
    private JobChecklistView jobView(JobChecklist job) {
        List<ChecklistItem> itemRows = items.findByTemplateIdOrderBySortOrderAscIdAsc(job.template.id);
        List<ChecklistResponse> responseRows = responses.findByChecklistId(job.id);
        int required = (int)itemRows.stream().filter(i -> i.required).count();
        int done = (int)itemRows.stream().filter(i -> i.required).filter(i -> responseRows.stream().anyMatch(r -> r.item.id.equals(i.id) && (Boolean.TRUE.equals(r.checked) || r.valueText != null && !r.valueText.isBlank()))).count();
        return new JobChecklistView(job.id, job.request.getId(), job.visit == null ? null : job.visit.getId(), job.template.id, job.template.name,
                job.templateVersion, job.status, required, done, itemRows.stream().map(this::itemView).toList(),
                responseRows.stream().map(r -> new ResponseView(r.item.id, r.checked, r.valueText, r.technician.getId(), r.respondedAt)).toList(), job.updatedAt);
    }
    private void admin() { User actor = identities.actor(); if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) throw denied(); }
    private static String clean(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); return v.length() > max ? v.substring(0, max) : v; }
    private static WorkflowException missing() { return new WorkflowException(404, "Checklist not found"); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Access denied"); }
}
