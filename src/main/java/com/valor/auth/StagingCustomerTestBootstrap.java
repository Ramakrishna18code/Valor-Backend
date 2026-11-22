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
class StagingCustomerTestBootstrap implements ApplicationRunner {

    private static final String STAGING_ENV_ID =
        "f33b2aeb-167d-42a1-9e28-4c9074a73c2b";

    private final UserRepo users;
    private final CustomerRepo customers;
    private final PasswordEncoder encoder;
    private final String environmentId;
    private final boolean enabled;
    private final String email;
    private final String password;

    StagingCustomerTestBootstrap(
        UserRepo users,
        CustomerRepo customers,
        PasswordEncoder encoder,
        @Value("${RAILWAY_ENVIRONMENT_ID:}") String environmentId,
        @Value("${staging.customer-test.enabled:false}") boolean enabled,
        @Value("${staging.customer-test.email:}") String email,
        @Value("${staging.customer-test.password:}") String password
    ) {
        this.users = users;
        this.customers = customers;
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
            throw new IllegalStateException(
                "Customer test bootstrap is restricted to Valor staging."
            );
        }

        String normalizedEmail = email == null
            ? "" : email.trim().toLowerCase(Locale.ROOT);

        if (!normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Invalid staging test email.");
        }

        if (password == null
            || password.getBytes(StandardCharsets.UTF_8).length < 8
            || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                "Staging test password must be 8-72 UTF-8 bytes."
            );
        }

        var existing = users.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.CUSTOMER) {
                throw new IllegalStateException(
                    "Staging test email belongs to a non-customer account.");
            }
            user.setPasswordHash(encoder.encode(password));
            user.setActive(true);
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            users.saveAndFlush(user);
            if (!encoder.matches(password, user.getPasswordHash())) {
                throw new IllegalStateException(
                    "Staging customer password verification failed.");
            }
            if (customers.findByUserId(user.getId()).isEmpty()) {
                CustomerProfile profile = new CustomerProfile();
                profile.setUser(user);
                profile.setFullName("Valor Test Customer");
                profile.status = "ACTIVE";
                profile.active = true;
                customers.saveAndFlush(profile);
            }
            System.out.println("Staging customer test account password updated.");
            return;
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(Role.CUSTOMER);
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setFullName("Valor Test Customer");
        profile.status = "ACTIVE";
        profile.active = true;
        customers.saveAndFlush(profile);
        System.out.println("Staging customer test account created.");
    }
}
