package com.projecthk5.authservice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class CurrentAccountAuthorityTest {
    private final UUID id = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    private final Instant now = Instant.parse("2026-10-23T01:00:00Z");

    private Hk5JwtCodec jwt() {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) 17);
        return new Hk5JwtCodec("https://identity.example.invalid", "smart-ecommerce-api",
                "only", Map.of("only", new SecretKeySpec(bytes, "HmacSHA256")),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private Account account(String status, Set<Account.Role> roles) {
        return new Account(id, "customer@example.invalid", "fixture-hash",
                status, roles, now);
    }

    @Test
    void staleJwtAdminIsDeniedOnceCurrentGrantIsRemoved() {
        var repo = mock(AccountRepository.class);
        when(repo.findById(id)).thenReturn(
                Optional.of(account("ACTIVE", Set.of(Account.Role.CUSTOMER, Account.Role.ADMIN))),
                Optional.of(account("ACTIVE", Set.of(Account.Role.CUSTOMER))));
        var guard = new CurrentAccountAuthority(repo, "ACTIVE");
        var jwt = jwt();
        String signed = jwt.issue(guard.requireCurrentForIssue(id));
        var token = jwt.verify(signed);
        var failure = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForRequest(token, Account.Role.ADMIN));
        assertEquals(403, failure.httpStatus());
        assertEquals("AUTH_FORBIDDEN", failure.code());
    }

    @Test
    void currentGrantCannotElevateAnOldTokenMissingTheRole() {
        var repo = mock(AccountRepository.class);
        when(repo.findById(id)).thenReturn(
                Optional.of(account("ACTIVE", Set.of(Account.Role.CUSTOMER))),
                Optional.of(account("ACTIVE", Set.of(Account.Role.CUSTOMER, Account.Role.SELLER))));
        var guard = new CurrentAccountAuthority(repo, "ACTIVE");
        var token = jwt().verify(jwt().issue(guard.requireCurrentForIssue(id)));
        var failure = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForRequest(token, Account.Role.SELLER));
        assertEquals(403, failure.httpStatus());
    }

    @Test
    void inactiveOrMissingAccountReturns401ButDependencyFailureReturns503() {
        var repo = mock(AccountRepository.class);
        var guard = new CurrentAccountAuthority(repo, "ACTIVE");
        when(repo.findById(id)).thenReturn(Optional.empty());
        var absent = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForIssue(id));
        assertEquals(401, absent.httpStatus());
        when(repo.findById(id)).thenReturn(Optional.of(account("SUSPENDED", Set.of(Account.Role.CUSTOMER))));
        var suspended = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForIssue(id));
        assertEquals(401, suspended.httpStatus());
        when(repo.findById(id)).thenThrow(new DataAccessResourceFailureException("fixture database unavailable"));
        var outage = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForIssue(id));
        assertEquals(503, outage.httpStatus());
        assertEquals("AUTHORIZATION_DEPENDENCY_UNAVAILABLE", outage.code());
    }

    @Test
    void activeStatusIsExplicitAndNoRawPrincipalCanBeCreatedFromClientData() {
        var repo = mock(AccountRepository.class);
        assertThrows(IllegalArgumentException.class, () -> new CurrentAccountAuthority(repo, ""));
        var guard = new CurrentAccountAuthority(repo, "ACTIVE");
        var failure = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> guard.requireCurrentForRequest(null, Account.Role.ADMIN));
        assertEquals(401, failure.httpStatus());
    }
    @Test
    void lazyRolesReadExceptionMapsTo503WithoutMaskingInactiveAccounts() {
        var repo = mock(AccountRepository.class);
        var loadedAccount = mock(Account.class);
        when(repo.findById(id)).thenReturn(Optional.of(loadedAccount));
        when(loadedAccount.getStatus()).thenReturn("ACTIVE");
        // JPA lazy collections can query after findById returns, outside exception translation.
        when(loadedAccount.getRoles()).thenThrow(new PersistenceException("fixture role grant SELECT failure"));

        var failure = assertThrows(CurrentAccountAuthority.DecisionFailure.class,
                () -> new CurrentAccountAuthority(repo, "ACTIVE").requireCurrentForIssue(id));
        assertEquals(503, failure.httpStatus());
        assertEquals("AUTHORIZATION_DEPENDENCY_UNAVAILABLE", failure.code());
        verify(loadedAccount).getRoles();
    }


}
