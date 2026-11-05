package com.projecthk5.authservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** The read check answers from the account as it is now, never from the token's claims. */
class WriteReservationsHoldsTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JdbcTemplate sql = mock(JdbcTemplate.class);
    private final WriteReservations reservations = new WriteReservations(accounts, sql, "");

    @Test
    void activeAccountWithEveryClaimedRoleHoldsAuthority() {
        UUID id = account(AuthService.ACTIVE, Account.Role.CUSTOMER, Account.Role.ADMIN);
        assertTrue(reservations.holds(id.toString(), List.of("CUSTOMER", "ADMIN")));
        verifyNoInteractions(sql);
    }

    @Test
    void revokedRoleOrDisabledAccountNoLongerHoldsIt() {
        UUID revoked = account(AuthService.ACTIVE, Account.Role.CUSTOMER);
        assertFalse(reservations.holds(revoked.toString(), List.of("CUSTOMER", "ADMIN")));
        UUID disabled = account("DISABLED", Account.Role.CUSTOMER);
        assertFalse(reservations.holds(disabled.toString(), List.of("CUSTOMER")));
    }

    @Test
    void unknownOrMalformedSubjectHoldsNothing() {
        when(accounts.findById(any())).thenReturn(Optional.empty());
        assertFalse(reservations.holds(UUID.randomUUID().toString(), List.of()));
        assertFalse(reservations.holds("not-a-uuid", List.of()));
    }

    private UUID account(String status, Account.Role... roles) {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.of(
                new Account(id, id + "@test.local", "hash", status, Set.of(roles), Instant.now())));
        return id;
    }
}
