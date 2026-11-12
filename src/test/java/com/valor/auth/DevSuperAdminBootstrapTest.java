package com.valor.auth;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevSuperAdminBootstrapTest {
    private final UserRepo users = mock(UserRepo.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsOnlyAnExplicitlyConfiguredHashedSuperAdmin() {
        when(users.findByEmail("admin@valor.local")).thenReturn(Optional.empty());
        when(encoder.encode("operator-supplied-test-password")).thenReturn("bcrypt-test-hash");

        new DevSuperAdminBootstrap(users, encoder, true, " Admin@Valor.Local ",
                "operator-supplied-test-password").run(new DefaultApplicationArguments());

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertEquals("admin@valor.local", captor.getValue().getEmail());
        assertEquals(Role.SUPER_ADMIN, captor.getValue().getRole());
        assertEquals("bcrypt-test-hash", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void neverRewritesAnExistingAccountOrEncodesItsPassword() {
        User existing = new User();
        when(users.findByEmail("admin@valor.local")).thenReturn(Optional.of(existing));

        new DevSuperAdminBootstrap(users, encoder, true, "admin@valor.local",
                "operator-supplied-test-password").run(new DefaultApplicationArguments());

        verify(users, never()).saveAndFlush(any(User.class));
        verify(encoder, never()).encode(any(CharSequence.class));
    }
}
