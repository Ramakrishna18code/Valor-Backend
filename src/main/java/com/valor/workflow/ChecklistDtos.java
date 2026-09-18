package com.valor.workflow;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

final class ChecklistDtos {
    private ChecklistDtos() {}
    interface StrictInput { @JsonAnySetter default void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported checklist field"); } }
    record TemplateWrite(@NotBlank @Size(max=160) String name, @Size(max=1000) String description,
            Boolean active, Set<WorkflowServiceType> serviceTypes) implements StrictInput {}
    record ItemWrite(@NotBlank @Size(max=255) String label, @Size(max=1000) String description,
            Boolean required, @PositiveOrZero Integer sortOrder, ChecklistInputType inputType) implements StrictInput {}
    record ResponseWrite(@NotNull Long itemId, Boolean checked, @Size(max=2000) String valueText) implements StrictInput {}
    record ResponseBatch(List<ResponseWrite> responses) implements StrictInput {}
    record TemplateView(Long id, String name, String description, boolean active, Set<WorkflowServiceType> serviceTypes,
            int version, List<ItemView> items, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record ItemView(Long id, Long templateId, String label, String description, boolean required, int sortOrder, ChecklistInputType inputType) {}
    record ResponseView(Long itemId, Boolean checked, String valueText, Long technicianProfileId, LocalDateTime respondedAt) {}
    record JobChecklistView(Long id, Long serviceRequestId, Long serviceVisitId, Long templateId, String templateName,
            int templateVersion, JobChecklistStatus status, int requiredTotal, int requiredCompleted,
            List<ItemView> items, List<ResponseView> responses, LocalDateTime updatedAt) {}
}
