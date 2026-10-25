package com.valor.realtime;

import com.valor.auth.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RealtimeChannelInterceptor implements ChannelInterceptor {
    private final JwtService jwt; private final UserDetailsService users;
    public RealtimeChannelInterceptor(JwtService jwt, UserDetailsService users) { this.jwt = jwt; this.users = users; }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
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
            boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            if (destination.equals("/topic/admin") && !admin) throw new IllegalArgumentException("Subscription not allowed");
            if (!destination.equals("/topic/admin") && !destination.equals("/user/queue/valor-events")) throw new IllegalArgumentException("Subscription not allowed");
        }
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (!StringUtils.hasText(destination)) throw new IllegalArgumentException("Message destination required");
            throw new IllegalArgumentException("Client messages are not supported");
        }
        return message;
    }
}
