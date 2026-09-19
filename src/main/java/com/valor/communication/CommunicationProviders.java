package com.valor.communication;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "email")
record EmailProperties(String provider, String host, Integer port, String username, String password,
        String apiKey, String from, String fromName, String replyTo, boolean enabled) {
    EmailProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider.trim();
        from = from == null || from.isBlank() ? "no-reply@example.test" : from.trim();
        fromName = fromName == null || fromName.isBlank() ? "Valor" : fromName.trim();
        replyTo = replyTo == null || replyTo.isBlank() ? from : replyTo.trim();
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

@Component class MockEmailProvider implements EmailProvider {
    private final EmailProperties properties;
    MockEmailProvider(EmailProperties properties) { this.properties = properties; }
    public ProviderResult send(ProviderRequest request) {
        if (request.body().contains("FORCE_PROVIDER_FAILURE")) return ProviderResult.fail("MOCK_EMAIL", "mock email failure token=hidden");
        return ProviderResult.ok("MOCK_EMAIL");
    }
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
