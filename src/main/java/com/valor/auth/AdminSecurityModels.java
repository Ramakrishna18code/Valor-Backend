package com.valor.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

@Entity
@Table(name = "roles")
class ManagedRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 40) String name;
    @Column(length = 255) String description;
    @Column(nullable = false) boolean enabled = true;
    @Column(name = "system_role", nullable = false) boolean systemRole = true;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
}

@Entity
@Table(name = "permissions")
class ManagedPermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 80) String code;
    @Column(nullable = false, length = 255) String description;
    @Column(nullable = false, length = 60) String category;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
}

@Entity
@Table(name = "audit_logs")
class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "actor_user_id") Long actorUserId;
    @Column(name = "actor_role", length = 40) String actorRole;
    @Column(nullable = false, length = 100) String action;
    @Column(name = "entity_type", nullable = false, length = 80) String entityType;
    @Column(name = "entity_id", length = 80) String entityId;
    @Column(name = "result_status", nullable = false, length = 40) String resultStatus;
    @Column(length = 1000) String summary;
    @Column(name = "before_summary", length = 1000) String beforeSummary;
    @Column(name = "after_summary", length = 1000) String afterSummary;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
}

interface ManagedRoleRepo extends JpaRepository<ManagedRole, Long> {
    Optional<ManagedRole> findByName(String name);
}

interface ManagedPermissionRepo extends JpaRepository<ManagedPermission, Long> {
    List<ManagedPermission> findByOrderByCategoryAscCodeAsc();
    List<ManagedPermission> findByCodeIn(Collection<String> codes);
}

interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    @Query("""
        select a from AuditLog a where
        (:actorId is null or a.actorUserId = :actorId) and
        (:role is null or a.actorRole = :role) and
        (:action is null or a.action = :action) and
        (:entityType is null or a.entityType = :entityType) and
        (:entityId is null or a.entityId = :entityId) and
        (:from is null or a.createdAt >= :from) and
        (:to is null or a.createdAt < :to)
        """)
    Page<AuditLog> search(@Param("actorId") Long actorId, @Param("role") String role, @Param("action") String action,
            @Param("entityType") String entityType, @Param("entityId") String entityId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable page);
}
