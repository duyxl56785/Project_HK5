package com.projecthk5.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class GatewayJwtDecoderTest {
    private static final byte[] KEY = new byte[32];
    private static Map<String, String> env() {
        return new HashMap<>(Map.of(
                "HK5_AUTH_JWT_ISSUER", "https://identity.example.invalid",
                "HK5_AUTH_JWT_AUDIENCE", "smart-ecommerce-api",
                "HK5_AUTH_JWT_CURRENT_KID", "current",
                "HK5_AUTH_JWT_CURRENT_KEY_B64", Base64.getEncoder().encodeToString(KEY)));
    }
    private static String token(String kid, byte[] key, Instant issued, String issuer, List<String> roles) {
        try {
            var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT).keyID(kid).build();
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer).audience("smart-ecommerce-api")
                    .subject("00000000-0000-4000-8000-000000000001")
                    .issueTime(Date.from(issued)).notBeforeTime(Date.from(issued))
                    .expirationTime(Date.from(issued.plusSeconds(900)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("roles", roles).build();
            SignedJWT value = new SignedJWT(header, claims);
            value.sign(new MACSigner(key));
            return value.serialize();
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    @Test
    void verifiedClaimsAndTamperNegative() {
        var decoder = new GatewayJwtDecoder(env());
        String valid = token("current", KEY, Instant.now(), "https://identity.example.invalid", List.of("CUSTOMER"));
        var jwt = decoder.decode(valid).block();
        assertNotNull(jwt);
        assertEquals("00000000-0000-4000-8000-000000000001", jwt.getSubject());
        assertThrows(JwtException.class, () -> decoder.decode(valid + "A").block());
        assertThrows(JwtException.class, () -> decoder.decode(
                token("unknown", KEY, Instant.now(), "https://identity.example.invalid", List.of("CUSTOMER"))).block());
        assertThrows(JwtException.class, () -> decoder.decode(
                token("current", KEY, Instant.now(), "https://wrong.example.invalid", List.of("CUSTOMER"))).block());
        assertThrows(JwtException.class, () -> decoder.decode(
                token("current", KEY, Instant.now().minusSeconds(1000),
                        "https://identity.example.invalid", List.of("CUSTOMER"))).block());
        assertThrows(JwtException.class, () -> decoder.decode(
                token("current", KEY, Instant.now(), "https://identity.example.invalid", List.of("GOD"))).block());
        assertThrows(JwtException.class, () -> decoder.decode(
                token("current", KEY, Instant.now(), "https://identity.example.invalid", List.of("CUSTOMER", "CUSTOMER"))).block());
    }

    @Test
    void previousKeyCanVerifyButUnknownAndRetiredCannot() {
        byte[] oldKey = new byte[32];
        oldKey[0] = 1;
        Map<String, String> active = env();
        active.put("HK5_AUTH_JWT_PREVIOUS_KID", "previous");
        active.put("HK5_AUTH_JWT_PREVIOUS_KEY_B64", Base64.getEncoder().encodeToString(oldKey));
        String oldToken = token("previous", oldKey, Instant.now(),
                "https://identity.example.invalid", List.of("CUSTOMER"));
        assertNotNull(new GatewayJwtDecoder(active).decode(oldToken).block());
        assertThrows(JwtException.class, () -> new GatewayJwtDecoder(env()).decode(oldToken).block());
    }

    @Test
    void noUntrustedKeyOrImplicitIssuerIsAccepted() {
        var missing = env();
        missing.remove("HK5_AUTH_JWT_CURRENT_KEY_B64");
        assertThrows(IllegalArgumentException.class, () -> new GatewayJwtDecoder(missing));
        var malformed = env();
        malformed.put("HK5_AUTH_JWT_CURRENT_KEY_B64", "YQ==");
        assertThrows(IllegalArgumentException.class, () -> new GatewayJwtDecoder(malformed));
        var duplicateKey = env();
        duplicateKey.put("HK5_AUTH_JWT_PREVIOUS_KID", "previous");
        duplicateKey.put("HK5_AUTH_JWT_PREVIOUS_KEY_B64", env().get("HK5_AUTH_JWT_CURRENT_KEY_B64"));
        assertThrows(IllegalArgumentException.class, () -> new GatewayJwtDecoder(duplicateKey));
    }
}
