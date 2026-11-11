package com.valor.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtServiceConfigurationTest {
    @Test
    void acceptsASecretAtLeastThirtyTwoBytesLong() {
        assertDoesNotThrow(() -> new JwtService("secure-random-test-secret-with-at-least-32-bytes"));
    }

    @Test
    void rejectsShortAndKnownPlaceholderSecrets() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short"));
        assertThrows(IllegalStateException.class, () -> new JwtService("CHANGE_ME_TO_A_LONG_RANDOM_LOCAL_SECRET"));
        assertThrows(IllegalStateException.class, () -> new JwtService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }
}
