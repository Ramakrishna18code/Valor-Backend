package com.valor.communication;

import java.util.UUID;
import org.springframework.stereotype.Component;

record ProviderRequest(String recipient, String subject, String body, String idempotencyKey) {}
record ProviderResult(boolean success, String provider, String providerMessageId, String failureReason) {
    static ProviderResult ok(String provider) { return new ProviderResult(true, provider, provider + "-" + UUID.randomUUID(), null); }
    static ProviderResult fail(String provider, String reason) { return new ProviderResult(false, provider, null, reason); }
}

interface EmailProvider { ProviderResult send(ProviderRequest request); }
interface SmsProvider { ProviderResult send(ProviderRequest request); }
interface WhatsAppProvider { ProviderResult send(ProviderRequest request); }

@Component class MockEmailProvider implements EmailProvider {
    public ProviderResult send(ProviderRequest request) { return request.body().contains("FORCE_PROVIDER_FAILURE") ? ProviderResult.fail("MOCK_EMAIL", "provider failed token=hidden") : ProviderResult.ok("MOCK_EMAIL"); }
}
@Component class MockSmsProvider implements SmsProvider {
    public ProviderResult send(ProviderRequest request) { return request.body().contains("FORCE_PROVIDER_FAILURE") ? ProviderResult.fail("MOCK_SMS", "provider failed token=hidden") : ProviderResult.ok("MOCK_SMS"); }
}
@Component class MockWhatsAppProvider implements WhatsAppProvider {
    public ProviderResult send(ProviderRequest request) { return request.body().contains("FORCE_PROVIDER_FAILURE") ? ProviderResult.fail("MOCK_WHATSAPP", "provider failed token=hidden") : ProviderResult.ok("MOCK_WHATSAPP"); }
}
