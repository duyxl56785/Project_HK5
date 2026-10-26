package com.projecthk5.authservice.security;

import com.projecthk5.authservice.persistence.AccountRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Opt-in Auth service keyring and current-account authority bootstrap.
 * There is intentionally NO HTTP endpoint, security filter or default signing key.
 * Only enable after the canonical active-status policy and secret provisioning are reviewed.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hk5.auth.bootstrap.enabled", havingValue = "true")
public class AuthBootstrapConfiguration {
    static final String ISSUER = "HK5_AUTH_JWT_ISSUER";
    static final String AUDIENCE = "HK5_AUTH_JWT_AUDIENCE";
    static final String CURRENT_KID = "HK5_AUTH_JWT_CURRENT_KID";
    static final String CURRENT_KEY = "HK5_AUTH_JWT_CURRENT_KEY_B64";
    static final String PREVIOUS_KID = "HK5_AUTH_JWT_PREVIOUS_KID";
    static final String PREVIOUS_KEY = "HK5_AUTH_JWT_PREVIOUS_KEY_B64";
    static final String ACTIVE_STATUS = "HK5_AUTH_ACTIVE_STATUS";

    @Bean
    Hk5JwtCodec hk5JwtCodec(ConfigurableEnvironment environment) {
        return codecFromEnvironment(environment.getSystemEnvironment(), Clock.systemUTC());
    }

    @Bean
    CurrentAccountAuthority hk5CurrentAccountAuthority(AccountRepository accounts, ConfigurableEnvironment environment) {
        return authorityFromEnvironment(accounts, environment.getSystemEnvironment());
    }

    static Hk5JwtCodec codecFromEnvironment(Map<String, ?> env, Clock clock) {
        Objects.requireNonNull(env);
        String issuer = required(env, ISSUER);
        String audience = required(env, AUDIENCE);
        String currentKid = required(env, CURRENT_KID);
        String previousKid = optional(env, PREVIOUS_KID);
        String previousSecret = optional(env, PREVIOUS_KEY);
        if ((previousKid == null) != (previousSecret == null)) {
            throw invalidConfig();
        }
        if (previousKid != null && previousKid.equals(currentKid)) throw invalidConfig();
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        keys.put(currentKid, decodeKey(required(env, CURRENT_KEY)));
        if (previousKid != null) {
            SecretKey oldKey = decodeKey(previousSecret);
            if (Arrays.equals(keys.get(currentKid).getEncoded(), oldKey.getEncoded())) {
                throw invalidConfig(); // A rotated kid must never alias the same signing secret.
            }
            keys.put(previousKid, oldKey);
        }
        return new Hk5JwtCodec(issuer, audience, currentKid, keys, Objects.requireNonNull(clock));
    }

    static CurrentAccountAuthority authorityFromEnvironment(AccountRepository accounts, Map<String, ?> env) {
        Objects.requireNonNull(env);
        String status = required(env, ACTIVE_STATUS);
        if (!"ACTIVE".equals(status)) throw invalidConfig();
        // Approved TV1 policy: configuration cannot redefine an inactive state as active.
        return new CurrentAccountAuthority(accounts, status);
    }

    private static SecretKey decodeKey(String raw) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException badEncoding) {
            throw invalidConfig();
        }
        try {
            if (decoded.length < 32 || decoded.length > 4096
                    || !Base64.getEncoder().encodeToString(decoded).equals(raw)) throw invalidConfig();
            return new SecretKeySpec(decoded, "HmacSHA256");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private static String required(Map<String, ?> env, String name) {
        String value = optional(env, name);
        if (value == null) throw invalidConfig();
        return value;
    }

    private static String optional(Map<String, ?> env, String name) {
        Object raw = env.get(name);
        if (raw == null) return null;
        if (!(raw instanceof String value)) throw invalidConfig();
        if (value.isBlank() || !value.equals(value.strip())) throw invalidConfig();
        return value;
    }

    private static IllegalArgumentException invalidConfig() {
        // Never echo secret values or partial key bytes in errors or logs.
        return new IllegalArgumentException("Invalid Auth bootstrap configuration");
    }
}
