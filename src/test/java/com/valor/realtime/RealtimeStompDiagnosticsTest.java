package com.valor.realtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.valor.auth.JwtService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

class RealtimeStompDiagnosticsTest {
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final Logger interceptorLog = (Logger) LoggerFactory.getLogger(RealtimeChannelInterceptor.class);
    private final Logger protocolLog = (Logger) LoggerFactory.getLogger(RealtimeStompErrorHandler.class);
    private final RealtimeChannelInterceptor interceptor =
            new RealtimeChannelInterceptor(mock(JwtService.class), mock(UserDetailsService.class));

    @BeforeEach void attach() {
        events.start();
        interceptorLog.addAppender(events);
        protocolLog.addAppender(events);
    }

    @AfterEach void detach() {
        interceptorLog.detachAppender(events);
        protocolLog.detachAppender(events);
        events.stop();
    }

    @Test void laterInterceptorFailureIsCapturedByDispatchWithoutAllowingMessage() {
        var channel = new ExecutorSubscribableChannel();
        channel.addInterceptor(interceptor);
        var original = new IllegalStateException("synthetic-sensitive-exception");
        channel.addInterceptor(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel ignored) {
                throw original;
            }
        });
        var failure = assertThrows(MessageDeliveryException.class, () -> channel.send(subscription()));
        assertSame(original, failure.getCause());
        assertTrue(logs().contains("stage=dispatch command=SUBSCRIBE"));
        assertTrue(logs().contains("destination=/user/queue/valor-events"));
        assertTrue(logs().contains("java.lang.IllegalStateException"));
        assertTrue(logs().contains("RealtimeStompDiagnosticsTest.java:"));
        assertFalse(logs().contains("synthetic-sensitive-exception"));
    }

    @Test void executorFailureAfterSendReturnsIsCapturedWithoutSwallowingIt() {
        // Deterministically run the queued worker after the dispatch callback has completed.
        List<Runnable> pending = new ArrayList<>();
        var channel = new ExecutorSubscribableChannel(pending::add);
        channel.addInterceptor(interceptor);
        var original = new IllegalStateException("synthetic-sensitive-worker");
        channel.subscribe(message -> { throw original; });
        assertTrue(channel.send(subscription()));
        assertTrue(events.list.isEmpty());
        var failure = assertThrows(MessageDeliveryException.class, () -> pending.getFirst().run());
        assertSame(original, failure.getCause());
        assertTrue(logs().contains("stage=executor command=SUBSCRIBE"));
        assertTrue(logs().contains("java.lang.IllegalStateException"));
        assertFalse(logs().contains("synthetic-sensitive-worker"));
    }

    @Test void protocolBoundaryCapturesCauseChainAndKeepsSpringErrorResponse() {
        Message<byte[]> message = subscription();
        var cause = new IllegalStateException("synthetic-secret-in-cause");
        cause.addSuppressed(new IllegalArgumentException("synthetic-secret-in-suppressed"));
        var failure = new MessageDeliveryException(message, "generic delivery failure", cause);
        Message<byte[]> response = new RealtimeStompErrorHandler().handleClientMessageProcessingError(message, failure);
        var headers = StompHeaderAccessor.wrap(response);
        assertEquals(StompCommand.ERROR, headers.getCommand());
        assertEquals("generic delivery failure", headers.getMessage());
        assertEquals("synthetic-receipt", headers.getReceiptId());
        assertEquals(0, response.getPayload().length);
        assertTrue(logs().contains("stage=protocol command=SUBSCRIBE"));
        assertTrue(logs().contains("Caused by: java.lang.IllegalStateException"));
        assertTrue(logs().contains("Suppressed: java.lang.IllegalArgumentException"));
        assertTrue(logs().contains("authenticated=true roles=[ROLE_ADMIN]"));
        assertFalse(logs().contains("synthetic-secret"));
        assertFalse(logs().contains("Authorization"));
        assertFalse(logs().contains("synthetic-identity"));
        assertTrue(events.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    }

    @Test void undecodedMessageAndUntrustedDestinationCannotLeakValues() {
        new RealtimeStompErrorHandler().handleClientMessageProcessingError(null, new IllegalArgumentException("synthetic-secret"));
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/synthetic-secret/private");
        headers.setSessionId("synthetic-secret-session");
        interceptor.afterSendCompletion(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
                null, false, new IllegalArgumentException("synthetic-secret"));
        assertTrue(logs().contains("command=null"));
        assertTrue(logs().contains("destination=<other-redacted>"));
        assertFalse(logs().contains("synthetic-secret"));
    }

    private Message<byte[]> subscription() {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/valor-events");
        headers.setSubscriptionId("synthetic-subscription");
        headers.setSessionId("00000000-0000-0000-0000-000000000001");
        headers.setReceipt("synthetic-receipt");
        headers.addNativeHeader("Authorization", "Bearer synthetic-secret-header");
        var user = User.withUsername("synthetic-identity").password("synthetic-secret-password").roles("ADMIN").build();
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private String logs() {
        return events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
    }
}
