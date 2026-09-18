package com.valor.auth;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PermissionService {
    private final EntityManager em;
    private final AssetIdentityAccess identities;

    PermissionService(EntityManager em, AssetIdentityAccess identities) {
        this.em = em;
        this.identities = identities;
    }

    Set<String> permissionsFor(Role role) {
        if (role == null) return Set.of();
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery("""
                select p.code from roles r
                join role_permissions rp on rp.role_id = r.id
                join permissions p on p.id = rp.permission_id
                where r.name = :role and r.enabled = true
                order by p.code
                """).setParameter("role", role.name()).getResultList();
        return new LinkedHashSet<>(rows);
    }

    boolean currentHas(String permission) {
        User actor = identities.actor();
        if (actor.getRole() == Role.SUPER_ADMIN) return true;
        return permissionsFor(actor.getRole()).contains(permission);
    }

    void require(String permission) {
        if (!currentHas(permission)) throw new AccessDeniedException("Access denied");
    }
}
