package com.projecthk5.authservice.runtime;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in Auth business core. NO controller or public route is registered by this class.
 * Requires reviewed Auth bootstrap and explicit HTTP/security gates before activation.
 */
@Service
@ConditionalOnProperty(name = "hk5.auth.http.enabled", havingValue = "true")
public class AuthSessionService {
    public static final long REFRESH_FAMILY_SECONDS = 604800L;
    private final AccountRepository accounts;
    private final AccountPersistence persistence;
    private final RefreshFamilyRepository families;
    private final RefreshTokenRepository tokens;
    private final CurrentAccountAuthority authority;
    private final Hk5JwtCodec jwt;
    private final AuthPasswordHasher hasher;
    private final Clock clock;
    private final SecureRandom random;
    private final String dummyHash;

    @Autowired
    public AuthSessionService(AccountRepository accounts, AccountPersistence persistence,
            RefreshFamilyRepository families, RefreshTokenRepository tokens,
            CurrentAccountAuthority authority, Hk5JwtCodec jwt) {
        this(accounts, persistence, families, tokens, authority, jwt,
                new AuthPasswordHasher(), Clock.systemUTC(), new SecureRandom());
    }

    // Package-private injection for deterministic tests; never a second production bean.
    AuthSessionService(AccountRepository accounts, AccountPersistence persistence,
            RefreshFamilyRepository families, RefreshTokenRepository tokens,
            CurrentAccountAuthority authority, Hk5JwtCodec jwt,
            AuthPasswordHasher hasher, Clock clock, SecureRandom random) {
        this.accounts = Objects.requireNonNull(accounts);
        this.persistence = Objects.requireNonNull(persistence);
        this.families = Objects.requireNonNull(families);
        this.tokens = Objects.requireNonNull(tokens);
        this.authority = Objects.requireNonNull(authority);
        this.jwt = Objects.requireNonNull(jwt);
        this.hasher = Objects.requireNonNull(hasher);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.dummyHash = hasher.hashForRegistration("fixture-dummy-not-a-user-password");
    }

    public record AccountView(String userId, String email, Set<Account.Role> roles) {
        public AccountView {
            roles = Set.copyOf(roles);
        }
    }
    public record SessionView(String accessToken, String tokenType, long expiresIn,
                              AccountView account, String refreshToken, Instant familyExpiresAt) {}
    public record RefreshResult(boolean accepted, SessionView session) {
        public static RefreshResult invalid() { return new RefreshResult(false, null); }
        public static RefreshResult valid(SessionView session) { return new RefreshResult(true, session); }
    }
    public static final class AuthFailure extends RuntimeException {
        private final int httpStatus;
        private final String code;
        private AuthFailure(int httpStatus, String code) {
            super(code);
            this.httpStatus = httpStatus;
            this.code = code;
        }
        public int httpStatus() { return httpStatus; }
        public String code() { return code; }
    }

    /** The durable profile provisioning task is committed atomically with the account. */
    @Transactional
    public AccountView register(String rawEmail, String password) {
        String email = validatedEmail(rawEmail);
        String hash;
        try {
            hash = hasher.hashForRegistration(password);
        } catch (IllegalArgumentException invalid) {
            throw new AuthFailure(400, "VALIDATION_FAILED");
        }
        try {
            if (accounts.findByEmail(email).isPresent()) {
                throw new AuthFailure(409, "AUTH_EMAIL_UNAVAILABLE");
            }
            Account account = new Account(UUID.randomUUID(), email, hash, "ACTIVE",
                    Set.of(Account.Role.CUSTOMER), clock.instant());
            persistence.create(account); // flush inside same local transaction, includes durable task
            return view(account, account.getRoles());
        } catch (DataIntegrityViolationException conflict) {
            // DB unique constraint is the authority for racing registration attempts.
            throw new AuthFailure(409, "AUTH_EMAIL_UNAVAILABLE");
        }
    }

    /** Credential checks and creation of one refresh family are one local transaction. */
    @Transactional
    public SessionView login(String rawEmail, String password) {
        String email = loginEmail(rawEmail);
        Account account;
        try {
            account = email == null ? null : accounts.findByEmail(email).orElse(null);
            String record = account == null ? dummyHash : account.getPasswordHash();
            if (!hasher.verifyForLogin(password, record) || account == null) {
                throw invalidCredentials();
            }
            CurrentAccountAuthority.CurrentAccount current = authority.requireCurrentForIssue(account.getId());
            return createFamilySession(account, current, clock.instant());
        } catch (CurrentAccountAuthority.DecisionFailure failure) {
            if (failure.httpStatus() == 503) throw new AuthFailure(503, failure.code());
            throw invalidCredentials();
        }
    }

    /**
     * A used refresh token revokes the entire family and RETURNS a negative result
     * without throwing: throwing would roll back the security-critical revocation.
     */
    @Transactional
    public RefreshResult refresh(String rawToken) {
        if (!validTokenText(rawToken)) return RefreshResult.invalid();
        String hash = sha256(rawToken);
        Optional<RefreshFamily> locked = families.lockByTokenHash(hash);
        if (locked.isEmpty()) return RefreshResult.invalid();
        RefreshFamily family = locked.get();
        RefreshToken prior = tokens.findByTokenHash(hash).orElse(null);
        if (prior == null || !prior.getFamilyId().equals(family.getId())) return RefreshResult.invalid();
        Instant now = clock.instant();
        if (prior.getUsedAt() != null) {
            family.revoke(now); // commit even though HTTP adapter returns 401
            return RefreshResult.invalid();
        }
        if (family.getRevokedAt() != null || !now.isBefore(family.getExpiresAt())
                || now.isBefore(family.getCreatedAt())
                || prior.getRevokedAt() != null || !now.isBefore(prior.getExpiresAt())
                || now.isBefore(prior.getCreatedAt())) {
            return RefreshResult.invalid();
        }
        CurrentAccountAuthority.CurrentAccount current;
        try {
            current = authority.requireCurrentForIssue(family.getAccountId());
        } catch (CurrentAccountAuthority.DecisionFailure rejected) {
            if (rejected.httpStatus() == 503) throw new AuthFailure(503, rejected.code());
            return RefreshResult.invalid();
        }
        if (tokens.consume(prior.getId(), now) != 1) {
            family.revoke(now);
            return RefreshResult.invalid();
        }
        Account account = accounts.findById(family.getAccountId()).orElseThrow(AuthSessionService::invalidRefresh);
        String rawChild = generateRefreshToken();
        tokens.save(new RefreshToken(UUID.randomUUID(), family.getId(), sha256(rawChild),
                prior.getId(), now, family.getExpiresAt()));
        return RefreshResult.valid(new SessionView(jwt.issue(current), "Bearer",
                Hk5JwtCodec.ACCESS_SECONDS, view(account, current.roles()), rawChild, family.getExpiresAt()));
    }

    /** Missing/revoked/unknown tokens are idempotent; DB outages must propagate. */
    @Transactional
    public void logout(String rawToken) {
        if (!validTokenText(rawToken)) return;
        families.lockByTokenHash(sha256(rawToken)).ifPresent(family -> family.revoke(clock.instant()));
    }

    /** A verified JWT alone is insufficient: current Auth DB state is checked again. */
    @Transactional(readOnly = true)
    public AccountView me(String bearerToken) {
        Hk5JwtCodec.VerifiedPrincipal principal = jwt.verify(bearerToken);
        CurrentAccountAuthority.CurrentAccount current = authority.requireCurrentForRequest(principal);
        Account account = accounts.findById(current.accountId())
                .orElseThrow(() -> new AuthFailure(401, "AUTH_ACCESS_INVALID"));
        return view(account, current.roles());
    }

    private SessionView createFamilySession(Account account,
            CurrentAccountAuthority.CurrentAccount current, Instant now) {
        Instant expires = now.plusSeconds(REFRESH_FAMILY_SECONDS);
        UUID familyId = UUID.randomUUID();
        String rawToken = generateRefreshToken();
        families.save(new RefreshFamily(familyId, account.getId(), now, expires));
        tokens.save(new RefreshToken(UUID.randomUUID(), familyId, sha256(rawToken), null, now, expires));
        return new SessionView(jwt.issue(current), "Bearer", Hk5JwtCodec.ACCESS_SECONDS,
                view(account, current.roles()), rawToken, expires);
    }

    private String generateRefreshToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } finally {
            java.util.Arrays.fill(value, (byte) 0);
        }
    }

    private static boolean validTokenText(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9_-]{43}");
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.US_ASCII));
            try { return HexFormat.of().formatHex(digest); }
            finally { java.util.Arrays.fill(digest, (byte) 0); }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static AccountView view(Account account, Set<Account.Role> roles) {
        return new AccountView(account.getId().toString(), account.getEmail(), roles);
    }

    private static String validatedEmail(String raw) {
        String normalized = loginEmail(raw);
        if (normalized == null || normalized.length() > 254) {
            throw new AuthFailure(400, "VALIDATION_FAILED");
        }
        return normalized;
    }

    private static String loginEmail(String raw) {
        if (raw == null || raw.length() > 254) return null;
        String email = Account.normalizeEmail(raw);
        int at = email.indexOf('@');
        if (at < 1 || at != email.lastIndexOf('@') || at + 1 >= email.length()) return null;
        String domain = email.substring(at + 1);
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) return null;
        if (email.chars().anyMatch(Character::isWhitespace)) return null;
        if (email.contains("/") || email.contains("\\") || email.contains("\"")) return null;
        return email;
    }

    private static AuthFailure invalidCredentials() {
        return new AuthFailure(401, "AUTH_INVALID_CREDENTIALS");
    }

    private static AuthFailure invalidRefresh() {
        return new AuthFailure(401, "AUTH_REFRESH_INVALID");
    }
}
