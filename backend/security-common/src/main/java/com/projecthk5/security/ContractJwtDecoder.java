package com.projecthk5.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** TV1 Contract v1.0: pinned HS256, trusted current/previous kid keyring, strict access claims. */
public final class ContractJwtDecoder implements JwtDecoder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROLES = Set.of("CUSTOMER", "SELLER", "ADMIN");
    private final Map<String, JwtDecoder> decoders;
    private final String issuer;
    private final String audience;

    public ContractJwtDecoder(String issuer, String audience, String currentKid, String currentBase64,
                              String previousKid, String previousBase64) {
        if (blank(issuer) || blank(audience) || blank(currentKid) || blank(currentBase64))
            throw new IllegalStateException("HS256 verifier not configured; service is not ready");
        if (currentKid.length() > 100 || !currentKid.matches("[A-Za-z0-9._-]+"))
            throw new IllegalStateException("Invalid current kid");
        this.issuer = issuer;
        this.audience = audience;
        Map<String, JwtDecoder> trusted = new HashMap<>();
        trusted.put(currentKid, decoderFor(currentBase64));
        if (blank(previousKid) != blank(previousBase64))
            throw new IllegalStateException("Previous key id and key must be configured together");
        if (!blank(previousKid)) {
            if (previousKid.equals(currentKid) || previousKid.length() > 100
                    || !previousKid.matches("[A-Za-z0-9._-]+"))
                throw new IllegalStateException("Invalid previous kid");
            trusted.put(previousKid, decoderFor(previousBase64));
        }
        this.decoders = Map.copyOf(trusted);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static JwtDecoder decoderFor(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) throw new IllegalStateException("HS256 key must have at least 256 bits");
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
            return decoder;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("HS256 key must be valid Base64", e);
        }
    }

    @Override
    public Jwt decode(String token) {
        try {
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3 || segments[0].length() > 4096)
                throw new BadJwtException("Invalid JWT format");
            JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(segments[0]));
            if (!header.isObject() || !"JWT".equals(header.path("typ").asText())
                    || !"HS256".equals(header.path("alg").asText()))
                throw new BadJwtException("Unsupported JWT profile");
            String kid = header.path("kid").asText("");
            JwtDecoder decoder = decoders.get(kid);
            if (decoder == null) throw new BadJwtException("Unknown or missing kid");
            Jwt jwt = decoder.decode(token);
            validate(jwt);
            return jwt;
        } catch (BadJwtException e) {
            throw e;
        } catch (Exception e) {
            throw new BadJwtException("JWT does not match Auth contract", e);
        }
    }

    private void validate(Jwt jwt) {
        Instant now = Instant.now();
        Instant iat = jwt.getIssuedAt(), nbf = jwt.getNotBefore(), exp = jwt.getExpiresAt();
        if (!issuer.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                || jwt.getAudience() == null || !jwt.getAudience().contains(audience))
            throw new BadJwtException("Invalid issuer or audience");
        if (!(jwt.getClaim("sub") instanceof String sub) || sub.isBlank()
                || !(jwt.getClaim("jti") instanceof String jti) || jti.isBlank())
            throw new BadJwtException("Missing access identity");
        if (iat == null || nbf == null || exp == null || !iat.equals(nbf)
                || !exp.isAfter(iat) || Duration.between(iat, exp).compareTo(Duration.ofSeconds(900)) > 0
                || iat.isAfter(now.plusSeconds(60))
                || nbf.isAfter(now.plusSeconds(60))
                || !exp.isAfter(now.minusSeconds(60)))
            throw new BadJwtException("Invalid access token lifetime");
        Object roles = jwt.getClaim("roles");
        if (!(roles instanceof List<?> list) || list.isEmpty()
                || list.stream().anyMatch(r -> !(r instanceof String s) || !ROLES.contains(s)))
            throw new BadJwtException("Invalid roles claim");
    }
}
