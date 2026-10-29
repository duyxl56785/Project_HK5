package com.projecthk5.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalPairVerifierTest {
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    private final String current = java.util.UUID.randomUUID().toString(), previous = java.util.UUID.randomUUID().toString();
    private InternalPairVerifier verifier(Instant time) {
        return new InternalPairVerifier("seller", "auth", Set.of(new InternalPairVerifier.Scope(
                "POST", "/internal/auth/seller-role-sync", "SELLER_ROLE_SYNC")),
                () -> new InternalPairVerifier.Credentials(current, previous, now.plusSeconds(60)),
                Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test void acceptsCurrentAndUnexpiredPreviousOnlyForExactServerOwnedScope() {
        for (String token : Set.of(current, previous)) {
            var decision = verifier(now).verify(token, "POST", "/internal/auth/seller-role-sync");
            assertEquals("SELLER_ROLE_SYNC", decision.operation());
            assertEquals("seller", decision.caller());
            assertEquals("auth", decision.callee());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "post", "DELETE"})
    void validTokenDoesNotGrantAnotherMethod(String method) {
        assertFailure(403, "INTERNAL_FORBIDDEN", () -> verifier(now).verify(current, method, "/internal/auth/seller-role-sync"));
    }

    @ParameterizedTest @ValueSource(strings = {"/internal/auth/seller-role-sync/", "/internal/auth/seller-role-sync?x=1", "/internal/auth/%73eller-role-sync", "/internal/seller/store-facts/batch", "/api/auth/me"})
    void validPairCannotAccessAnotherOrNormalizedPath(String path) {
        assertFailure(403, "INTERNAL_FORBIDDEN", () -> verifier(now).verify(current, "POST", path));
    }

    @Test void rejectsMissingForgedOtherPairAndExpiredPreviousCredentials() {
        for (String invalid : new String[]{null, "", " ", "Bearer " + current, current + " ", java.util.UUID.randomUUID().toString()}) {
            assertFailure(401, "INTERNAL_AUTH_INVALID", () -> verifier(now).verify(invalid, "POST", "/internal/auth/seller-role-sync"));
        }
        assertFailure(401, "INTERNAL_AUTH_INVALID", () -> verifier(now.plusSeconds(60)).verify(previous, "POST", "/internal/auth/seller-role-sync"));
        assertEquals("SELLER_ROLE_SYNC", verifier(now.plusSeconds(60)).verify(current, "POST", "/internal/auth/seller-role-sync").operation());
    }

    @Test void unavailableCredentialSourceFailsClosedWithoutLeakingSecrets() {
        var v = new InternalPairVerifier("seller", "auth", Set.of(new InternalPairVerifier.Scope(
                "POST", "/internal/auth/seller-role-sync", "SELLER_ROLE_SYNC")),
                () -> { throw new IllegalStateException(current); }, Clock.systemUTC());
        var failure = assertThrows(InternalPairVerifier.Failure.class,
                () -> v.verify(current, "POST", "/internal/auth/seller-role-sync"));
        assertEquals(503, failure.status());
        assertFalse(failure.toString().contains(current));
        assertNull(failure.getCause());
    }

    @Test void invalidRotationAndEmptyScopeAreRejectedAtConfigurationBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new InternalPairVerifier.Credentials(current, previous, null));
        assertThrows(IllegalArgumentException.class, () -> new InternalPairVerifier.Credentials(current, current, now));
        assertThrows(IllegalArgumentException.class, () -> new InternalPairVerifier.Credentials("short", null, null));
        assertThrows(IllegalArgumentException.class, () -> new InternalPairVerifier("seller", "auth", Set.of(),
                () -> null, Clock.systemUTC()));
    }

    @ParameterizedTest @ValueSource(strings = {"\uD800", "\uDC00"})
    void malformedUnicodeCannotCollideWithAValidOpaqueCredential(String malformed) {
        String valid = "A".repeat(31) + "?";
        var v = new InternalPairVerifier("seller", "auth", Set.of(new InternalPairVerifier.Scope(
                "POST", "/internal/auth/seller-role-sync", "SELLER_ROLE_SYNC")),
                () -> new InternalPairVerifier.Credentials(valid, null, null), Clock.systemUTC());
        assertFailure(401, "INTERNAL_AUTH_INVALID", () -> v.verify("A".repeat(31) + malformed,
                "POST", "/internal/auth/seller-role-sync"));
        assertThrows(IllegalArgumentException.class, () -> new InternalPairVerifier.Credentials(
                "A".repeat(31) + malformed, null, null));
    }

    private void assertFailure(int status, String code, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(InternalPairVerifier.Failure.class, operation);
        assertEquals(status, failure.status());
        assertEquals(code, failure.code());
    }
}
