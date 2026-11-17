package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projecthk5.security.ApiException;
import com.projecthk5.sellerservice.voucher.VoucherRedemptionService.Quote;
import com.projecthk5.sellerservice.voucher.VoucherRedemptionService.Redeem;
import com.projecthk5.sellerservice.voucher.VoucherRedemptionService.ReleaseReason;
import com.projecthk5.sellerservice.voucher.VoucherRedemptionService.State;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "hk5.voucher-c2.enabled=true")
@Testcontainers
@Tag("postgresql")
class VoucherRedemptionPostgresIT {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired JdbcTemplate db;
    @Autowired VoucherRedemptionService service;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void migrationUpgradesV10WithoutRewritingExistingVoucherOrFlywayChecksum() {
        String schema = "voucher_c2_upgrade";
        var config = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .defaultSchema(schema).schemas(schema).locations("classpath:db/migration");
        config.target("10").load().migrate();
        Fixture fixture = seed(1, schema + ".");
        Integer checksum = db.queryForObject("SELECT checksum FROM " + schema
                + ".flyway_schema_history WHERE version = '10'", Integer.class);
        Flyway upgraded = config.target("11").load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals(checksum, db.queryForObject("SELECT checksum FROM " + schema
                + ".flyway_schema_history WHERE version = '10'", Integer.class));
        assertEquals("DEMO30", db.queryForObject("SELECT code FROM " + schema + ".vouchers WHERE id = ?",
                String.class, fixture.voucher()));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM " + schema + ".voucher_redemptions", Integer.class));

        // The disposable V7 rehearsal removes these tables together without CASCADE.
        db.execute("DROP TABLE " + schema + ".voucher_redemptions, " + schema + ".voucher_status_history, "
                + schema + ".vouchers, " + schema + ".store_voucher_limits, "
                + schema + ".voucher_limit_request_history, " + schema + ".voucher_limit_requests");
    }

    @Test
    void verifiesStoreAndKeepsImmutableMoneyOnReplayAfterVoucherPause() {
        Fixture first = seed(2);
        Fixture other = seed(2);
        var quote = service.quote(first.store(), "customer", "demo30", 200_000);
        assertEquals(30_000, quote.discountAmount());
        assertEquals(170_000, quote.gross());
        assertEquals("VOUCHER_NOT_FOUND", rejection(() -> service.quote(first.store(), "customer", "MISSING", 200_000)));

        UUID key = UUID.randomUUID();
        assertEquals("VOUCHER_NOT_FOUND", rejection(() -> service.redeem(command(key, other, "customer", quote))));
        var reserved = service.redeem(command(key, first, "customer", quote));
        db.update("UPDATE vouchers SET status = 'PAUSED', version = version + 1 WHERE id = ?", first.voucher());
        assertEquals(reserved, service.redeem(command(key, first, "customer", quote)));
        assertEquals("VOUCHER_CHANGED", rejection(() -> service.redeem(command(UUID.randomUUID(), first, "other", quote))));
        assertEquals("VOUCHER_REDEMPTION_NOT_FOUND", rejection(() -> service.status(other.store(), key)));
        assertEquals(0, rows(other));
        assertEquals(1, rows(first));
    }

    @Test
    void rejectsChangedQuoteOrChangedReplayWithoutCreatingAnotherRow() {
        Fixture fixture = seed(2);
        Quote quote = quote(fixture, "customer");
        UUID key = UUID.randomUUID();
        assertEquals("VOUCHER_CHANGED", rejection(() -> service.redeem(new Redeem(key, fixture.store(), "customer",
                quote.voucherId(), quote.version(), quote.merchandiseAmount(), 1))));
        assertEquals(0, rows(fixture));
        service.redeem(command(key, fixture, "customer", quote));
        assertEquals("VOUCHER_REDEMPTION_KEY_REUSED",
                rejection(() -> service.redeem(command(key, fixture, "different", quote))));
        assertEquals(1, rows(fixture));
    }

    @Test
    void percentCapAndStoreOrderAmountsUseWholeVnd() {
        Fixture store = seed(2);
        Fixture other = seed(2);
        db.update("""
                UPDATE vouchers SET discount_type = 'PERCENT', discount_value = 15, max_discount = 30_000
                WHERE id = ?
                """, store.voucher());
        Quote priced = service.quote(store.store(), "customer", "DEMO30", 199_999);
        Quote otherQuote = service.quote(other.store(), "customer", "DEMO30", 200_000);
        assertEquals(30_000, priced.discountAmount());
        assertEquals(169_999, priced.gross());
        assertEquals(170_000, otherQuote.gross());
        var held = service.redeem(command(UUID.randomUUID(), store, "customer", priced));
        assertEquals(priced, held.quote());
        assertEquals(0, rows(other));
    }

    @Test
    void expiredClosedOrSuspendedCannotRedeemAfterQuote() {
        Fixture ended = seed(2);
        var quote = quote(ended, "customer");
        db.update("UPDATE vouchers SET ends_at = CURRENT_TIMESTAMP - interval '1 minute' WHERE id = ?", ended.voucher());
        assertEquals("VOUCHER_CHANGED",
                rejection(() -> service.redeem(command(UUID.randomUUID(), ended, "customer", quote))));
        assertEquals(0, rows(ended));

        Fixture closed = seed(2);
        var openPrice = quote(closed, "customer");
        db.update("UPDATE stores SET status = 'CLOSED' WHERE id = ?", closed.store());
        assertEquals("VOUCHER_CHANGED",
                rejection(() -> service.redeem(command(UUID.randomUUID(), closed, "customer", openPrice))));
        assertEquals(0, rows(closed));

        Fixture suspended = seed(2);
        var approvedPrice = quote(suspended, "customer");
        db.update("UPDATE seller_profiles SET status = 'SUSPENDED', review_reason = ? WHERE id = ?",
                "Synthetic suspension for voucher eligibility test", suspended.seller());
        assertEquals("VOUCHER_CHANGED",
                rejection(() -> service.redeem(command(UUID.randomUUID(), suspended, "customer", approvedPrice))));
        assertEquals(0, rows(suspended));
    }

    @Test
    void replayLookupAndCommitResolveAnUnobservedReservationWithoutReleasingIt() {
        Fixture fixture = seed(1);
        var request = command(UUID.randomUUID(), fixture, "customer", quote(fixture, "customer"));
        service.redeem(request); // Simulate a caller dropping the completed result.
        assertEquals(State.RESERVED, service.status(fixture.store(), request.sellerOrderId()).state());
        assertEquals("VOUCHER_REDEMPTION_INVALID_STATE", rejection(() ->
                service.release(fixture.store(), request.sellerOrderId(), ReleaseReason.REJECTED_BEFORE_DISPATCH)));
        var committed = service.commit(fixture.store(), request.sellerOrderId());
        assertEquals(State.COMMITTED, committed.state());
        assertEquals(committed, service.commit(fixture.store(), request.sellerOrderId()));
        assertEquals(committed, service.redeem(request));
        assertEquals("VOUCHER_REDEMPTION_INVALID_STATE",
                rejection(() -> service.release(fixture.store(), request.sellerOrderId(), ReleaseReason.CHECKOUT_FAILED)));
        assertEquals(1, rows(fixture));
    }

    @Test
    void databaseRollbackAfterInsertKeepsTheSlotAvailable() {
        Fixture fixture = seed(1);
        var request = command(UUID.randomUUID(), fixture, "customer", quote(fixture, "customer"));
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertEquals(State.RESERVED, service.redeem(request).state());
            transaction.setRollbackOnly();
        });
        assertEquals(0, rows(fixture));
        assertEquals("VOUCHER_REDEMPTION_NOT_FOUND",
                rejection(() -> service.status(fixture.store(), request.sellerOrderId())));
        assertEquals(State.RESERVED, service.redeem(request).state());
    }

    @Test
    void onlyDefinitiveFailureOrPreDispatchCancellationAndRejectionReleaseSlots() {
        Fixture fixture = seed(1);
        for (ReleaseReason reason : ReleaseReason.values()) {
            var request = command(UUID.randomUUID(), fixture, "customer", quote(fixture, "customer"));
            service.redeem(request);
            if (reason != ReleaseReason.CHECKOUT_FAILED) {
                service.commit(fixture.store(), request.sellerOrderId());
            }
            var released = service.release(fixture.store(), request.sellerOrderId(), reason);
            assertEquals(State.RELEASED, released.state());
            assertEquals(reason, released.releaseReason());
            assertEquals(released, service.release(fixture.store(), request.sellerOrderId(), reason));
            assertEquals(released, service.redeem(request));
            assertEquals("VOUCHER_REDEMPTION_INVALID_STATE",
                    rejection(() -> service.commit(fixture.store(), request.sellerOrderId())));
        }
        assertEquals(3, rows(fixture));
    }

    @Test
    void concurrentDistinctCustomersDoNotOversellTheLastSlot() throws Exception {
        Fixture fixture = seed(1);
        var a = command(UUID.randomUUID(), fixture, "customer-a", quote(fixture, "customer-a"));
        var b = command(UUID.randomUUID(), fixture, "customer-b", quote(fixture, "customer-b"));
        var results = race(() -> result(a), () -> result(b));
        assertTrue(results.containsAll(List.of("RESERVED", "VOUCHER_USAGE_LIMIT_REACHED")), results.toString());
        assertEquals(1, rows(fixture));
    }

    @Test
    void concurrentDifferentOrdersOfSameCustomerOnlyConsumeOneSlot() throws Exception {
        Fixture fixture = seed(2);
        var quote = quote(fixture, "customer");
        var a = command(UUID.randomUUID(), fixture, "customer", quote);
        var b = command(UUID.randomUUID(), fixture, "customer", quote);
        var results = race(() -> result(a), () -> result(b));
        assertTrue(results.containsAll(List.of("RESERVED", "VOUCHER_ALREADY_USED")), results.toString());
        assertEquals(1, rows(fixture));
    }

    @Test
    void concurrentIdenticalReplayConsumesOneSlotAndPrimaryKeyRejectsOtherStore() throws Exception {
        Fixture a = seed(1);
        Fixture b = seed(1);
        UUID key = UUID.randomUUID();
        var request = command(key, a, "customer", quote(a, "customer"));
        assertEquals(List.of("RESERVED", "RESERVED"), race(() -> result(request), () -> result(request)));
        assertEquals("VOUCHER_REDEMPTION_KEY_REUSED", result(command(key, b, "customer", quote(b, "customer"))));

        Fixture c = seed(1);
        Fixture d = seed(1);
        UUID competingKey = UUID.randomUUID();
        var fromC = command(competingKey, c, "customer", quote(c, "customer"));
        var fromD = command(competingKey, d, "customer", quote(d, "customer"));
        assertTrue(race(() -> result(fromC), () -> result(fromD))
                .containsAll(List.of("RESERVED", "VOUCHER_REDEMPTION_KEY_REUSED")));
        assertEquals(1, rows(a));
        assertEquals(0, rows(b));
        assertEquals(1, rows(c) + rows(d));
    }

    @Test
    void databaseRejectsCrossStoreAssociationEvenOutsideService() {
        Fixture fixture = seed(2);
        Fixture other = seed(2);
        var reservation = service.redeem(command(UUID.randomUUID(), fixture, "customer", quote(fixture, "customer")));
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "UPDATE voucher_redemptions SET store_id = ? WHERE seller_order_id = ?",
                other.store(), reservation.sellerOrderId()));
    }

    private List<String> race(Callable<String> left, Callable<String> right) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> synchronizedCall(left, ready, start));
            var b = executor.submit(() -> synchronizedCall(right, ready, start));
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }

    private String synchronizedCall(Callable<String> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        return operation.call();
    }

    private String result(Redeem request) {
        try {
            return service.redeem(request).state().name();
        } catch (ApiException error) {
            return error.code();
        }
    }

    private String rejection(Executable work) {
        return assertThrows(ApiException.class, work).code();
    }

    private int rows(Fixture fixture) {
        return db.queryForObject("SELECT count(*) FROM voucher_redemptions WHERE voucher_id = ?",
                Integer.class, fixture.voucher());
    }

    private Quote quote(Fixture fixture, String customer) {
        return service.quote(fixture.store(), customer, "DEMO30", 200_000);
    }

    private Redeem command(UUID key, Fixture fixture, String customer, Quote quote) {
        return new Redeem(key, fixture.store(), customer, quote.voucherId(), quote.version(),
                quote.merchandiseAmount(), quote.discountAmount());
    }

    private Fixture seed(int limit) {
        return seed(limit, "");
    }

    private Fixture seed(int limit, String schema) {
        var fixture = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        db.update("INSERT INTO " + schema + "seller_profiles (id, owner_reference, status) VALUES (?, ?, 'APPROVED')",
                fixture.seller(), fixture.seller().toString());
        db.update("INSERT INTO " + schema + "stores (id, seller_id, name, slug, status) VALUES (?, ?, ?, ?, 'OPEN')",
                fixture.store(), fixture.seller(), "Voucher Test Store", fixture.store().toString());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        db.update("INSERT INTO " + schema + "vouchers " + """
                (id, store_id, code, name, discount_type, discount_value, min_order_amount,
                 usage_limit, starts_at, ends_at, status)
                VALUES (?, ?, 'DEMO30', 'Synthetic voucher', 'FIXED', 30000, 150000, ?, ?, ?, 'PUBLISHED')
                """, fixture.voucher(), fixture.store(), limit, now.minusHours(1), now.plusHours(1));
        return fixture;
    }

    private record Fixture(UUID store, UUID seller, UUID voucher) { }
}
