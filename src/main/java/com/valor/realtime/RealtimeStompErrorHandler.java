package com.valor.realtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/** Diagnostics only: Spring still creates the ERROR frame and closes the session. */
final class RealtimeStompErrorHandler extends StompSubProtocolErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeStompErrorHandler.class);
    private static final Set<String> ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_CUSTOMER", "ROLE_TECHNICIAN");

    @Override public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> message, Throwable failure) {
        logFailure(log, "protocol", message, failure);
        return super.handleClientMessageProcessingError(message, failure);
    }

    static void logFailure(Logger logger, String stage, Message<?> message, Throwable failure) {
        if (!logger.isWarnEnabled()) return;
        StompHeaderAccessor accessor = message == null ? null
                : MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        String destination = accessor == null ? null : accessor.getDestination();
        String session = accessor == null ? null : accessor.getSessionId();
        var principal = accessor == null ? null : accessor.getUser();
        Authentication auth = principal instanceof Authentication value ? value : null;
        String roles = auth == null ? "[]" : auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).filter(ROLES::contains).sorted().toList().toString();
        // Never render a Message, Principal, Throwable, or arbitrary header value:
        // exception messages (including nested MessagingExceptions) can contain JWTs.
        StringBuilder trace = new StringBuilder();
        appendTrace(trace, failure, "", Collections.newSetFromMap(new IdentityHashMap<>()));
        logger.warn("STOMP inbound failure stage={} command={} session={} destination={} principalPresent={} authenticated={} roles={} trace:\n{}",
                stage, accessor == null ? null : accessor.getCommand(),
                session != null && session.matches("[a-fA-F0-9-]{36}") ? session : "<redacted-or-absent>",
                destination == null ? "<absent>" :
                        Set.of("/topic/admin", "/user/queue/valor-events").contains(destination)
                                ? destination : "<other-redacted>",
                principal != null, auth != null && auth.isAuthenticated(), roles, trace.toString());
    }

    private static void appendTrace(StringBuilder result, Throwable failure, String relation, Set<Throwable> seen) {
        if (failure == null) return;
        if (!seen.add(failure)) {
            result.append(relation).append("<cycle>\n");
            return;
        }
        result.append(relation).append(failure.getClass().getName()).append(" [message omitted]\n");
        for (StackTraceElement frame : failure.getStackTrace()) result.append("\tat ").append(frame).append('\n');
        for (Throwable suppressed : failure.getSuppressed()) appendTrace(result, suppressed, "Suppressed: ", seen);
        appendTrace(result, failure.getCause(), "Caused by: ", seen);
    }
}
