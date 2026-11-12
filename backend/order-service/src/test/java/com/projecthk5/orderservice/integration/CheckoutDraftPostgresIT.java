package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.*;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.orderservice.service.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Order PostgreSQL/Flyway/transactions, Inventory HTTP fixture, mocked Cart/Catalog. Not live E2E. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties="app.checkout.reconcile-scan-ms=86400000")
class CheckoutDraftPostgresIT {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("app.security.issuer",()->"https://fixture.example.invalid");
        r.add("app.security.audience",()->"fixture-api");
        r.add("app.security.current-kid",()->"fixture-kid");
        r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired IdempotencyCoordinator keys;
    @Autowired OrderWriteService writer;
    @Autowired OrderRepository orders;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired CheckoutDraftJournal drafts;
    @Autowired InventoryOperationJournal journal;
    @Autowired CheckoutReconciliationScanner scanner;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    private HttpServer peer;
    private InventoryHttpAdapter inventory;
    private CheckoutService checkout;
    private CartPort cart;
    private CatalogPort catalog;
    private final AtomicInteger sends=new AtomicInteger();
    private final AtomicReference<CheckoutDraft> seenByPeer=new AtomicReference<>();
    private final AtomicBoolean uncertain=new AtomicBoolean();
    private final AtomicBoolean holdResponse=new AtomicBoolean();
    private final CountDownLatch requestReceived=new CountDownLatch(1);
    private final CountDownLatch replyAllowed=new CountDownLatch(1);
    private String customer;
    private static final String BEARER="Bearer fixture-not-a-token", KEY="private-fixture-key";
    private final CheckoutRequest request=new CheckoutRequest(List.of(new ExpectedItem("p1",2),new ExpectedItem("p2",3)),7L);
    private final Order.Shipping shipping=new Order.Shipping("Fixture Customer","fixture-phone","fixture-address");

    @BeforeEach void setup() throws Exception {
        customer="draft-"+UUID.randomUUID();
        peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/api/inventory/operations",exchange->{
            JsonNode body=json.readTree(exchange.getRequestBody());
            sends.incrementAndGet();
            String operationId=body.path("operationId").asText();
            var t1=journal.load(operationId);
            // Independent request thread/DB connection must see both committed records before HTTP response.
            seenByPeer.set(drafts.load(t1.intent().checkoutAttemptId()));
            requestReceived.countDown();
            if (holdResponse.get()) {
                try { replyAllowed.await(10,TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) {Thread.currentThread().interrupt();}
            }
            List<Map<String,Object>> items=new ArrayList<>();
            body.path("items").forEach(i->items.add(Map.of("sku",i.path("sku").asText(),
                "availableQuantity",100,"reservedQuantity",i.path("quantity").asInt(),"updatedAt",Instant.now().toString())));
            byte[] response=json.writeValueAsBytes(uncertain.get()?Map.of("code","IDEMPOTENCY_CLAIM_CONFLICT"):
                Map.of("operationId",operationId,"type","RESERVE","replayed",false,"items",items));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(uncertain.get()?409:200,response.length);
            exchange.getResponseBody().write(response);exchange.close();
        });
        peer.start();
        inventory=new InventoryHttpAdapter("http://127.0.0.1:"+peer.getAddress().getPort(),"fixture-pair",journal);
        cart=mock(CartPort.class);catalog=mock(CatalogPort.class);
        when(cart.claim(BEARER,KEY,7L)).thenReturn(new CartPort.CartSnapshot(customer,8,true,List.of(
            new CartPort.Item("p1","store-a","seller-a",2,new BigDecimal("12.34")),
            new CartPort.Item("p2","store-b","seller-b",3,new BigDecimal("5.50")))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot("p1","seller-a","store-a",true,true,true,
            false,new BigDecimal("12.34"),"SKU-A","Fixture A"));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot("p2","seller-b","store-b",true,true,true,
            true,new BigDecimal("5.50"),"SKU-B","Fixture B"));
        checkout=caller(inventory);
    }
    @AfterEach void stop(){replyAllowed.countDown();peer.stop(0);}
    private CheckoutService caller(InventoryPort port){return new CheckoutService(cart,catalog,port,keys,writer,orders,json,
        new MoneyRules(true,"0.05","2","true"));}
    private CheckoutResult place(){return checkout.checkout(customer,BEARER,KEY,request,shipping);}
    private CheckoutAttempt attempt(){return attempts.findByCustomerIdAndIdempotencyKey(customer,KEY).orElseThrow();}
    private CheckoutDraft freshLoad(){return new CheckoutDraftJournal(db,json).load(attempt().getId());}
    private int orderCount(){return db.queryForObject("select count(*) from orders where customer_id=?",Integer.class,customer);}
    private int outboxCount(){return db.queryForObject("select count(*) from outbox_events where aggregate_id=?",Integer.class,attempt().getId());}

    @Test void completeBusinessSnapshotCommitsBeforeHttpAndMatchesPlacedRowsAndOutbox() throws Exception {
        CheckoutResult result=place();
        CheckoutDraft draft=freshLoad();
        assertEquals(draft,seenByPeer.get());
        assertEquals(result.orderId(),draft.orderId());
        assertEquals(customer,draft.customerId());
        assertEquals(RequestFingerprint.compute(request),draft.requestFingerprint());
        assertEquals(7L,draft.expectedRevision());assertEquals(8,draft.claimedCartRevision());
        assertEquals(shipping,draft.shipping());assertEquals("COD",draft.paymentMethod());
        assertEquals(new BigDecimal("41.18"),draft.total());
        assertEquals(List.of(false,true),draft.sellerOrders().stream().map(CheckoutDraft.Store::houseStore).toList());
        assertEquals(new BigDecimal("1.23"),draft.sellerOrders().get(0).platformFee());
        assertEquals(new BigDecimal("23.45"),draft.sellerOrders().get(0).sellerNet());
        for (var store:draft.sellerOrders()) {
            assertEquals(store.id(),db.queryForObject("select id from seller_orders where order_id=? and store_id=?",
                UUID.class,result.orderId(),store.storeId()));
            for(var item:store.items()) {
                Map<String,Object> row=db.queryForMap("select * from order_items where id=?",item.id());
                assertEquals(item.sku(),row.get("sku"));assertEquals(item.quantity(),row.get("quantity"));
                assertEquals(item.productName(),row.get("product_name"));
                assertEquals(item.unitPriceSnapshot(),row.get("unit_price_snapshot"));
                assertEquals(item.lineTotal(),row.get("line_total"));
            }
        }
        assertEquals(draft.outboxPayload(),db.queryForObject("select payload from outbox_events where aggregate_id=?",String.class,result.orderId()));
        assertEquals(result.orderId().toString(),json.readTree(draft.outboxPayload()).path("orderId").asText());
        String payload=db.queryForObject("select payload from checkout_order_drafts where checkout_attempt_id=?",String.class,result.orderId());
        assertFalse(payload.contains(KEY));assertFalse(payload.contains(BEARER));
        assertFalse(draft.toString().contains(shipping.address()));
        clearInvocations(catalog);
        assertEquals(result.orderId(),place().orderId());
        assertEquals(draft,freshLoad());assertEquals(1,sends.get());verifyNoInteractions(catalog);
    }

    @Test void unknownSurvivesNewServiceInstanceAndSameKeyNeverReservesOrFinalizes() {
        uncertain.set(true);
        assertThrows(ResponseStatusException.class,this::place);
        CheckoutDraft saved=freshLoad();
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(InventoryHttpAdapter.reserveId(saved.orderId())).state());
        clearInvocations(cart,catalog);
        assertThrows(ResponseStatusException.class,()->caller(inventory).checkout(customer,BEARER,KEY,request,shipping));
        assertEquals(saved,freshLoad());assertEquals(1,sends.get());
        assertEquals(0,orderCount());assertEquals(0,outboxCount());
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
        verifyNoInteractions(cart,catalog);
    }

    @Test void executionCutAfterDraftBeforeT1LeavesFactsForScannerWithoutAutomaticRecovery() {
        InventoryPort cut=mock(InventoryPort.class);
        doThrow(new SimulatedProcessCut()).when(cut).reserve(any(),anyList());
        assertThrows(SimulatedProcessCut.class,()->caller(cut).checkout(customer,BEARER,KEY,request,shipping));
        CheckoutDraft saved=freshLoad();
        assertEquals(CheckoutAttempt.State.PROCESSING,attempt().getState());
        assertEquals(0,db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?",Integer.class,saved.orderId()));
        db.update("update checkout_attempts set created_at=now()-interval '1 hour' where id=?",saved.orderId());
        scanner.flagAbandonedAttempts();
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
        assertThrows(ResponseStatusException.class,this::place);
        assertEquals(saved,freshLoad());assertEquals(0,sends.get());
        assertEquals(0,orderCount());assertEquals(0,outboxCount());
        verify(cart,never()).release(anyString(),anyString());verify(cart,never()).finalizeCart(anyString(),anyString());
    }
    private static class SimulatedProcessCut extends Error {}

    @Test void outboxTransactionRollbackRetainsExactDraftAndConfirmedReservation() {
        db.execute("alter table outbox_events add constraint tv3_draft_outbox_fault check(topic<>'order.placed.v1') not valid");
        try {
            assertThrows(ResponseStatusException.class,this::place);
            assertEquals(seenByPeer.get(),freshLoad());
            assertEquals(InventoryOperationJournal.State.CONFIRMED,journal.load(InventoryHttpAdapter.reserveId(attempt().getId())).state());
            assertEquals(0,orderCount());assertEquals(0,outboxCount());
            assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
            assertThrows(ResponseStatusException.class,this::place);assertEquals(1,sends.get());
            verify(cart,never()).release(anyString(),anyString());
        } finally {db.execute("alter table outbox_events drop constraint tv3_draft_outbox_fault");}
    }

    @Test void draftCommitFaultPreventsT1AndAllInventoryHttp() {
        db.execute("alter table checkout_order_drafts add constraint tv3_draft_insert_fault check(snapshot_version<>1) not valid");
        try {
            assertThrows(RuntimeException.class,this::place);
            assertEquals(0,sends.get());assertEquals(0,orderCount());assertEquals(0,outboxCount());
            assertEquals(0,db.queryForObject("select count(*) from checkout_order_drafts where checkout_attempt_id=?",Integer.class,attempt().getId()));
            assertEquals(0,db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?",Integer.class,attempt().getId()));
            assertEquals(CheckoutAttempt.State.FAILED,attempt().getState());
            verify(cart).release(BEARER,KEY);
        } finally {db.execute("alter table checkout_order_drafts drop constraint tv3_draft_insert_fault");}
    }

    private CheckoutDraft seed() {
        var a=keys.begin(customer,KEY,RequestFingerprint.compute(request)).attempt();
        Order o=new Order(a.getId(),customer);o.ship(shipping);
        SellerOrder s=new SellerOrder(o,"seller-a","store-a",new BigDecimal("24.68"),new BigDecimal("1.23"));
        s.getItems().add(new OrderItem(s,"p1",2,new BigDecimal("12.34"),new BigDecimal("24.68"),"SKU-A","Fixture A"));
        o.addSellerOrder(s);
        SellerOrder second=new SellerOrder(o,"seller-b","store-b",new BigDecimal("16.50"),new BigDecimal("0.00"));
        second.getItems().add(new OrderItem(second,"p2",3,new BigDecimal("5.50"),new BigDecimal("16.50"),"SKU-B","Fixture B"));
        o.addSellerOrder(second);
        String event=json.createObjectNode().put("eventId",UUID.randomUUID().toString()).put("orderId",o.getId().toString())
            .put("customerId",customer).put("sellerOrderCount",2).toString();
        return CheckoutDraft.capture(o,a.getFingerprint(),7L,8,Map.of("store-a",false,"store-b",true),event);
    }
    private CheckoutDraft changed(CheckoutDraft d,String customerId,String fingerprint,Order.Shipping address) {
        return new CheckoutDraft(d.version(),d.orderId(),customerId,fingerprint,d.expectedRevision(),d.claimedCartRevision(),
            d.createdAt(),d.paymentMethod(),address,d.total(),d.sellerOrders(),d.outboxPayload());
    }

    @Test void concurrentSameDraftIsOneRowAndChangedShippingCannotOverwriteIt() throws Exception {
        CheckoutDraft original=seed();
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<?>> tasks=new ArrayList<>();
            for(int i=0;i<2;i++) tasks.add(workers.submit(()->{start.await();drafts.prepare(original);return null;}));
            start.countDown();for(var task:tasks)task.get(10,TimeUnit.SECONDS);
        } finally {workers.shutdownNow();}
        assertEquals(1,db.queryForObject("select count(*) from checkout_order_drafts where checkout_attempt_id=?",Integer.class,original.orderId()));
        assertThrows(IllegalStateException.class,()->drafts.prepare(changed(original,customer,original.requestFingerprint(),
            new Order.Shipping("Different","other","other"))));
        assertEquals(original,freshLoad());assertEquals(0,sends.get());
    }

    @Test void wrongCustomerFingerprintOrClosedAttemptCannotWriteDraft() {
        CheckoutDraft original=seed();
        assertThrows(IllegalStateException.class,()->drafts.prepare(changed(original,"another-customer",original.requestFingerprint(),shipping)));
        assertThrows(IllegalStateException.class,()->drafts.prepare(changed(original,customer,"a".repeat(64),shipping)));
        keys.fail(original.orderId(),true);
        assertThrows(IllegalStateException.class,()->drafts.prepare(original));
        assertEquals(0,db.queryForObject("select count(*) from checkout_order_drafts where checkout_attempt_id=?",Integer.class,original.orderId()));
        assertEquals(0,sends.get());
    }

    @Test void corruptPayloadFailsClosedWithoutNetworkOrOrderCreation() {
        CheckoutDraft original=seed();drafts.prepare(original);
        db.update("update checkout_order_drafts set payload=replace(payload,'SKU-A','REMAPPED') where checkout_attempt_id=?",original.orderId());
        assertThrows(IllegalStateException.class,this::freshLoad);
        assertThrows(IllegalStateException.class,()->drafts.prepare(original));
        assertEquals(0,sends.get());assertEquals(0,orderCount());assertEquals(0,outboxCount());
    }

    @ParameterizedTest @ValueSource(strings={"AFTER_DRAFT","AFTER_T1","AFTER_RESERVE"})
    void committedDraftSurvivesAbruptChildJvmHaltWithoutCompletingOrReleasing(String point) throws Exception {
        CheckoutDraft original=seed();
        assertBusinessConsistent(original);
        // Gradle's test worker loads the runtime classpath via URLClassLoader rather than java.class.path alone.
        Set<String> classpath=new LinkedHashSet<>(List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for(ClassLoader loader=getClass().getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs())
                if("file".equals(url.getProtocol()))classpath.add(Path.of(url.toURI()).toString());
        // On Windows the Gradle test worker classpath may exceed CreateProcess' command limit.
        // Java 21 expands @argfile itself; keep the long classpath out of the OS command line.
        Path javaArgs = CrashJvmArgFile.writeClasspath(String.join(java.io.File.pathSeparator, classpath));
        try {
            ProcessBuilder builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "@"+javaArgs.toAbsolutePath(),CheckoutDraftCrashProcess.class.getName(),point,
                "http://127.0.0.1:"+peer.getAddress().getPort());
            builder.environment().put("TV3_CRASH_PG_URL",PG.getJdbcUrl());
            builder.environment().put("TV3_CRASH_PG_USER",PG.getUsername());
            builder.environment().put("TV3_CRASH_PG_PASSWORD",PG.getPassword());
            // No dump of child env/payload/logs: stdin contains only synthetic shipping data, never a live token.
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            Process child=builder.start();
            try {
                child.getOutputStream().write(json.writeValueAsBytes(original));child.getOutputStream().close();
                assertTrue(child.waitFor(20,TimeUnit.SECONDS),"Crash probe must terminate");
                assertEquals(86,child.exitValue(),"Child must reach the deliberate post-commit halt");
            } finally {if(child.isAlive())child.destroyForcibly().waitFor();}
        } finally {
            java.nio.file.Files.deleteIfExists(javaArgs);
        }
        var independentDb=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        var restored=new CheckoutDraftJournal(independentDb,json).load(original.orderId());
        assertEquals(original,restored);
        assertBusinessConsistent(restored);
        assertEquals(original.requestFingerprint(),independentDb.queryForObject(
            "select fingerprint from checkout_attempts where id=?",String.class,original.orderId()));
        if(point.equals("AFTER_RESERVE")) {
            assertEquals(InventoryOperationJournal.State.CONFIRMED,journal.load(InventoryHttpAdapter.reserveId(original.orderId())).state());
            assertEquals(original,seenByPeer.get());assertEquals(1,sends.get());
        } else if(point.equals("AFTER_T1")) {
            var saved=journal.load(InventoryHttpAdapter.reserveId(original.orderId()));
            assertEquals(InventoryOperationJournal.State.PENDING,saved.state());
            assertEquals(0,saved.sends());assertEquals(0,sends.get());
            assertEquals(original.orderId(),saved.intent().checkoutAttemptId());
            assertEquals(List.of(new InventoryOperationIntent.Line("SKU-A",2),new InventoryOperationIntent.Line("SKU-B",3)),saved.intent().lines());
        } else {
            assertEquals(0,sends.get());
            assertEquals(0,db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?",Integer.class,original.orderId()));
        }
        db.update("update checkout_attempts set created_at=now()-interval '1 hour' where id=?",original.orderId());
        scanner.flagAbandonedAttempts();
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
        assertThrows(ResponseStatusException.class,this::place);
        assertEquals(0,orderCount());assertEquals(0,outboxCount());
        verifyNoInteractions(cart,catalog);
    }

    private void assertBusinessConsistent(CheckoutDraft draft) throws Exception {
        var frozenRequest=new CheckoutRequest(draft.sellerOrders().stream().flatMap(s->s.items().stream())
            .map(i->new ExpectedItem(i.productId(),i.quantity())).toList(),draft.expectedRevision());
        assertEquals(RequestFingerprint.compute(request),RequestFingerprint.compute(frozenRequest),"Draft graph must match the request fingerprint");
        assertEquals(RequestFingerprint.compute(frozenRequest),draft.requestFingerprint());
        assertEquals(attempt().getId(),draft.orderId());assertEquals(customer,draft.customerId());
        assertEquals(7L,draft.expectedRevision());assertEquals(8,draft.claimedCartRevision());
        assertEquals(shipping,draft.shipping());assertEquals("COD",draft.paymentMethod());
        assertEquals(new BigDecimal("41.18"),draft.total());assertEquals(2,draft.sellerOrders().size());
        Map<String,CheckoutDraft.Store> stores=new HashMap<>();draft.sellerOrders().forEach(s->stores.put(s.storeId(),s));
        var a=stores.get("store-a");var b=stores.get("store-b");assertNotNull(a);assertNotNull(b);
        assertEquals("seller-a",a.sellerId());assertEquals("seller-b",b.sellerId());
        assertFalse(a.houseStore());assertTrue(b.houseStore());
        assertEquals(new BigDecimal("24.68"),a.gross());assertEquals(new BigDecimal("1.23"),a.platformFee());
        assertEquals(new BigDecimal("23.45"),a.sellerNet());
        assertEquals(new BigDecimal("16.50"),b.gross());assertEquals(new BigDecimal("0.00"),b.platformFee());
        assertEquals(new BigDecimal("16.50"),b.sellerNet());
        assertEquals(1,a.items().size());assertEquals(1,b.items().size());
        assertEquals("p1",a.items().get(0).productId());assertEquals("SKU-A",a.items().get(0).sku());
        assertEquals(2,a.items().get(0).quantity());assertEquals(new BigDecimal("12.34"),a.items().get(0).unitPriceSnapshot());
        assertEquals(new BigDecimal("24.68"),a.items().get(0).lineTotal());assertEquals("Fixture A",a.items().get(0).productName());
        assertEquals("p2",b.items().get(0).productId());assertEquals("SKU-B",b.items().get(0).sku());
        assertEquals(3,b.items().get(0).quantity());assertEquals(new BigDecimal("5.50"),b.items().get(0).unitPriceSnapshot());
        assertEquals(new BigDecimal("16.50"),b.items().get(0).lineTotal());assertEquals("Fixture B",b.items().get(0).productName());
        Set<UUID> ids=new HashSet<>();ids.add(draft.orderId());
        draft.sellerOrders().forEach(s->{assertTrue(ids.add(s.id()));s.items().forEach(i->assertTrue(ids.add(i.id())));});
        assertEquals(5,ids.size());
        var event=json.readTree(draft.outboxPayload());UUID.fromString(event.path("eventId").asText());
        assertEquals(draft.orderId().toString(),event.path("orderId").asText());
        assertEquals(customer,event.path("customerId").asText());assertEquals(2,event.path("sellerOrderCount").asInt());
    }

    @Test void realHttpReadTimeoutRetainsUnknownDraftAndCannotSendOrReleaseOnReplay() {
        holdResponse.set(true); // The fixture reads the committed request, but never replies inside the real 5s client timeout.
        ResponseStatusException error=assertThrows(ResponseStatusException.class,this::place);
        assertEquals(503,error.getStatusCode().value());
        boolean timedOut=false;
        for(Throwable cause=error;cause!=null;cause=cause.getCause())
            if(cause instanceof java.net.SocketTimeoutException)timedOut=true;
        assertTrue(timedOut,"Exercise an actual socket read timeout, not a mocked exception or HTTP 503");
        CheckoutDraft saved=freshLoad();
        String operation=InventoryHttpAdapter.reserveId(saved.orderId());
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(operation).state());
        assertEquals(1,journal.load(operation).sends());
        assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
        assertThrows(ResponseStatusException.class,this::place);
        var different=new CheckoutRequest(List.of(new ExpectedItem("p1",9)),7L);
        var conflict=assertThrows(ResponseStatusException.class,()->checkout.checkout(customer,BEARER,KEY,different,shipping));
        assertEquals(409,conflict.getStatusCode().value());
        assertEquals("IDEMPOTENCY_PAYLOAD_MISMATCH",conflict.getReason());
        assertEquals(1,sends.get());assertEquals(saved,freshLoad());
        assertEquals(0,orderCount());assertEquals(0,outboxCount());
        assertEquals(1,db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?",Integer.class,saved.orderId()));
        verify(cart,never()).release(anyString(),anyString());verify(cart,never()).finalizeCart(anyString(),anyString());
    }

    @Test void overlappingSameKeyCheckoutCreatesOneDraftOrderOutboxAndInventorySend() throws Exception {
        holdResponse.set(true);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<CheckoutResult> first=worker.submit(this::place);
            assertTrue(requestReceived.await(5,TimeUnit.SECONDS));
            var conflict=assertThrows(ResponseStatusException.class,this::place);
            assertEquals(409,conflict.getStatusCode().value());
            replyAllowed.countDown();
            CheckoutResult placed=first.get(10,TimeUnit.SECONDS);
            assertEquals(placed.orderId(),place().orderId());
            assertEquals(1,sends.get());assertEquals(1,orderCount());assertEquals(1,outboxCount());
            assertEquals(2,db.queryForObject("select count(*) from seller_orders where order_id=?",Integer.class,placed.orderId()));
            assertEquals(1,db.queryForObject("select count(*) from checkout_order_drafts where checkout_attempt_id=?",Integer.class,placed.orderId()));
            assertEquals(1,db.queryForObject("select count(*) from inventory_operation_journal where checkout_attempt_id=?",Integer.class,placed.orderId()));
            verify(cart,times(1)).claim(BEARER,KEY,7L);
            verify(cart,never()).release(anyString(),anyString());
        } finally {replyAllowed.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void lateHttpSuccessCannotOverwriteUnknownOrCommitOrderAfterStaleScan() throws Exception {
        holdResponse.set(true);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=worker.submit(()->assertThrows(ResponseStatusException.class,this::place));
            assertTrue(requestReceived.await(5,TimeUnit.SECONDS));
            String operation=InventoryHttpAdapter.reserveId(attempt().getId());
            db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",operation);
            assertEquals(1,journal.flagStaleInFlight(Duration.ofMinutes(10)));
            replyAllowed.countDown();first.get(10,TimeUnit.SECONDS);
            assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(operation).state());
            assertEquals(1,journal.load(operation).sends());
            assertEquals(CheckoutAttempt.State.NEEDS_RECONCILIATION,attempt().getState());
            assertEquals(seenByPeer.get(),freshLoad());
            assertThrows(ResponseStatusException.class,this::place);
            assertEquals(1,sends.get());assertEquals(0,orderCount());assertEquals(0,outboxCount());
            verify(cart,never()).release(anyString(),anyString());verify(cart,never()).finalizeCart(anyString(),anyString());
        } finally {replyAllowed.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
