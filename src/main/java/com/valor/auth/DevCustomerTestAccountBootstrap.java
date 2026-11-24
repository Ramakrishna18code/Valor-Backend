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
@Profile("dev & !prod")
class DevCustomerTestAccountBootstrap implements ApplicationRunner {
    private final UserRepo users;
    private final CustomerRepo customers;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String configuredEmail;
    private final String configuredPassword;

    DevCustomerTestAccountBootstrap(
            UserRepo users,
            CustomerRepo customers,
            PasswordEncoder passwordEncoder,
            @Value("${dev.customer-test.enabled:false}") boolean enabled,
            @Value("${dev.customer-test.email:testcustomer@valor.local}") String configuredEmail,
            @Value("${dev.customer-test.password:}") String configuredPassword) {
        this.users = users;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        String email = configuredEmail == null ? "" : configuredEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Development Customer test seed requires a valid email.");
        }
        if (configuredPassword == null || configuredPassword.isBlank()
                || configuredPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Development Customer test seed requires a password of at most 72 UTF-8 bytes.");
        }

        User user = users.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setPasswordHash(passwordEncoder.encode(configuredPassword));
            created.setRole(Role.CUSTOMER);
            created.setActive(true);
            created.setLocked(false);
            created.setFailedLoginAttempts(0);
            created.setLockedUntil(null);
            return users.saveAndFlush(created);
        });

        if (user.getRole() != Role.CUSTOMER) {
            throw new IllegalStateException("Development Customer test seed email belongs to a non-Customer account.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(configuredPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(configuredPassword));
        }
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.saveAndFlush(user);

        CustomerProfile profile = customers.findByUserId(user.getId()).orElseGet(() -> {
            CustomerProfile created = new CustomerProfile();
            created.setUser(user);
            created.setFullName("Valor Test Customer");
            created.status = "ACTIVE";
            created.active = true;
            return created;
        });
        profile.fullName = "Valor Test Customer";
        profile.status = "ACTIVE";
        profile.active = true;
        customers.saveAndFlush(profile);
    }
}
