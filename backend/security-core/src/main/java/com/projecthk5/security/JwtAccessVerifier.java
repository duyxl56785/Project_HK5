package com.projecthk5.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Shared verification only; no issuer, database authority, filter or Spring registration. */
public final class JwtAccessVerifier {
    public static final long ACCESS_SECONDS = 900;
    private final String issuer, audience;
    private final Clock clock;
    private final Map<String, NimbusJwtDecoder> verifiers;

    public JwtAccessVerifier(String issuer, String audience, String currentKid, Map<String, SecretKey> keys, Clock clock) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || currentKid == null || keys == null || keys.isEmpty() || keys.size() > 2 || !keys.containsKey(currentKid)) {
            throw new IllegalArgumentException("Explicit issuer/audience/current key and 1-2 configured keys required");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.clock = Objects.requireNonNull(clock);
        var configured = new LinkedHashMap<String, NimbusJwtDecoder>();
        for (var entry : keys.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9._-]{1,64}")
                    || entry.getValue() == null || entry.getValue().getEncoded() == null
                    || entry.getValue().getEncoded().length < 32 || entry.getValue().getEncoded().length > 4096) {
                throw new IllegalArgumentException("Invalid local HS256 keyring");
            }
            byte[] bytes = entry.getValue().getEncoded().clone();
            var key = new SecretKeySpec(bytes, "HmacSHA256");
            Arrays.fill(bytes, (byte) 0);
            var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
            configured.put(entry.getKey(), decoder);
        }
        verifiers = Map.copyOf(configured);
    }

    public VerifiedPrincipal verify(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) throw invalid();
        try {
            var signed = SignedJWT.parse(token);
            var header = signed.getHeader();
            if (!JWSAlgorithm.HS256.equals(header.getAlgorithm()) || !JOSEObjectType.JWT.equals(header.getType())
                    || header.getKeyID() == null || !verifiers.containsKey(header.getKeyID())
                    || header.getJWKURL() != null || header.getX509CertURL() != null) throw invalid();
            var jwt = verifiers.get(header.getKeyID()).decode(token);
            var raw = JSONObjectUtils.parse(signed.getPayload().toString());
            Object rawAudience = raw.get("aud");
            // Nimbus encodes the existing issuer's single audience as a string (JWT permits both forms).
            boolean trustedAudience = rawAudience instanceof String single && audience.equals(single)
                    || rawAudience instanceof List<?> audiences
                    && audiences.stream().allMatch(value -> value instanceof String) && audiences.contains(audience);
            if (!(raw.get("iss") instanceof String) || !(raw.get("sub") instanceof String)
                    || !(raw.get("jti") instanceof String) || !trustedAudience) throw invalid();
            for (String field : List.of("iat", "nbf", "exp")) {
                Object value = raw.get(field);
                if (!(value instanceof Long || value instanceof Integer)) throw invalid();
            }
            Instant iat = jwt.getIssuedAt(), nbf = jwt.getNotBefore(), exp = jwt.getExpiresAt(), now = clock.instant();
            if (iat == null || nbf == null || exp == null || !nbf.equals(iat)
                    || !exp.equals(iat.plusSeconds(ACCESS_SECONDS)) || iat.isAfter(now.plusSeconds(60))
                    || nbf.isAfter(now.plusSeconds(60)) || !exp.isAfter(now.minusSeconds(60))
                    || jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getId() == null || jwt.getId().isBlank()) throw invalid();
            if (!(raw.get("roles") instanceof List<?> list) || list.isEmpty()) throw invalid();
            var roles = new HashSet<String>();
            for (Object value : list) {
                if (!(value instanceof String role) || !Set.of("CUSTOMER", "SELLER", "ADMIN").contains(role)
                        || !roles.add(role)) throw invalid();
            }
            return new VerifiedPrincipal(jwt.getSubject(), Set.copyOf(roles), jwt.getId(),exp);
        } catch (ParseException | IllegalArgumentException | ClassCastException | java.time.DateTimeException
                 | ArithmeticException | JwtException error) {
            throw invalid();
        }
    }

    private static JwtException invalid() { return new JwtException("Invalid access token"); }

    public static final class VerifiedPrincipal {
        private final String subject, tokenId;
        private final Set<String> tokenRoles;
        private final Instant expiresAt;
        private VerifiedPrincipal(String subject, Set<String> tokenRoles, String tokenId,Instant expiresAt) {
            this.subject = subject; this.tokenRoles = tokenRoles; this.tokenId = tokenId;this.expiresAt=expiresAt;
        }
        public String subject() { return subject; }
        public Set<String> tokenRoles() { return tokenRoles; }
        public String tokenId() { return tokenId; }
        public Instant expiresAt() {return expiresAt;}
    }
}
