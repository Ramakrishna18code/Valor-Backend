package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.response.ApiResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class AuthConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).distinct().toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("CORS requires explicit origins");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        // The API uses bearer headers, not cross-origin cookies.
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain filter(HttpSecurity http, JwtFilter jwt, ObjectMapper json,
            UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            json.writeValue(response.getOutputStream(), ApiResponse.error("Authentication required", 401));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            json.writeValue(response.getOutputStream(), ApiResponse.error("Access denied", 403));
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/api/v1/health", "/error").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login/**",
                                "/api/v1/auth/otp/**", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/customers/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/admin/dashboard/summary").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/admin/technicians").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/customers", "/api/v1/admin/customers/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/service-visits/**", "/api/v1/admin/visit-change-requests/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/notifications")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/technician/**").hasRole("TECHNICIAN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/service-requests")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/service-requests")
                                .hasAnyRole("CUSTOMER", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/service-requests/*/assignments/*/accept").hasRole("TECHNICIAN")
                        .requestMatchers("/api/v1/service-requests/*/assignments").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/service-requests/*/status").hasAnyRole("ADMIN", "SUPER_ADMIN", "TECHNICIAN", "CUSTOMER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/amc-contracts")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "CUSTOMER")
                        .requestMatchers("/api/v1/buildings", "/api/v1/buildings/**",
                                "/api/v1/lifts", "/api/v1/lifts/**", "/api/v1/amc-contracts", "/api/v1/amc-contracts/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
