package com.valor.auth;

import java.time.LocalDateTime;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical identity checks for asset services; never returns identity data to HTTP clients. */
@Service
@Transactional(readOnly = true)
public class AssetIdentityAccess {
    private final UserRepo users;
    private final CustomerRepo customers;

    public AssetIdentityAccess(UserRepo users, CustomerRepo customers) {
        this.users = users;
        this.customers = customers;
    }

    public User actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BadCredentialsException("Authentication required");
        }
        User user = users.findById(id).orElseThrow(() -> new BadCredentialsException("Authentication required"));
        if (!usable(user)) throw new BadCredentialsException("Authentication required");
        return user;
    }

    public void requireAdmin() {
        Role role = actor().getRole();
        if (role != Role.ADMIN && role != Role.SUPER_ADMIN) throw new AccessDeniedException("Access denied");
    }

    public CustomerProfile activeCustomer(Long id) {
        CustomerProfile profile = customers.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer profile"));
        if (!profile.isActive() || !"ACTIVE".equals(profile.getStatus()) || profile.getUser().getRole() != Role.CUSTOMER
                || !usable(profile.getUser())) throw new IllegalArgumentException("Customer profile unavailable");
        return profile;
    }

    public void requireActiveCustomerUser(User user) {
        CustomerProfile profile = customers.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (!profile.isActive() || !"ACTIVE".equals(profile.getStatus())) throw new AccessDeniedException("Access denied");
    }

    private boolean usable(User user) {
        return user.isActive() && !user.isLocked()
                && (user.getLockedUntil() == null || !user.getLockedUntil().isAfter(LocalDateTime.now()));
    }
}
