package com.valor.realtime;

import static org.junit.jupiter.api.Assertions.*;

import com.valor.auth.RealtimeProductionWiringTestConfiguration;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RealtimeProductionWiringTest {
    private AnnotationConfigServletWebServerApplicationContext context;
    private WebSocketStompClient client;
    private ThreadPoolTaskScheduler scheduler;
    private MessageHandler brokerObserver;
    private MessageHandler outboundObserver;
    private MessageHandler resolverObserver;
    private final BlockingQueue<Message<?>> brokerMessages = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message<?>> outboundMessages = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> resolverResults = new LinkedBlockingQueue<>();

    @BeforeEach
    void start() {
        context = new AnnotationConfigServletWebServerApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-realtime-properties",
                Map.of("app.cors.allowed-origins", "https://allowed.test",
                        "jwt.expiration", "86400000",
                        "jwt.secret", "synthetic-test-secret-not-used-for-token-validation-1234567890")));
        context.register(RealtimeProductionWiringTestConfiguration.class);
        context.refresh();
        assertEquals(1, context.getBeansOfType(SecurityFilterChain.class).size());
        RealtimeProductionWiringTestConfiguration.CONNECTED.clear();
        RealtimeProductionWiringTestConfiguration.SUBSCRIBED.clear();
        RealtimeProductionWiringTestConfiguration.LIFECYCLE.clear();
        RealtimeProductionWiringTestConfiguration.HANDSHAKE.clear();
        brokerMessages.clear();
        outboundMessages.clear();
        resolverResults.clear();
        brokerObserver = brokerMessages::offer;
        outboundObserver = outboundMessages::offer;
        resolverObserver = message -> {
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (destination != null && destination.startsWith("/user/")) {
                UserDestinationResult result = context.getBean(UserDestinationResolver.class).resolveDestination(message);
                resolverResults.offer("destination=" + destination + ",result=" + describeResolution(result));
            }
        };
        context.getBean("brokerChannel", SubscribableChannel.class).subscribe(brokerObserver);
        context.getBean("brokerChannel", SubscribableChannel.class).subscribe(resolverObserver);
        context.getBean("clientOutboundChannel", SubscribableChannel.class).subscribe(outboundObserver);
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler);
    }

    @AfterEach
    void stop() {
        if (client != null) client.stop();
        if (context != null && brokerObserver != null) {
            context.getBean("brokerChannel", SubscribableChannel.class).unsubscribe(brokerObserver);
            context.getBean("brokerChannel", SubscribableChannel.class).unsubscribe(resolverObserver);
            context.getBean("clientOutboundChannel", SubscribableChannel.class).unsubscribe(outboundObserver);
        }
        if (scheduler != null) scheduler.shutdown();
        if (context != null) context.close();
    }

    @Test
    void allowedHandshakeAndStompConnectUseProductionWiring() throws Exception {
        RealtimeProductionWiringTestConfiguration.enableHttpHandshakeUser("1");
        ClientHandler handler = new ClientHandler();
        StompSession session = connect("1", "https://allowed.test", handler, true);
        try {
            assertTrue(session.isConnected());
            awaitConnected("1");
            subscribeAndAwait(session, handler, "1");
            String registry = registrySnapshot();
            context.getBean(SimpMessagingTemplate.class).convertAndSendToUser("1", "/queue/valor-events", "production-wiring");
            assertEquals("production-wiring", handler.payloads.poll(5, TimeUnit.SECONDS), diagnostics(handler) + ",registry=" + registry);
        } finally {
            session.disconnect();
        }
    }

    @Test
    void authenticatedHttpHandshakeRegistersUserAndEnablesDelivery() throws Exception {
        RealtimeProductionWiringTestConfiguration.enableHttpHandshakeUser("1");
        ClientHandler handler = new ClientHandler();
        StompSession session = connect("1", "https://allowed.test", handler, true);
        try {
            assertTrue(session.isConnected());
            awaitConnected("1");
            subscribeAndAwait(session, handler, "1");
            String registry = registrySnapshot();
            assertTrue(registry.contains("user=1"), diagnostics(handler) + ",registry=" + registry);
            assertTrue(registry.contains("principal=1"), diagnostics(handler) + ",registry=" + registry);
            context.getBean(SimpMessagingTemplate.class).convertAndSendToUser("1", "/queue/valor-events", "handshake-principal");
            assertEquals("handshake-principal", handler.payloads.poll(5, TimeUnit.SECONDS),
                    diagnostics(handler) + ",registry=" + registry);
        } finally {
            session.disconnect();
        }
    }

    @Test
    void missingAndInvalidStompAuthenticationAreRejected() throws Exception {
        assertConnectRejected(null);
        assertConnectRejected("Bearer invalid-token");
    }

    @Test
    void invalidHttpHandshakeAuthenticationIsRejected() {
        ClientHandler handler = new ClientHandler();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer token-1");
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin("https://allowed.test");
        http.add("Authorization", "Bearer invalid-token");
        assertThrows(Exception.class, () -> client.connectAsync(
                URI.create("ws://localhost:" + context.getWebServer().getPort() + "/ws"), http, headers, handler)
                .get(5, TimeUnit.SECONDS));
    }

    @Test
    void disallowedOriginIsRejectedAtHandshake() {
        ClientHandler handler = new ClientHandler();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer token-1");
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin("https://denied.test");
        assertThrows(Exception.class, () -> client.connectAsync(
                URI.create("ws://localhost:" + context.getWebServer().getPort() + "/ws"), http, headers, handler)
                .get(5, TimeUnit.SECONDS));
    }

    @Test
    void twoProductionWiringClientsReceiveOnlyTheirOwnMessages() throws Exception {
        RealtimeProductionWiringTestConfiguration.enableHttpHandshakeUser("1");
        RealtimeProductionWiringTestConfiguration.enableHttpHandshakeUser("2");
        ClientHandler a = new ClientHandler();
        ClientHandler b = new ClientHandler();
        StompSession sa = connect("1", "https://allowed.test", a, true);
        StompSession sb = connect("2", "https://allowed.test", b, true);
        try {
            awaitConnected("1");
            awaitConnected("2");
            subscribeAndAwait(sa, a, "1");
            subscribeAndAwait(sb, b, "2");
            String registry = registrySnapshot();
            SimpMessagingTemplate messaging = context.getBean(SimpMessagingTemplate.class);
            messaging.convertAndSendToUser("1", "/queue/valor-events", "for-a");
            assertEquals("for-a", a.payloads.poll(5, TimeUnit.SECONDS), diagnostics(a) + ",registry=" + registry);
            assertNull(b.payloads.poll(500, TimeUnit.MILLISECONDS));
            messaging.convertAndSendToUser("2", "/queue/valor-events", "for-b");
            assertEquals("for-b", b.payloads.poll(5, TimeUnit.SECONDS), diagnostics(b));
            assertNull(a.payloads.poll(500, TimeUnit.MILLISECONDS));
        } finally {
            sa.disconnect();
            sb.disconnect();
        }
    }

    private StompSession connect(String user, String origin, ClientHandler handler) throws Exception {
        return connect(user, origin, handler, false);
    }

    private StompSession connect(String user, String origin, ClientHandler handler, boolean authenticateHttpHandshake) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer token-" + user);
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin(origin);
        if (authenticateHttpHandshake) http.add("Authorization", "Bearer token-" + user);
        return client.connectAsync(URI.create("ws://localhost:" + context.getWebServer().getPort() + "/ws"), http, headers, handler)
                .get(5, TimeUnit.SECONDS);
    }

    private void assertConnectRejected(String authorization) {
        ClientHandler handler = new ClientHandler();
        StompHeaders headers = new StompHeaders();
        if (authorization != null) headers.add("Authorization", authorization);
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin("https://allowed.test");
        assertThrows(Exception.class, () -> client.connectAsync(
                URI.create("ws://localhost:" + context.getWebServer().getPort() + "/ws"), http, headers, handler)
                .get(5, TimeUnit.SECONDS));
    }

    private void awaitConnected(String expectedPrincipal) throws Exception {
        String event = RealtimeProductionWiringTestConfiguration.CONNECTED.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "No SessionConnectedEvent for expected principal " + expectedPrincipal);
    }

    private void subscribeAndAwait(StompSession session, ClientHandler handler, String expectedPrincipal) throws Exception {
        session.setAutoReceipt(true);
        StompSession.Subscription subscription = session.subscribe("/user/queue/valor-events", handler);
        String event = RealtimeProductionWiringTestConfiguration.SUBSCRIBED.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "No SessionSubscribeEvent for expected principal " + expectedPrincipal);
        java.util.concurrent.CompletableFuture<Void> receipt = new java.util.concurrent.CompletableFuture<>();
        subscription.addReceiptTask(headers -> receipt.complete(null));
        subscription.addReceiptLostTask(() -> receipt.completeExceptionally(new IllegalStateException("receipt lost")));
        assertTrue(receipt.completeOnTimeout(null, 5, TimeUnit.SECONDS).get(6, TimeUnit.SECONDS) == null,
                "Subscription receipt did not complete: " + event);
    }

    private String diagnostics(ClientHandler handler) {
        return "connected=" + RealtimeProductionWiringTestConfiguration.CONNECTED
                + ",subscribed=" + RealtimeProductionWiringTestConfiguration.SUBSCRIBED
                + ",lifecycle=" + RealtimeProductionWiringTestConfiguration.LIFECYCLE
                + ",handshake=" + RealtimeProductionWiringTestConfiguration.HANDSHAKE
                + ",broker=" + summarize(brokerMessages)
                + ",outbound=" + summarize(outboundMessages)
                + ",resolver=" + resolverResults
                + ",clientErrors=" + handler.errors;
    }

    private String registrySnapshot() {
        SimpUserRegistry registry = context.getBean(SimpUserRegistry.class);
        return registry.getUsers().stream().map(user -> "user=" + user.getName() + ",sessions="
                + user.getSessions().stream().map(session -> "id=" + session.getId() + ",principal="
                        + (session.getUser() == null ? "<null>" : session.getUser().getName())).toList()).toList().toString();
    }

    private static String describeResolution(UserDestinationResult result) {
        if (result == null) return "<null>";
        return "source=" + result.getSourceDestination() + ",target=" + result.getTargetDestinations()
                + ",subscribeDestination=" + result.getSubscribeDestination();
    }

    private static String summarize(BlockingQueue<Message<?>> messages) {
        return messages.stream().map(message -> {
            var h = message.getHeaders();
            return "{type=" + h.get("simpMessageType") + ",destination=" + h.get("simpDestination")
                    + ",session=" + h.get("simpSessionId") + ",subscription=" + h.get("simpSubscriptionId") + "}";
        }).toList().toString();
    }

    private static final class ClientHandler extends StompSessionHandlerAdapter implements StompFrameHandler {
        final BlockingQueue<String> payloads = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) { payloads.offer(String.valueOf(payload)); }
        @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) { errors.offer(exception.getClass().getSimpleName()); }
        @Override public void handleTransportError(StompSession session, Throwable exception) { errors.offer(exception.getClass().getSimpleName()); }
    }
}
