package com.projecthk5.gateway;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Inert Gateway verifier for opt-in route validation. Keys come from runtime
 * process environment only, never JWT headers/URLs or frontend input.
 * JWT roles are informational at Gateway: downstream checks current DB/ownership.
 */
public final class GatewayJwtDecoder implements ReactiveJwtDecoder {
    private final Map<String, ReactiveJwtDecoder> verifiers;
    private final String issuer;
    private final String audience;

    public GatewayJwtDecoder(Map<String, ?> env) {
        issuer = required(env, "HK5_AUTH_JWT_ISSUER");
        audience = required(env, "HK5_AUTH_JWT_AUDIENCE");
        String kid = required(env, "HK5_AUTH_JWT_CURRENT_KID");
        String oldKid = optional(env, "HK5_AUTH_JWT_PREVIOUS_KID");
        String oldSecret = optional(env, "HK5_AUTH_JWT_PREVIOUS_KEY_B64");
        if ((oldKid == null) != (oldSecret == null) || (oldKid != null && oldKid.equals(kid)))
            throw configError();
        SecretKey current = secret(required(env, "HK5_AUTH_JWT_CURRENT_KEY_B64"));
        var keys = new HashMap<String, ReactiveJwtDecoder>();
        if (!validKid(kid)) throw configError();
        keys.put(kid, NimbusReactiveJwtDecoder.withSecretKey(current).macAlgorithm(MacAlgorithm.HS256).build());
        if (oldKid != null) {
            if (!validKid(oldKid)) throw configError();
            SecretKey previous = secret(oldSecret);
            if (MessageDigest.isEqual(current.getEncoded(), previous.getEncoded())) throw configError();
            keys.put(oldKid, NimbusReactiveJwtDecoder.withSecretKey(previous).macAlgorithm(MacAlgorithm.HS256).build());
        }
        verifiers = Map.copyOf(keys);
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return Mono.defer(() -> {
            if (token == null || token.isBlank() || token.length() > 8192) return Mono.error(invalid());
            SignedJWT signed;
            try { signed = SignedJWT.parse(token); }
            catch (ParseException exception) { return Mono.error(invalid()); }
            var header = signed.getHeader();
            if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())
                    || !new JOSEObjectType("JWT").equals(header.getType())
                    || header.getKeyID() == null || !verifiers.containsKey(header.getKeyID())
                    || header.getJWKURL() != null || header.getX509CertURL() != null) {
                return Mono.error(invalid());
            }
            return verifiers.get(header.getKeyID()).decode(token)
                    .onErrorMap(e -> invalid())
                    .flatMap(jwt -> validClaims(jwt) ? Mono.just(jwt) : Mono.error(invalid()));
        });
    }

    private boolean validClaims(Jwt jwt) {
        Instant iat = jwt.getIssuedAt(), nbf = jwt.getNotBefore(), exp = jwt.getExpiresAt();
        Instant now = Instant.now();
        if (iat == null || nbf == null || exp == null || !iat.equals(nbf)
                || !exp.equals(iat.plusSeconds(900))
                || iat.isAfter(now.plusSeconds(60)) || nbf.isAfter(now.plusSeconds(60))
                || !exp.isAfter(now.minusSeconds(60))
                || jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())
                || !jwt.getAudience().contains(audience)
                || jwt.getSubject() == null || jwt.getSubject().isBlank()
                || jwt.getId() == null || jwt.getId().isBlank()) return false;
        Object raw = jwt.getClaims().get("roles");
        if (!(raw instanceof List<?> roles) || roles.isEmpty()) return false;
        var unique = new HashSet<String>();
        for (Object role : roles) {
            if (!(role instanceof String name)
                    || !(name.equals("CUSTOMER") || name.equals("SELLER") || name.equals("ADMIN"))
                    || !unique.add(name)) return false;
        }
        return true;
    }

    private static boolean validKid(String kid) {
        return kid != null && kid.matches("[A-Za-z0-9._-]{1,64}");
    }

    private static String optional(Map<String, ?> env, String key) {
        Object raw = env.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String s) || s.isBlank() || !s.equals(s.strip())) throw configError();
        return s;
    }

    private static String required(Map<String, ?> env, String key) {
        String value = optional(env, key);
        if (value == null) throw configError();
        return value;
    }

    private static SecretKey secret(String encoded) {
        byte[] raw;
        try { raw = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException invalid) { throw configError(); }
        try {
            if (raw.length < 32 || raw.length > 4096
                    || !Base64.getEncoder().encodeToString(raw).equals(encoded)) throw configError();
            return new SecretKeySpec(raw, "HmacSHA256");
        } finally { Arrays.fill(raw, (byte) 0); }
    }

    private static BadJwtException invalid() { return new BadJwtException("Invalid access token"); }
    private static IllegalArgumentException configError() {
        return new IllegalArgumentException("Invalid Gateway JWT configuration");
    }
}
