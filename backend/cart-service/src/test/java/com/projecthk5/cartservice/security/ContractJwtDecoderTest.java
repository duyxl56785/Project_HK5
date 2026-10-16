package com.projecthk5.cartservice.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

class ContractJwtDecoderTest {
    private static final byte[] A = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
    private ContractJwtDecoder decoder() {
        return new ContractJwtDecoder("https://issuer.example.invalid","ecommerce", "new",Base64.getEncoder().encodeToString(A),
            "old",Base64.getEncoder().encodeToString(B));
    }
    private String token(byte[] key, String kid, String issuer, List<String> roles, long ttl) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject("user-123")
            .audience(List.of("ecommerce")).issueTime(Date.from(now))
            .notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(ttl)))
            .jwtID("test-jti-1").claim("roles",roles).build();
        SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT).keyID(kid).build(), claims);
        signed.sign(new MACSigner(key));
        return signed.serialize();
    }
    @Test void currentAndPreviousKidsAccepted() throws Exception {
        assertEquals("user-123",decoder().decode(token(A,"new","https://issuer.example.invalid",List.of("CUSTOMER"),900)).getSubject());
        assertEquals("user-123",decoder().decode(token(B,"old","https://issuer.example.invalid",List.of("CUSTOMER"),900)).getSubject());
    }
    @Test void wrongKidSignatureIssuerRoleAndTtlRejected() throws Exception {
        assertThrows(JwtException.class,()->decoder().decode(token(A,"unknown","https://issuer.example.invalid",List.of("CUSTOMER"),900)));
        assertThrows(JwtException.class,()->decoder().decode(token(A,"old","https://issuer.example.invalid",List.of("CUSTOMER"),900)));
        assertThrows(JwtException.class,()->decoder().decode(token(A,"new","https://other.example.invalid",List.of("CUSTOMER"),900)));
        assertThrows(JwtException.class,()->decoder().decode(token(A,"new","https://issuer.example.invalid",List.of("ROLE_ADMIN"),900)));
        assertThrows(JwtException.class,()->decoder().decode(token(A,"new","https://issuer.example.invalid",List.of("CUSTOMER"),901)));
    }
    @Test void missingShortAndMismatchedKeyringRejectStartup() {
        assertThrows(IllegalStateException.class,()->new ContractJwtDecoder("","ecommerce","x",Base64.getEncoder().encodeToString(A),"",""));
        assertThrows(IllegalStateException.class,()->new ContractJwtDecoder("issuer","ecommerce","x","YmFk","",""));
        assertThrows(IllegalStateException.class,()->new ContractJwtDecoder("issuer","ecommerce","x",Base64.getEncoder().encodeToString(A),"old",""));
    }
}
