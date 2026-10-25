package com.valor.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.user.UserDestinationResolver;
import org.springframework.messaging.simp.user.UserDestinationResult;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class RealtimeWebSocketAuthenticatedTest {
    private static AnnotationConfigServletWebServerApplicationContext context;
    private static WebSocketStompClient client;
    private static MessageHandler brokerObserver;
    private static MessageHandler outboundObserver;
    private static final BlockingQueue<Message<?>> BROKER_MESSAGES = new java.util.concurrent.LinkedBlockingQueue<>();
    private static final BlockingQueue<Message<?>> OUTBOUND_MESSAGES = new java.util.concurrent.LinkedBlockingQueue<>();
    private static ThreadPoolTaskScheduler scheduler;

    @BeforeAll
    static void startServer() {
        RealtimeWebSocketAuthenticatedConfiguration.CONNECTED.clear();
        RealtimeWebSocketAuthenticatedConfiguration.SUBSCRIBED.clear();
        RealtimeWebSocketAuthenticatedConfiguration.INBOUND.clear();
        RealtimeWebSocketAuthenticatedConfiguration.INBOUND_ERRORS.clear();
        context = new AnnotationConfigServletWebServerApplicationContext();
        context.register(RealtimeWebSocketAuthenticatedConfiguration.class);
        context.refresh();
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        BROKER_MESSAGES.clear();
        OUTBOUND_MESSAGES.clear();
        brokerObserver = BROKER_MESSAGES::offer;
        outboundObserver = OUTBOUND_MESSAGES::offer;
        context.getBean("brokerChannel", SubscribableChannel.class).subscribe(brokerObserver);
        context.getBean("clientOutboundChannel", SubscribableChannel.class).subscribe(outboundObserver);
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler);
    }

    @AfterAll
    static void stopServer() {
        if (client != null) client.stop();
        if (context != null) {
            context.getBean("brokerChannel", SubscribableChannel.class).unsubscribe(brokerObserver);
            context.getBean("clientOutboundChannel", SubscribableChannel.class).unsubscribe(outboundObserver);
        }
        if (scheduler != null) scheduler.shutdown();
        if (context != null) context.close();
    }

    @Test
    void realSessionResolvesAndReceivesUserMessage() throws Exception {
        ClientHandler handler = new ClientHandler();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer token-1");
        StompSession session = client.connectAsync(
                URI.create("ws://localhost:" + context.getWebServer().getPort() + "/test-ws"),
                null, connectHeaders, handler).get(5, TimeUnit.SECONDS);

        var connected = RealtimeWebSocketAuthenticatedConfiguration.CONNECTED.poll(5, TimeUnit.SECONDS);
        assertNotNull(connected);
        assertNotNull(connected.getUser());
        assertEquals("1", connected.getUser().getName());

        session.setAutoReceipt(true);
        StompSession.Subscription subscription = session.subscribe("/user/queue/valor-events", handler);
        CompletableFuture<Void> receipt = new CompletableFuture<>();
        subscription.addReceiptTask(headers -> receipt.complete(null));
        subscription.addReceiptLostTask(() -> receipt.complete(null));
        var subscribed = RealtimeWebSocketAuthenticatedConfiguration.SUBSCRIBED.poll(5, TimeUnit.SECONDS);
        assertNotNull(subscribed, diagnostics(handler));
        String serverSessionId = SimpMessageHeaderAccessor.getSessionId(subscribed.getMessage().getHeaders());
        assertNotNull(serverSessionId);

        SimpUser user = context.getBean(SimpUserRegistry.class).getUser("1");
        assertNotNull(user);
        assertNotNull(user.getSession(serverSessionId),
                () -> "clientSession=" + session.getSessionId()
                        + ", registrySessions=" + user.getSessions()
                        + ", subscribedHeaders=" + subscribed.getMessage().getHeaders());

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination("/user/1/queue/valor-events");
        accessor.setUser(connected.getUser());
        Message<String> probe = MessageBuilder.createMessage("probe", accessor.getMessageHeaders());
        UserDestinationResult resolved = context.getBean(UserDestinationResolver.class).resolveDestination(probe);
        assertNotNull(resolved);
        assertTrue(resolved.getTargetDestinations().stream().anyMatch(destination -> destination.contains(serverSessionId)));

        context.getBean(SimpMessagingTemplate.class).convertAndSendToUser("1", "/queue/valor-events", "revalidated");
        assertEquals("revalidated", handler.payloads.poll(5, TimeUnit.SECONDS),
                () -> "broker=" + describe(BROKER_MESSAGES)
                        + ", outbound=" + describe(OUTBOUND_MESSAGES)
                        + ", clientErrors=" + handler.errors);
        session.disconnect();
    }

    @Test
    void twoClientsReceiveOnlyTheirOwnUserMessages() throws Exception {
        ClientHandler handlerA = new ClientHandler();
        ClientHandler handlerB = new ClientHandler();
        StompSession sessionA = connect("1", handlerA);
        StompSession sessionB = connect("2", handlerB);
        try {
            Map<String, String> serverSessions = new HashMap<>();
            subscribeAndRecord("1", sessionA, handlerA, serverSessions);
            subscribeAndRecord("2", sessionB, handlerB, serverSessions);

            SimpUserRegistry registry = context.getBean(SimpUserRegistry.class);
            assertNotNull(registry.getUser("1"));
            assertNotNull(registry.getUser("2"));
            assertNotNull(registry.getUser("1").getSession(serverSessions.get("1")));
            assertNotNull(registry.getUser("2").getSession(serverSessions.get("2")));

            SimpMessagingTemplate messaging = context.getBean(SimpMessagingTemplate.class);
            messaging.convertAndSendToUser("1", "/queue/valor-events", "event-for-A");
            assertEquals("event-for-A", handlerA.payloads.poll(5, TimeUnit.SECONDS));
            assertNull(handlerB.payloads.poll(500, TimeUnit.MILLISECONDS));

            messaging.convertAndSendToUser("2", "/queue/valor-events", "event-for-B");
            assertEquals("event-for-B", handlerB.payloads.poll(5, TimeUnit.SECONDS));
            assertNull(handlerA.payloads.poll(500, TimeUnit.MILLISECONDS));

            messaging.convertAndSendToUser("1", "/queue/valor-events", "event-for-A-2");
            assertEquals("event-for-A-2", handlerA.payloads.poll(5, TimeUnit.SECONDS));
            assertNull(handlerB.payloads.poll(500, TimeUnit.MILLISECONDS));

            messaging.convertAndSendToUser("2", "/queue/valor-events", "event-for-B-2");
            assertEquals("event-for-B-2", handlerB.payloads.poll(5, TimeUnit.SECONDS));
            assertNull(handlerA.payloads.poll(500, TimeUnit.MILLISECONDS));
        } finally {
            sessionA.disconnect();
            sessionB.disconnect();
        }
    }

    @Test
    void adminTopicIsDeliveredOnlyToAuthorizedRoles() throws Exception {
        ClientHandler adminHandler = new ClientHandler();
        ClientHandler superAdminHandler = new ClientHandler();
        StompSession adminSession = connect("3", adminHandler);
        StompSession superAdminSession = connect("4", superAdminHandler);
        try {
            Map<String, String> serverSessions = new HashMap<>();
            subscribeAndRecord("3", adminSession, adminHandler, serverSessions, "/topic/admin");
            subscribeAndRecord("4", superAdminSession, superAdminHandler, serverSessions, "/topic/admin");
            assertNotNull(context.getBean(SimpUserRegistry.class).getUser("3").getSession(serverSessions.get("3")));
            assertNotNull(context.getBean(SimpUserRegistry.class).getUser("4").getSession(serverSessions.get("4")));

            context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/admin", "admin-event");
            assertEquals("admin-event", adminHandler.payloads.poll(5, TimeUnit.SECONDS));
            assertEquals("admin-event", superAdminHandler.payloads.poll(5, TimeUnit.SECONDS));
        } finally {
            closeIfConnected(adminSession);
            closeIfConnected(superAdminSession);
        }

        ClientHandler customerHandler = new ClientHandler();
        ClientHandler technicianHandler = new ClientHandler();
        StompSession customerSession = connect("1", customerHandler);
        StompSession technicianSession = connect("2", technicianHandler);
        try {
            assertRejectedSubscription(customerSession, customerHandler, "/topic/admin");
            assertRejectedSubscription(technicianSession, technicianHandler, "/topic/admin");
            context.getBean(SimpMessagingTemplate.class).convertAndSend("/topic/admin", "admin-event-negative");
            assertNull(customerHandler.payloads.poll(500, TimeUnit.MILLISECONDS));
            assertNull(technicianHandler.payloads.poll(500, TimeUnit.MILLISECONDS));
        } finally {
            closeIfConnected(customerSession);
            closeIfConnected(technicianSession);
        }

        for (String destination : new String[]{"/topic/admin-extra", "/topic/admin/private"}) {
            ClientHandler adminNegativeHandler = new ClientHandler();
            StompSession negativeSession = connect("3", adminNegativeHandler);
            try {
                assertRejectedSubscription(negativeSession, adminNegativeHandler, destination);
            } finally {
                closeIfConnected(negativeSession);
            }
        }
    }

    private StompSession connect(String userId, ClientHandler handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer token-" + userId);
        return client.connectAsync(
                URI.create("ws://localhost:" + context.getWebServer().getPort() + "/test-ws?user=" + userId),
                null, connectHeaders, handler).get(5, TimeUnit.SECONDS);
    }

    private void subscribeAndRecord(String userId, StompSession session, ClientHandler handler,
                                    Map<String, String> serverSessions) throws Exception {
        subscribeAndRecord(userId, session, handler, serverSessions, "/user/queue/valor-events");
    }

    private void subscribeAndRecord(String userId, StompSession session, ClientHandler handler,
                                    Map<String, String> serverSessions, String destination) throws Exception {
        session.subscribe(destination, handler);
        SessionSubscribeEvent event;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            event = RealtimeWebSocketAuthenticatedConfiguration.SUBSCRIBED.poll(100, TimeUnit.MILLISECONDS);
        } while (event != null && !userId.equals(event.getUser().getName()) && System.nanoTime() < deadline);
        assertNotNull(event);
        assertEquals(userId, event.getUser().getName());
        serverSessions.put(userId, SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders()));
    }

    private void assertRejectedSubscription(StompSession session, ClientHandler handler, String destination) throws Exception {
        session.subscribe(destination, handler);
        assertNotNull(handler.errors.poll(5, TimeUnit.SECONDS),
                () -> "No rejection evidence for destination " + destination
                        + ", inbound=" + RealtimeWebSocketAuthenticatedConfiguration.INBOUND);
    }

    private void closeIfConnected(StompSession session) {
        if (session != null && session.isConnected()) session.disconnect();
    }

    private static String describe(BlockingQueue<Message<?>> messages) {
        return messages.stream().map(message -> {
            var headers = message.getHeaders();
            return "{type=" + headers.get("simpMessageType")
                    + ", destination=" + headers.get("simpDestination")
                    + ", session=" + headers.get("simpSessionId")
                    + ", subscription=" + headers.get("simpSubscriptionId")
                    + ", payloadType=" + message.getPayload().getClass().getName() + "}";
        }).toList().toString();
    }

    private String diagnostics(ClientHandler handler) {
        return "inbound=" + RealtimeWebSocketAuthenticatedConfiguration.INBOUND.stream()
                .map(message -> message.getHeaders().toString()).toList()
                + ", inboundErrors=" + RealtimeWebSocketAuthenticatedConfiguration.INBOUND_ERRORS
                + ", clientErrors=" + handler.errors;
    }

    private static final class ClientHandler extends StompSessionHandlerAdapter implements StompFrameHandler {
        private final BlockingQueue<String> payloads = new java.util.concurrent.LinkedBlockingQueue<>();
        private final BlockingQueue<String> errors = new java.util.concurrent.LinkedBlockingQueue<>();
        @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            errors.offer(headers + " payload=" + payload);
            payloads.offer((String) payload);
        }
        @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) { errors.offer(exception.toString()); }
        @Override public void handleTransportError(StompSession session, Throwable exception) { errors.offer(exception.toString()); }
    }
}
