package com.projecthk5.authservice.service;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Stale, replayed or reordered Seller events must never change the SELLER role. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "TV1_DATABASE_TESTS", matches = "true")
class SellerRoleRevisionDatabaseTest {
    private static final String SERVICE = "http://127.0.0.1:1";
    @Autowired
    private AuthService auth;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private WriteReservations reservations;
    @Autowired
    private JdbcTemplate sql;

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
    void reservationReflectsTheAccountRightNow() {
        UUID id = account();
        assertFalse(hasSeller(id));
        auth.changeSellerRole(id, true, 1);
        assertTrue(hasSeller(id));
        assertTrue(reservations.reserve(UUID.randomUUID().toString(), List.of(), SERVICE).isEmpty());
        assertTrue(reservations.reserve("not-a-uuid", List.of(), SERVICE).isEmpty());
    }

    @Test
    void disabledAccountCannotReserve() {
        UUID id = account();
        sql.update("update accounts set status = 'DISABLED' where id = ?", id);
        assertTrue(reservations.reserve(id.toString(), List.of("CUSTOMER"), SERVICE).isEmpty());
    }

    @Test
    void revocationAbortsReservedWritesThroughTheirService() throws Exception {
        UUID id = account();
        auth.changeSellerRole(id, true, 1);
        HttpServer service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> closed = new CopyOnWriteArrayList<>();
        service.createContext("/internal/write-fences/", exchange -> {
            closed.add(exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst(InternalTokens.HEADER));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        service.start();
        String url = "http://127.0.0.1:" + service.getAddress().getPort();
        var live = new WriteReservations(accounts, sql, "pair@" + url);
        UUID reservation = reservations.reserve(id.toString(), List.of("SELLER"), url).orElseThrow();
        auth.changeSellerRole(id, false, 2);
        try {
            live.closeAll(id);
        } finally {
            service.stop(0);
        }
        assertEquals(List.of("/internal/write-fences/" + reservation + "/close pair"), closed);
        assertEquals(0, held(reservation));
    }

    @Test
    void unreachableServiceKeepsTheReservationSoTheRelayRetries() {
        UUID id = account();
        UUID reservation = reservations.reserve(id.toString(), List.of(), SERVICE).orElseThrow();
        var live = new WriteReservations(accounts, sql, "pair@" + SERVICE);
        assertThrows(ApiException.class, () -> live.closeAll(id));
        assertEquals(1, held(reservation));
    }

    @Test
    void revocationAndReservationNeverInterleave() {
        for (int round = 0; round < 10; round++) {
            UUID id = account();
            auth.changeSellerRole(id, true, 1);
            var reserve = CompletableFuture.supplyAsync(() -> reservations.reserve(id.toString(), List.of("SELLER"), SERVICE));
            var revoke = CompletableFuture.runAsync(() -> auth.changeSellerRole(id, false, 2));
            var reserved = reserve.join();
            revoke.join();
            assertEquals(reserved.isPresent(), sql.queryForObject(
                    "select count(*) from write_reservations where account_id = ?", Integer.class, id) == 1);
            assertFalse(hasSeller(id));
        }
    }

    private int held(UUID reservation) {
        return sql.queryForObject("select count(*) from write_reservations where id = ?", Integer.class, reservation);
    }

    private boolean hasSeller(UUID id) {
        return sql.queryForObject("select count(*) from account_role_grants where account_id = ? and role = 'SELLER'",
                Integer.class, id) == 1;
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        accounts.saveAndFlush(new Account(id, id + "@test.local", "hash", "ACTIVE", Set.of(Account.Role.CUSTOMER), Instant.now()));
        return id;
    }
}
