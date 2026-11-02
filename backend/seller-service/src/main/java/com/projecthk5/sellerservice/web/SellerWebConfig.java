package com.projecthk5.sellerservice.web;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.JwtResourceServer;
import com.projecthk5.sellerservice.http.SellerStoreJson;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.SellerAccessPolicy;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceException;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.store.StoreAccessPolicy;
import com.projecthk5.sellerservice.store.StorePersistenceException;
import com.projecthk5.sellerservice.store.StorePersistenceService;
import com.projecthk5.sellerservice.store.StoreRepository;
import com.projecthk5.sellerservice.store.StoreRuleException;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Binds the prepared Seller/Store domain to verified JWT principals and HTTP. */
@Configuration
@RestControllerAdvice
public class SellerWebConfig {
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
    SecurityFilterChain sellerSecurity(HttpSecurity http) throws Exception {
        return JwtResourceServer.configure(http).authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/stores", "/api/stores/by-slug/*").permitAll()
                // Internal pair routes authenticate with X-Internal-Token inside the controller.
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/sellers", "/api/sellers/**", "/api/stores/**").authenticated()
                .anyRequest().denyAll()).build();
    }

    /** Actor must be the verified JWT subject; moderation additionally needs the ADMIN role. */
    @Bean
    SellerAccessPolicy sellerAccessPolicy() {
        return new SellerAccessPolicy() {
            @Override public void requireAuthenticated(SellerIdentity actor) {
                if (actor == null || !Actor.current().id().equals(actor.reference()))
                    throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Actor is not the verified principal");
            }
            @Override public void requireSellerModerationPermission(SellerIdentity actor) {
                requireAuthenticated(actor);
                if (!Actor.current().has("ADMIN"))
                    throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "ADMIN role required");
            }
        };
    }

    @Bean
    SellerPersistenceService sellerPersistenceService(SellerProfileRepository repository, SellerAccessPolicy policy) {
        return new SellerPersistenceService(repository, policy);
    }

    /** Store management needs the SELLER role in the token AND a currently APPROVED persisted Seller. */
    @Bean
    StoreAccessPolicy storeAccessPolicy(SellerPersistenceService sellers) {
        return actor -> {
            Actor current = Actor.current();
            if (actor == null || !current.id().equals(actor.reference()) || !current.has("SELLER"))
                throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "SELLER role required");
            return sellers.findOwn(actor);
        };
    }

    @Bean
    StorePersistenceService storePersistenceService(StoreRepository stores, EntityManager em, StoreAccessPolicy policy) {
        return new StorePersistenceService(stores, em, policy);
    }

    @Bean
    SellerStoreJson sellerStoreJson() { return new SellerStoreJson(); }

    @ExceptionHandler(SellerStoreJson.InvalidInput.class)
    ResponseEntity<Map<String, String>> invalidJson(SellerStoreJson.InvalidInput e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request body");
    }

    @ExceptionHandler(SellerRuleException.class)
    ResponseEntity<Map<String, String>> sellerRule(SellerRuleException e) {
        return switch (e.getCode()) {
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage());
            case INVALID_STATE -> error(HttpStatus.CONFLICT, "SELLER_INVALID_STATE", e.getMessage());
            case NOT_OWNER, ACCESS_DENIED -> error(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", e.getMessage());
            case SELF_MODERATION -> error(HttpStatus.FORBIDDEN, "SELLER_SELF_MODERATION", e.getMessage());
        };
    }

    @ExceptionHandler(SellerPersistenceException.class)
    ResponseEntity<Map<String, String>> sellerPersistence(SellerPersistenceException e) {
        return switch (e.getCode()) {
            case SELLER_NOT_FOUND -> error(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", e.getMessage());
            case SELLER_ALREADY_EXISTS -> error(HttpStatus.CONFLICT, "SELLER_ALREADY_EXISTS", e.getMessage());
            case SELLER_VERSION_CONFLICT -> error(HttpStatus.CONFLICT, "SELLER_VERSION_CONFLICT", e.getMessage());
        };
    }

    @ExceptionHandler(StoreRuleException.class)
    ResponseEntity<Map<String, String>> storeRule(StoreRuleException e) {
        return switch (e.getCode()) {
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage());
            case ACCESS_DENIED -> error(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", e.getMessage());
            case SELLER_NOT_APPROVED -> error(HttpStatus.FORBIDDEN, "SELLER_NOT_APPROVED", e.getMessage());
            case AUTHORIZATION_UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE", e.getMessage());
        };
    }

    @ExceptionHandler(StorePersistenceException.class)
    ResponseEntity<Map<String, String>> storePersistence(StorePersistenceException e) {
        return switch (e.getCode()) {
            case STORE_NOT_FOUND -> error(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", e.getMessage());
            case STORE_ALREADY_EXISTS -> error(HttpStatus.CONFLICT, "STORE_ALREADY_EXISTS", e.getMessage());
            case SLUG_ALREADY_EXISTS -> error(HttpStatus.CONFLICT, "STORE_SLUG_EXISTS", e.getMessage());
            case STORE_VERSION_CONFLICT -> error(HttpStatus.CONFLICT, "STORE_VERSION_CONFLICT", e.getMessage());
        };
    }

    static ApiException invalid() { return ApiException.badRequest("VALIDATION_FAILED", "Invalid request"); }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message == null ? code : message));
    }
}
