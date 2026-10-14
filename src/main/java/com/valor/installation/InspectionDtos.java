package com.valor.installation;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
final class InspectionDtos {
 private InspectionDtos(){}
 record ScheduleRequest(@NotNull LocalDateTime scheduledAt,@Size(max=80) String scheduledTimezone,@PositiveOrZero Long version){}
 record AssignRequest(@NotNull @Positive Long technicianProfileId,@PositiveOrZero Long version){}
 record RescheduleRequest(@NotNull LocalDateTime scheduledAt,@Size(max=80) String scheduledTimezone,@Size(max=500) String reason,@PositiveOrZero Long version){}
 record CancelRequest(@NotBlank @Size(max=500) String reason,@PositiveOrZero Long version){}
 record AssessmentRequest(@Size(max=40) String conclusion,@Size(max=10000) String measurements,@Size(max=10000) String findings,@Size(max=10000) String siteConstraints,@Size(max=10000) String readinessObservations,@Size(max=10000) String issues,@Size(max=10000) String risks,@Size(max=10000) String recommendedActions,@PositiveOrZero Long version){}
 record ReviewRequest(@NotBlank String decision,@Size(max=2000) String notes,@PositiveOrZero Long version){}
 record EvidenceRequest(@NotBlank @Size(max=80) String documentType,@NotBlank @Size(max=255) String originalFilename,@NotBlank @Size(max=100) String contentType,@NotBlank @Size(max=500) String storageKey,@PositiveOrZero Long version){}
 record History(Long id,InspectionStatus fromStatus,InspectionStatus toStatus,String notes,LocalDateTime changedAt,Long changedByUserId){}
 record Evidence(Long id,String documentType,String originalFilename,String contentType,String storageKey,LocalDateTime createdAt){}
 record View(Long id,Long installationRequestId,InspectionStatus status,LocalDateTime scheduledAt,String scheduledTimezone,Long assignedTechnicianId,LocalDateTime visitStartedAt,LocalDateTime submittedAt,String conclusion,String measurements,String findings,String siteConstraints,String readinessObservations,String issues,String risks,String recommendedActions,Long reviewerUserId,String reviewNotes,LocalDateTime reviewedAt,Long version,List<History> history,List<Evidence> evidence){}
 record Summary(Long id,Long installationRequestId,InspectionStatus status,LocalDateTime scheduledAt,String scheduledTimezone,Long assignedTechnicianId,LocalDateTime visitStartedAt,LocalDateTime submittedAt,String propertyType,String siteAddressLine1,String siteAddressLine2,String siteLocality,String siteCity,String siteState,String sitePostalCode,String siteCountry,String sitePropertyDescription,Long version){}
}
