package com.projecthk5.authservice.service;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Stale, replayed or reordered Seller events must never change the SELLER role. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "TV1_DATABASE_TESTS", matches = "true")
class SellerRoleRevisionDatabaseTest {
    @Autowired
    private AuthService auth;
    @Autowired
    private AccountRepository accounts;

    @Test
    void staleGrantCannotUndoANewerRevocation() {
        UUID id = account();
        auth.changeSellerRole(id, true, 1);
        auth.changeSellerRole(id, false, 2);
        auth.changeSellerRole(id, true, 1);
        assertFalse(hasSeller(id));
    }

    @Test
    void replayOfTheCurrentRevisionChangesNothing() {
        UUID id = account();
        auth.changeSellerRole(id, true, 5);
        auth.changeSellerRole(id, true, 5);
        assertTrue(hasSeller(id));
        auth.changeSellerRole(id, false, 5);
        assertTrue(hasSeller(id));
    }

    @Test
    void reinstatementWithANewerRevisionGrantsAgain() {
        UUID id = account();
        auth.changeSellerRole(id, false, 3);
        auth.changeSellerRole(id, true, 4);
        assertTrue(hasSeller(id));
    }

    @Test
    void concurrentOutOfOrderDeliveriesConvergeOnTheHighestRevision() {
        for (int round = 0; round < 10; round++) {
            UUID id = account();
            List<CompletableFuture<Void>> deliveries = List.of(
                    CompletableFuture.runAsync(() -> auth.changeSellerRole(id, true, 1)),
                    CompletableFuture.runAsync(() -> auth.changeSellerRole(id, false, 3)),
                    CompletableFuture.runAsync(() -> auth.changeSellerRole(id, true, 2)));
            deliveries.forEach(CompletableFuture::join);
            assertFalse(hasSeller(id));
        }
    }

    @Test
    void authorityReflectsTheAccountRightNow() {
        UUID id = account();
        assertEquals(Set.of(Account.Role.CUSTOMER), auth.liveRoles(id.toString()).orElseThrow());
        auth.changeSellerRole(id, true, 1);
        assertTrue(auth.liveRoles(id.toString()).orElseThrow().contains(Account.Role.SELLER));
        assertTrue(auth.liveRoles(UUID.randomUUID().toString()).isEmpty());
        assertTrue(auth.liveRoles("not-a-uuid").isEmpty());
    }

    private boolean hasSeller(UUID id) {
        return auth.liveRoles(id.toString()).orElseThrow().contains(Account.Role.SELLER);
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        accounts.saveAndFlush(new Account(id, id + "@test.local", "hash", "ACTIVE", Set.of(Account.Role.CUSTOMER), Instant.now()));
        return id;
    }
}
