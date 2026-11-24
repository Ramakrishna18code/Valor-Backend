package com.valor.communication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MailtrapEmailProviderTest {
    private static final ProviderRequest REQUEST = new ProviderRequest(
            "customer@example.test", "no-reply@example.test", "Valor", "support@example.test",
            "Verify your Valor email", "<p>Code: 123456</p>", "Code: 123456", "42",
            "CUSTOMER_EMAIL_VERIFICATION", "email-registration", "email-registration:customer");

    @Test
    void disabledMailtrapFailsSafelyWithoutExternalCredentials() {
        MailtrapEmailProvider provider = new MailtrapEmailProvider(properties(false, "", false, 0L));

        ProviderResult result = provider.send(REQUEST);

        assertFalse(result.success());
        assertEquals("MAILTRAP", result.provider());
        assertEquals("Mailtrap delivery disabled", result.failureReason());
    }

    @Test
    void missingApiTokenFailsClosed() {
        MailtrapEmailProvider provider = new MailtrapEmailProvider(properties(true, "", false, 0L));

        ProviderResult result = provider.send(REQUEST);

        assertFalse(result.success());
        assertEquals("Mailtrap configuration missing or invalid", result.failureReason());
    }

    @Test
    void sandboxRequiresSandboxId() {
        MailtrapEmailProvider provider = new MailtrapEmailProvider(properties(true, "test-only-placeholder", true, 0L));

        ProviderResult result = provider.send(REQUEST);

        assertFalse(result.success());
        assertEquals("Mailtrap sandbox configuration missing or invalid", result.failureReason());
    }

    private static EmailProperties properties(boolean enabled, String apiKey, boolean sandbox, Long sandboxId) {
        return new EmailProperties("mailtrap", "", 0, "", "", apiKey,
                "no-reply@example.test", "Valor", "support@example.test", enabled,
                5000, 5000, 5000, sandbox, sandboxId);
    }
}
