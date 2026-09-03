package com.valor.response;

public record AdminResponse(
        Long id,
        String name,
        String email,
        String phone,
        String employeeId,
        String role,
        String designation,
        boolean active
) { }