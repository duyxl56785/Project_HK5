package com.projecthk5.productservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ProductSecurityConfiguration {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain unconfiguredProductRoutes(HttpSecurity http, ObjectMapper mapper) throws Exception {
        // Feature-specific chains run first; new routes require an explicit security policy.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeDenied(response, mapper))
                        .accessDeniedHandler((request, response, exception) -> writeDenied(response, mapper)))
                .build();
    }

    private void writeDenied(HttpServletResponse response, ObjectMapper mapper) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of(
                "code", "PRODUCT_ROUTE_AUTH_NOT_CONFIGURED",
                "message", "Route access is disabled pending an explicit security policy"));
    }
}
