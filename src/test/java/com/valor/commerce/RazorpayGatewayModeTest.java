package com.valor.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RazorpayGatewayModeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void explicitTestModeAcceptsTestKeyWithoutCallingGateway() {
        RazorpayGateway gateway = gateway("rzp_test_fixture", "test", false);

        assertDoesNotThrow(gateway::validateMode);
    }

    @Test
    void explicitLiveModeAcceptsLiveKeyDuringConfigurationValidationOnly() {
        RazorpayGateway gateway = gateway("rzp_live_fixture", "live", false);

        assertDoesNotThrow(gateway::validateMode);
    }

    @Test
    void mismatchedModeFailsBeforeExternalRequest() {
        RestClient rest = mock(RestClient.class);
        RazorpayGateway gateway = gateway("rzp_live_fixture", "test", false, rest);

        assertThrows(CommerceException.class,
                () -> gateway.createOrder(new BigDecimal("10.00"), "INR", "receipt"));
        verifyNoInteractions(rest);
    }

    @Test
    void missingOrInvalidModeFailsBeforeExternalRequest() {
        for (String mode : new String[]{"", "sandbox", "production"}) {
            RestClient rest = mock(RestClient.class);
            RazorpayGateway gateway = gateway("rzp_test_fixture", mode, false, rest);

            assertThrows(CommerceException.class,
                    () -> gateway.createOrder(new BigDecimal("10.00"), "INR", "receipt"));
            verifyNoInteractions(rest);
        }
    }

    @Test
    void missingCredentialsRemainSafelyRejected() {
        RazorpayGateway gateway = gateway("", "test", false);

        CommerceException error = assertThrows(CommerceException.class,
                () -> gateway.createOrder(new BigDecimal("10.00"), "INR", "receipt"));
        assertEquals(503, error.status);
        assertFalse(error.getMessage().contains("rzp_"));
    }

    @Test
    void offlineModeRemainsSeparateFromExternalModeGuard() {
        RazorpayGateway gateway = gateway("rzp_test_fixture", "", true);

        assertDoesNotThrow(() -> gateway.createOrder(new BigDecimal("10.00"), "INR", "receipt"));
    }

    private RazorpayGateway gateway(String keyId, String mode, boolean offline) {
        return gateway(keyId, mode, offline, mock(RestClient.class));
    }

    private RazorpayGateway gateway(String keyId, String mode, boolean offline, RestClient rest) {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(rest);
        return new RazorpayGateway(keyId, "secret-fixture", "webhook-fixture", mode, offline, builder, mapper);
    }
}
