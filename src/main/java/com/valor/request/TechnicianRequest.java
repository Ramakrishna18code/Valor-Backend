package com.valor.request;

import com.valor.enums.RoleName;
import com.valor.enums.TechnicianAvailabilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TechnicianRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") String email,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,
        String phone,
        String employeeId,
        String assignedArea,
        String specialization,
        Integer currentWorkload,
        Integer pendingJobs,
        Double rating,
        RoleName role,
        TechnicianAvailabilityStatus availabilityStatus
) {
}