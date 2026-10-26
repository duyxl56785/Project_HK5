package com.projecthk5.authservice.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Always closed by default; no API is activated by merely adding dependencies. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthServletSecurityConfiguration {
    @Bean
    SecurityFilterChain authFilterChain(HttpSecurity http, Environment env) throws Exception {
        boolean enabled = "true".equals(env.getProperty("hk5.auth.http.enabled", "false"));
        http.httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Contract's Origin + custom-header gate guards every cookie mutation.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll();
                    if (enabled) {
                        a.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll();
                        a.requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll();
                    }
                    a.anyRequest().denyAll();
                });
        return http.build();
    }
}
