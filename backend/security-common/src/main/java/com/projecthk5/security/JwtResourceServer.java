package com.projecthk5.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.client.RestClient;

/**
 * TV1 contract v1.0 resource-server wiring shared by servlet services: HS256 keyring verifier,
 * roles claim to ROLE_* authorities, stateless sessions and JSON 401/403 bodies.
 */
@Configuration
public class JwtResourceServer {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    JwtDecoder contractJwtDecoder(@Value("${JWT_ISSUER_URI:}") String issuer,
                                  @Value("${JWT_AUDIENCE:}") String audience,
                                  @Value("${JWT_HS256_CURRENT_KID:}") String currentKid,
                                  @Value("${JWT_HS256_CURRENT_KEY_B64:}") String currentKey,
                                  @Value("${JWT_HS256_PREVIOUS_KID:}") String previousKid,
                                  @Value("${JWT_HS256_PREVIOUS_KEY_B64:}") String previousKey) {
        return new ContractJwtDecoder(issuer, audience, currentKid, currentKey, previousKid, previousKey);
    }

    /** Bounded-timeout client for internal pair calls. */
    public static RestClient internalClient(String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).requestInterceptor(InternalDiscovery::route).baseUrl(baseUrl).build();
    }

    /** Applies the shared JWT/session/error policy; callers then add their own route rules. */
    @SuppressWarnings("unchecked")
    public static HttpSecurity configure(HttpSecurity http) throws Exception {
        requireCurrentAuthority(http);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> new JwtAuthenticationToken(token,
                                ((List<String>) token.getClaim("roles")).stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList())))
                        .authenticationEntryPoint((request, response, ex) -> {
                            boolean invalid = ex instanceof InvalidBearerTokenException;
                            response.setHeader("WWW-Authenticate", invalid
                                    ? "Bearer realm=\"smart-ecommerce\", error=\"invalid_token\""
                                    : "Bearer realm=\"smart-ecommerce\"");
                            write(response, 401, invalid ? "AUTH_ACCESS_INVALID" : "AUTH_ACCESS_REQUIRED",
                                    invalid ? "Access token is invalid or expired" : "Access token required");
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setHeader("WWW-Authenticate", "Bearer realm=\"smart-ecommerce\"");
                            write(response, 401, "AUTH_ACCESS_REQUIRED", "Access token required");
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                write(response, 403, "AUTH_FORBIDDEN", "Access denied")));
    }

    /** On unless a service opts out; without a URL or token every write fails closed. */
    public static void requireCurrentAuthority(HttpSecurity http) {
        Environment env = http.getSharedObject(ApplicationContext.class).getEnvironment();
        if (env.getProperty("security.current-authority.enabled", Boolean.class, true)) {
            http.addFilterAfter(new CurrentAuthorityFilter(env.getProperty("AUTH_INTERNAL_URL", "http://localhost:8101"),
                    env.getProperty("AUTH_AUTHORITY_TOKEN", "")), BearerTokenAuthenticationFilter.class);
        }
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        JSON.writeValue(response.getOutputStream(), Map.of("code", code, "message", message));
    }
}
