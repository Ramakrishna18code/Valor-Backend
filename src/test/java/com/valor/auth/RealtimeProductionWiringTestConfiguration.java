package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.realtime.RealtimeChannelInterceptor;
import com.valor.realtime.RealtimeWebSocketConfig;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.sql.DataSource;
import org.apache.tomcat.websocket.server.WsSci;
import org.mockito.Mockito;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSecurity
@Import({AuthConfig.class, RealtimeWebSocketConfig.class})
public class RealtimeProductionWiringTestConfiguration {
    public static final java.util.concurrent.BlockingQueue<String> CONNECTED = new java.util.concurrent.LinkedBlockingQueue<>();
    public static final java.util.concurrent.BlockingQueue<String> SUBSCRIBED = new java.util.concurrent.LinkedBlockingQueue<>();
    public static final java.util.concurrent.BlockingQueue<String> LIFECYCLE = new java.util.concurrent.LinkedBlockingQueue<>();
    public static final java.util.concurrent.BlockingQueue<String> HANDSHAKE = new java.util.concurrent.LinkedBlockingQueue<>();
    public static final Map<String, UserDetails> USERS = Map.of(
            "1", User.withUsername("1").password("unused").roles("CUSTOMER").build(),
            "2", User.withUsername("2").password("unused").roles("CUSTOMER").build(),
            "3", User.withUsername("3").password("unused").roles("ADMIN").build(),
            "4", User.withUsername("4").password("unused").roles("SUPER_ADMIN").build());
    private static UserRepo handshakeUsers;

    public static void enableHttpHandshakeUser(String id) {
        if (handshakeUsers == null) throw new IllegalStateException("Test context is not initialized");
        long numericId = Long.parseLong(id);
        com.valor.auth.User user = Mockito.mock(com.valor.auth.User.class);
        Mockito.when(user.getId()).thenReturn(numericId);
        Mockito.when(user.isActive()).thenReturn(true);
        Mockito.when(user.isLocked()).thenReturn(false);
        Mockito.when(user.getLockedUntil()).thenReturn(null);
        Mockito.when(user.getRole()).thenReturn(Role.CUSTOMER);
        Mockito.when(handshakeUsers.findById(numericId)).thenReturn(Optional.of(user));
    }

    @Bean
    JwtService jwtService() {
        JwtService jwt = Mockito.mock(JwtService.class);
        USERS.keySet().forEach(id -> Mockito.when(jwt.subject("token-" + id)).thenReturn(Long.valueOf(id)));
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

    @Bean
    UserRepo userRepo() {
        handshakeUsers = Mockito.mock(UserRepo.class);
        return handshakeUsers;
    }

    @Bean
    PermissionService permissionService() {
        PermissionService permissions = Mockito.mock(PermissionService.class);
        Mockito.when(permissions.permissionsFor(Mockito.any())).thenReturn(Set.of());
        return permissions;
    }

    @Bean
    JwtFilter jwtFilter(JwtService jwt, UserRepo users, PermissionService permissions) {
        return new JwtFilter(jwt, users, permissions);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "mvcHandlerMappingIntrospector")
    HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
        return new HandlerMappingIntrospector();
    }

    @Bean
    org.springframework.context.ApplicationListener<SessionConnectedEvent> connectedObserver() {
        return event -> {
            String summary = connectionSummary(event);
            CONNECTED.offer(summary);
            LIFECYCLE.offer("CONNECTED," + summary);
        };
    }

    @Bean
    org.springframework.context.ApplicationListener<SessionConnectEvent> connectObserver() {
        return event -> LIFECYCLE.offer("CONNECT," + messageSummary(event.getMessage(), event.getUser()));
    }

    @Bean
    org.springframework.context.ApplicationListener<SessionSubscribeEvent> subscribedObserver() {
        return event -> {
            String summary = subscriptionSummary(event);
            SUBSCRIBED.offer(summary);
            LIFECYCLE.offer("SUBSCRIBE," + summary);
        };
    }

    @Bean
    org.springframework.context.ApplicationListener<SessionDisconnectEvent> disconnectObserver() {
        return event -> LIFECYCLE.offer("DISCONNECT,session=" + event.getSessionId() + ",principal="
                + (event.getUser() == null ? "<null>" : event.getUser().getName()));
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<Filter> handshakeObserver() {
        Filter filter = (request, response, chain) -> {
            chain.doFilter(request, response);
            if (request instanceof HttpServletRequest http && http.getRequestURI().equals("/ws")) {
                java.security.Principal principal = http.getUserPrincipal();
                HANDSHAKE.offer("principal=" + (principal == null ? "<null>" : principal.getName()));
            }
        };
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/ws");
        registration.setOrder(Integer.MAX_VALUE);
        return registration;
    }

    private static String messageSummary(org.springframework.messaging.Message<?> message, java.security.Principal principal) {
        String session = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        return "session=" + session + ",principal=" + (principal == null ? "<null>" : principal.getName())
                + ",destination=" + destination;
    }

    private static String connectionSummary(SessionConnectedEvent event) {
        String session = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        java.security.Principal principal = event.getUser();
        return "session=" + session + ",principal=" + (principal == null ? "<null>" : principal.getName());
    }

    private static String subscriptionSummary(SessionSubscribeEvent event) {
        String session = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        String subscription = SimpMessageHeaderAccessor.getSubscriptionId(event.getMessage().getHeaders());
        java.security.Principal principal = event.getUser();
        String destination = SimpMessageHeaderAccessor.getDestination(event.getMessage().getHeaders());
        return "session=" + session + ",principal=" + (principal == null ? "<null>" : principal.getName())
                + ",destination=" + destination + ",subscription=" + subscription;
    }

    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    ServletWebServerFactory servletWebServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setPort(0);
        factory.addContextCustomizers(context -> context.addServletContainerInitializer(new WsSci(), Set.of()));
        return factory;
    }

    @Bean
    DispatcherServlet dispatcherServlet(WebApplicationContext context) {
        return new DispatcherServlet(context);
    }

    @Bean
    ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration(DispatcherServlet servlet) {
        ServletRegistrationBean<DispatcherServlet> registration = new ServletRegistrationBean<>(servlet, "/");
        registration.setName("dispatcher");
        return registration;
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        return new ServletServerContainerFactoryBean();
    }

    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor safetyGuard() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanDefinitionNames()) {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("messagebrokertaskscheduler")) continue;
                if (lower.equals("initializeauthenticationproviderbeanmanagerconfigurer")
                        || lower.equals("initializeuserdetailsbeanmanagerconfigurer")) continue;
                if (lower.contains("datasource") || lower.contains("flyway") || lower.contains("entitymanager")
                        || lower.contains("scheduler") || lower.contains("razorpay") || lower.contains("smtp")
                        || lower.contains("msg91") || lower.contains("provider")) {
                    throw new IllegalStateException("Unsafe bean registered in isolated realtime context: " + name);
                }
            }
        };
    }
}
