package com.valor.request;

import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.enums.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ServiceRequestRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Description is required") String description,
        String issueCategory,
        PriorityLevel priority,
        JobStatus status,
        ServiceType serviceType,
        String customerRemarks,
        String technicianRemarks,
        LocalDateTime serviceRequestedAt,
        LocalDate preferredVisitDate,
        String preferredTimeSlot,
        String internalAdminNotes,
        @NotNull(message = "Customer id is required") Long customerId,
        @NotNull(message = "Lift id is required") Long liftId,
        Long assignedTechnicianId
) {
}
