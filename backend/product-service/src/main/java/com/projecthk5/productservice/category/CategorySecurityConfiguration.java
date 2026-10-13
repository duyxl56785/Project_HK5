package com.projecthk5.productservice.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class CategorySecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain categorySecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.securityMatcher("/api/categories", "/api/categories/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/categories", "/api/categories/**").permitAll()
                        // Fail closed until TV1 confirms JWT verification and Category write permissions.
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeBlocked(response, mapper))
                        .accessDeniedHandler((request, response, exception) ->
                                writeBlocked(response, mapper)))
                .build();
    }

    private void writeBlocked(HttpServletResponse response, ObjectMapper mapper) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new CategoryError(
                "CATEGORY_WRITE_AUTH_NOT_CONFIGURED", "Category writes are disabled pending Auth integration"));
    }
}
