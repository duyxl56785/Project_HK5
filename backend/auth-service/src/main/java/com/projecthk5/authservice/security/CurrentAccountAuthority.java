package com.projecthk5.authservice.security;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import jakarta.persistence.PersistenceException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth DB is the authority for account state and granted roles, not JWT role claims or headers.
 * Intentionally not autowired until Auth API and transaction-managed runtime are implemented.
 */
public class CurrentAccountAuthority {
    private final AccountRepository accounts;
    private final String activeStatus;

    /**
     * Status vocabulary was deliberately not fixed by V1 schema. Integration MUST provide
     * the explicit, reviewed canonical active state, without guessing a database default.
     */
    public CurrentAccountAuthority(AccountRepository accounts, String activeStatus) {
        this.accounts = Objects.requireNonNull(accounts);
        if (activeStatus == null || activeStatus.isBlank()) {
            throw new IllegalArgumentException("Explicit active account status is required");
        }
        this.activeStatus = activeStatus;
    }

    /** Call after credential verification, before creating an access JWT. */
    @Transactional(readOnly = true)
    public CurrentAccount requireCurrentForIssue(UUID accountId) {
        if (accountId == null) throw DecisionFailure.accessInvalid();
        return loadCurrent(accountId);
    }

    /**
     * Call only with a VerifiedPrincipal returned by a signature-validating Hk5JwtCodec.
     * A stale token role never adds privileges; current DB role AND token role are required.
     */
    @Transactional(readOnly = true)
    public CurrentAccount requireCurrentForRequest(
            Hk5JwtCodec.VerifiedPrincipal principal, Account.Role requiredRole) {
        if (principal == null) throw DecisionFailure.accessInvalid();
        if (requiredRole == null) throw new IllegalArgumentException("Required role missing");
        UUID accountId;
        try {
            accountId = UUID.fromString(principal.subject());
            if (!accountId.toString().equals(principal.subject())) throw DecisionFailure.accessInvalid();
        } catch (IllegalArgumentException invalid) {
            throw DecisionFailure.accessInvalid();
        }
        CurrentAccount current = loadCurrent(accountId);
        if (!principal.tokenRoles().contains(requiredRole) || !current.roles().contains(requiredRole)) {
            throw DecisionFailure.forbidden();
        }
        return current;
    }

    /**
     * Used by /me only after Hk5JwtCodec verified the access JWT. The JWT must
     * share at least one role with current DB grants; token claims cannot elevate.
     * Returned effective roles are strictly JWT-signed roles INTERSECT current DB grants.
     */
    @Transactional(readOnly = true)
    public CurrentAccount requireCurrentForRequest(Hk5JwtCodec.VerifiedPrincipal principal) {
        if (principal == null) throw DecisionFailure.accessInvalid();
        UUID accountId;
        try {
            accountId = UUID.fromString(principal.subject());
            if (!accountId.toString().equals(principal.subject())) throw DecisionFailure.accessInvalid();
        } catch (IllegalArgumentException badSubject) {
            throw DecisionFailure.accessInvalid();
        }
        CurrentAccount current = loadCurrent(accountId);
        var effectiveRoles = java.util.EnumSet.copyOf(current.roles());
        effectiveRoles.retainAll(principal.tokenRoles());
        if (effectiveRoles.isEmpty()) {
            throw DecisionFailure.forbidden();
        }
        // Never expose newly granted DB roles from a JWT issued before promotion.
        return new CurrentAccount(accountId, Set.copyOf(effectiveRoles));
    }

    private CurrentAccount loadCurrent(UUID id) {
        try {
            Account account = accounts.findById(id).orElseThrow(DecisionFailure::accessInvalid);
            if (!activeStatus.equals(account.getStatus())) throw DecisionFailure.accessInvalid();
            Set<Account.Role> granted = account.getRoles();
            if (granted.isEmpty()) throw DecisionFailure.accessInvalid();
            return new CurrentAccount(account.getId(), Set.copyOf(granted));
        } catch (DataAccessException | PersistenceException unavailable) {
            // Repository calls translate failures; lazy role loading may raise JPA failures directly.
            // Never turn either read failure into an allow, unknown user, or false 403.
            throw DecisionFailure.unavailable();
        }
    }

    /** Only created after the authoritative account+role read. */
    public static final class CurrentAccount {
        private final UUID accountId;
        private final Set<Account.Role> roles;
        private CurrentAccount(UUID accountId, Set<Account.Role> roles) {
            this.accountId = accountId;
            this.roles = roles;
        }
        public UUID accountId() { return accountId; }
        public Set<Account.Role> roles() { return roles; }
    }

    /** Internal decision result; a later HTTP adapter maps this to reviewed 401/403/503. */
    public static final class DecisionFailure extends RuntimeException {
        private final int httpStatus;
        private final String code;
        private DecisionFailure(int status, String code) {
            super(code);
            this.httpStatus = status;
            this.code = code;
        }
        public int httpStatus() { return httpStatus; }
        public String code() { return code; }
        private static DecisionFailure accessInvalid() { return new DecisionFailure(401, "AUTH_ACCESS_INVALID"); }
        private static DecisionFailure forbidden() { return new DecisionFailure(403, "AUTH_FORBIDDEN"); }
        private static DecisionFailure unavailable() {
            return new DecisionFailure(503, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE");
        }
    }
}
