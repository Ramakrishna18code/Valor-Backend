package com.valor.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the explicitly configured local administrator only when it is absent. */
@Component
@Profile("dev & !prod")
class DevSuperAdminBootstrap implements ApplicationRunner {
    private final UserRepo users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String configuredEmail;
    private final String configuredPassword;

    DevSuperAdminBootstrap(
            UserRepo users,
            PasswordEncoder passwordEncoder,
            @Value("${dev.bootstrap.enabled:false}") boolean enabled,
            @Value("${dev.bootstrap.super-admin-email:}") String configuredEmail,
            @Value("${dev.bootstrap.super-admin-password:}") String configuredPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        String email = configuredEmail == null ? "" : configuredEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Development admin bootstrap requires a valid configured email.");
        }
        if (configuredPassword == null || configuredPassword.isBlank()
                || configuredPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Development admin bootstrap requires a configured password of at most 72 UTF-8 bytes.");
        }

        if (users.findByEmail(email).isPresent()) return;

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(configuredPassword));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActive(true);
        users.saveAndFlush(admin);
    }
}
