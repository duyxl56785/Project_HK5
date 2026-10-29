package com.projecthk5.security;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtAccessVerifierTest {
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    private final byte[] current = randomKey(), previous = randomKey();

    private static byte[] randomKey() {
        byte[] key = new byte[64];
        new java.security.SecureRandom().nextBytes(key);
        return key;
    }

    private JwtAccessVerifier verifier(Instant time) {
        return new JwtAccessVerifier("https://auth.example.invalid", "catalog", "new",
                Map.of("new", new SecretKeySpec(current, "HmacSHA256"),
                        "old", new SecretKeySpec(previous, "HmacSHA256")), Clock.fixed(time, ZoneOffset.UTC));
    }

    private Map<String, Object> claims() {
        return new LinkedHashMap<>(Map.of("iss", "https://auth.example.invalid", "aud", List.of("catalog"),
                "sub", " Exact-Opaque-Ä ", "jti", "token-1", "iat", now.getEpochSecond(),
                "nbf", now.getEpochSecond(), "exp", now.plusSeconds(900).getEpochSecond(),
                "roles", List.of("CUSTOMER", "SELLER")));
    }

    private String token(Map<String, Object> claims, String kid, byte[] key, JWSAlgorithm algorithm) throws Exception {
        var jws = new JWSObject(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).keyID(kid).build(),
                new Payload(JSONObjectUtils.toJSONString(claims)));
        jws.sign(new MACSigner(key));
        return jws.serialize();
    }

    @ParameterizedTest @ValueSource(strings = {"new", "old"})
    void verifiesBothKeysPreservingOpaqueSubjectAndImmutableSignedRoles(String kid) throws Exception {
        var verified = verifier(now).verify(token(claims(), kid, kid.equals("new") ? current : previous, JWSAlgorithm.HS256));
        assertEquals(" Exact-Opaque-Ä ", verified.subject());
        assertEquals(Set.of("CUSTOMER", "SELLER"), verified.tokenRoles());
        assertThrows(UnsupportedOperationException.class, () -> verified.tokenRoles().add("ADMIN"));
    }

    @ParameterizedTest @ValueSource(strings = {"issuer", "audience", "numeric-audience", "subject", "jti", "roles-empty", "roles-duplicate", "roles-admin-case", "roles-scalar", "roles-number", "missing-exp", "fraction-iat", "string-iat", "different-nbf", "ttl", "overflow-date"})
    void rejectsMalformedOrUntrustedClaims(String mode) throws Exception {
        var c = claims();
        switch (mode) {
            case "issuer" -> c.put("iss", "https://other.invalid");
            case "audience" -> c.put("aud", List.of("other"));
            case "numeric-audience" -> c.put("aud", 1);
            case "subject" -> c.put("sub", " ");
            case "jti" -> c.put("jti", "");
            case "roles-empty" -> c.put("roles", List.of());
            case "roles-duplicate" -> c.put("roles", List.of("ADMIN", "ADMIN"));
            case "roles-admin-case" -> c.put("roles", List.of("admin"));
            case "roles-scalar" -> c.put("roles", "ADMIN");
            case "roles-number" -> c.put("roles", List.of(1));
            case "missing-exp" -> c.remove("exp");
            case "fraction-iat" -> c.put("iat", now.getEpochSecond() + 0.5);
            case "string-iat" -> c.put("iat", Long.toString(now.getEpochSecond()));
            case "different-nbf" -> c.put("nbf", now.plusSeconds(1).getEpochSecond());
            case "ttl" -> c.put("exp", now.plusSeconds(901).getEpochSecond());
            case "overflow-date" -> c.put("exp", Long.MAX_VALUE);
        }
        String raw = token(c, "new", current, JWSAlgorithm.HS256);
        assertThrows(JwtException.class, () -> verifier(now).verify(raw));
    }

    @Test void retainsCompatibilityWithExistingNimbusSingleAudienceTokens() throws Exception {
        var c = claims();
        c.put("aud", "catalog");
        assertEquals(" Exact-Opaque-Ä ", verifier(now).verify(token(c, "new", current, JWSAlgorithm.HS256)).subject());
    }

    @Test void rejectsExpiredFutureUnknownKidWrongAlgorithmAndWrongSignature() throws Exception {
        String raw = token(claims(), "new", current, JWSAlgorithm.HS256);
        assertThrows(JwtException.class, () -> verifier(now.plusSeconds(961)).verify(raw));
        assertThrows(JwtException.class, () -> verifier(now.minusSeconds(61)).verify(raw));
        for (String invalid : List.of(token(claims(), "unknown", current, JWSAlgorithm.HS256),
                token(claims(), "new", previous, JWSAlgorithm.HS256),
                token(claims(), "new", current, JWSAlgorithm.HS384), "garbage", "x".repeat(8193))) {
            assertThrows(JwtException.class, () -> verifier(now).verify(invalid));
        }
    }

    @Test void requiresExplicitTrustedConfigurationAndDefensivelyCopiesKeys() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new JwtAccessVerifier("", "catalog", "new",
                Map.of("new", new SecretKeySpec(current, "HmacSHA256")), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new JwtAccessVerifier("issuer", "catalog", "new",
                Map.of("new", new SecretKeySpec(new byte[16], "HmacSHA256")), Clock.systemUTC()));
        var v = verifier(now);
        var raw = token(claims(), "new", current, JWSAlgorithm.HS256);
        Arrays.fill(current, (byte) 0);
        assertEquals(" Exact-Opaque-Ä ", v.verify(raw).subject());
    }
}
