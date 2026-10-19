package com.valor.communication;

import java.util.UUID;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.util.Properties;

@ConfigurationProperties(prefix = "email")
record EmailProperties(String provider, String host, Integer port, String username, String password,
        String apiKey, String from, String fromName, String replyTo, boolean enabled,
        Integer connectionTimeoutMs, Integer readTimeoutMs, Integer writeTimeoutMs) {
    EmailProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider.trim();
        from = from == null || from.isBlank() ? "no-reply@example.test" : from.trim();
        fromName = fromName == null || fromName.isBlank() ? "Valor" : fromName.trim();
        replyTo = replyTo == null || replyTo.isBlank() ? from : replyTo.trim();
        connectionTimeoutMs = connectionTimeoutMs == null ? 5000 : connectionTimeoutMs;
        readTimeoutMs = readTimeoutMs == null ? 5000 : readTimeoutMs;
        writeTimeoutMs = writeTimeoutMs == null ? 5000 : writeTimeoutMs;
    }
}

@ConfigurationProperties(prefix = "sms")
record SmsProperties(String provider, String msg91AuthKey, String msg91TemplateId, String msg91SenderId, boolean enabled) {
    SmsProperties { provider = provider == null || provider.isBlank() ? "mock" : provider.trim(); }
}

@ConfigurationProperties(prefix = "whatsapp")
record WhatsAppProperties(String provider, String apiKey, String templateId, String namespace, String sender, boolean enabled) {
    WhatsAppProperties { provider = provider == null || provider.isBlank() ? "mock" : provider.trim(); }
}

record ProviderRequest(String recipient, String sender, String senderName, String replyTo,
        String subject, String htmlBody, String textBody, String communicationMessageId,
        String eventType, String templateKey, String idempotencyKey) {
    String body() { return (htmlBody == null ? "" : htmlBody) + "\n" + (textBody == null ? "" : textBody); }
}
record ProviderResult(boolean success, String provider, String providerMessageId, String failureReason) {
    static ProviderResult ok(String provider) { return new ProviderResult(true, provider, provider + "-" + UUID.randomUUID(), null); }
    static ProviderResult fail(String provider, String reason) { return new ProviderResult(false, provider, null, reason); }
}

interface EmailProvider { ProviderResult send(ProviderRequest request); }
interface SmsProvider { ProviderResult send(ProviderRequest request); }
interface WhatsAppProvider { ProviderResult send(ProviderRequest request); }

@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "mock", matchIfMissing = true)
class MockEmailProvider implements EmailProvider {
    private final EmailProperties properties;
    MockEmailProvider(EmailProperties properties) { this.properties = properties; }
    public ProviderResult send(ProviderRequest request) {
        if (request.body().contains("FORCE_PROVIDER_FAILURE")) return ProviderResult.fail("MOCK_EMAIL", "mock email failure token=hidden");
        return ProviderResult.ok("MOCK_EMAIL");
    }
}

@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "smtp")
class SmtpEmailProvider implements EmailProvider {
    private final EmailProperties properties;
    private final JavaMailSender testSender;

    SmtpEmailProvider(EmailProperties properties) { this(properties, null); }
    SmtpEmailProvider(EmailProperties properties, JavaMailSender testSender) { this.properties = properties; this.testSender = testSender; }

    @Override
    public ProviderResult send(ProviderRequest request) {
        if (!properties.enabled()) return ProviderResult.fail("SMTP", "SMTP delivery disabled");
        String configurationError = configurationError();
        if (configurationError != null) return ProviderResult.fail("SMTP", configurationError);
        try {
            JavaMailSender sender = testSender == null ? configuredSender() : testSender;
            var message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(request.recipient());
            helper.setFrom(new InternetAddress(request.sender(), request.senderName()).toString());
            if (request.replyTo() != null && !request.replyTo().isBlank()) helper.setReplyTo(request.replyTo());
            helper.setSubject(request.subject() == null ? request.eventType() : request.subject());
            helper.setText(request.textBody() == null ? "" : request.textBody(), request.htmlBody());
            sender.send(message);
            return ProviderResult.ok("SMTP");
        } catch (MessagingException | java.io.UnsupportedEncodingException | RuntimeException ex) {
            return ProviderResult.fail("SMTP", "SMTP delivery failed (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private JavaMailSender configuredSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host().trim()); sender.setPort(properties.port());
        sender.setUsername(properties.username().trim()); sender.setPassword(properties.password());
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.transport.protocol", "smtp"); javaMail.put("mail.smtp.auth", "true");
        javaMail.put("mail.smtp.starttls.enable", "true"); javaMail.put("mail.smtp.starttls.required", "true");
        javaMail.put("mail.smtp.connectiontimeout", String.valueOf(properties.connectionTimeoutMs()));
        javaMail.put("mail.smtp.timeout", String.valueOf(properties.readTimeoutMs()));
        javaMail.put("mail.smtp.writetimeout", String.valueOf(properties.writeTimeoutMs()));
        return sender;
    }

    private String configurationError() {
        if (blank(properties.host()) || properties.port() == null || properties.port() < 1 || properties.port() > 65535)
            return "SMTP configuration missing or invalid";
        if (blank(properties.username()) || blank(properties.password()) || blank(properties.from()))
            return "SMTP configuration missing or invalid";
        if (properties.connectionTimeoutMs() < 1 || properties.readTimeoutMs() < 1 || properties.writeTimeoutMs() < 1)
            return "SMTP configuration missing or invalid";
        return null;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
@Component class MockSmsProvider implements SmsProvider {
    public ProviderResult send(ProviderRequest request) { return request.body().contains("FORCE_PROVIDER_FAILURE") ? ProviderResult.fail("MOCK_SMS", "provider failed token=hidden") : ProviderResult.ok("MOCK_SMS"); }
}
@Component class MockWhatsAppProvider implements WhatsAppProvider {
    public ProviderResult send(ProviderRequest request) { return request.body().contains("FORCE_PROVIDER_FAILURE") ? ProviderResult.fail("MOCK_WHATSAPP", "provider failed token=hidden") : ProviderResult.ok("MOCK_WHATSAPP"); }
}

class Msg91WhatsAppProvider implements WhatsAppProvider {
    private final WhatsAppProperties properties;
    Msg91WhatsAppProvider(WhatsAppProperties properties) { this.properties = properties; }
    public ProviderResult send(ProviderRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) return ProviderResult.fail("MSG91_WHATSAPP", "MSG91 WhatsApp configuration missing");
        return ProviderResult.fail("MSG91_WHATSAPP", "MSG91 WhatsApp external delivery not activated");
    }
}
