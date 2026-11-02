package com.projecthk5.cartservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;

@Configuration
public class ResourceSecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        com.projecthk5.security.JwtResourceServer.requireCurrentAuthority(http);
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(token ->
                new JwtAuthenticationToken(token,
                    ((List<String>) token.getClaim("roles")).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()))))
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
