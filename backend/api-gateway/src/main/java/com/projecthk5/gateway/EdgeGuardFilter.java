package com.projecthk5.gateway;

import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Service-to-service routes (/internal/**) and the Inventory stock API are never reachable from
 * the edge, even if a route predicate is later widened by mistake.
 */
@Component
public class EdgeGuardFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (blocked(path)) return notFound(exchange.getResponse());
        return chain.filter(exchange);
    }

    static boolean blocked(String path) {
        String p = path.toLowerCase();
        return p.startsWith("/internal") || p.startsWith("/api/inventory") || p.contains("/../") || p.contains("%2e");
    }

    static Mono<Void> notFound(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory()
                .wrap("{\"code\":\"ROUTE_NOT_FOUND\",\"message\":\"Not found\"}".getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
