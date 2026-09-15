package com.valor.workflow;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class ServiceVisitDtos {
    private ServiceVisitDtos() {}
    public interface StrictWrite {
        @JsonAnySetter default void rejectUnknown(String field, JsonNode value) { throw new IllegalArgumentException("Unsupported visit field"); }
    }
    public record VisitCreateRequest(@NotNull @Positive Long serviceRequestId, @NotNull @Positive Long technicianProfileId,
            @NotNull LocalDate scheduledDate, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
            @Size(max=2000) String notes) implements StrictWrite {}
    public record VisitUpdateRequest(@NotNull @Positive Long technicianProfileId, @NotNull LocalDate scheduledDate,
            @NotNull LocalTime startTime, @NotNull LocalTime endTime, @Size(max=2000) String notes) implements StrictWrite {}
    public record VisitCancelRequest(@NotBlank @Size(max=2000) String reason) implements StrictWrite {}
    public record VisitProgressRequest(@NotNull VisitStatus status, @Size(max=2000) String notes) implements StrictWrite {}
    public record ChangeRequestCreate(@NotBlank @Size(max=2000) String reason, @NotNull LocalDate requestedDate,
            @NotNull LocalTime requestedStartTime, @NotNull LocalTime requestedEndTime) implements StrictWrite {}
    public record ChangeRequestDecision(@Size(max=2000) String reviewNotes, Long technicianProfileId,
            LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) implements StrictWrite {}
    public record VisitView(Long id, Long serviceRequestId, String serviceId, Long customerProfileId, Long liftId,
            String title, Long technicianProfileId, String technicianEmployeeId, String technicianSpecialization,
            LocalDate scheduledDate, LocalTime startTime, LocalTime endTime, VisitStatus status, String notes,
            List<VisitHistoryView> history, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record VisitHistoryView(Long id, String eventType, VisitStatus fromStatus, VisitStatus toStatus,
            Long changedByUserId, Long fromTechnicianProfileId, Long toTechnicianProfileId,
            LocalDate scheduledDate, LocalTime startTime, LocalTime endTime, String reason, LocalDateTime changedAt) {}
    public record VisitChangeRequestView(Long id, Long serviceRequestId, Long visitId, VisitChangeRequestType type,
            VisitChangeRequestStatus status, Long requestedByUserId, Long requestedTechnicianProfileId,
            LocalDate requestedDate, LocalTime requestedStartTime, LocalTime requestedEndTime,
            String reason, Long reviewedByUserId, String reviewNotes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
