package com.projecthk5.gateway;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

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
            ServerAuthenticationEntryPoint entryPoint = (exchange, failure) -> {
                String code = "AUTH_ACCESS_REQUIRED";
                if (failure instanceof OAuth2AuthenticationException oauth) {
                    code = "invalid_request".equals(oauth.getError().getErrorCode())
                            ? "AUTH_REQUEST_INVALID" : "AUTH_ACCESS_INVALID";
                }
                return denied(exchange, code);
            };
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                    .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                    .authorizeExchange(a -> a.pathMatchers("/actuator/health", "/actuator/info").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                            .anyExchange().denyAll())
                    .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler((exchange, failure) -> denied(exchange, "AUTH_FORBIDDEN")))
                    .oauth2ResourceServer(o -> o.authenticationEntryPoint(entryPoint)
                            .jwt(jwt -> jwt.jwtDecoder(decoder))).build();
        }

        private static Mono<Void> denied(ServerWebExchange exchange, String code) {
            String path = exchange.getRequest().getPath().value();
            int status;
            if (!Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/me", "/actuator/health", "/actuator/info").contains(path)) {
                status = 404; code = "GATEWAY_ROUTE_NOT_FOUND";
            } else {
                HttpMethod allowed = path.equals("/api/auth/me") || path.startsWith("/actuator/")
                        ? HttpMethod.GET : HttpMethod.POST;
                if (!allowed.equals(exchange.getRequest().getMethod())) {
                    status = 405; code = "METHOD_NOT_ALLOWED";
                    exchange.getResponse().getHeaders().set(HttpHeaders.ALLOW, allowed.name());
                } else {
                    status = "AUTH_FORBIDDEN".equals(code) ? 403 : "AUTH_REQUEST_INVALID".equals(code) ? 400 : 401;
                }
            }
            return error(exchange, status, code);
        }

        private static Mono<Void> error(ServerWebExchange exchange, int status, String code) {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.valueOf(status));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().setCacheControl("no-store");
            response.getHeaders().setPragma("no-cache");
            if (status == 401 || status == 400) {
                String challenge = "Bearer realm=\"smart-ecommerce\"";
                if ("AUTH_ACCESS_INVALID".equals(code)) challenge += ", error=\"invalid_token\"";
                if (status == 400) challenge += ", error=\"invalid_request\"";
                response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
            }
            byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"Request rejected\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
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
                            .uri(upstream))
                    .route("auth-me-exact", p -> p.path("/api/auth/me").and().method(HttpMethod.GET)
                            .uri(upstream))
                    .build();
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        WebFilter authCors(ConfigurableEnvironment env) {
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
            for (String path : List.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/me")) {
                var routeConfig = new CorsConfiguration(config);
                routeConfig.setAllowedMethods(List.of(path.equals("/api/auth/me") ? "GET" : "POST"));
                source.registerCorsConfiguration(path, routeConfig);
            }
            var processor = new DefaultCorsProcessor();
            return (exchange, chain) -> {
                if (!processor.process(source.getCorsConfiguration(exchange), exchange)) {
                    return error(exchange, 403, "AUTH_CSRF_REJECTED");
                }
                if (CorsUtils.isPreFlightRequest(exchange.getRequest())) return exchange.getResponse().setComplete();
                return chain.filter(exchange);
            };
        }

        /**
         * NettyRoutingFilter calls HttpHeadersFilter on OUTBOUND headers before
         * proxying. Never mutate inbound ReadOnlyHttpHeaders or decorate the
         * request: both approaches were incompatible with this Spring 6.1 runtime.
         */
        @Bean
        org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter outgoingAuthHeaderSanitizer() {
            return (headers, exchange) -> {
                var sanitized = new org.springframework.http.HttpHeaders();
                headers.forEach((name, values) ->
                        sanitized.put(name, new ArrayList<>(values)));
                for (String name : UNTRUSTED_HEADERS) sanitized.remove(name);
                return sanitized;
            };
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
