package com.valor.response;

public record TechnicianResponse(
        Long id,
        String name,
        String email,
        String phone,
        String employeeId,
        String assignedArea,
        String specialization,
        Integer currentWorkload,
        Integer pendingJobs,
        Double rating,
        String role,
        String availabilityStatus
) { }