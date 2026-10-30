package com.projecthk5.authservice.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.security.JwtAccessVerifier;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Auth-owned issuer; verification is shared without exporting Auth database types. */
public final class Hk5JwtCodec {
    public static final long ACCESS_SECONDS = JwtAccessVerifier.ACCESS_SECONDS;
    private final String issuer, audience, signingKid;
    private final Clock clock;
    private final NimbusJwtEncoder encoder;
    private final JwtAccessVerifier verifier;

    public Hk5JwtCodec(String issuer, String audience, String signingKid, Map<String, SecretKey> keys, Clock clock) {
        this.verifier = new JwtAccessVerifier(issuer, audience, signingKid, keys, clock);
        this.issuer = issuer;
        this.audience = audience;
        this.signingKid = signingKid;
        this.clock = clock;
        var jwk = new OctetSequenceKey.Builder(keys.get(signingKid))
                .keyID(signingKid).algorithm(JWSAlgorithm.HS256).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    public String issue(CurrentAccountAuthority.CurrentAccount current) {
        Objects.requireNonNull(current, "Authoritative current account required");
        if (current.roles().isEmpty()) throw new IllegalArgumentException("No current roles");
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var roles = current.roles().stream().map(Enum::name).sorted().toList();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(current.accountId().toString()).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(ACCESS_SECONDS)).id(UUID.randomUUID().toString())
                .claim("roles", roles).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").keyId(signingKid).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public VerifiedPrincipal verify(String token) {
        var principal = verifier.verify(token);
        return new VerifiedPrincipal(principal.subject(), principal.tokenRoles().stream()
                .map(Account.Role::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet()), principal.tokenId(),principal.expiresAt());
    }

    /** Signed roles only; CurrentAccountAuthority still checks current account/grants. */
    public static final class VerifiedPrincipal {
        private final String subject, tokenId;
        private final Set<Account.Role> tokenRoles;
        private final Instant expiresAt;
        private VerifiedPrincipal(String subject, Set<Account.Role> tokenRoles, String tokenId, Instant expiresAt) {
            this.subject = subject; this.tokenRoles = tokenRoles; this.tokenId = tokenId; this.expiresAt=expiresAt;
        }
        public String subject() { return subject; }
        public Set<Account.Role> tokenRoles() { return tokenRoles; }
        public String tokenId() { return tokenId; }
        public Instant expiresAt() { return expiresAt; }
    }
}
