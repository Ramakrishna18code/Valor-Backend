package com.valor.response;

import com.valor.enums.JobStatus;
import com.valor.enums.PriorityLevel;
import com.valor.enums.ServiceType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ServiceRequestResponse(
        Long id,
        String serviceId,
        Long customerId,
        Long liftId,
        Long assignedTechnicianId,
        String title,
        String description,
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
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String customerSignaturePath,
        String serviceReportPath
) { }