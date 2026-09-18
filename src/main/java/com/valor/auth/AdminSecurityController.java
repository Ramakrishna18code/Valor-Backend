package com.valor.auth;

import com.valor.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import static com.valor.auth.AdminSecurityDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
class AdminSecurityController {
    private final AdminSecurityService service;

    AdminSecurityController(AdminSecurityService service) {
        this.service = service;
    }

    @Operation(operationId = "listPermissions")
    @GetMapping("/permissions")
    ApiResponse<List<PermissionView>> permissions() { return ApiResponse.success("Permissions", service.permissions(), 200); }

    @Operation(operationId = "listRoles")
    @GetMapping("/roles")
    ApiResponse<List<RoleView>> roles() { return ApiResponse.success("Roles", service.roles(), 200); }

    @Operation(operationId = "getRole")
    @GetMapping("/roles/{name}")
    ApiResponse<RoleView> role(@PathVariable String name) { return ApiResponse.success("Role", service.role(name), 200); }

    @Operation(operationId = "updateRolePermissions")
    @PutMapping("/roles/{name}/permissions")
    ApiResponse<RoleView> updatePermissions(@PathVariable String name, @Valid @RequestBody PermissionUpdate input) {
        return ApiResponse.success("Role permissions updated", service.updatePermissions(name, input.permissions()), 200);
    }

    @Operation(operationId = "listAuditLogs")
    @GetMapping("/audit-logs")
    ApiResponse<PageView<AuditView>> audit(@RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Audit logs", service.audit(actorUserId, role, action, entityType, entityId, dateFrom, dateTo, page, size), 200);
    }

    @Operation(operationId = "getAuditLog")
    @GetMapping("/audit-logs/{id}")
    ApiResponse<AuditView> auditDetail(@PathVariable Long id) { return ApiResponse.success("Audit log", service.auditDetail(id), 200); }
}

@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
class AdminSecurityService {
    private final ManagedRoleRepo roles;
    private final ManagedPermissionRepo permissions;
    private final AuditLogRepo logs;
    private final AssetIdentityAccess identities;
    private final PermissionService guards;
    private final AuditService audit;
    private final EntityManager em;

    AdminSecurityService(ManagedRoleRepo roles, ManagedPermissionRepo permissions, AuditLogRepo logs,
            AssetIdentityAccess identities, PermissionService guards, AuditService audit, EntityManager em) {
        this.roles = roles; this.permissions = permissions; this.logs = logs; this.identities = identities;
        this.guards = guards; this.audit = audit; this.em = em;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<PermissionView> permissions() {
        guards.require("ROLE_READ");
        return permissions.findByOrderByCategoryAscCodeAsc().stream().map(this::view).toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<RoleView> roles() {
        guards.require("ROLE_READ");
        return roles.findAll(Sort.by("name")).stream().map(this::view).toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    RoleView role(String name) {
        guards.require("ROLE_READ");
        return view(roleByName(name));
    }

    RoleView updatePermissions(String name, List<String> codes) {
        User actor = identities.actor();
        if (actor.getRole() != Role.SUPER_ADMIN) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        ManagedRole role = roleByName(name);
        if (Role.SUPER_ADMIN.name().equals(role.name)) throw new IllegalArgumentException("SUPER_ADMIN permissions are fixed");
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String code : codes == null ? List.<String>of() : codes) {
            if (code == null || code.isBlank() || code.length() > 80) throw new IllegalArgumentException("Invalid permission");
            requested.add(code.trim().toUpperCase(Locale.ROOT));
        }
        List<ManagedPermission> found = permissions.findByCodeIn(requested);
        if (found.size() != requested.size()) throw new IllegalArgumentException("Invalid permission");
        List<String> before = permissionCodes(role.name);
        em.createNativeQuery("delete from role_permissions where role_id=:roleId").setParameter("roleId", role.id).executeUpdate();
        for (ManagedPermission permission : found) {
            em.createNativeQuery("insert into role_permissions(role_id, permission_id) values(:roleId, :permissionId)")
                    .setParameter("roleId", role.id).setParameter("permissionId", permission.id).executeUpdate();
        }
        role.updatedAt = java.time.LocalDateTime.now();
        em.flush();
        List<String> after = permissionCodes(role.name);
        audit.record("ROLE_PERMISSIONS_UPDATE", "ROLE", role.name, "Updated role permissions",
                "permissions=" + before, "permissions=" + after, "SUCCESS");
        return view(role);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    PageView<AuditView> audit(Long actorUserId, String role, String action, String entityType, String entityId,
            LocalDate from, LocalDate to, int page, int size) {
        guards.require("AUDIT_READ");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("Invalid date range");
        Page<AuditLog> result = logs.search(actorUserId, norm(role), norm(action), norm(entityType), norm(entityId),
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageView<>(result.map(this::view).getContent(), result.getTotalElements(), result.getTotalPages(), page, size);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    AuditView auditDetail(Long id) {
        guards.require("AUDIT_READ");
        return view(logs.findById(id).orElseThrow(() -> new IllegalArgumentException("Audit log not found")));
    }

    private ManagedRole roleByName(String name) {
        String role = norm(name);
        if (role == null || Arrays.stream(Role.values()).noneMatch(r -> r.name().equals(role))) throw new IllegalArgumentException("Invalid role");
        return roles.findByName(role).orElseThrow(() -> new IllegalArgumentException("Invalid role"));
    }

    private RoleView view(ManagedRole role) {
        return new RoleView(role.id, role.name, role.description, role.enabled, role.systemRole, permissionCodes(role.name), role.createdAt, role.updatedAt);
    }

    private PermissionView view(ManagedPermission permission) {
        return new PermissionView(permission.id, permission.code, permission.description, permission.category);
    }

    private AuditView view(AuditLog log) {
        return new AuditView(log.id, log.actorUserId, log.actorRole, log.action, log.entityType, log.entityId,
                log.resultStatus, log.summary, log.beforeSummary, log.afterSummary, log.createdAt);
    }

    private List<String> permissionCodes(String role) {
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery("""
                select p.code from roles r join role_permissions rp on rp.role_id=r.id
                join permissions p on p.id=rp.permission_id
                where r.name=:role order by p.code
                """).setParameter("role", role).getResultList();
        return rows;
    }

    private static String norm(String value) {
        String v = value == null ? null : value.trim();
        return v == null || v.isEmpty() ? null : v.toUpperCase(Locale.ROOT);
    }
}
