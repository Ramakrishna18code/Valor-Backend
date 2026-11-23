package com.valor.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod")
class StagingTechnicianTestBootstrap implements ApplicationRunner {

    private static final String STAGING_ENV_ID =
        "f33b2aeb-167d-42a1-9e28-4c9074a73c2b";

    private final UserRepo users;
    private final TechRepo technicians;
    private final PasswordEncoder encoder;
    private final String environmentId;
    private final boolean enabled;
    private final String email;
    private final String password;

    StagingTechnicianTestBootstrap(
        UserRepo users,
        TechRepo technicians,
        PasswordEncoder encoder,
        @Value("${RAILWAY_ENVIRONMENT_ID:}") String environmentId,
        @Value("${staging.technician-test.enabled:false}") boolean enabled,
        @Value("${staging.technician-test.email:}") String email,
        @Value("${staging.technician-test.password:}") String password
    ) {
        this.users = users;
        this.technicians = technicians;
        this.encoder = encoder;
        this.environmentId = environmentId;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!STAGING_ENV_ID.equals(environmentId)) {
            throw new IllegalStateException("Technician test bootstrap is restricted to Valor staging.");
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Invalid staging technician test email.");
        }
        if (password == null
            || password.getBytes(StandardCharsets.UTF_8).length < 8
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Staging technician test password must be 8-72 UTF-8 bytes.");
        }

        User user;
        var existing = users.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            user = existing.get();
            if (user.getRole() != Role.TECHNICIAN) {
                throw new IllegalStateException("Staging technician test email belongs to a non-technician account.");
            }
            user.setPasswordHash(encoder.encode(password));
            user.setActive(true);
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            users.saveAndFlush(user);
            System.out.println("Staging technician test account password updated.");
        } else {
            user = new User();
            user.setEmail(normalizedEmail);
            user.setPasswordHash(encoder.encode(password));
            user.setRole(Role.TECHNICIAN);
            user.setActive(true);
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            users.saveAndFlush(user);
            System.out.println("Staging technician test account created.");
        }

        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalStateException("Staging technician password verification failed.");
        }

        var profile = technicians.findByUserId(user.getId()).orElseGet(() -> {
            TechnicianProfile created = new TechnicianProfile();
            created.user = user;
            created.employeeId = "VAL-TECH-STAGING";
            created.assignedArea = "Staging";
            created.specialization = "Testing";
            created.availabilityStatus = "AVAILABLE";
            created.active = true;
            return created;
        });
        technicians.saveAndFlush(profile);
    }
}
