package com.valor.auth;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "msg91")
record Msg91OtpProperties(String authKey, String templateId, String senderId, int otpExpirySeconds,
        String provider, boolean enabled) {
    Msg91OtpProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider.trim();
        otpExpirySeconds = otpExpirySeconds <= 0 ? 300 : otpExpirySeconds;
    }
    boolean configured() { return authKey != null && !authKey.isBlank() && templateId != null && !templateId.isBlank(); }
}

record OtpProviderRequest(String phone, String otp, Long requestId, String idempotencyKey) {}
record OtpProviderResult(boolean success, String provider, String providerReference, String failureReason, boolean transientFailure) {
    static OtpProviderResult ok(String provider) { return new OtpProviderResult(true, provider, provider + "-" + UUID.randomUUID(), null, false); }
    static OtpProviderResult fail(String provider, String reason, boolean transientFailure) { return new OtpProviderResult(false, provider, null, reason, transientFailure); }
}

interface OtpProvider {
    OtpProviderResult send(OtpProviderRequest request);
    OtpProviderResult resend(OtpProviderRequest request);
}

@Component
class MockOtpProvider implements OtpProvider {
    public OtpProviderResult send(OtpProviderRequest request) { return result(request); }
    public OtpProviderResult resend(OtpProviderRequest request) { return result(request); }
    private OtpProviderResult result(OtpProviderRequest request) {
        if (request.phone().endsWith("000")) return OtpProviderResult.fail("MOCK_OTP", "mock provider timeout authKey=hidden", true);
        if (request.phone().endsWith("999")) return OtpProviderResult.fail("MOCK_OTP", "mock provider rejected templateId=hidden", false);
        return OtpProviderResult.ok("MOCK_OTP");
    }
}

class Msg91OtpProvider implements OtpProvider {
    private final Msg91OtpProperties properties;
    Msg91OtpProvider(Msg91OtpProperties properties) { this.properties = properties; }
    public OtpProviderResult send(OtpProviderRequest request) { return disabled(); }
    public OtpProviderResult resend(OtpProviderRequest request) { return disabled(); }
    private OtpProviderResult disabled() {
        if (!properties.configured()) return OtpProviderResult.fail("MSG91_OTP", "MSG91 OTP configuration missing", false);
        return OtpProviderResult.fail("MSG91_OTP", "MSG91 OTP external delivery not activated", true);
    }
}
