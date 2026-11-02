package com.projecthk5.authservice.api;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.security.JwtResourceServer;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(AuthSecurityConfig.class);

    @Bean

    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
    SecurityFilterChain authSecurity(HttpSecurity http) throws Exception {
        return JwtResourceServer.configure(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/internal/accounts/*/roles/SELLER").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/accounts/*/write-reservations").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/internal/accounts/write-reservations/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers("/api/admin/accounts/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .build();
    }

    /** ADMIN is never self-registered: provision it once from controlled runtime configuration. */
    @Bean
    ApplicationRunner adminBootstrap(AuthService auth, AccountRepository accounts,
                                     @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String email,
                                     @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String password) {
        return args -> {
            if (email.isBlank() || password.isBlank() || accounts.findByEmailNormalized(email).isPresent()) return;
            auth.create(email, password, Set.of(Account.Role.ADMIN, Account.Role.CUSTOMER));
            log.info("Provisioned bootstrap ADMIN account");
        };
    }
}
