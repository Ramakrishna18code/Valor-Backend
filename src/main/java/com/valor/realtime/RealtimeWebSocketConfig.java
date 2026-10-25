package com.valor.realtime;

import com.valor.auth.JwtService;
import java.util.Arrays;
import java.util.List;
import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String AUTHENTICATED_PRINCIPAL = RealtimeWebSocketConfig.class.getName() + ".principal";
    private final RealtimeChannelInterceptor interceptor;
    private final JwtService jwt;
    private final UserDetailsService users;
    @Value("${app.cors.allowed-origins:http://localhost:8081,http://localhost:19006,http://localhost:8090}") private String allowedOrigins;
    public RealtimeWebSocketConfig(RealtimeChannelInterceptor interceptor, JwtService jwt, UserDetailsService users) {
        this.interceptor = interceptor; this.jwt = jwt; this.users = users;
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) { registry.enableSimpleBroker("/topic", "/queue"); registry.setApplicationDestinationPrefixes("/app"); registry.setUserDestinationPrefix("/user"); }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toList());
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins.toArray(String[]::new))
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        String header = request.getHeaders().getFirst("Authorization");
                        if (!StringUtils.hasText(header)) return true;
                        if (!StringUtils.startsWithIgnoreCase(header, "Bearer ")) return false;
                        try {
                            String subject = String.valueOf(jwt.subject(header.substring(7).trim()));
                            if (!StringUtils.hasText(subject)) return false;
                            UserDetails details = users.loadUserByUsername(subject);
                            if (!subject.equals(details.getUsername())) return false;
                            attributes.put(AUTHENTICATED_PRINCIPAL,
                                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
                            return true;
                        } catch (RuntimeException rejected) {
                            return false;
                        }
                    }
                    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) { }
                })
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                            Map<String, Object> attributes) {
                        return (Principal) attributes.get(AUTHENTICATED_PRINCIPAL);
                    }
                });
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(interceptor); }
}
