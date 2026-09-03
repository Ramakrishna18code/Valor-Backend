package com.valor.request;

import com.valor.enums.RoleName;
import com.valor.enums.TechnicianAvailabilityStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TechnicianRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") String email,
        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters") String password,
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