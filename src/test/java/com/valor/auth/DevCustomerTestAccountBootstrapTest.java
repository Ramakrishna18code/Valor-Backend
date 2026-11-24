package com.valor.auth;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class DevCustomerTestAccountBootstrapTest {
    private final UserRepo users = mock(UserRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsAnActiveHashedCustomerProfile() {
        when(users.findByEmail("testcustomer@valor.local")).thenReturn(Optional.empty());
        when(encoder.encode("ValorTest@123")).thenReturn("bcrypt-test-hash");
        doAnswer(invocation -> invocation.getArgument(0)).when(users).saveAndFlush(any(User.class));

        var bootstrap = new DevCustomerTestAccountBootstrap(users, customers, encoder,
                true, "testcustomer@valor.local", "ValorTest@123");
        bootstrap.run(new DefaultApplicationArguments());

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        assertEquals("testcustomer@valor.local",
                captor.getAllValues().get(captor.getAllValues().size() - 1).getEmail());
        assertEquals(Role.CUSTOMER,
                captor.getAllValues().get(captor.getAllValues().size() - 1).getRole());
        assertEquals("bcrypt-test-hash",
                captor.getAllValues().get(captor.getAllValues().size() - 1).getPasswordHash());
        assertTrue(captor.getAllValues().get(captor.getAllValues().size() - 1).isActive());
    }

    @Test
    void refusesToReuseTheEmailForAnotherRole() {
        User existing = new User();
        existing.setRole(Role.ADMIN);
        existing.setEmail("testcustomer@valor.local");
        when(users.findByEmail("testcustomer@valor.local")).thenReturn(Optional.of(existing));

        var bootstrap = new DevCustomerTestAccountBootstrap(users, customers, encoder,
                true, "testcustomer@valor.local", "ValorTest@123");

        assertThrows(IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments()));
        verify(users, never()).saveAndFlush(any(User.class));
    }
}
