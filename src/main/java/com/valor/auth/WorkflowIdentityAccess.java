package com.valor.auth;

import java.time.LocalDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkflowIdentityAccess {
    private final CustomerRepo customers;
    private final TechRepo technicians;
    private final AssetIdentityAccess access;
    public WorkflowIdentityAccess(CustomerRepo customers, TechRepo technicians, AssetIdentityAccess access) {
        this.customers = customers; this.technicians = technicians; this.access = access;
    }
    public CustomerProfile customer(User actor) {
        if (actor.getRole() != Role.CUSTOMER) throw new AccessDeniedException("Access denied");
        CustomerProfile profile = customers.findByUserId(actor.getId()).orElseThrow(() -> new AccessDeniedException("Access denied"));
        return access.activeCustomer(profile.getId());
    }
    public TechnicianProfile technician(User actor) {
        if (actor.getRole() != Role.TECHNICIAN) throw new AccessDeniedException("Access denied");
        TechnicianProfile profile = technicians.findByUserId(actor.getId()).orElseThrow(() -> new AccessDeniedException("Access denied"));
        return activeTechnician(profile.getId());
    }
    public TechnicianProfile activeTechnician(Long id) {
        TechnicianProfile profile = technicians.findById(id).orElseThrow(() -> new IllegalArgumentException("Technician unavailable"));
        User user = profile.getUser();
        if (!profile.isActive() || user.getRole() != Role.TECHNICIAN || !user.isActive() || user.isLocked()
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Technician unavailable");
        }
        return profile;
    }
}
