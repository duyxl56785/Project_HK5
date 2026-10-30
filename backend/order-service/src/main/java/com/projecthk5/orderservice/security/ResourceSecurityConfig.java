package com.projecthk5.orderservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ResourceSecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        // Shared contract v1.0 policy: verified HS256 JWT, roles from the token only, JSON 401/403.
        return com.projecthk5.security.JwtResourceServer.configure(http)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/error").permitAll()
                // Pair routes authenticate with X-Internal-Token inside the controller; the gateway never forwards them.
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/api/orders", "/api/orders/**").hasRole("CUSTOMER")
                .requestMatchers("/api/seller/**").hasRole("SELLER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.issuer:}") String issuer,
                          @Value("${app.security.audience:}") String audience,
                          @Value("${app.security.current-kid:}") String currentKid,
                          @Value("${app.security.current-key-b64:}") String currentKey,
                          @Value("${app.security.previous-kid:}") String previousKid,
                          @Value("${app.security.previous-key-b64:}") String previousKey) {
        // No embedded signing key, no JWKS fallback. Missing config fails startup/readiness.
        return new ContractJwtDecoder(issuer, audience, currentKid, currentKey, previousKid, previousKey);
    }
}
