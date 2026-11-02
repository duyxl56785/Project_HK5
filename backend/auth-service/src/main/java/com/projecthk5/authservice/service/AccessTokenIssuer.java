package com.projecthk5.authservice.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Issues contract v1.0 access tokens: HS256 + kid, iss/sub/aud/iat=nbf/exp(900s)/jti/roles. */
@Component
public class AccessTokenIssuer {
    public static final long TTL_SECONDS = 900;
    private final NimbusJwtEncoder encoder;
    private final String kid;
    private final String issuer;
    private final String audience;

    public AccessTokenIssuer(@Value("${JWT_ISSUER_URI:}") String issuer,
                             @Value("${JWT_AUDIENCE:}") String audience,
                             @Value("${JWT_HS256_CURRENT_KID:}") String kid,
                             @Value("${JWT_HS256_CURRENT_KEY_B64:}") String keyB64) {
        if (issuer.isBlank() || audience.isBlank() || kid.isBlank() || keyB64.isBlank())
            throw new IllegalStateException("Access token signing is not configured");
        byte[] key = Base64.getDecoder().decode(keyB64);
        if (key.length < 32) throw new IllegalStateException("HS256 key must have at least 256 bits");
        var jwk = new OctetSequenceKey.Builder(new SecretKeySpec(key, "HmacSHA256")).keyID(kid).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        this.kid = kid;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(UUID accountId, Collection<String> roles) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(accountId.toString()).audience(List.of(audience))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(TTL_SECONDS))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles.stream().sorted().toList())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).keyId(kid).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
