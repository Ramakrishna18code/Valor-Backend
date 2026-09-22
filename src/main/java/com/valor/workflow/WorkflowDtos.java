package com.valor.workflow;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;

public final class WorkflowDtos {
    private WorkflowDtos() {}
    public interface StrictWrite {
        @JsonAnySetter
        default void rejectUnknown(String field, JsonNode value) { throw new IllegalArgumentException("Unsupported workflow field"); }
    }
    public record CreateRequest(
            @Schema(description="Admin-only; required for admin creation. CUSTOMER submissions are rejected; customer ownership comes from JWT.") @Positive Long customerProfileId, @Positive Long liftId,
            @NotBlank @Size(max = 200) String title, @NotBlank String description,
            @Size(max = 100) String issueCategory, RequestPriority priority,
            @NotNull WorkflowServiceType serviceType,
            @Size(max = 2000) String customerRemarks, LocalDate preferredVisitDate,
            @Size(max = 80) String preferredTimeSlot, @Schema(description="Admin-only input. CUSTOMER submissions are rejected.") @Size(max = 2000) String internalAdminNotes,
            @PositiveOrZero Integer estimatedCompletionMinutes) implements StrictWrite {}
    public record AssignRequest(@NotNull @Positive Long technicianProfileId, @Size(max = 2000) String notes) implements StrictWrite {}
    public record StatusRequest(@NotNull RequestStatus toStatus, @Size(max = 2000) String notes) implements StrictWrite {}
    public record ReportRequest(@NotBlank String diagnosis, @NotBlank String workPerformed,
            @NotBlank String testingResult, String completionNotes) implements StrictWrite {}

    public record RequestView(Long id, String serviceId, Long customerProfileId, Long liftId,
            String title, String description, String issueCategory, RequestPriority priority, RequestStatus status,
            WorkflowServiceType serviceType, String customerRemarks, String technicianRemarks,
            LocalDateTime serviceRequestedAt, LocalDate preferredVisitDate, String preferredTimeSlot,
            String internalAdminNotes, LocalDateTime completedAt, Integer estimatedCompletionMinutes,
            LocalDateTime createdAt, LocalDateTime updatedAt, String customerName, String buildingName,
            String buildingAddress, String liftName, String liftNumber, String technicianName) {}
    public record AssignmentView(Long id, Long serviceRequestId, Long technicianProfileId, AssignmentStatus status,
            Long assignedByUserId, LocalDateTime assignedAt, LocalDateTime acceptedAt, LocalDateTime releasedAt,
            String notes, String technicianName) {}
    public record HistoryView(Long id, RequestStatus fromStatus, RequestStatus toStatus,
            Long changedByUserId, String notes, LocalDateTime changedAt) {}
    public record ReportView(Long id, Long serviceRequestId, Long assignmentId, String diagnosis,
            String workPerformed, String testingResult, String completionNotes, Long reportedByUserId,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record AttachmentView(Long id, Long serviceRequestId, String originalFilename, String contentType,
            Long fileSize, Long uploadedByUserId, LocalDateTime createdAt) {}
    public record FeedbackWrite(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String comment) implements StrictWrite {}
    public record FeedbackView(Long id, Long serviceRequestId, Long customerProfileId, Integer rating,
            String comment, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Detail(RequestView request, @Schema(nullable=true) AssignmentView activeAssignment, List<HistoryView> history, @Schema(nullable=true) ReportView report) {}
    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
