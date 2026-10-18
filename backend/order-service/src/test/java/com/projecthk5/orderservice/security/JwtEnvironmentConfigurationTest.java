package com.projecthk5.orderservice.security;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtEnvironmentConfigurationTest {
    private WebApplicationContextRunner context(Map<String, Object> environment) {
        return new WebApplicationContextRunner()
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("jwt-test-environment", environment)))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                WebMvcAutoConfiguration.class))
            .withUserConfiguration(ResourceSecurityConfig.class);
    }

    @Test
    void documentedEnvironmentBindsThroughRealYamlAndVerifiesHs256() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Map<String, Object> environment = Map.of(
            "JWT_ISSUER_URI", "https://fixture.example.invalid",
            "JWT_AUDIENCE", "fixture-api",
            "JWT_HS256_CURRENT_KID", "fixture-current",
            "JWT_HS256_CURRENT_KEY_B64", Base64.getEncoder().encodeToString(key));
        String valid = token(key, "fixture-api");
        String wrongAudience = token(key, "other-api");
        context(environment).run(ctx -> {
            assertNull(ctx.getStartupFailure(), "The documented environment must configure Order JWT");
            JwtDecoder decoder = ctx.getBean(JwtDecoder.class);
            assertEquals("customer-fixture", decoder.decode(valid).getSubject());
            assertThrows(JwtException.class, () -> decoder.decode(wrongAudience));
        });
    }

    @Test
    void missingKeyStillFailsClosed() {
        context(Map.of("JWT_ISSUER_URI", "https://fixture.example.invalid",
            "JWT_AUDIENCE", "fixture-api")).run(ctx ->
                assertNotNull(ctx.getStartupFailure(), "Missing key must never create a fallback decoder"));
    }

    private String token(byte[] key, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("https://fixture.example.invalid")
            .subject("customer-fixture").audience(audience).issueTime(Date.from(now))
            .notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
            .jwtID("fixture-jti").claim("roles", List.of("CUSTOMER")).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT).keyID("fixture-current").build(), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
