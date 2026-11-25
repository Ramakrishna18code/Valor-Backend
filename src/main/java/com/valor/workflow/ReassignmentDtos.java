package com.valor.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

final class ReassignmentDtos {
    private ReassignmentDtos() {}
    interface StrictWrite { @com.fasterxml.jackson.annotation.JsonAnySetter default void reject(String name, JsonNode value) { throw new IllegalArgumentException("Unsupported reassignment field."); } }
    record Handover(@Size(max=2000) String workCompleted, @Size(max=2000) String workPending, @Size(max=2000) String partsSafetyContext, @Size(max=2000) String otherContext) implements StrictWrite {}
    record Create(@NotNull ReassignmentReason reasonCode, @NotBlank @Size(max=2000) String reasonExplanation, Handover handover) implements StrictWrite {}
    record Decision(@NotBlank @Size(max=2000) String reason) implements StrictWrite {}
    record Approve(@NotNull @Positive Long replacementTechnicianProfileId, @Size(max=2000) String decisionReason) implements StrictWrite {}
    record View(Long id, Long serviceRequestId, Long originalAssignmentId, Long requestingTechnicianProfileId, ReassignmentReason reasonCode,
            String reasonExplanation, String workCompleted, String workPending, String partsSafetyContext, String otherContext,
            ReassignmentStatus status, Long reviewedByUserId, String decisionReason, Long replacementTechnicianProfileId, Long newAssignmentId,
            LocalDateTime createdAt, LocalDateTime decidedAt, LocalDateTime transferredAt, LocalDateTime withdrawnAt, LocalDateTime cancelledAt) {}
    record AssignmentHistory(Long id, Long serviceRequestId, Long technicianProfileId, AssignmentStatus status, Long assignedByUserId,
            LocalDateTime assignedAt, LocalDateTime acceptedAt, LocalDateTime releasedAt, String notes) {}
    record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
