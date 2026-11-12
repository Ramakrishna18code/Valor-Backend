package com.valor.realtime;

import com.valor.auth.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RealtimeChannelInterceptor implements ExecutorChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RealtimeChannelInterceptor.class);
    private final JwtService jwt; private final UserDetailsService users;
    public RealtimeChannelInterceptor(JwtService jwt, UserDetailsService users) { this.jwt = jwt; this.users = users; }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        try {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String header = accessor.getFirstNativeHeader("Authorization");
                if (!StringUtils.startsWithIgnoreCase(header, "Bearer ")) throw new IllegalArgumentException("Authentication required");
                String token = header.substring(7).trim(); String subject = String.valueOf(jwt.subject(token));
                if (!StringUtils.hasText(subject)) throw new IllegalArgumentException("Authentication failed");
                UserDetails details = users.loadUserByUsername(subject); if (!subject.equals(details.getUsername())) throw new IllegalArgumentException("Authentication failed");
                accessor.setUser(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
                message = MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                Authentication auth = (Authentication) accessor.getUser(); String destination = accessor.getDestination();
                if (!StringUtils.hasText(destination)) throw new IllegalArgumentException("Subscription destination required");
                if (auth == null || !auth.isAuthenticated()) throw new IllegalArgumentException("Authentication required");
                boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> {
                    String authority = a.getAuthority();
                    return "ROLE_ADMIN".equals(authority) || "ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority) || "SUPER_ADMIN".equals(authority);
                });
                boolean adminTopic = "/topic/admin".equals(destination);
                boolean userEventsTopic = "/user/queue/valor-events".equals(destination)
                        || destination.matches("^/user/[^/]+/queue/valor-events$");
                if (adminTopic && !admin) throw new IllegalArgumentException("Subscription not allowed");
                if (!adminTopic && !userEventsTopic) throw new IllegalArgumentException("Subscription not allowed");
            }
            if (StompCommand.SEND.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (!StringUtils.hasText(destination)) throw new IllegalArgumentException("Message destination required");
                throw new IllegalArgumentException("Client messages are not supported");
            }
            return message;
        } catch (RuntimeException failure) {
            RealtimeStompErrorHandler.logFailure(log, "preSend", message, failure);
            throw failure;
        }
    }

    @Override public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex == null || message == null) return;
        RealtimeStompErrorHandler.logFailure(log, "dispatch", message, ex);
    }

    @Override public void afterMessageHandled(Message<?> message, MessageChannel channel,
            MessageHandler handler, Exception ex) {
        if (ex != null) RealtimeStompErrorHandler.logFailure(log, "executor", message, ex);
    }
}
