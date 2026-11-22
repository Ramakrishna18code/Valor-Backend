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

class StagingCustomerTestBootstrapTest {
    private static final String STAGING_ENV_ID =
        "f33b2aeb-167d-42a1-9e28-4c9074a73c2b";

    private final UserRepo users = mock(UserRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsCustomerOnlyInStaging() {
        when(users.findByEmail("testcustomer@valor.test")).thenReturn(Optional.empty());
        when(encoder.encode("Staging-Test-123")).thenReturn("bcrypt-test-hash");

        var bootstrap = new StagingCustomerTestBootstrap(
            users, customers, encoder, STAGING_ENV_ID, true,
            " testcustomer@valor.test ", "Staging-Test-123");
        bootstrap.run(new DefaultApplicationArguments());

        verify(users).saveAndFlush(any(User.class));
        verify(customers).saveAndFlush(any(CustomerProfile.class));
    }

    @Test
    void rejectsAnotherEnvironment() {
        var bootstrap = new StagingCustomerTestBootstrap(
            users, customers, encoder, "another-environment", true,
            "testcustomer@valor.test", "Staging-Test-123");

        assertThrows(IllegalStateException.class,
            () -> bootstrap.run(new DefaultApplicationArguments()));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void updatesReservedCustomerAccountPassword() {
        var existing = new User();
        existing.setRole(Role.CUSTOMER);
        existing.setPasswordHash("old-hash");
        when(users.findByEmail("testcustomer@valor.test"))
            .thenReturn(Optional.of(existing));
        when(encoder.encode("Staging-Test-123")).thenReturn("new-hash");

        var bootstrap = new StagingCustomerTestBootstrap(
            users, customers, encoder, STAGING_ENV_ID, true,
            "testcustomer@valor.test", "Staging-Test-123");
        bootstrap.run(new DefaultApplicationArguments());

        assertEquals("new-hash", existing.getPasswordHash());
        verify(users).saveAndFlush(existing);
        verify(customers, never()).saveAndFlush(any());
    }
}
