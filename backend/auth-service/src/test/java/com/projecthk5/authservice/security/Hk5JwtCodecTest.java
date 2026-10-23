package com.projecthk5.authservice.security;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.mockito.Mockito.*;

class Hk5JwtCodecTest {
    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    private static final String ISSUER = "https://identity.example.invalid";
    private static final String AUDIENCE = "smart-ecommerce-api";
    private final UUID id = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private AccountRepository repository;
    private CurrentAccountAuthority authority;

    @BeforeEach
    void setUp() {
        repository = mock(AccountRepository.class);
        authority = new CurrentAccountAuthority(repository, "ACTIVE");
    }

    private static SecretKey key(int byteValue) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) byteValue);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private Hk5JwtCodec codec(String issuer, String audience, String currentKid, Clock clock) {
        return new Hk5JwtCodec(issuer, audience, currentKid,
                Map.of("current", key(7), "previous", key(8)), clock);
    }

    private Clock fixed() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private void account(String status, Set<Account.Role> roles) {
        when(repository.findById(id)).thenReturn(Optional.of(new Account(
                id, "test@example.invalid", "fixture-hash-not-a-secret",
                status, roles, NOW)));
    }

    @Test
    void issuesExactContractAndVerifiesCurrentRoleAfterDatabaseRead() {
        account("ACTIVE", Set.of(Account.Role.CUSTOMER, Account.Role.ADMIN));
        var issuer = codec(ISSUER, AUDIENCE, "current", fixed());
        String jwt = issuer.issue(authority.requireCurrentForIssue(id));
        var verified = issuer.verify(jwt);
        assertEquals(id.toString(), verified.subject());
        assertEquals(Set.of(Account.Role.CUSTOMER, Account.Role.ADMIN), verified.tokenRoles());
        assertNotNull(verified.tokenId());
        assertEquals(id, authority.requireCurrentForRequest(verified, Account.Role.ADMIN).accountId());
        verify(repository, times(2)).findById(id);
    }

    @Test
    void rejectsTamperingUnknownKeyAndWrongIssuerAudience() {
        account("ACTIVE", Set.of(Account.Role.CUSTOMER));
        var signer = codec(ISSUER, AUDIENCE, "current", fixed());
        String jwt = signer.issue(authority.requireCurrentForIssue(id));
        String[] parts = jwt.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> signer.verify(String.join(".", parts)));
        assertThrows(JwtException.class, () -> codec("https://other.invalid", AUDIENCE, "current", fixed()).verify(jwt));
        assertThrows(JwtException.class, () -> codec(ISSUER, "other-audience", "current", fixed()).verify(jwt));
        var foreignKid = new Hk5JwtCodec(ISSUER, AUDIENCE, "foreign", Map.of("foreign", key(7)), fixed());
        String foreign = foreignKid.issue(authority.requireCurrentForIssue(id));
        assertThrows(JwtException.class, () -> signer.verify(foreign));
        assertThrows(JwtException.class, () -> signer.verify(jwt + ".extra"));
    }

    @Test
    void acceptsLocalPreviousKeyUntilRotationRetirementAndRejectsExpired() {
        account("ACTIVE", Set.of(Account.Role.CUSTOMER));
        String oldToken = codec(ISSUER, AUDIENCE, "previous", fixed())
                .issue(authority.requireCurrentForIssue(id));
        assertEquals(id.toString(), codec(ISSUER, AUDIENCE, "current", fixed()).verify(oldToken).subject());
        var currentOnly = new Hk5JwtCodec(ISSUER, AUDIENCE, "current", Map.of("current", key(7)), fixed());
        assertThrows(JwtException.class, () -> currentOnly.verify(oldToken));
        Clock afterExpiryAndSkew = Clock.offset(fixed(), java.time.Duration.ofSeconds(962));
        assertThrows(JwtException.class, () ->
                codec(ISSUER, AUDIENCE, "current", afterExpiryAndSkew).verify(oldToken));
    }

    @Test
    void missingWeakOrExcessKeysCannotInitializeAndAbsentConfigNeverDefaults() {
        assertThrows(IllegalArgumentException.class,
                () -> new Hk5JwtCodec("", AUDIENCE, "current", Map.of("current", key(7)), fixed()));
        assertThrows(IllegalArgumentException.class,
                () -> new Hk5JwtCodec(ISSUER, AUDIENCE, "missing", Map.of("current", key(7)), fixed()));
        assertThrows(IllegalArgumentException.class,
                () -> new Hk5JwtCodec(ISSUER, AUDIENCE, "current", Map.of(
                        "current", new SecretKeySpec(new byte[16], "HmacSHA256")), fixed()));
        assertThrows(IllegalArgumentException.class,
                () -> new Hk5JwtCodec(ISSUER, AUDIENCE, "current", Map.of(
                        "current", key(7), "previous", key(8), "extra", key(9)), fixed()));
    }
}
