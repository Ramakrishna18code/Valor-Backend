package com.valor.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CanonicalUserDetailsServiceTest {
    private final UserRepo users = mock(UserRepo.class);
    private final CanonicalUserDetailsService service = new CanonicalUserDetailsService(users);

    @Test
    void resolvesJwtSubjectAsCanonicalUserId() {
        User user = user(42L, Role.ADMIN, true, false);
        when(users.findById(42L)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("42");

        assertEquals("42", details.getUsername());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsUnknownOrInactiveJwtSubject() {
        when(users.findById(42L)).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("42"));

        User inactive = user(43L, Role.CUSTOMER, false, false);
        when(users.findById(43L)).thenReturn(Optional.of(inactive));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("43"));
    }

    private User user(Long id, Role role, boolean active, boolean locked) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        when(user.isActive()).thenReturn(active);
        when(user.isLocked()).thenReturn(locked);
        when(user.getLockedUntil()).thenReturn(null);
        when(user.getPasswordHash()).thenReturn("$2a$10$12345678901234567890123456789012345678901234567890123");
        return user;
    }
}
