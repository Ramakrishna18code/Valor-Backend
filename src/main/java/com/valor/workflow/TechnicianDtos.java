package com.valor.workflow;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

final class TechnicianDtos {
    private TechnicianDtos() {}
    interface StrictInput {
        @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported technician field"); }
    }
    record Profile(Long userId, Long technicianProfileId, String email, String phone, String employeeId,
            String assignedArea, String specialization,
            @Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"}) String availabilityStatus,
            boolean active, LocalDateTime lastActiveAt) {}
    record ProfileUpdate(@Schema(allowableValues={"AVAILABLE","BUSY","OFF_DUTY","ON_LEAVE"})
            @Pattern(regexp="AVAILABLE|BUSY|OFF_DUTY|ON_LEAVE") String availabilityStatus) implements StrictInput {}
    record Dashboard(Profile profile, long assignedJobs, long pendingJobs, long inProgressJobs,
            long completedJobs, long completedThisQuarter, long todaysScheduledVisits, long emergencyJobs) {}
}
