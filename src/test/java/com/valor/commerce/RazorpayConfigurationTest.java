package com.valor.commerce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RazorpayConfigurationTest {
    private RazorpayGateway gateway(String keyId, String keySecret, String webhookSecret, String mode, boolean offline) {
        return new RazorpayGateway(keyId, keySecret, webhookSecret, mode, offline, true, false, RestClient.builder(), new ObjectMapper());
    }

    @Test
    void acceptsCompleteSandboxConfiguration() {
        assertDoesNotThrow(() -> gateway("rzp_test_id", "secret", "dashboard-webhook-secret", "test", false)
                .validateConfiguration());
    }

    @Test
    void rejectsIncompleteOrMismatchedConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> gateway("rzp_test_id", "secret", "", "test", false).validateConfiguration());
        assertThrows(IllegalStateException.class,
                () -> gateway("rzp_live_id", "secret", "dashboard-webhook-secret", "test", false).validateConfiguration());
    }

    @Test
    void permitsExplicitOfflineTestModeWithoutProviderCredentials() {
        assertDoesNotThrow(() -> gateway("", "", "", "", true).validateConfiguration());
    }

    @Test
    void devDisabledGatewayStartsWithoutCredentialsAndRejectsOperations() {
        RazorpayGateway gateway = new RazorpayGateway("", "", "", "", false, false, false,
                RestClient.builder(), new ObjectMapper());

        assertDoesNotThrow(gateway::validateConfiguration);
        assertThrows(CommerceException.class,
                () -> gateway.createOrder(java.math.BigDecimal.TEN, "INR", "dev-disabled"));
    }

    @Test
    void productionRequiredModeFailsClosedForMissingCredentials() {
        RazorpayGateway gateway = new RazorpayGateway("", "", "", "", false, true, true,
                RestClient.builder(), new ObjectMapper());

        assertThrows(IllegalStateException.class, gateway::validateConfiguration);
    }
}
