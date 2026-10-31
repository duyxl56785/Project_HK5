package com.projecthk5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;

/** Auto-configured because some services do not scan this package; the Boot customizer attaches the listener. */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty("AUTH_AUTHORITY_TOKEN")
public class WriteFenceConfiguration {
    private static final Pattern CLOSE = Pattern.compile("/internal/write-fences/([0-9a-fA-F-]{36})/close");

    @Bean
    WriteFence writeFence(JdbcTemplate jdbc) {
        return new WriteFence(jdbc);
    }

    @Bean
    WriteFenceErrors writeFenceErrors() {
        return new WriteFenceErrors();
    }

    /** Runs after a service's own advice, so only a service that has no ApiException handler lands here. */
    @RestControllerAdvice
    static class WriteFenceErrors {
        @ExceptionHandler(ApiException.class)
        public ResponseEntity<Map<String, Object>> refused(ApiException e) {
            return new ApiErrorAdvice().api(e);
        }
    }

    /** A servlet filter, ahead of Spring Security, because each service has its own route rules. */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> writeFenceClose(WriteFence fence, @Value("${AUTH_AUTHORITY_TOKEN}") String token) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                Matcher path = CLOSE.matcher(request.getRequestURI());
                if (!"POST".equals(request.getMethod()) || !path.matches()) {
                    chain.doFilter(request, response);
                } else if (!InternalTokens.matchesAny(request.getHeader(InternalTokens.HEADER), token)) {
                    JwtResourceServer.write(response, 401, "INTERNAL_AUTH_INVALID", "Internal credential invalid");
                } else {
                    UUID operation;
                    try {
                        operation = UUID.fromString(path.group(1));
                    } catch (IllegalArgumentException malformed) {
                        JwtResourceServer.write(response, 400, "WRITE_FENCE_INVALID", "Operation id is invalid");
                        return;
                    }
                    fence.close(operation);
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            }
        });
        registration.addUrlPatterns("/internal/write-fences/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
