package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.domain.VoucherSnapshot;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.ports.VoucherPort.*;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.*;
import com.projecthk5.security.ApiException;
import java.math.BigDecimal;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Order PostgreSQL, Flyway and transactions with controllable peer replies. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
    "hk5.voucher-c2.enabled=true", "hk5.voucher-c2.scan-ms=86400000",
    "app.outbox.relay-enabled=false",
    "app.checkout.reconcile-scan-ms=86400000", "app.finance.policy-approved=true",
    "app.finance.currency-scale=0", "app.finance.platform-rate=0.05", "app.finance.house-exempt=true"
})
class VoucherCheckoutPostgresIT {
    private static final String BEARER = "Bearer fixture";
    private static final String STORE_A = "00000000-0000-0000-0000-000000000001";
    private static final String STORE_B = "00000000-0000-0000-0000-000000000002";
    private static final String KEY = "c2-fixture";
    private static final Order.Shipping SHIPPING = new Order.Shipping("Demo", "0900000000", "Demo address");
    private static final List<ExpectedItem> ITEMS = List.of(new ExpectedItem("one", 1), new ExpectedItem("two", 2));
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("app.security.issuer", () -> "https://fixture.example.invalid");
        registry.add("app.security.audience", () -> "fixture-api");
        registry.add("app.security.current-kid", () -> "fixture-kid");
        registry.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired CheckoutService checkout;
    @Autowired IdempotencyCoordinator keys;
    @Autowired ObjectMapper json;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired OrderRepository orders;
    @Autowired OrderWriteService writer;
    @Autowired VoucherCheckoutJournal sagaJournal;
    @Autowired CheckoutDraftJournal drafts;
    @Autowired InventoryOperationJournal stockJournal;
    @Autowired FulfillmentService fulfillment;
    @Autowired ReturnRequestService returns;
    @Autowired FinanceReport finance;
    @Autowired JdbcTemplate db;
    @MockBean CartPort cart;
    @MockBean CatalogPort catalog;
    @MockBean VoucherPort seller;
    @MockBean InventoryPort inventory;

    private String customer;
    private final Map<UUID, VoucherPort.Reservation> ledger = new ConcurrentHashMap<>();
    private final AtomicReference<Intent> attemptedRedeem = new AtomicReference<>();
    private final AtomicReference<String> fault = new AtomicReference<>();

    @BeforeEach void prepare() {
        customer = UUID.randomUUID().toString();
        List<CartPort.Item> lines = List.of(
                new CartPort.Item("one", STORE_A, "seller-a", 1, new BigDecimal("199999")),
                new CartPort.Item("two", STORE_B, "seller-b", 2, new BigDecimal("50000")));
        when(cart.view(BEARER)).thenReturn(new CartPort.CartSnapshot(customer, 7, false, lines));
        when(cart.claim(BEARER, KEY, 7L)).thenReturn(new CartPort.CartSnapshot(customer, 8, true, lines));
        when(catalog.verify("one")).thenReturn(product("one", STORE_A, "seller-a", 199999, false));
        when(catalog.verify("two")).thenReturn(product("two", STORE_B, "seller-b", 50000, false));
        when(seller.quote(eq(UUID.fromString(STORE_A)), eq(customer), eq("QA1500"), eq(199999L))).thenReturn(
                new VoucherSnapshot(UUID.randomUUID(), 2, "QA1500", "PERCENT", 15, 50000L, 199999, 30000));
        when(seller.quote(eq(UUID.fromString(STORE_B)), eq(customer), eq("QA6000"), eq(100000L))).thenReturn(
                new VoucherSnapshot(UUID.randomUUID(), 3, "QA6000", "FIXED", 6000, null, 100000, 6000));
        when(seller.status(any())).thenAnswer(call ->
                Optional.ofNullable(ledger.get(call.<Intent>getArgument(0).sellerOrderId())));
        when(seller.redeem(any())).thenAnswer(call -> {
            Intent intent = call.getArgument(0);
            assertTrue(sagaJournal.steps(sagaJournal.find(attempt().getId()).orElseThrow()).stream()
                    .anyMatch(step -> step.intent().equals(intent)
                            && step.progress() == VoucherCheckoutJournal.Progress.REDEEMING));
            attemptedRedeem.set(intent);
            cut("before-redeem");
            var row = ledger.computeIfAbsent(intent.sellerOrderId(), id -> receipt(intent, State.RESERVED, null));
            cut("after-redeem");
            return row;
        });
        when(seller.commit(any())).thenAnswer(call -> {
            Intent intent = call.getArgument(0);
            assertTrue(orders.findByIdAndCustomerId(attempt().getId(), customer).isPresent());
            cut("before-commit");
            var row = ledger.computeIfPresent(intent.sellerOrderId(), (id, old) -> receipt(intent, State.COMMITTED, null));
            cut("after-commit");
            return row;
        });
        when(seller.abort(any())).thenAnswer(call -> {
            Intent i = call.getArgument(0);
            return ledger.compute(i.sellerOrderId(), (id, old) -> receipt(i, State.RELEASED, ReleaseReason.CHECKOUT_FAILED));
        });
        when(seller.release(any(), any())).thenAnswer(call -> {
            Intent i = call.getArgument(0);
            ReleaseReason reason = call.getArgument(1);
            var row = ledger.computeIfPresent(i.sellerOrderId(), (id, old) -> receipt(i, State.RELEASED, reason));
            cut("after-release");
            return row;
        });
        doAnswer(call -> {
            UUID order = call.getArgument(0);
            List<InventoryPort.Reservation> items = call.getArgument(1);
            String id = prepareStock(order, items);
            if (stockJournal.load(id).state() == InventoryOperationJournal.State.CONFIRMED) return null;
            Integer send = stockJournal.claimPendingForSend(id);
            if (send == null) throw new IllegalStateException("Stock outcome pending");
            if ("stock-unknown".equals(fault.get())) {
                stockJournal.unknown(id, send, "FIXTURE");
                throw new IllegalStateException("Lost stock reply");
            }
            if ("stock-rejected".equals(fault.get())) {
                stockJournal.reject(id, send, "INSUFFICIENT_STOCK");
                throw new InventoryPort.Rejected("INSUFFICIENT_STOCK", "Fixture stock");
            }
            stockJournal.confirm(id, send);
            return null;
        }).when(inventory).reserve(any(), anyList());
    }

    @Test void twoStoreReplayAndImmutableFinanceBasis() {
        var request = request(true);
        var placed = place(request);
        money("263999", placed.total());
        assertEquals(2, placed.sellerOrderCount());
        var prices = pricesByStore(placed);
        money("8500", new BigDecimal(prices.get(STORE_A).platformFee()));
        money("4700", new BigDecimal(prices.get(STORE_B).platformFee()));
        var replay = place(request);
        assertEquals(placed.orderId(), replay.orderId());
        assertEquals(placed.status(), replay.status());
        assertEquals(placed.total(), replay.total());
        assertEquals(placed.sellerOrderCount(), replay.sellerOrderCount());
        assertEquals(prices, pricesByStore(replay));
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH", assertThrows(ResponseStatusException.class,
                () -> place(new CheckoutRequest(ITEMS, 7L, List.of(request.vouchers().getFirst())))).getReason());
        assertEquals(2, ledger.size());
        assertTrue(ledger.values().stream().allMatch(reservation -> reservation.state() == State.COMMITTED));
        assertEquals(1, countOrder());
        assertEquals(1, db.queryForObject("select count(*) from outbox_events where aggregate_id=? and topic='order.placed.v1'",
                Integer.class, placed.orderId()));
        assertEquals(2, db.queryForObject("select count(*) from seller_orders where order_id=? and voucher_snapshot is not null",
                Integer.class, placed.orderId()));
        verify(inventory, times(1)).reserve(eq(placed.orderId()), anyList());
        verify(cart, times(1)).recover(customer, KEY, 8, true);
    }

    @Test void oneVoucherDoesNotChangeTheOtherStoresGrossOrFinance() {
        var placed = place(request(false));
        money("269999", placed.total());
        var otherStore = pricesByStore(placed).get(STORE_B);
        assertNull(otherStore.voucherCode());
        money("100000", new BigDecimal(otherStore.gross()));
        money("5000", new BigDecimal(otherStore.platformFee()));
        assertEquals(1, ledger.size());
    }

    @Test void nativeSqlCannotChangeOrRemoveTheVoucherSnapshot() {
        var placed = place(request(false));
        UUID child = storeOrder(placed.orderId(), STORE_A);
        String original = db.queryForObject(
                "select voucher_snapshot::text from seller_orders where id=?", String.class, child);
        for (String change : List.of(
                "jsonb_set(voucher_snapshot, '{code}', '\"OTHER\"'::jsonb)",
                "jsonb_set(voucher_snapshot, '{voucherId}', '\"00000000-0000-0000-0000-000000000099\"'::jsonb)",
                "jsonb_set(voucher_snapshot, '{version}', '99'::jsonb)",
                "jsonb_set(voucher_snapshot, '{type}', '\"FIXED\"'::jsonb)",
                "jsonb_set(voucher_snapshot, '{discountValue}', '10'::jsonb)",
                "jsonb_set(voucher_snapshot, '{maxDiscount}', '40000'::jsonb)",
                "jsonb_set(jsonb_set(voucher_snapshot, '{merchandiseAmount}', '200000'::jsonb),"
                        + " '{discountAmount}', '30001'::jsonb)",
                "null::jsonb")) {
            assertSnapshotUpdateRejected("update seller_orders set voucher_snapshot=" + change + " where id=?", child);
            assertEquals(original, db.queryForObject(
                    "select voucher_snapshot::text from seller_orders where id=?", String.class, child));
        }
    }

    @Test void nativeSqlCannotAttachAVoucherToAnOrderCreatedWithoutOne() {
        var placed = place(request(false));
        UUID child = storeOrder(placed.orderId(), STORE_B);
        assertSnapshotUpdateRejected("""
                update seller_orders set voucher_snapshot=jsonb_build_object(
                    'voucherId', '00000000-0000-0000-0000-000000000099',
                    'version', 1, 'code', 'OTHER', 'type', 'FIXED',
                    'discountValue', 1, 'merchandiseAmount', 100001, 'discountAmount', 1)
                where id=?
                """, child);
        assertNull(db.queryForObject(
                "select voucher_snapshot::text from seller_orders where id=?", String.class, child));
        money("100000", db.queryForObject("select gross from seller_orders where id=?", BigDecimal.class, child));
    }

    @Test void unchangedSnapshotsAndFulfillmentUpdatesRemainAllowed() {
        var placed = place(request(false));
        UUID withVoucher = storeOrder(placed.orderId(), STORE_A);
        UUID withoutVoucher = storeOrder(placed.orderId(), STORE_B);
        String original = db.queryForObject(
                "select voucher_snapshot::text from seller_orders where id=?", String.class, withVoucher);
        assertEquals(1, db.update(
                "update seller_orders set voucher_snapshot=?::jsonb, updated_at=updated_at+interval '1 second' where id=?",
                original, withVoucher));
        assertEquals(1, db.update(
                "update seller_orders set voucher_snapshot=null, updated_at=updated_at+interval '1 second' where id=?",
                withoutVoucher));
        fulfillment.sellerAction(STORE_A, withVoucher, "accept", null);
        fulfillment.sellerAction(STORE_B, withoutVoucher, "accept", null);
        assertEquals("ACCEPTED", db.queryForObject(
                "select status from seller_orders where id=?", String.class, withVoucher));
        assertEquals("ACCEPTED", db.queryForObject(
                "select status from seller_orders where id=?", String.class, withoutVoucher));
        assertEquals(original, db.queryForObject(
                "select voucher_snapshot::text from seller_orders where id=?", String.class, withVoucher));
        assertNull(db.queryForObject(
                "select voucher_snapshot::text from seller_orders where id=?", String.class, withoutVoucher));
    }

    @Test void immutableVoucherSnapshotKeepsGrossBoundToTheOriginalDiscount() {
        var placed = place(request(false));
        UUID child = storeOrder(placed.orderId(), STORE_A);
        var failure = assertThrows(DataIntegrityViolationException.class,
                () -> db.update("update seller_orders set gross=gross+1 where id=?", child));
        var sql = assertInstanceOf(PSQLException.class, failure.getMostSpecificCause());
        assertEquals("23514", sql.getSQLState());
        assertEquals("ck_seller_order_voucher_snapshot", sql.getServerErrorMessage().getConstraint());
        money("169999", db.queryForObject("select gross from seller_orders where id=?", BigDecimal.class, child));
    }

    private static Map<String, StorePrice> pricesByStore(CheckoutResult result) {
        return result.prices().stream().collect(Collectors.toMap(StorePrice::storeId, price -> price));
    }

    private void assertSnapshotUpdateRejected(String update, UUID child) {
        var failure = assertThrows(DataIntegrityViolationException.class, () -> db.update(update, child));
        var sql = assertInstanceOf(PSQLException.class, failure.getMostSpecificCause());
        assertEquals("23514", sql.getSQLState());
        assertEquals("seller_order_voucher_snapshot_immutable", sql.getServerErrorMessage().getConstraint());
    }

    @Test void promotionMustBeAuthoritativeAndQuoteCannotBeRepricedSilently() {
        var request = request(false);
        when(catalog.verify("one")).thenReturn(product("one", STORE_A, "seller-a", 199999, null));
        assertEquals("VOUCHER_PROMOTION_UNVERIFIED", assertThrows(ApiException.class,
                () -> checkout.quote(customer, BEARER, new VoucherQuoteRequest(ITEMS, 7L,
                        List.of(new VoucherCode(STORE_A, "QA1500"))))).code());
        when(catalog.verify("one")).thenReturn(product("one", STORE_A, "seller-a", 199999, true));
        assertEquals("VOUCHER_PROMOTION_CONFLICT", assertThrows(ApiException.class,
                () -> checkout.quote(customer, BEARER, new VoucherQuoteRequest(ITEMS, 7L,
                        List.of(new VoucherCode(STORE_A, "QA1500"))))).code());
        when(catalog.verify("one")).thenReturn(product("one", STORE_A, "seller-a", 199999, false));
        when(seller.quote(eq(UUID.fromString(STORE_A)), eq(customer), eq("QA1500"), eq(199999L))).thenReturn(
                new VoucherSnapshot(UUID.randomUUID(), 3, "QA1500", "PERCENT", 15, 50000L, 199999, 30000));
        assertEquals("VOUCHER_CHANGED", assertThrows(ApiException.class, () -> place(request)).code());
        verify(seller, never()).redeem(any());
        verify(inventory, never()).reserve(any(), anyList());
        verify(cart).release(BEARER, KEY);
    }

    @Test void crashBoundaryBeforeRedeemAndLostReplyAfterRedeemReuseTheDurableIntent() {
        var request = request(false);
        fault.set("before-redeem");
        pending(() -> place(request));
        Intent intent = attemptedRedeem.get();
        assertTrue(ledger.isEmpty());
        assertEquals(0, countOrder());
        fault.set("after-redeem");
        recover();
        assertEquals(intent, attemptedRedeem.get());
        assertEquals(State.RESERVED, ledger.get(intent.sellerOrderId()).state());
        assertEquals(0, countOrder());
        verifyNoInteractions(inventory);
        verify(seller, never()).abort(any());
        recover();
        assertEquals(1, countOrder());
        assertEquals(State.COMMITTED, ledger.get(intent.sellerOrderId()).state());
        assertEquals(attempt().getId(), place(request).orderId());
        verify(seller, times(2)).redeem(intent);
        verify(inventory, times(1)).reserve(eq(attempt().getId()), anyList());
    }

    @Test void abruptJvmHaltAfterC2DraftCommitLeavesTheSameSellerOrderForRecovery() throws Exception {
        var request = request(false);
        String fingerprint = RequestFingerprint.compute(request, SHIPPING);
        UUID id = keys.begin(customer, KEY, fingerprint).attempt().getId();
        var draftOrder = new Order(id, customer);
        draftOrder.ship(SHIPPING);
        var voucher = seller.quote(UUID.fromString(STORE_A), customer, "QA1500", 199999);
        var first = new SellerOrder(draftOrder, "seller-a", STORE_A, new BigDecimal("169999"), new BigDecimal("8500"), voucher);
        first.getItems().add(new OrderItem(first, "one", 1, new BigDecimal("199999"), new BigDecimal("199999"), "sku-one", "Fixture one"));
        var second = new SellerOrder(draftOrder, "seller-b", STORE_B, new BigDecimal("100000"), new BigDecimal("5000"));
        second.getItems().add(new OrderItem(second, "two", 2, new BigDecimal("50000"), new BigDecimal("100000"), "sku-two", "Fixture two"));
        draftOrder.addSellerOrder(first);
        draftOrder.addSellerOrder(second);
        var event = json.createObjectNode().put("eventId", UUID.randomUUID().toString())
                .put("orderId", id.toString()).put("customerId", customer).put("sellerOrderCount", 2);
        var draft = CheckoutDraft.capture(draftOrder, fingerprint, 7L, 8, Map.of(STORE_A, false, STORE_B, false), event.toString());
        var classpath = new LinkedHashSet<>(List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls)
                for (var url : urls.getURLs())
                    if ("file".equals(url.getProtocol())) classpath.add(Path.of(url.toURI()).toString());
        }
        Path args = CrashJvmArgFile.writeClasspath(String.join(java.io.File.pathSeparator, classpath));
        try {
            var probe = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "@" + args.toAbsolutePath(), CheckoutDraftCrashProcess.class.getName(), "AFTER_DRAFT");
            probe.environment().put("TV3_CRASH_PG_URL", PG.getJdbcUrl());
            probe.environment().put("TV3_CRASH_PG_USER", PG.getUsername());
            probe.environment().put("TV3_CRASH_PG_PASSWORD", PG.getPassword());
            probe.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            var child = probe.start();
            try {
                try (var stdin = child.getOutputStream()) { stdin.write(json.writeValueAsBytes(draft)); }
                assertTrue(child.waitFor(45, TimeUnit.SECONDS));
                assertEquals(86, child.exitValue());
            } finally {
                if (child.isAlive()) child.destroyForcibly().waitFor();
            }
        } finally {
            Files.deleteIfExists(args);
        }
        assertEquals(draft, drafts.load(id));
        assertEquals(0, countOrder());
        verify(seller, never()).redeem(any());
        recover();
        assertEquals(first.getId(), attemptedRedeem.get().sellerOrderId());
        assertEquals(id, place(request).orderId());
        verify(seller, times(1)).redeem(any());
        verify(inventory, times(1)).reserve(eq(id), anyList());
    }

    @Test void inventoryUnknownKeepsVoucherUntilMatchingPositiveReceipt() {
        var request = request(false);
        fault.set("stock-unknown");
        pending(() -> place(request));
        UUID id = attempt().getId();
        String operation = InventoryHttpAdapter.reserveId(id);
        assertEquals(InventoryOperationJournal.State.UNKNOWN, stockJournal.load(operation).state());
        recover();
        assertEquals(0, countOrder());
        assertEquals(State.RESERVED, ledger.values().iterator().next().state());
        verify(seller, never()).abort(any());
        verify(seller, never()).release(any(), any());
        assertThrows(IllegalStateException.class, () -> stockJournal.confirmVoucherReserveReceipt(id, operation, "0".repeat(64)));
        when(inventory.confirmReserve(eq(id), anyList())).thenAnswer(call -> {
            stockJournal.confirmVoucherReserveReceipt(id, operation, stockJournal.load(operation).intent().fingerprint());
            return true;
        });
        recover();
        assertEquals(1, stockJournal.load(operation).sends());
        assertEquals(id, place(request).orderId());
        assertEquals(State.COMMITTED, ledger.values().iterator().next().state());
    }

    @Test void inventoryDefinitiveRejectionAbortsTheRedeemAndReleasesTheCart() {
        var request = request(false);
        fault.set("stock-rejected");
        assertEquals("INSUFFICIENT_STOCK", assertThrows(ApiException.class, () -> place(request)).code());
        assertEquals(0, countOrder());
        assertEquals(CheckoutAttempt.State.FAILED, attempt().getState());
        assertEquals(ReleaseReason.CHECKOUT_FAILED, ledger.values().iterator().next().releaseReason());
        verify(cart).recover(customer, KEY, 8, false);
        assertEquals("INSUFFICIENT_STOCK", assertThrows(ApiException.class, () -> place(request)).code());
        verify(seller, times(1)).abort(any());
    }

    @Test void sameKeyConcurrentlyCannotAcquireTwoLeasesOrRedeemTwice() throws Exception {
        var request = request(false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            Intent intent = call.getArgument(0);
            started.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture deadline");
            var saved = receipt(intent, State.RESERVED, null);
            ledger.put(intent.sellerOrderId(), saved);
            return saved;
        }).when(seller).redeem(any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var inFlight = pool.submit(() -> place(request));
            try {
                assertTrue(started.await(15, TimeUnit.SECONDS));
                pending(() -> place(request));
                assertEquals(0, countOrder());
            } finally { release.countDown(); }
            assertEquals(inFlight.get(15, TimeUnit.SECONDS).orderId(), place(request).orderId());
        }
        verify(seller, times(1)).redeem(any());
        verify(inventory, times(1)).reserve(any(), anyList());
    }

    @Test void orderRollbackKeepsConfirmedStockAndScannerCommitsOnlyAfterPlacement() {
        var request = request(false);
        db.execute("alter table outbox_events add constraint c2_fixture_cut check(topic<>'order.placed.v1') not valid");
        try {
            pending(() -> place(request));
            assertEquals(0, countOrder());
            assertEquals(InventoryOperationJournal.State.CONFIRMED,
                    stockJournal.load(InventoryHttpAdapter.reserveId(attempt().getId())).state());
            verify(seller, never()).commit(any());
        } finally {
            db.execute("alter table outbox_events drop constraint c2_fixture_cut");
        }
        fault.set("before-commit");
        recover();
        assertEquals(1, countOrder());
        assertEquals(State.RESERVED, ledger.values().iterator().next().state());
        fault.set("after-commit");
        recover();
        assertEquals(State.COMMITTED, ledger.values().iterator().next().state());
        recover();
        assertEquals(attempt().getId(), place(request).orderId());
        assertEquals(1, stockJournal.load(InventoryHttpAdapter.reserveId(attempt().getId())).sends());
        verify(seller, times(2)).commit(any());
        verify(cart, times(1)).recover(customer, KEY, 8, true);
    }

    @Test void draftRollbackIsProvenBeforeReleasingTheCartAndNeverRedeems() {
        var request = request(false);
        db.execute("alter table checkout_order_drafts add constraint c2_fixture_draft_cut check(snapshot_version<>2) not valid");
        try {
            assertEquals("VOUCHER_CHECKOUT_NOT_PLACED", assertThrows(ApiException.class, () -> place(request)).code());
            assertEquals(CheckoutAttempt.State.FAILED, attempt().getState());
            assertTrue(sagaJournal.find(attempt().getId()).isEmpty());
            verify(cart).release(BEARER, KEY);
            verify(seller, never()).redeem(any());
            verifyNoInteractions(inventory);
        } finally {
            db.execute("alter table checkout_order_drafts drop constraint c2_fixture_draft_cut");
        }
    }

    @Test void cancellationAndRejectionReleaseOnceAcrossLostReplyAndRecovery() {
        var placed = place(request(true));
        UUID a = storeOrder(placed.orderId(), STORE_A);
        UUID b = storeOrder(placed.orderId(), STORE_B);
        assertThrows(ApiException.class, () -> fulfillment.sellerAction(STORE_A, b, "reject", "Wrong Store"));
        assertThrows(ApiException.class, () -> fulfillment.customerCancel("other", placed.orderId(), a, null));
        fulfillment.customerCancel(customer, placed.orderId(), a, null);
        fulfillment.sellerAction(STORE_B, b, "reject", "QA rejection");
        fault.set("after-release");
        recover();
        recover();
        recover();
        assertEquals(ReleaseReason.CANCELLED_BEFORE_DISPATCH, ledger.get(a).releaseReason());
        assertEquals(ReleaseReason.REJECTED_BEFORE_DISPATCH, ledger.get(b).releaseReason());
        assertThrows(ApiException.class, () -> fulfillment.customerCancel(customer, placed.orderId(), a, null));
        assertThrows(ApiException.class, () -> fulfillment.sellerAction(STORE_B, b, "reject", "Duplicate"));
        verify(seller, times(2)).release(argThat(intent -> intent.sellerOrderId().equals(a)),
                eq(ReleaseReason.CANCELLED_BEFORE_DISPATCH));
        verify(seller, times(1)).release(argThat(intent -> intent.sellerOrderId().equals(b)),
                eq(ReleaseReason.REJECTED_BEFORE_DISPATCH));
        verify(inventory, never()).release(any());
    }

    @Test void deliveredCodAndReturnUseTheDiscountedGrossAndKeepTheRedemption() {
        var placed = place(request(false));
        UUID child = storeOrder(placed.orderId(), STORE_A);
        for (String action : List.of("accept", "prepare", "ready", "dispatch", "deliver"))
            fulfillment.sellerAction(STORE_A, child, action, null);
        assertThrows(ApiException.class, () -> fulfillment.customerCancel(customer, placed.orderId(), child, null));
        fulfillment.collectCod("fixture-admin", child);
        var returned = returns.request(customer, placed.orderId(), child, "return-fixture", "QA return");
        money("169999", new BigDecimal(returned.request().refundAmount()));
        var summary = finance.forStore(STORE_A, "Fixture Store").totals();
        money("169999", summary.gross());
        money("30000", summary.voucherDiscount());
        money("8500", summary.platformFee());
        money("161499", summary.sellerNet());
        money("169999", summary.codCollected());
        recover();
        assertEquals(State.COMMITTED, ledger.get(child).state());
        verify(seller, never()).release(any(), any());
    }

    private CatalogPort.ProductSnapshot product(String id, String store, String sellerId, long price, Boolean promo) {
        return new CatalogPort.ProductSnapshot(id, sellerId, store, true, true, true, false, BigDecimal.valueOf(price),
                "sku-" + id, "Fixture " + id, promo);
    }
    private CheckoutRequest request(boolean both) {
        var codes = both ? List.of(new VoucherCode(STORE_A, "QA1500"), new VoucherCode(STORE_B, "QA6000"))
                : List.of(new VoucherCode(STORE_A, "QA1500"));
        var quote = checkout.quote(customer, BEARER, new VoucherQuoteRequest(ITEMS, 7L, codes));
        var selections = quote.stores().stream().filter(price -> price.voucherCode() != null)
                .map(price -> new VoucherSelection(price.storeId(), price.voucherCode(), price.quoteFingerprint())).toList();
        return new CheckoutRequest(ITEMS, 7L, selections);
    }
    private CheckoutResult place(CheckoutRequest request) { return checkout.checkout(customer, BEARER, KEY, request, SHIPPING); }
    private CheckoutAttempt attempt() { return attempts.findByCustomerIdAndIdempotencyKey(customer, KEY).orElseThrow(); }
    private int countOrder() {
        return db.queryForObject("select count(*) from orders where customer_id=?", Integer.class, customer);
    }
    private UUID storeOrder(UUID order, String store) {
        return db.queryForObject("select id from seller_orders where order_id=? and store_id=?", UUID.class, order, store);
    }
    private void recover() {
        UUID id = attempt().getId();
        db.update("update checkout_voucher_sagas set retry_at=now() where attempt_id=?", id);
        // Recreate the worker from persisted state. Other saga fixtures are not part of this recovery.
        new VoucherCheckoutSaga(sagaJournal, drafts, seller, inventory, cart, catalog, writer, orders, true).recover();
    }
    private void cut(String phase) {
        if (fault.compareAndSet(phase, null)) throw new IllegalStateException("Fixture interruption");
    }
    private String prepareStock(UUID order, List<InventoryPort.Reservation> items) {
        String id = InventoryHttpAdapter.reserveId(order);
        var lines = items.stream().map(i -> new InventoryOperationIntent.Line(i.sku(), i.quantity())).toList();
        stockJournal.prepare(InventoryOperationIntent.prepare(order, id, InventoryOperationIntent.Type.RESERVE, lines));
        return id;
    }
    private static VoucherPort.Reservation receipt(Intent i, State state, ReleaseReason reason) {
        return new VoucherPort.Reservation(i.sellerOrderId(), i.storeId(), i.customerId(), i.quote(), state, reason);
    }
    private static void pending(org.junit.jupiter.api.function.Executable call) {
        assertEquals("VOUCHER_CHECKOUT_PENDING", assertThrows(ApiException.class, call).code());
    }
    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString());
    }
}
