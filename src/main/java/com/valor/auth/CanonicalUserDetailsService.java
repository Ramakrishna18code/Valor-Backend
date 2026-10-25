package com.valor.auth;

import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads password-authentication identities exclusively from the canonical users table. */
@Service
class CanonicalUserDetailsService implements UserDetailsService {
    private final UserRepo users;

    CanonicalUserDetailsService(UserRepo users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identity) {
        if (identity == null || identity.isBlank()) {
            throw rejected();
        }
        String normalized = identity.trim().toLowerCase(Locale.ROOT);
        User user;
        if (normalized.matches("\\d+")) {
            // Access JWTs carry the canonical users.id as their subject. The
            // realtime CONNECT path must resolve that identity without
            // falling through to the email/phone login identifiers.
            try {
                user = users.findById(Long.valueOf(normalized)).orElseThrow(this::rejected);
            } catch (NumberFormatException rejected) {
                throw rejected();
            }
        } else if (normalized.contains("@")) {
            user = users.findByEmail(normalized).orElseThrow(this::rejected);
        } else {
            normalized = normalized.replaceAll("[\\s\\-()]", "");
            if (!normalized.matches("\\+[1-9]\\d{7,14}")) {
                throw rejected();
            }
            user = users.findByPhone(normalized).filter(u -> u.getRole() == Role.CUSTOMER)
                    .orElseThrow(this::rejected);
        }
        if (!user.isActive() || user.isLocked()
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
                || user.getPasswordHash() == null
                || !user.getPasswordHash().matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            throw rejected();
        }
        return org.springframework.security.core.userdetails.User.withUsername(String.valueOf(user.getId()))
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    private UsernameNotFoundException rejected() {
        return new UsernameNotFoundException("Authentication failed");
    }
}
