package com.projecthk5.authservice.service;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountPersistence;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.persistence.RefreshFamily;
import com.projecthk5.authservice.persistence.RefreshFamilyRepository;
import com.projecthk5.authservice.persistence.RefreshToken;
import com.projecthk5.authservice.persistence.RefreshTokenRepository;
import com.projecthk5.security.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final String ACTIVE = "ACTIVE";
    static final Duration FAMILY_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();
    // PBKDF2 hashes the full password (no 72-byte bcrypt truncation), satisfying the 128-character rule.
    private final PasswordEncoder passwords = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String dummyHash = passwords.encode("timing-equalizer-not-a-password");
    private final AccountRepository accounts;
    private final AccountPersistence persistence;
    private final RefreshFamilyRepository families;
    private final RefreshTokenRepository tokens;
    private final AccessTokenIssuer issuer;

    public AuthService(AccountRepository accounts, AccountPersistence persistence, RefreshFamilyRepository families,
                       RefreshTokenRepository tokens, AccessTokenIssuer issuer) {
        this.accounts = accounts;
        this.persistence = persistence;
        this.families = families;
        this.tokens = tokens;
        this.issuer = issuer;
    }

    public record Session(Account account, String accessToken, String refreshToken, Instant refreshExpiresAt) {}

    /** Refresh outcome; a rejected attempt still commits its revocations (no rollback on 401). */
    public record RefreshResult(Session session) {
        public boolean rejected() { return session == null; }
    }

    public Account register(String email, String password) {
        return create(email, password, Set.of(Account.Role.CUSTOMER));
    }

    public Account create(String email, String password, Set<Account.Role> roles) {
        String normalized = Account.normalizeEmail(email);
        if (accounts.findByEmail(normalized).isPresent()) throw emailUnavailable();
        Account account = new Account(UUID.randomUUID(), normalized, passwords.encode(password), ACTIVE, roles, Instant.now());
        try {
            persistence.create(account);
        } catch (DataIntegrityViolationException duplicate) {
            throw emailUnavailable();
        }
        return account;
    }

    @Transactional
    public Session login(String email, String password) {
        Optional<Account> found = accounts.findByEmailNormalized(email);
        // Always run one hash verification so unknown emails cost the same as wrong passwords.
        boolean matches = passwords.matches(password, found.map(Account::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches || !ACTIVE.equals(found.get().getStatus()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Email or password is incorrect");
        Account account = found.get();
        Instant now = Instant.now();
        RefreshFamily family = families.save(new RefreshFamily(UUID.randomUUID(), account.getId(), now, now.plus(FAMILY_TTL)));
        return session(account, family, null, now);
    }

    @Transactional
    public RefreshResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return new RefreshResult(null);
        String hash = hash(rawToken);
        Optional<RefreshFamily> locked = families.lockByTokenHash(hash);
        if (locked.isEmpty()) return new RefreshResult(null);
        RefreshFamily family = locked.get();
        RefreshToken token = tokens.findByTokenHash(hash).orElseThrow();
        Instant now = Instant.now();
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            // Replay of a rotated token: revoke the whole family (RFC 9700 reuse detection).
            family.revoke(now);
            return new RefreshResult(null);
        }
        if (family.getRevokedAt() != null || !now.isBefore(family.getExpiresAt())) return new RefreshResult(null);
        Account account = accounts.findById(family.getAccountId()).orElse(null);
        if (account == null || !ACTIVE.equals(account.getStatus())) {
            family.revoke(now);
            return new RefreshResult(null);
        }
        token.markUsed(now);
        tokens.saveAndFlush(token);
        return new RefreshResult(session(account, family, token.getId(), now));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        families.lockByTokenHash(hash(rawToken)).ifPresent(family -> family.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public Account current(String subject) {
        UUID id;
        try { id = UUID.fromString(subject); } catch (IllegalArgumentException e) { id = null; }
        Account account = id == null ? null : accounts.findById(id).orElse(null);
        if (account == null || !ACTIVE.equals(account.getStatus()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_INVALID", "Account is no longer valid");
        return account;
    }

    /** Idempotent role change requested by a verified internal pair (Seller approval/suspension). */
    @Transactional
    public Account changeRole(UUID accountId, Account.Role role, boolean grant) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("AUTH_ACCOUNT_NOT_FOUND", "Account not found"));
        if (grant) account.grant(role); else account.revoke(role);
        return accounts.saveAndFlush(account);
    }

    private Session session(Account account, RefreshFamily family, UUID parentId, Instant now) {
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        tokens.saveAndFlush(new RefreshToken(UUID.randomUUID(), family.getId(), hash(raw), parentId, now, family.getExpiresAt()));
        String access = issuer.issue(account.getId(), account.getRoles().stream().map(Enum::name).toList());
        return new Session(account, access, raw, family.getExpiresAt());
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException emailUnavailable() {
        return ApiException.conflict("AUTH_EMAIL_UNAVAILABLE", "Email cannot be registered");
    }
}
