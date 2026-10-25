package com.valor.realtime;

import com.valor.auth.JwtService;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@EnableWebSocketMessageBroker
class RealtimeBrokerTestConfiguration implements WebSocketMessageBrokerConfigurer {
    static final Map<String, UserDetails> USERS = Map.of(
            "1", User.withUsername("1").password("unused").roles("CUSTOMER").build(),
            "2", User.withUsername("2").password("unused").roles("TECHNICIAN").build(),
            "3", User.withUsername("3").password("unused").roles("ADMIN").build(),
            "4", User.withUsername("4").password("unused").roles("SUPER_ADMIN").build());
    @Autowired RealtimeChannelInterceptor interceptor;

    @Bean
    JwtService jwtService() {
        JwtService jwt = mock(JwtService.class);
        USERS.keySet().forEach(id -> when(jwt.subject("token-" + id)).thenReturn(Long.valueOf(id)));
        return jwt;
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            UserDetails details = USERS.get(username);
            if (details == null) throw new UsernameNotFoundException(username);
            return details;
        };
    }

    @Bean
    RealtimeChannelInterceptor realtimeChannelInterceptor(JwtService jwt, UserDetailsService users) {
        return new RealtimeChannelInterceptor(jwt, users);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/test-ws");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
