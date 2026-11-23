package com.valor.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class StagingTechnicianTestBootstrapTest {
    private static final String STAGING_ENV_ID =
        "f33b2aeb-167d-42a1-9e28-4c9074a73c2b";

    private final UserRepo users = mock(UserRepo.class);
    private final TechRepo technicians = mock(TechRepo.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsTechnicianOnlyInStaging() {
        when(users.findByEmail("stagingtechnician@valor.test")).thenReturn(Optional.empty());
        when(encoder.encode("Ram123@#")).thenReturn("bcrypt-test-hash");
        when(encoder.matches("Ram123@#", "bcrypt-test-hash")).thenReturn(true);

        var bootstrap = new StagingTechnicianTestBootstrap(
            users, technicians, encoder, STAGING_ENV_ID, true,
            " stagingtechnician@valor.test ", "Ram123@#");
        bootstrap.run(new DefaultApplicationArguments());

        verify(users).saveAndFlush(any(User.class));
        verify(technicians).saveAndFlush(any(TechnicianProfile.class));
    }

    @Test
    void refusesAnotherEnvironment() {
        var bootstrap = new StagingTechnicianTestBootstrap(
            users, technicians, encoder, "another-environment", true,
            "stagingtechnician@valor.test", "Ram123@#");

        assertThrows(IllegalStateException.class,
            () -> bootstrap.run(new DefaultApplicationArguments()));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesNonTechnicianEmailOwner() {
        var existing = new User();
        existing.setRole(Role.CUSTOMER);
        when(users.findByEmail("stagingtechnician@valor.test")).thenReturn(Optional.of(existing));

        var bootstrap = new StagingTechnicianTestBootstrap(
            users, technicians, encoder, STAGING_ENV_ID, true,
            "stagingtechnician@valor.test", "Ram123@#");

        assertThrows(IllegalStateException.class,
            () -> bootstrap.run(new DefaultApplicationArguments()));
    }
}
