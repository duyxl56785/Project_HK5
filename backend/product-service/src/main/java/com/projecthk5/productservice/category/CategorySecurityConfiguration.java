package com.projecthk5.productservice.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import com.projecthk5.security.JwtResourceServer;
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
        // Contract v1.0: public reads; writes need a verified JWT with the ADMIN role (401/403 JSON).
        return JwtResourceServer.configure(http.securityMatcher("/api/categories", "/api/categories/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/categories", "/api/categories/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .build();
    }
}
