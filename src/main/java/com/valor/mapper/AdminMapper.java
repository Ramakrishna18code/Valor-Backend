package com.valor.mapper;

import com.valor.entity.Admin;
import com.valor.request.AdminRequest;
import com.valor.response.AdminResponse;
import com.valor.enums.RoleName;

public final class AdminMapper {

    private AdminMapper() {
    }

    public static Admin toEntity(AdminRequest request) {
        Admin admin = new Admin();
        admin.setName(request.name());
        admin.setEmail(request.email());
        admin.setPhone(request.phone());
        admin.setPassword(request.password());
        admin.setRole(request.role() == null ? RoleName.ADMIN : request.role());
        admin.setActive(request.active() == null || request.active());
        admin.setEmployeeId(request.employeeId());
        admin.setDesignation(request.designation());
        return admin;
    }

    public static AdminResponse toResponse(Admin admin) {
        return new AdminResponse(
            admin.getId(),
            admin.getName(),
            admin.getEmail(),
            admin.getPhone(),
            admin.getEmployeeId(),
            admin.getRole() == null ? null : admin.getRole().name(),
            admin.getDesignation(),
            Boolean.TRUE.equals(admin.getActive())
        );
    }
}