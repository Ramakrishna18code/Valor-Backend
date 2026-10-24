package com.valor.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.messaging.simp.stomp.StompCommand.CONNECT;
import static org.springframework.messaging.simp.stomp.StompCommand.SEND;
import static org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;

@SpringJUnitConfig(RealtimeBrokerTestConfiguration.class)
class RealtimeBrokerIntegrationTest {
    @Autowired MessageChannel clientInboundChannel;
    @Autowired RealtimeChannelInterceptor interceptor;

    @Test
    void authenticatedConnectsUseSyntheticServerLoadedIdentities() {
        for (String id : new String[]{"1", "2", "3", "4"}) {
            assertDoesNotThrow(() -> clientInboundChannel.send(frame(CONNECT, null, id, "token-" + id)));
        }
    }

    @Test
    void missingAndInvalidConnectAuthenticationAreRejectedAtInboundBoundary() {
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(frame(CONNECT, null, "1", null), mock(MessageChannel.class)));
        assertThrows(UsernameNotFoundException.class, () -> interceptor.preSend(frame(CONNECT, null, "1", "not-a-token"), mock(MessageChannel.class)));
    }

    @Test
    void brokerInboundSubscriptionsEnforceAdminAndUserDestinations() {
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(frame(SUBSCRIBE, "/topic/admin", "1", null), mock(MessageChannel.class)));
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(frame(SUBSCRIBE, "/topic/admin", "2", null), mock(MessageChannel.class)));
        assertDoesNotThrow(() -> clientInboundChannel.send(frame(SUBSCRIBE, "/topic/admin", "3", null)));
        assertDoesNotThrow(() -> clientInboundChannel.send(frame(SUBSCRIBE, "/topic/admin", "4", null)));
        assertDoesNotThrow(() -> clientInboundChannel.send(frame(SUBSCRIBE, "/user/queue/valor-events", "1", null)));
        assertDoesNotThrow(() -> clientInboundChannel.send(frame(SUBSCRIBE, "/user/queue/valor-events", "2", null)));
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(frame(SUBSCRIBE, "/topic/other", "1", null), mock(MessageChannel.class)));
    }

    @Test
    void clientSendsAreRejectedForAllRelevantDestinations() {
        for (String destination : new String[]{"/app/example", "/topic/admin", "/user/queue/valor-events", "/queue/other"}) {
            assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(frame(SEND, destination, "1", null), mock(MessageChannel.class)));
        }
    }

    private Message<byte[]> frame(org.springframework.messaging.simp.stomp.StompCommand command, String destination, String userId, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-" + userId);
        if (destination != null) accessor.setDestination(destination);
        if (SUBSCRIBE.equals(command)) accessor.setSubscriptionId("subscription-" + userId + "-" + destination);
        if (token != null) accessor.addNativeHeader("Authorization", "Bearer " + token);
        if (!CONNECT.equals(command)) {
            var details = RealtimeBrokerTestConfiguration.USERS.get(userId);
            accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
