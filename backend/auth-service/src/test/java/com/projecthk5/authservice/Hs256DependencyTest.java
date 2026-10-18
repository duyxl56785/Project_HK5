package com.projecthk5.authservice;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class Hs256DependencyTest {
    @Test
    void managedJoseDependenciesSupportHs256AndRejectAnotherKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        SecretKey key = generator.generateKey();
        var jwk = new OctetSequenceKey.Builder(key).keyID("readiness-fixture").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        var claims = JwtClaimsSet.builder().subject("readiness-fixture")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).keyId("readiness-fixture").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        assertEquals("readiness-fixture", decoder.decode(token).getSubject());
        var wrongKeyDecoder = NimbusJwtDecoder.withSecretKey(generator.generateKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
        assertThrows(JwtException.class, () -> wrongKeyDecoder.decode(token));
    }
}
