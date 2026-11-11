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
        if (actor.getRole() != Role.CUSTOMER) throw new AccessDeniedException("You do not have permission to access this information.");
        CustomerProfile profile = customers.findByUserId(actor.getId()).orElseThrow(() -> new AccessDeniedException("You do not have permission to access this information."));
        return access.activeCustomer(profile.getId());
    }
    @Transactional
    public CustomerProfile customerForWrite(User actor) {
        if (actor.getRole() != Role.CUSTOMER) throw new AccessDeniedException("You do not have permission to access this information.");
        CustomerProfile profile = customers.findByUserId(actor.getId()).orElseThrow(() -> new AccessDeniedException("You do not have permission to access this information."));
        CustomerProfile locked = customers.lockById(profile.getId()).orElseThrow(() -> new AccessDeniedException("You do not have permission to access this information."));
        return access.activeCustomer(locked.getId());
    }
    public TechnicianProfile technician(User actor) {
        if (actor.getRole() != Role.TECHNICIAN) throw new AccessDeniedException("You do not have permission to access this information.");
        TechnicianProfile profile = technicians.findByUserId(actor.getId()).orElseThrow(() -> new AccessDeniedException("You do not have permission to access this information."));
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
