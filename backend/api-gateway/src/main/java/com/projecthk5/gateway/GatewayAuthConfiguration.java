package com.projecthk5.gateway;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Gateway is deny-all in default configuration. Opt-in routes are restricted to
 * the five Auth method/path pairs. Unknown, internal and business paths do not route.
 * Unreviewed servlet principal/downstream adapters still block production enablement.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAuthConfiguration {
    @Bean
    @ConditionalOnProperty(name = "hk5.gateway.auth.enabled", havingValue = "false", matchIfMissing = true)
    SecurityWebFilterChain closedGateway(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(a -> a.pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyExchange().denyAll()).build();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "hk5.gateway.auth.enabled", havingValue = "true")
    static class OptIn {
        private static final String[] UNTRUSTED_HEADERS = {
                "X-User-Id", "X-Customer-Id", "X-Seller-Id", "X-Store-Id",
                "X-Roles", "X-Authorities", "X-Authenticated-User", "X-Internal-Token",
                "X-Service-Name"
        };

        @Bean
        GatewayJwtDecoder gatewayJwtDecoder(ConfigurableEnvironment environment) {
            return new GatewayJwtDecoder(environment.getSystemEnvironment());
        }

        /** Public Auth mutations ignore stale Bearer and validate Origin/CSRF downstream. */
        @Bean
        @Order(0)
        SecurityWebFilterChain publicAuth(ServerHttpSecurity http) {
            return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .authorizeExchange(a -> a.anyExchange().permitAll()).build();
        }

        @Bean
        @Order(1)
        SecurityWebFilterChain protectedAuth(ServerHttpSecurity http, GatewayJwtDecoder decoder) {
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .authorizeExchange(a -> a.pathMatchers("/actuator/health", "/actuator/info").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                            .anyExchange().denyAll())
                    .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtDecoder(decoder))).build();
        }

        @Bean
        RouteLocator reviewedAuthRoutes(RouteLocatorBuilder builder, ConfigurableEnvironment env) {
            String upstream = required(env, "HK5_AUTH_UPSTREAM_URI");
            URI uri;
            try { uri = URI.create(upstream); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid Auth upstream"); }
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getRawPath().isEmpty() || !upstream.equals(uri.toString())) {
                throw new IllegalArgumentException("Invalid Auth upstream");
            }
            return builder.routes()
                    .route("auth-public-exact", p -> p.path("/api/auth/register", "/api/auth/login",
                                    "/api/auth/refresh", "/api/auth/logout")
                            .and().method(HttpMethod.POST)
                            .filters(OptIn::removeUntrustedHeaders).uri(upstream))
                    .route("auth-me-exact", p -> p.path("/api/auth/me").and().method(HttpMethod.GET)
                            .filters(OptIn::removeUntrustedHeaders).uri(upstream))
                    .build();
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        CorsWebFilter authCors(ConfigurableEnvironment env) {
            String values = required(env, "HK5_AUTH_ALLOWED_ORIGINS");
            var origins = new HashSet<String>();
            for (String value : values.split(",", -1)) {
                if (value.isBlank() || !value.equals(value.strip()) || value.contains("*"))
                    throw new IllegalArgumentException("Invalid Gateway Origin config");
                URI uri;
                try { uri = URI.create(value); }
                catch (IllegalArgumentException malformed) {
                    throw new IllegalArgumentException("Invalid Gateway Origin config");
                }
                if (uri.getHost() == null || uri.getUserInfo() != null
                        || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                        || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
                        || uri.getRawFragment() != null || !origins.add(value)) {
                    throw new IllegalArgumentException("Invalid Gateway Origin config");
                }
            }
            var config = new CorsConfiguration();
            config.setAllowedOrigins(List.copyOf(origins));
            config.setAllowCredentials(true);
            config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Protection"));
            config.setExposedHeaders(List.of("WWW-Authenticate", "Retry-After", "Location"));
            var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/auth/**", config);
            return new CorsWebFilter(source);
        }

        static org.springframework.cloud.gateway.filter.GatewayFilter sanitizeHeaders() {
            return (exchange, chain) -> {
                // Spring Web 6.1 / Gateway 4.1 may supply ReadOnlyHttpHeaders even
                // inside request.mutate().headers(...). Never mutate that backing map.
                // Copy each value list into a fresh, case-insensitive HttpHeaders map.
                var safe = new org.springframework.http.HttpHeaders();
                exchange.getRequest().getHeaders().forEach((name, values) ->
                        safe.put(name, new ArrayList<>(values)));
                for (String header : UNTRUSTED_HEADERS) safe.remove(header);

                // Preserve request method, URI, body and native Netty connection while
                // supplying only the sanitized header snapshot to downstream filters.
                var request = new org.springframework.http.server.reactive.ServerHttpRequestDecorator(
                        exchange.getRequest()) {
                    @Override
                    public org.springframework.http.HttpHeaders getHeaders() {
                        return safe;
                    }
                };
                return chain.filter(exchange.mutate().request(request).build());
            };
        }

        private static GatewayFilterSpec removeUntrustedHeaders(GatewayFilterSpec filters) {
            return filters.filter(sanitizeHeaders());
        }

        private static String required(ConfigurableEnvironment env, String name) {
            Object raw = env.getSystemEnvironment().get(name);
            if (!(raw instanceof String value) || value.isBlank() || !value.equals(value.strip())) {
                throw new IllegalArgumentException("Explicit Gateway runtime configuration required");
            }
            return value;
        }
    }
}
