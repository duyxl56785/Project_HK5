package com.projecthk5.authservice.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.authservice.persistence.Account;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Inert JWT foundation: no Spring bean, API route or default secret is installed by this class.
 * A future Auth controller must load the keyring from secret runtime and obtain a CurrentAccount
 * from the authoritative DB before calling issue. Consumers must verify independently.
 */
public final class Hk5JwtCodec {
    public static final long ACCESS_SECONDS = 900L;
    private static final long MAX_CLOCK_SKEW_SECONDS = 60L;

    private final String issuer;
    private final String audience;
    private final String signingKid;
    private final Clock clock;
    private final NimbusJwtEncoder encoder;
    private final Map<String, NimbusJwtDecoder> verifiers;

    public Hk5JwtCodec(String issuer, String audience, String signingKid,
                       Map<String, SecretKey> keys, Clock clock) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || signingKid == null || keys == null || keys.isEmpty() || keys.size() > 2
                || !keys.containsKey(signingKid)) {
            throw new IllegalArgumentException("Explicit issuer/audience/current key and 1-2 configured keys required");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.signingKid = signingKid;
        this.clock = Objects.requireNonNull(clock);
        var verified = new LinkedHashMap<String, NimbusJwtDecoder>();
        for (var entry : keys.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9._-]{1,64}")
                    || entry.getValue() == null || entry.getValue().getEncoded() == null
                    || entry.getValue().getEncoded().length < 32) {
                throw new IllegalArgumentException("Invalid local HS256 keyring");
            }
            // Defensively copy key bytes; never log/serialize key material.
            byte[] bytes = entry.getValue().getEncoded().clone();
            SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
            Arrays.fill(bytes, (byte) 0);
            var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            // Timestamp validation below uses the explicitly supplied clock (max 60s skew).
            decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
            verified.put(entry.getKey(), decoder);
        }
        this.verifiers = Map.copyOf(verified);
        SecretKey signingKey = keys.get(signingKid);
        var jwk = new OctetSequenceKey.Builder(signingKey)
                .keyID(signingKid).algorithm(JWSAlgorithm.HS256).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** Issue only after CurrentAccountAuthority has checked actual DB status/grants. */
    public String issue(CurrentAccountAuthority.CurrentAccount current) {
        Objects.requireNonNull(current, "Authoritative current account required");
        if (current.roles().isEmpty()) throw new IllegalArgumentException("No current roles");
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var roles = current.roles().stream().map(Enum::name).sorted().toList();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(current.accountId().toString()).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(ACCESS_SECONDS)).id(UUID.randomUUID().toString())
                .claim("roles", roles).build();
        var header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT").keyId(signingKid).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Returns an opaque verified principal, not current account or permission authority.
     * Current ADMIN/SELLER decisions still require CurrentAccountAuthority + domain owner facts.
     */
    public VerifiedPrincipal verify(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) throw invalid();
        try {
            var signed = SignedJWT.parse(token);
            var header = signed.getHeader();
            if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())
                    || !new JOSEObjectType("JWT").equals(header.getType())
                    || header.getKeyID() == null
                    || !verifiers.containsKey(header.getKeyID())
                    || header.getJWKURL() != null || header.getX509CertURL() != null) {
                throw invalid();
            }
            Jwt jwt = verifiers.get(header.getKeyID()).decode(token);
            Instant iat = jwt.getIssuedAt();
            Instant nbf = jwt.getNotBefore();
            Instant exp = jwt.getExpiresAt();
            Instant now = clock.instant();
            if (iat == null || nbf == null || exp == null
                    || !nbf.equals(iat) || !exp.equals(iat.plusSeconds(ACCESS_SECONDS))
                    || iat.isAfter(now.plusSeconds(MAX_CLOCK_SKEW_SECONDS))
                    || nbf.isAfter(now.plusSeconds(MAX_CLOCK_SKEW_SECONDS))
                    || !exp.isAfter(now.minusSeconds(MAX_CLOCK_SKEW_SECONDS))
                    || jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())
                    || !jwt.getAudience().contains(audience)
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getId() == null || jwt.getId().isBlank()) {
                throw invalid();
            }
            Object claim = jwt.getClaims().get("roles");
            if (!(claim instanceof List<?> list) || list.isEmpty()) throw invalid();
            var roles = java.util.EnumSet.noneOf(Account.Role.class);
            for (Object value : list) {
                if (!(value instanceof String role)) throw invalid();
                Account.Role parsed;
                try { parsed = Account.Role.valueOf(role); }
                catch (IllegalArgumentException badRole) { throw invalid(); }
                if (!roles.add(parsed)) throw invalid();
            }
            return new VerifiedPrincipal(jwt.getSubject(), Set.copyOf(roles), jwt.getId());
        } catch (ParseException | IllegalArgumentException | ClassCastException error) {
            throw invalid();
        }
    }

    private static JwtException invalid() { return new JwtException("Invalid access token"); }

    /** Instantiable only by this verifying codec. Not a Seller/Store authorization proof. */
    public static final class VerifiedPrincipal {
        private final String subject;
        private final Set<Account.Role> tokenRoles;
        private final String tokenId;

        private VerifiedPrincipal(String subject, Set<Account.Role> tokenRoles, String tokenId) {
            this.subject = subject;
            this.tokenRoles = tokenRoles;
            this.tokenId = tokenId;
        }
        public String subject() { return subject; }
        public Set<Account.Role> tokenRoles() { return tokenRoles; }
        public String tokenId() { return tokenId; }
    }
}
