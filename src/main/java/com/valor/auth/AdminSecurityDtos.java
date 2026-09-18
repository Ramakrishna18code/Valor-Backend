package com.valor.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

class AdminSecurityDtos {
    record PageView<T>(List<T> items, long totalElements, int totalPages, int page, int size) {}
    record PermissionView(Long id, String code, String description, String category) {}
    record RoleView(Long id, String name, String description, boolean enabled, boolean systemRole, List<String> permissions,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record PermissionUpdate(@NotNull List<@NotBlank @Size(max = 80) String> permissions) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported role field"); }
    }
    record AuditView(Long id, Long actorUserId, String actorRole, String action, String entityType, String entityId,
            String resultStatus, String summary, String beforeSummary, String afterSummary, LocalDateTime createdAt) {}
}
