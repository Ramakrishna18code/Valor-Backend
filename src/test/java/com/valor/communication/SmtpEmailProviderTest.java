package com.valor.communication;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailProviderTest {
    private static final ProviderRequest REQUEST = new ProviderRequest(
            "customer@example.test", "no-reply@example.test", "Valor", "support@example.test",
            "Payment update", "<p>Payment confirmed</p>", "Payment confirmed", "42",
            "PAYMENT_RESULT", "payment-result", "payment:42");

    @Test void disabledSmtpFailsSafelyWithoutSending() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailProvider provider = new SmtpEmailProvider(properties(false, "smtp.example.test", 587), sender);

        ProviderResult result = provider.send(REQUEST);

        assertFalse(result.success());
        assertEquals("SMTP", result.provider());
        verifyNoInteractions(sender);
    }

    @Test void missingConfigurationFailsClosedWithoutSending() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailProvider provider = new SmtpEmailProvider(properties(true, "", 587), sender);

        ProviderResult result = provider.send(REQUEST);

        assertFalse(result.success());
        assertEquals("SMTP configuration missing or invalid", result.failureReason());
        verifyNoInteractions(sender);
    }

    @Test void configuredSmtpUsesMockedSenderAndNeverRequiresNetwork() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailProvider provider = new SmtpEmailProvider(properties(true, "smtp.example.test", 587), sender);

        ProviderResult result = provider.send(REQUEST);

        assertTrue(result.success(), result.failureReason());
        assertEquals("SMTP", result.provider());
        verify(sender).send(any(MimeMessage.class));
    }

    @Test void invalidTimeoutsFailClosed() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailProperties p = new EmailProperties("smtp", "smtp.example.test", 587, "test-user", "test-only-placeholder",
                "", "no-reply@example.test", "Valor", "", true, 0, 5000, 5000);
        ProviderResult result = new SmtpEmailProvider(p, sender).send(REQUEST);
        assertFalse(result.success());
        verifyNoInteractions(sender);
    }

    private static EmailProperties properties(boolean enabled, String host, int port) {
        return new EmailProperties("smtp", host, port, "test-user", "test-only-placeholder", "",
                "no-reply@example.test", "Valor", "support@example.test", enabled, 5000, 5000, 5000);
    }
}
