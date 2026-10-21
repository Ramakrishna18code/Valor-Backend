package com.valor.realtime;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeChannelInterceptor interceptor;
    @Value("${app.cors.allowed-origins:http://localhost:8081,http://localhost:19006,http://localhost:8090}") private String allowedOrigins;
    public RealtimeWebSocketConfig(RealtimeChannelInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) { registry.enableSimpleBroker("/topic", "/queue"); registry.setApplicationDestinationPrefixes("/app"); registry.setUserDestinationPrefix("/user"); }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) { List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toList()); registry.addEndpoint("/ws").setAllowedOrigins(origins.toArray(String[]::new)); }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(interceptor); }
}
