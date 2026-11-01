package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.orderservice.service.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual Order PostgreSQL/transactions and HTTP peer; no real Product/Inventory stock or live Auth. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.checkout.reconcile-scan-ms=86400000")
class CheckoutInventoryJournalPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://fixture.example.invalid");
        r.add("app.security.audience", () -> "fixture-api");
        r.add("app.security.current-kid", () -> "fixture-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired OrderRepository orders;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired InventoryOperationJournal journal;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    private HttpServer peer;
    private InventoryHttpAdapter inventory;
    private CartPort cart;
    private CatalogPort catalog;
    private CheckoutService checkout;
    private String customer;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> mode = new AtomicReference<>("ok");
    private final AtomicReference<InventoryOperationJournal.Snapshot> atSend = new AtomicReference<>();
    private final List<JsonNode> sent = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch continueReply = new CountDownLatch(1);
    private final CheckoutRequest request = new CheckoutRequest(List.of(new ExpectedItem("p1", 2), new ExpectedItem("p2", 3)), null);

    @BeforeEach void setup() throws Exception {
        customer = "journal-checkout-" + UUID.randomUUID();
        peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/api/inventory/operations", exchange -> {
            JsonNode body = json.readTree(exchange.getRequestBody());
            calls.incrementAndGet();
            sent.add(body);
            // This is another thread/connection: seeing T1 here proves it committed before HTTP.
            try { atSend.set(journal.load(body.path("operationId").asText())); }
            catch (RuntimeException missingOnUnpatchedSource) { atSend.set(null); }
            entered.countDown();
            if (mode.get().equals("hold")) {
                try { continueReply.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            int status = mode.get().equals("claim") || mode.get().equals("reject") ? 409
                    : mode.get().equals("503") ? 503 : 200;
            List<Map<String, Object>> items = new ArrayList<>();
            body.path("items").forEach(i -> items.add(Map.of("sku", i.path("sku").asText(),
                    "availableQuantity", 100, "reservedQuantity", i.path("quantity").asLong(), "updatedAt", "2026-10-31T00:00:00Z")));
            if (mode.get().equals("bad-skus")) items.clear();
            if (mode.get().equals("duplicate-skus")) items.add(items.get(0));
            String code = mode.get().equals("reject") ? "INSUFFICIENT_STOCK" : "IDEMPOTENCY_CLAIM_CONFLICT";
            byte[] reply = status != 200 ? json.writeValueAsBytes(Map.of("code", code))
                    : mode.get().equals("no-body") ? new byte[0] : json.writeValueAsBytes(Map.of(
                        "operationId", mode.get().equals("bad-id") ? "wrong" : body.path("operationId").asText(),
                        "type", mode.get().equals("bad-type") ? "RELEASE" : "RESERVE", "replayed", false, "items", items));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, reply.length == 0 ? -1 : reply.length);
            if (reply.length > 0) exchange.getResponseBody().write(reply);
            exchange.close();
        });
        peer.start();
        inventory = adapter();
        cart = mock(CartPort.class);
        catalog = mock(CatalogPort.class);
        when(cart.claim("Bearer fixture", "key", null)).thenReturn(new CartPort.CartSnapshot(customer, 1, true, List.of(
                new CartPort.Item("p1", "store1", "seller1", 2, new BigDecimal("10000")),
                new CartPort.Item("p2", "store2", "seller2", 3, new BigDecimal("10000")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1", "seller1", "store1", true, true, true, false, new BigDecimal("10000"), "SKU-番茄-A", "A"));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2", "seller2", "store2", true, true, true, false, new BigDecimal("10000"), "SKU-B", "B"));
        checkout = new CheckoutService(cart, catalog, inventory, keys, writer, orders, json, new MoneyRules(true, "0.05", "0", "true"));
    }

    private InventoryHttpAdapter adapter() {
        return new InventoryHttpAdapter("http://127.0.0.1:" + peer.getAddress().getPort(), "fixture-pair", journal);
    }
    @AfterEach void stop() { continueReply.countDown(); peer.stop(0); }
    private CheckoutAttempt attempt() { return attempts.findByCustomerIdAndIdempotencyKey(customer, "key").orElseThrow(); }
    private CheckoutResult place() { return checkout.checkout(customer, "Bearer fixture", "key", request); }
    private InventoryOperationJournal.Snapshot stored() { return journal.load(InventoryHttpAdapter.reserveId(attempt().getId())); }
    private List<InventoryPort.Reservation> lines() { return List.of(new InventoryPort.Reservation("p1", 2, "SKU-番茄-A"), new InventoryPort.Reservation("p2", 3, "SKU-B")); }

    private InventoryOperationIntent pendingIntent() {
        var a=keys.begin(customer,"key","fixture-fingerprint").attempt();
        var intent=InventoryOperationIntent.prepare(a.getId(),InventoryHttpAdapter.reserveId(a.getId()),
            InventoryOperationIntent.Type.RESERVE,List.of(new InventoryOperationIntent.Line("SKU-番茄-A",2),
                new InventoryOperationIntent.Line("SKU-B",3)));
        journal.prepare(intent);
        return intent;
    }
    private Map<String,Object> journalFacts(String id) {
        return db.queryForMap("select operation_id,checkout_attempt_id,operation_type,canonical_lines::text,"
            +"payload_fingerprint,state,send_attempts,last_error_code from inventory_operation_journal where operation_id=?",id);
    }
    /** Test-only HTTP peer transport, never registered as a production positive transport bean. */
    private InventoryOperationTransport peerTransport() {
        return request -> {
            try {
                var http=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+peer.getAddress().getPort()+"/api/inventory/operations"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type","application/json")
                    .header("X-Internal-Token","fixture-pair").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build();
                var response=HttpClient.newHttpClient().send(http,HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new InventoryOperationTransport.UnknownOutcome("Fixture outcome unknown");
                var body=json.readTree(response.body());
                List<String> skus=new ArrayList<>();body.path("items").forEach(i->skus.add(i.path("sku").asText()));
                return new InventoryOperationTransport.Reply(body.path("operationId").asText(),body.path("type").asText(),
                    body.path("replayed").asBoolean(),skus);
            } catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();throw new InventoryOperationTransport.UnknownOutcome("Fixture interrupted");
            } catch(java.io.IOException unavailable) {
                throw new InventoryOperationTransport.UnknownOutcome("Fixture transport unavailable");
            }
        };
    }

    @Test void unknownGenericDisabledTransportCannotReopenRowOrEnableHttpAdapter() {
        mode.set("503");assertThrows(ResponseStatusException.class,this::place);
        String operation=stored().intent().operationId();
        var before=journalFacts(operation);
        var disabled=spy(new UnconfiguredInventoryOperationTransport());
        var generic=new InventoryOperationCaller(journal,disabled);
        var outcome=generic.dispatchOnce(operation);
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(operation).state());
        assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,outcome);
        verify(disabled,never()).send(any());
        assertEquals(before,journalFacts(operation));
        mode.set("ok");
        assertThrows(ResponseStatusException.class,()->adapter().apply(operation,"RESERVE",lines()));
        assertThrows(ResponseStatusException.class,()->adapter().reserve(attempt().getId(),lines()));
        assertEquals(before,journalFacts(operation));assertEquals(1,calls.get());
        verify(cart,never()).release(anyString(),anyString());verify(cart,never()).finalizeCart(anyString(),anyString());
    }

    @Test void unknownCannotBeClaimedThroughLegacyJournalApi() {
        mode.set("503");assertThrows(ResponseStatusException.class,this::place);
        String operation=stored().intent().operationId();var before=journalFacts(operation);
        assertNull(journal.claimPendingForSend(operation));
        assertNull(journal.claimForSend(operation));
        assertEquals(before,journalFacts(operation));
    }

    @Test void stalePendingReadCannotClaimAConcurrentlyCommittedUnknown() throws Exception {
        var intent=pendingIntent();
        var disabled=spy(new UnconfiguredInventoryOperationTransport());
        var generic=new InventoryOperationCaller(journal,disabled);
        var pool=Executors.newSingleThreadExecutor();
        try(var holder=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())) {
            holder.setAutoCommit(false);
            try(var lock=holder.prepareStatement("select 1 from inventory_operation_journal where operation_id=? for update")) {
                lock.setString(1,intent.operationId());lock.executeQuery().close();
            }
            Future<?> send=pool.submit(()->generic.dispatchOnce(intent.operationId()));
            // Caller reads committed PENDING, then its real UPDATE waits on this connection's row lock.
            assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
                while(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from pg_stat_activity "
                    +"where wait_event_type='Lock' and lower(query) like '%update inventory_operation_journal%')",Boolean.class)))
                    Thread.sleep(25);
            });
            // Another sender's outcome commits while the old PENDING reader is waiting. Test DB only.
            try(var unknown=holder.prepareStatement("update inventory_operation_journal set state='UNKNOWN',send_attempts=1,"
                +"last_error_code='FIXTURE_CONCURRENT_UNKNOWN' where operation_id=?")) {
                unknown.setString(1,intent.operationId());unknown.executeUpdate();
            }
            holder.commit();
            var refused=assertThrows(ExecutionException.class,()->send.get(10,TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class,refused.getCause());
            assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
            assertEquals(1,journal.load(intent.operationId()).sends());
            verify(disabled,never()).send(any());assertEquals(0,calls.get());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void pendingGenericDisabledTransportKeepsNotSentPendingWithoutHttp() {
        var intent=pendingIntent();var disabled=spy(new UnconfiguredInventoryOperationTransport());
        var generic=new InventoryOperationCaller(journal,disabled);
        assertEquals(InventoryOperationCaller.Outcome.BLOCKED,generic.dispatchOnce(intent.operationId()));
        var saved=journal.load(intent.operationId());
        assertEquals(InventoryOperationJournal.State.PENDING,saved.state());assertEquals(1,saved.sends());
        assertEquals(intent.fingerprint(),saved.intent().fingerprint());assertEquals(intent.lines(),saved.intent().lines());
        verify(disabled).send(any());assertEquals(0,calls.get());
    }

    @Test void pendingGenericSuccessConfirmsOnceAndReplayDoesNotSend() {
        var intent=pendingIntent();var generic=new InventoryOperationCaller(journal,peerTransport());
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,generic.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,generic.dispatchOnce(intent.operationId()));
        assertEquals(InventoryOperationJournal.State.CONFIRMED,journal.load(intent.operationId()).state());
        assertEquals(1,journal.load(intent.operationId()).sends());assertEquals(1,calls.get());
        assertEquals(intent.fingerprint(),journal.load(intent.operationId()).intent().fingerprint());
    }

    @Test void concurrentGenericCallersCannotBothSend() throws Exception {
        var intent=pendingIntent();var generic=new InventoryOperationCaller(journal,peerTransport());mode.set("hold");
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->generic.dispatchOnce(intent.operationId()));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->generic.dispatchOnce(intent.operationId()));
            continueReply.countDown();
            assertEquals(InventoryOperationCaller.Outcome.CONFIRMED,first.get(10,TimeUnit.SECONDS));
            assertEquals(1,calls.get());assertEquals(1,journal.load(intent.operationId()).sends());
        } finally {continueReply.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void lateGenericReplyAfterStaleScanCannotConfirmOrRestorePending() throws Exception {
        var intent=pendingIntent();var generic=new InventoryOperationCaller(journal,peerTransport());mode.set("hold");
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->assertThrows(IllegalStateException.class,()->generic.dispatchOnce(intent.operationId())));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",intent.operationId());
            assertEquals(1,journal.flagStaleInFlight(Duration.ofMinutes(10)));
            continueReply.countDown();first.get(10,TimeUnit.SECONDS);
            assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
            assertThrows(IllegalStateException.class,()->journal.notSent(intent.operationId(),1,"LATE_DISABLED"));
            assertEquals(InventoryOperationCaller.Outcome.UNKNOWN,generic.dispatchOnce(intent.operationId()));
            assertEquals(1,calls.get());assertEquals(1,journal.load(intent.operationId()).sends());
        } finally {continueReply.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void intentIsCommittedBeforeHttpAndSuccessfulReplayUsesNoNewSnapshotOrSend() {
        CheckoutResult first = place();
        assertNotNull(atSend.get(), "T1 must be visible on the HTTP peer's independent DB connection");
        assertEquals(InventoryOperationJournal.State.IN_FLIGHT, atSend.get().state());
        assertEquals(1, atSend.get().sends());
        assertEquals(first.orderId(), atSend.get().intent().checkoutAttemptId());
        assertEquals(InventoryHttpAdapter.reserveId(first.orderId()), atSend.get().intent().operationId());
        assertEquals(List.of("SKU-B", "SKU-番茄-A"), atSend.get().intent().lines().stream().map(l -> l.sku()).toList());
        assertEquals(List.of(3L, 2L), atSend.get().intent().lines().stream().map(l -> l.quantity()).toList());
        assertEquals(InventoryOperationJournal.State.CONFIRMED, stored().state());
        assertEquals(2, first.sellerOrderCount());
        clearInvocations(catalog);
        assertEquals(first.orderId(), place().orderId());
        verifyNoInteractions(catalog);
        assertEquals(1, calls.get());
        assertEquals(1, db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?", Integer.class, first.orderId()));
    }

    @Test void missingCommittedAttemptPreventsAnyHttp() {
        assertThrows(RuntimeException.class, () -> inventory.reserve(UUID.randomUUID(), lines()));
        assertEquals(0, calls.get(), "A failed T1 FK/commit must stop before network I/O");
    }

    @Test void standaloneAdapterCannotFallBackToUnjournaledReserve() {
        var unconfigured = new InventoryHttpAdapter("http://127.0.0.1:" + peer.getAddress().getPort(), "fixture-pair");
        assertThrows(ResponseStatusException.class, () -> unconfigured.reserve(UUID.randomUUID(), lines()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest @ValueSource(strings = {"claim", "503"})
    void unknownRetainsImmutablePayloadAndSameKeyCannotSendAgain(String profile) {
        mode.set(profile);
        assertThrows(ResponseStatusException.class, this::place);
        var first = stored();
        assertEquals(InventoryOperationJournal.State.UNKNOWN, first.state());
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION, attempt().getState());
        assertThrows(ResponseStatusException.class, this::place);
        assertEquals(1, calls.get());
        assertEquals(first.intent().fingerprint(), stored().intent().fingerprint());
        verify(cart, never()).release(anyString(), anyString());
        assertTrue(orders.findByIdAndCustomerId(attempt().getId(), customer).isEmpty());
    }

    @Test void explicitPendingDispatchReloadsSavedTupleAndRejectsRemapping() {
        var a=keys.begin(customer,"key","fixture-fingerprint").attempt();
        String operation=InventoryHttpAdapter.reserveId(a.getId());
        var intent=InventoryOperationIntent.prepare(a.getId(),operation,InventoryOperationIntent.Type.RESERVE,
            List.of(new InventoryOperationIntent.Line("SKU-番茄-A",2),new InventoryOperationIntent.Line("SKU-B",3)));
        journal.prepare(intent); // Committed T1, positive proof that no send has been claimed yet.
        String fingerprint = stored().intent().fingerprint();
        assertThrows(IllegalArgumentException.class, () -> inventory.reserve(attempt().getId(), List.of(new InventoryPort.Reservation("p1", 2, "REMAPPED"))));
        assertEquals(0, calls.get());
        adapter().apply(operation, "RESERVE", lines()); // New caller, first send only, from the exact durable tuple.
        assertEquals(1, calls.get());
        assertEquals(operation,sent.get(0).path("operationId").asText());
        java.util.Map<String,Long> quantities=new java.util.HashMap<>();
        sent.get(0).path("items").forEach(i->quantities.put(i.path("sku").asText(),i.path("quantity").asLong()));
        assertEquals(Map.of("SKU-番茄-A",2L,"SKU-B",3L),quantities);
        assertEquals(1, stored().sends());
        assertEquals(fingerprint, stored().intent().fingerprint());
        assertEquals(InventoryOperationJournal.State.CONFIRMED, stored().state());
    }

    @Test void unresolvedUnknownCannotBeRedispatchedByEitherReserveEntryPoint() {
        mode.set("503");
        assertThrows(ResponseStatusException.class,this::place);
        var unknown=stored();
        assertNull(journal.claimPendingForSend(unknown.intent().operationId()),"Atomic DB predicate must also block a stale PENDING reader");
        mode.set("ok");
        assertThrows(ResponseStatusException.class,()->adapter().apply(unknown.intent().operationId(),"RESERVE",lines()));
        assertThrows(ResponseStatusException.class,()->adapter().reserve(attempt().getId(),lines()));
        assertEquals(1,calls.get());
        assertEquals(InventoryOperationJournal.State.UNKNOWN,stored().state());
        assertEquals(1,stored().sends());
        var retained=stored().intent();
        assertEquals(unknown.intent().operationId(),retained.operationId());
        assertEquals(unknown.intent().checkoutAttemptId(),retained.checkoutAttemptId());
        assertEquals(unknown.intent().type(),retained.type());
        assertEquals(unknown.intent().fingerprint(),retained.fingerprint());
        assertEquals(unknown.intent().lines(),retained.lines());
        assertTrue(orders.findByIdAndCustomerId(attempt().getId(),customer).isEmpty());
        verify(cart,never()).release(anyString(),anyString());
    }

    @Test void definitiveRejectionIsDurableBeforeCartRelease() {
        mode.set("reject");
        assertThrows(InventoryPort.Rejected.class, this::place);
        assertEquals(InventoryOperationJournal.State.REJECTED, stored().state());
        assertEquals(CheckoutAttempt.State.FAILED, attempt().getState());
        verify(cart).release("Bearer fixture", "key");
    }

    @ParameterizedTest @ValueSource(strings = {"bad-id", "bad-type", "bad-skus", "duplicate-skus", "no-body"})
    void unverifiedSuccessCannotConfirmJournalOrWriteOrder(String profile) {
        mode.set(profile);
        assertThrows(ResponseStatusException.class, this::place);
        assertEquals(InventoryOperationJournal.State.UNKNOWN, stored().state());
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION, attempt().getState());
        assertTrue(orders.findByIdAndCustomerId(attempt().getId(), customer).isEmpty());
        verify(cart, never()).release(anyString(), anyString());
    }

    @Test void failedOrderTransactionDoesNotLoseConfirmedInventoryIntent() {
        db.execute("alter table outbox_events add constraint tv3_late_write_fault check(topic <> 'order.placed.v1') not valid");
        try {
            assertThrows(ResponseStatusException.class, this::place);
            assertEquals(InventoryOperationJournal.State.CONFIRMED, stored().state());
            assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION, attempt().getState());
            assertTrue(orders.findByIdAndCustomerId(attempt().getId(), customer).isEmpty());
            verify(cart, never()).release(anyString(), anyString());
        } finally { db.execute("alter table outbox_events drop constraint tv3_late_write_fault"); }
    }

    @ParameterizedTest @ValueSource(strings = {"CONFIRMED", "REJECTED"})
    void failedT2CommitPreservesUnknownWithoutCartRelease(String forbiddenState) {
        if (forbiddenState.equals("REJECTED")) mode.set("reject");
        db.execute("alter table inventory_operation_journal add constraint tv3_t2_fault check(state <> '" + forbiddenState + "') not valid");
        try {
            assertThrows(ResponseStatusException.class, this::place);
            assertEquals(InventoryOperationJournal.State.UNKNOWN, stored().state());
            assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION, attempt().getState());
            assertEquals(1, calls.get());
            assertTrue(orders.findByIdAndCustomerId(attempt().getId(), customer).isEmpty());
            verify(cart, never()).release(anyString(), anyString());
        } finally { db.execute("alter table inventory_operation_journal drop constraint tv3_t2_fault"); }
    }

    @Test void checkoutRejectsAmbientTransactionBeforeAnyRemoteClaim() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> place()));
        verifyNoInteractions(cart, catalog);
        assertTrue(attempts.findByCustomerIdAndIdempotencyKey(customer, "key").isEmpty());
        assertEquals(0, calls.get());
    }

    @Test void concurrentSendersCannotBothDispatchOneJournalEntry() throws Exception {
        CheckoutAttempt a = keys.begin(customer, "key", "fixture-fingerprint").attempt();
        mode.set("hold");
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = threads.submit(() -> inventory.reserve(a.getId(), lines()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> second = threads.submit(() -> assertThrows(ResponseStatusException.class, () -> adapter().reserve(a.getId(), lines())));
            second.get(5, TimeUnit.SECONDS);
            continueReply.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            assertEquals(1, journal.load(InventoryHttpAdapter.reserveId(a.getId())).sends());
        } finally { continueReply.countDown(); threads.shutdownNow(); }
    }
}
