package com.valor.auth;

import com.valor.communication.RegistrationEmailSender;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:email_registration_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class EmailRegistrationIntegrationTest {
    @Autowired EmailRegistrationService registrations;
    @Autowired EmailRegistrationRepository challenges;
    @Autowired UserRepo users;
    @Autowired CustomerRepo customers;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @MockBean RegistrationEmailSender sender;

    private final AtomicReference<String> code = new AtomicReference<>();

    @BeforeEach
    void mailAccepted() {
        code.set(null);
        when(sender.send(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            code.set(invocation.getArgument(1, String.class));
            return true;
        });
    }

    private EmailRegistrationDtos.Start request(String email) {
        return new EmailRegistrationDtos.Start(email, null, "safe-password", "Pending Customer", null, null, null, null, null);
    }

    @Test
    void startStoresOnlyHashesAndDoesNotCreateAccount() {
        long before = users.count();
        var started = registrations.start(request(" Pending@Example.test "));
        var row = challenges.findById(started.challengeId()).orElseThrow();

        assertEquals(before, users.count());
        assertEquals(0, customers.count());
        assertEquals("ACCEPTED", row.deliveryStatus);
        assertNotNull(row.otpHash);
        assertNotEquals(code.get(), row.otpHash);
        assertNotEquals("safe-password", row.passwordHash);
        assertTrue(encoder.matches("safe-password", row.passwordHash));
    }

    @Test
    void acceptedCodeCreatesOneCustomerAndCannotReplay() {
        var started = registrations.start(request("one@example.test"));
        String submitted = code.get();

        User created = registrations.verify(started.challengeId(), submitted);
        assertEquals("one@example.test", created.getEmail());
        assertEquals(1, users.findByEmail("one@example.test").stream().count());
        assertTrue(customers.findByUserId(created.getId()).isPresent());
        var row = challenges.findById(started.challengeId()).orElseThrow();
        assertEquals("VERIFIED", row.deliveryStatus);
        assertNotNull(row.verifiedAt);
        assertEquals("invalidated", row.otpHash);
        assertThrows(IllegalArgumentException.class, () -> registrations.verify(started.challengeId(), submitted));
        assertEquals(1, users.findByEmail("one@example.test").stream().count());
    }

    @Test
    void deliveryFailureDoesNotPersistPendingAccount() {
        when(sender.send(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> registrations.start(request("failed@example.test")));
        assertTrue(users.findByEmail("failed@example.test").isEmpty());
        assertTrue(challenges.findByEmail("failed@example.test").isEmpty());
    }

    @Test
    void wrongCodesConsumeAttemptsAndExpireSafely() {
        var started = registrations.start(request("locked@example.test"));
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalArgumentException.class, () -> registrations.verify(started.challengeId(), "000000"));
        }
        var row = challenges.findById(started.challengeId()).orElseThrow();
        assertEquals(0, row.attemptsRemaining);
        assertNotNull(row.lockedUntil);
        assertTrue(row.lockedUntil.isAfter(LocalDateTime.now()));
        assertTrue(users.findByEmail("locked@example.test").isEmpty());
    }

    @Test
    void concurrentStartsUseTheEmailUniquenessConstraint() throws Exception {
        String email = "race@example.test";
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> concurrentStart(email, ready, go));
            var second = pool.submit(() -> concurrentStart(email, ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(users.findByEmail(email).stream().count() <= 1);
        assertTrue(challenges.findByEmail(email).stream().count() <= 1);
    }

    private void concurrentStart(String email, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
            registrations.start(request(email));
        } catch (Exception ignored) {
            // The losing transaction is expected to fail safely at the identity/unique constraint.
        }
    }

    @Test
    void concurrentVerificationCreatesAtMostOneAccount() throws Exception {
        String email = "verify-race@example.test";
        var started = registrations.start(request(email));
        String submitted = code.get();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> concurrentVerify(started.challengeId(), submitted, ready, go));
            var second = pool.submit(() -> concurrentVerify(started.challengeId(), submitted, ready, go));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, users.findByEmail(email).stream().count());
        assertEquals(1, challenges.findByEmail(email).stream().count());
        assertEquals("VERIFIED", challenges.findByEmail(email).orElseThrow().deliveryStatus);
    }

    private void concurrentVerify(String challengeId, String submitted, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
            registrations.verify(challengeId, submitted);
        } catch (Exception ignored) {
            // The second transaction is expected to observe the locked/completed challenge.
        }
    }
}
