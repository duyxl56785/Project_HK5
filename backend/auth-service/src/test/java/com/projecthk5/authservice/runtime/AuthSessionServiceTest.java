package com.projecthk5.authservice.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountPersistence;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.persistence.RefreshFamily;
import com.projecthk5.authservice.persistence.RefreshFamilyRepository;
import com.projecthk5.authservice.persistence.RefreshToken;
import com.projecthk5.authservice.persistence.RefreshTokenRepository;
import com.projecthk5.authservice.security.AuthPasswordHasher;
import com.projecthk5.authservice.security.CurrentAccountAuthority;
import com.projecthk5.authservice.security.Hk5JwtCodec;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AuthSessionServiceTest {
    private final UUID id = UUID.fromString("c3780d12-b47b-4b00-aeb2-c3f6237c414e");
    private final Instant now = Instant.parse("2026-10-26T05:00:00Z");
    private AccountRepository accounts;
    private AccountPersistence persistence;
    private RefreshFamilyRepository families;
    private RefreshTokenRepository tokens;
    private CurrentAccountAuthority authority;
    private Hk5JwtCodec jwt;
    private AuthPasswordHasher hasher;
    private AuthSessionService service;
    private Account customer;

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        persistence = mock(AccountPersistence.class);
        families = mock(RefreshFamilyRepository.class);
        tokens = mock(RefreshTokenRepository.class);
        hasher = new AuthPasswordHasher();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        authority = new CurrentAccountAuthority(accounts, "ACTIVE");
        jwt = new Hk5JwtCodec("https://auth.example.invalid", "smart-ecommerce-api", "test-kid",
                Map.of("test-kid", new SecretKeySpec(new byte[32], "HmacSHA256")), clock);
        customer = new Account(id, "customer@example.invalid",
                hasher.hashForRegistration("valid-password-2026"), "ACTIVE",
                Set.of(Account.Role.CUSTOMER), now);
        service = new AuthSessionService(accounts, persistence, families, tokens,
                authority, jwt, hasher, clock, new SecureRandom());
    }

    @Test
    void registrationCreatesActiveCustomerOnlyAndDurablePersistence() {
        when(accounts.findByEmail("new@example.invalid")).thenReturn(Optional.empty());
        var result = service.register(" New@Example.Invalid ", "registration-password-2026");
        var capture = ArgumentCaptor.forClass(Account.class);
        verify(persistence).create(capture.capture());
        assertEquals("ACTIVE", capture.getValue().getStatus());
        assertEquals("new@example.invalid", capture.getValue().getEmail());
        assertEquals(Set.of(Account.Role.CUSTOMER), capture.getValue().getRoles());
        assertEquals("new@example.invalid", result.email());
        assertEquals(Set.of(Account.Role.CUSTOMER), result.roles());
        assertFalse(result.userId().isBlank());
        assertNotEquals("registration-password-2026", capture.getValue().getPasswordHash());
    }

    @Test
    void rejectsInvalidRegistrationAndDuplicateWithoutPersisting() {
        when(accounts.findByEmail("existing@example.invalid")).thenReturn(Optional.of(customer));
        var duplicate = assertThrows(AuthSessionService.AuthFailure.class,
                () -> service.register("existing@example.invalid", "registration-password-2026"));
        assertEquals(409, duplicate.httpStatus());
        assertEquals("AUTH_EMAIL_UNAVAILABLE", duplicate.code());
        var badPass = assertThrows(AuthSessionService.AuthFailure.class,
                () -> service.register("new@example.invalid", "abcdefghijk\u0000"));
        assertEquals(400, badPass.httpStatus());
        assertEquals("VALIDATION_FAILED", badPass.code());
        var badEmail = assertThrows(AuthSessionService.AuthFailure.class,
                () -> service.register("not an email", "registration-password-2026"));
        assertEquals(400, badEmail.httpStatus());
        verifyNoInteractions(persistence);
    }

    @Test
    void loginUsesVerifiedCurrentAccountBeforePersistingRefreshFamily() {
        when(accounts.findByEmail("customer@example.invalid")).thenReturn(Optional.of(customer));
        when(accounts.findById(id)).thenReturn(Optional.of(customer));
        var session = service.login("Customer@Example.Invalid", "valid-password-2026");
        var principal = jwt.verify(session.accessToken());
        assertEquals(id.toString(), principal.subject());
        assertEquals(Set.of(Account.Role.CUSTOMER), principal.tokenRoles());
        assertEquals("Bearer", session.tokenType());
        assertEquals(900L, session.expiresIn());
        assertEquals(43, session.refreshToken().length());
        assertEquals(now.plusSeconds(604800), session.familyExpiresAt());
        var f = ArgumentCaptor.forClass(RefreshFamily.class);
        var t = ArgumentCaptor.forClass(RefreshToken.class);
        verify(families).save(f.capture());
        verify(tokens).save(t.capture());
        assertEquals(f.getValue().getId(), t.getValue().getFamilyId());
        assertEquals(session.familyExpiresAt(), t.getValue().getExpiresAt());
        assertNull(t.getValue().getParentId());
        assertFalse(t.getValue().getTokenHash().contains(session.refreshToken()));
    }

    @Test
    void disabledAndInvalidCredentialUseSameGeneric401AndNeverCreateFamily() {
        when(accounts.findByEmail("customer@example.invalid")).thenReturn(Optional.of(customer));
        when(accounts.findById(id)).thenReturn(Optional.of(customer));
        var wrong = assertThrows(AuthSessionService.AuthFailure.class,
                () -> service.login("customer@example.invalid", "wrong-password"));
        assertEquals("AUTH_INVALID_CREDENTIALS", wrong.code());
        Account disabled = new Account(id, "customer@example.invalid", customer.getPasswordHash(),
                "DISABLED", Set.of(Account.Role.CUSTOMER), now);
        when(accounts.findByEmail("customer@example.invalid")).thenReturn(Optional.of(disabled));
        when(accounts.findById(id)).thenReturn(Optional.of(disabled));
        var inactive = assertThrows(AuthSessionService.AuthFailure.class,
                () -> service.login("customer@example.invalid", "valid-password-2026"));
        assertEquals(401, inactive.httpStatus());
        assertEquals(wrong.code(), inactive.code());
        verifyNoInteractions(families, tokens);
    }

    @Test
    void refreshConsumesOnceAndReplayedTokenRevokesFamily() {
        when(accounts.findByEmail("customer@example.invalid")).thenReturn(Optional.of(customer));
        when(accounts.findById(id)).thenReturn(Optional.of(customer));
        AtomicReference<RefreshFamily> familyRef = new AtomicReference<>();
        AtomicReference<RefreshToken> rootRef = new AtomicReference<>();
        when(families.save(any(RefreshFamily.class))).thenAnswer(invocation -> {
            RefreshFamily value = invocation.getArgument(0);
            familyRef.set(value);
            return value;
        });
        when(tokens.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken value = invocation.getArgument(0);
            if (rootRef.get() == null) rootRef.set(value);
            return value;
        });
        var session = service.login("customer@example.invalid", "valid-password-2026");
        when(families.lockByTokenHash(anyString())).thenAnswer(v -> Optional.of(familyRef.get()));
        when(tokens.findByTokenHash(anyString())).thenAnswer(v -> Optional.of(rootRef.get()));
        when(tokens.consume(eq(rootRef.get().getId()), eq(now))).thenReturn(1);
        var rotated = service.refresh(session.refreshToken());
        assertTrue(rotated.accepted());
        assertNotEquals(session.refreshToken(), rotated.session().refreshToken());
        assertEquals(session.familyExpiresAt(), rotated.session().familyExpiresAt());
        assertEquals(1, rotated.session().account().roles().size());
        ReflectionTestUtils.setField(rootRef.get(), "usedAt", now);
        assertFalse(service.refresh(session.refreshToken()).accepted());
        assertEquals(now, familyRef.get().getRevokedAt());
    }

    @Test
    void logoutRevokesMatchingFamilyAndUnknownIsIdempotent() {
        service.logout(null);
        service.logout("not-a-valid-token");
        verifyNoInteractions(families);
        String token = "a".repeat(43);
        RefreshFamily family = new RefreshFamily(UUID.randomUUID(), id, now, now.plusSeconds(604800));
        when(families.lockByTokenHash(anyString())).thenReturn(Optional.of(family));
        service.logout(token);
        assertEquals(now, family.getRevokedAt());
    }

    @Test
    void meRequiresCryptographicProofAndCurrentDbRoles() {
        when(accounts.findById(id)).thenReturn(Optional.of(customer));
        String token = jwt.issue(authority.requireCurrentForIssue(id));
        var account = service.me(token);
        assertEquals(id.toString(), account.userId());
        assertEquals(Set.of(Account.Role.CUSTOMER), account.roles());
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
                () -> service.me(token + "tampered"));
        Account disabled = new Account(id, "customer@example.invalid", customer.getPasswordHash(),
                "DISABLED", Set.of(Account.Role.CUSTOMER), now);
        when(accounts.findById(id)).thenReturn(Optional.of(disabled));
        assertEquals(401, assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> service.me(token)).httpStatus());
    }
}
