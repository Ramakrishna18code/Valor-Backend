package com.valor.workflow;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;

final class TechnicianDtos {
    private TechnicianDtos() {}
    interface StrictInput {
        @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported technician field"); }
    }
    record Profile(Long userId, Long technicianProfileId, String email, String phone, String employeeId,
            String assignedArea, String specialization,
            @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}) String availabilityStatus,
            boolean active, LocalDateTime lastActiveAt, String profilePhotoUrl, LocalDate dateOfBirth,
            String gender, String address, String emergencyContactName, String emergencyContactPhone) {}
    record ProfileUpdate(@Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"})
            @Pattern(regexp="AVAILABLE|BUSY|OFF_DUTY|ON_LEAVE") String availabilityStatus,
            @Size(max=500) String profilePhotoUrl, LocalDate dateOfBirth, @Size(max=40) String gender,
            @Size(max=500) String address, @Size(max=160) String emergencyContactName,
            @Size(max=20) String emergencyContactPhone) implements StrictInput {}
    record Dashboard(Profile profile, long assignedJobs, long pendingJobs, long inProgressJobs,
            long completedJobs, long completedThisQuarter, long todaysScheduledVisits, long emergencyJobs) {}
}
