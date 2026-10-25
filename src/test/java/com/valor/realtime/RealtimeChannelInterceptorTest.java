package com.valor.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.valor.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

class RealtimeChannelInterceptorTest {
    private final JwtService jwt = mock(JwtService.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private RealtimeChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RealtimeChannelInterceptor(jwt, users);
    }

    @Test
    void authenticatesConnectUsingExistingJwtIdentity() {
        UserDetails details = User.withUsername("42").password("unused").roles("CUSTOMER").build();
        when(jwt.subject("token")).thenReturn(42L);
        when(users.loadUserByUsername("42")).thenReturn(details);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token");

        Message<?> authenticated = interceptor.preSend(message(accessor), channel);
        assertEquals("42", StompHeaderAccessor.wrap(authenticated).getUser().getName());
        verify(jwt).subject("token");
        verify(users).loadUserByUsername("42");
    }

    @Test
    void rejectsMissingConnectAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void restrictsAdminTopicToAdminRoles() {
        UserDetails details = User.withUsername("42").password("unused").roles("TECHNICIAN").build();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin");
        accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));

        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void rejectsUnauthenticatedUserQueueSubscriptions() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/valor-events");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void permitsExactAdminTopicOnlyToAdminRoles() {
        UserDetails details = User.withUsername("42").password("unused").roles("ADMIN").build();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin");
        accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        assertDoesNotThrow(() -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void rejectsAdminPrefixedButNonExactDestinations() {
        UserDetails details = User.withUsername("42").password("unused").roles("ADMIN").build();
        for (String destination : new String[]{"/topic/admin-extra", "/topic/admin/private"}) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination(destination);
            accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
        }
    }

    @Test
    void rejectsSubscriptionsWithoutMeaningfulDestinations() {
        UserDetails details = User.withUsername("42").password("unused").roles("CUSTOMER").build();
        for (String destination : new String[]{null, "", "   "}) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination(destination);
            accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
        }
    }

    @Test
    void permitsOnlyTheUserQueueForNonAdminSubscriptions() {
        UserDetails details = User.withUsername("42").password("unused").roles("CUSTOMER").build();
        StompHeaderAccessor allowed = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        allowed.setDestination("/user/queue/valor-events");
        allowed.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        assertDoesNotThrow(() -> interceptor.preSend(message(allowed), channel));

        StompHeaderAccessor forbidden = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        forbidden.setDestination("/user/queue/another-user");
        forbidden.setUser(allowed.getUser());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(forbidden), channel));
    }

    @Test
    void rejectsClientSendToServerOwnedAdminTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/admin");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void rejectsClientSendToServerOwnedUserQueue() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/user/queue/valor-events");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void rejectsClientSendToApplicationDestinationBecauseNoClientHandlersExist() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/example");
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    @Test
    void rejectsClientSendWithoutDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message(accessor), channel));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
