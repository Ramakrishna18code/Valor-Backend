package com.valor.realtime;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.boot.test.context.TestConfiguration;
import org.apache.tomcat.websocket.server.WsSci;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;

@TestConfiguration
@Import(RealtimeBrokerTestConfiguration.class)
class RealtimeWebSocketAuthenticatedConfiguration {
    static final BlockingQueue<SessionConnectedEvent> CONNECTED = new LinkedBlockingQueue<>();
    static final BlockingQueue<SessionSubscribeEvent> SUBSCRIBED = new LinkedBlockingQueue<>();
    static final BlockingQueue<Message<?>> INBOUND = new LinkedBlockingQueue<>();
    static final BlockingQueue<Throwable> INBOUND_ERRORS = new LinkedBlockingQueue<>();

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
    org.springframework.context.ApplicationListener<SessionConnectedEvent> connectedObserver() {
        return CONNECTED::offer;
    }

    @Bean
    org.springframework.context.ApplicationListener<SessionSubscribeEvent> subscribedObserver() {
        return SUBSCRIBED::offer;
    }

    @Bean
    RealtimeBrokerEndpointConfigurer endpointConfigurer() {
        return new RealtimeBrokerEndpointConfigurer();
    }

    @Bean
    ChannelInterceptor inboundDiagnosticObserver() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                INBOUND.offer(message);
                return message;
            }

            @Override public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                if (ex != null) INBOUND_ERRORS.offer(ex);
            }
        };
    }

    static final class RealtimeBrokerEndpointConfigurer implements org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer {
        @Override
        public void registerStompEndpoints(org.springframework.web.socket.config.annotation.StompEndpointRegistry registry) {
            registry.addEndpoint("/test-ws").setHandshakeHandler(new DefaultHandshakeHandler() {
                @Override
                protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attributes) {
                    String userId = request.getURI().getQuery();
                    if (userId == null || !userId.startsWith("user=")) userId = "user=1";
                    userId = userId.substring("user=".length());
                    var details = RealtimeBrokerTestConfiguration.USERS.get(userId);
                    if (details == null) throw new IllegalArgumentException("Unknown synthetic user");
                    return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                }
            });
        }

        @Override
        public void configureClientInboundChannel(ChannelRegistration registration) {
            registration.interceptors(new ChannelInterceptor() {
                @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                    INBOUND.offer(message);
                    return message;
                }

                @Override public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                    if (ex != null) INBOUND_ERRORS.offer(ex);
                }
            });
        }
    }
}
