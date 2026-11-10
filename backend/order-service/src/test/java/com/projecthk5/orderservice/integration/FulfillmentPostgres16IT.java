package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.*;
import com.projecthk5.security.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL 16 + Flyway V3: HTTP role rules, Store scoping, row locks, outbox side effects, COD and finance. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.outbox.relay-enabled=false")
@AutoConfigureMockMvc
class FulfillmentPostgres16IT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://test-issuer.example.invalid");
        r.add("app.security.audience", () -> "ecommerce");
        r.add("app.security.current-kid", () -> "test-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired MockMvc mvc;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate sql;
    @MockBean StoreDirectory stores;

    private final String store1 = UUID.randomUUID().toString(), store2 = UUID.randomUUID().toString();
    private final String customer = "customer-" + UUID.randomUUID();
    private Order order;
    private SellerOrder so1, so2;

    @BeforeEach
    void seed() {
        when(stores.managedBy("owner-1")).thenReturn(store(store1, "owner-1"));
        when(stores.managedBy("owner-2")).thenReturn(store(store2, "owner-2"));
        when(stores.managedBy("no-store")).thenThrow(ApiException.forbidden("This account does not manage a Store"));
        when(stores.facts(anySet())).thenReturn(List.of(store(store1, "owner-1"), store(store2, "owner-2")));
        CheckoutAttempt a = attempts.saveAndFlush(new CheckoutAttempt(customer, "k-" + UUID.randomUUID(), "fp"));
        order = new Order(a.getId(), customer);
        order.ship(new Order.Shipping("Nguyen Van A", "0901234567", "1 Le Loi"));
        so1 = new SellerOrder(order, "seller-1", store1, new BigDecimal("200000"), new BigDecimal("10000"));
        so1.getItems().add(new OrderItem(so1, "p1", 2, new BigDecimal("100000"), new BigDecimal("200000"), "SKU-1", "Ao"));
        so2 = new SellerOrder(order, "seller-2", store2, new BigDecimal("50000"), BigDecimal.ZERO);
        so2.getItems().add(new OrderItem(so2, "p2", 1, new BigDecimal("50000"), new BigDecimal("50000"), "SKU-2", "Mu"));
        order.addSellerOrder(so1);
        order.addSellerOrder(so2);
        orders.saveAndFlush(order);
    }

    private static StoreDirectory.Store store(String id, String owner) {
        return new StoreDirectory.Store(id, "seller-" + owner, owner, "Shop " + owner, "shop-" + owner, true, true, false);
    }

    private static StoreDirectory.Store suspended(String id, String owner) {
        return new StoreDirectory.Store(id, "seller-" + owner, owner, "Shop " + owner, "shop-" + owner, true, false, false);
    }

    private static RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(j -> j.subject(subject).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void act(String owner, SellerOrder so, String action, String reason, int expected) throws Exception {
        mvc.perform(post("/api/seller/orders/{id}/{a}", so.getId(), action).with(as(owner, "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().is(expected));
    }

    private int outbox(SellerOrder so, String like) {
        return sql.queryForObject("select count(*) from outbox_events where aggregate_id=? and payload like ?",
                Integer.class, so.getId(), "%" + like + "%");
    }

    @Test
    void rolesAreEnforcedPerRoute() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_REQUIRED"));
        mvc.perform(get("/api/seller/orders").with(as(customer, "CUSTOMER"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mvc.perform(get("/api/admin/finance").with(as("owner-1", "SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/seller/orders").with(as("no-store", "SELLER"))).andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyOwnOrdersWithShippingAndStoreSplit() throws Exception {
        mvc.perform(get("/api/orders/{id}", order.getId()).with(as(customer, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping.phone").value("0901234567"))
                .andExpect(jsonPath("$.storeOrders.length()").value(2));
        mvc.perform(get("/api/orders/{id}", order.getId()).with(as("someone-else", "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").with(as(customer, "CUSTOMER")))
                .andExpect(jsonPath("$[0].id").value(order.getId().toString()));
    }

    @Test
    void sellerListsAndActsOnlyOnOwnStore() throws Exception {
        mvc.perform(get("/api/seller/orders").with(as("owner-1", "SELLER")))
                .andExpect(jsonPath("$[?(@.id=='" + so1.getId() + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + so2.getId() + "')]").doesNotExist());
        mvc.perform(get("/api/seller/orders?status=ACCEPTED").with(as("owner-1", "SELLER")))
                .andExpect(jsonPath("$[?(@.id=='" + so1.getId() + "')]").doesNotExist());
        act("owner-2", so1, "accept", null, 404);
        act("owner-1", so1, "deliver", null, 409);
    }

    @Test
    void fullCodFlowWritesSideEffectsAndFinance() throws Exception {
        for (String a : new String[]{"accept", "prepare", "ready"}) act("owner-1", so1, a, null, 200);
        assertEquals(0, outbox(so1, "\"CONSUME\""));
        act("owner-1", so1, "dispatch", null, 200);
        assertEquals(1, outbox(so1, "\"CONSUME\""));
        act("owner-1", so1, "deliver", null, 200);
        assertEquals(1, outbox(so1, "\"CONSUME\""));
        assertEquals("DELIVERED", sql.queryForObject("select status from seller_orders where id=?", String.class, so1.getId()));

        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId()).with(as("admin", "ADMIN"))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId()).with(as("admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codStatus").value("COLLECTED"));
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId()).with(as("admin", "ADMIN"))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId()).with(as("admin", "ADMIN"))).andExpect(status().isOk());

        mvc.perform(get("/api/seller/finance").with(as("owner-1", "SELLER")))
                .andExpect(jsonPath("$.totals.deliveredOrders").value(1))
                .andExpect(jsonPath("$.totals.sellerNet").value(190000))
                .andExpect(jsonPath("$.totals.settledNet").value(190000));
        mvc.perform(get("/api/admin/finance").with(as("admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.platformFee").isNumber());
    }

    @Test
    void concurrentSettlementPaysOneSellerOrderOnlyOnce() throws Exception {
        for (String action : new String[]{"accept", "prepare", "ready", "dispatch", "deliver"}) {
            act("owner-1", so1, action, null, 200);
        }
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> settle = () -> {
                start.await();
                return mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId())
                        .with(as("admin", "ADMIN"))).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(settle), second = pool.submit(settle);
            start.countDown();
            List<Integer> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertTrue(results.contains(200) && results.contains(409), results.toString());
            assertEquals(1, outbox(so1, "\"SETTLED\""));
            assertNotNull(sql.queryForObject("select settled_at from seller_orders where id=?", java.sql.Timestamp.class, so1.getId()));
            mvc.perform(get("/api/seller/finance").with(as("owner-1", "SELLER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.settledNet").value(190000));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void suspendedSellerFinishesOpenOrdersButTakesNothingNewAndSeesNoFinance() throws Exception {
        act("owner-1", so1, "accept", null, 200);
        when(stores.managedBy("owner-1")).thenReturn(suspended(store1, "owner-1"));
        when(stores.managedBy("owner-2")).thenReturn(suspended(store2, "owner-2"));

        act("owner-2", so2, "accept", null, 403);
        act("owner-1", so2, "reject", "het hang", 404);
        act("owner-2", so2, "reject", "het hang", 200);
        act("owner-1", so1, "prepare", null, 200);
        act("owner-1", so1, "ready", null, 200);
        mvc.perform(post("/api/seller/orders/{id}/dispatch", so1.getId()).with(as("owner-1", "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.gross").value(200000))
                .andExpect(jsonPath("$.platformFee").doesNotExist());
        act("owner-1", so1, "deliver", null, 200);

        mvc.perform(get("/api/seller/orders/{id}", so1.getId()).with(as("owner-1", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gross").value(200000))
                .andExpect(jsonPath("$.shipping.phone").value("0901234567"))
                .andExpect(jsonPath("$.platformFee").doesNotExist())
                .andExpect(jsonPath("$.sellerNet").doesNotExist())
                .andExpect(jsonPath("$.codStatus").doesNotExist())
                .andExpect(jsonPath("$.settledAt").doesNotExist());
        mvc.perform(get("/api/seller/orders").with(as("owner-1", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + so1.getId() + "')].gross").value(200000.0))
                .andExpect(jsonPath("$[?(@.id=='" + so1.getId() + "')].sellerNet").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/seller/finance").with(as("owner-1", "SELLER"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        when(stores.managedBy("owner-1")).thenReturn(store(store1, "owner-1"));
        mvc.perform(get("/api/seller/finance").with(as("owner-1", "SELLER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sellerNet").value(190000));
        mvc.perform(get("/api/seller/orders/{id}", so1.getId()).with(as("owner-1", "SELLER")))
                .andExpect(jsonPath("$.sellerNet").value(190000));
    }

    @Test
    void financeFailsClosedWhenSellerFactsAreUnavailable() throws Exception {
        when(stores.managedBy("owner-1")).thenThrow(ApiException.unavailable("STORE_DIRECTORY_UNAVAILABLE", "Store facts unavailable"));
        mvc.perform(get("/api/seller/finance").with(as("owner-1", "SELLER"))).andExpect(status().isServiceUnavailable());
    }

    @Test
    void customerCancelAndSellerRejectReleaseStockAndCloseOrder() throws Exception {
        mvc.perform(post("/api/orders/{o}/store-orders/{s}/cancel", order.getId(), so1.getId()).with(as("intruder", "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{o}/store-orders/{s}/cancel", order.getId(), so1.getId()).with(as(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"doi y\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"));
        act("owner-2", so2, "reject", null, 400);
        act("owner-2", so2, "reject", "het hang", 200);
        assertEquals(1, outbox(so1, "\"RELEASE\""));
        assertEquals(1, outbox(so2, "\"RELEASE\""));
        assertEquals("CANCELLED", sql.queryForObject("select status from orders where id=?", String.class, order.getId()));
        assertEquals("VOID", sql.queryForObject("select cod_status from seller_orders where id=?", String.class, so2.getId()));
    }

    @Test
    void concurrentAcceptsApplyOnceUnderRowLock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> accept = () -> {
                start.await();
                return mvc.perform(post("/api/seller/orders/{id}/accept", so1.getId()).with(as("owner-1", "SELLER")))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(accept), b = pool.submit(accept);
            start.countDown();
            List<Integer> results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertTrue(results.contains(200) && results.contains(409), results.toString());
            assertEquals(1, outbox(so1, "ORDER_ACCEPTED"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseRejectsCodCollectionBeforeDelivery() {
        assertThrows(Exception.class, () -> sql.update("update seller_orders set cod_status='COLLECTED' where id=?", so2.getId()));
    }

    @Test
    void databaseRequiresNonblankReceiptOnlyForReceivedReturn() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "receipt-db-guard", "Receipt guard").andExpect(status().isCreated());
        UUID returnId = sql.queryForObject("select id from return_requests where seller_order_id=?", UUID.class, so1.getId());

        // Direct SQL must not bypass the API's receipt normalization.  The seller-review fields
        // satisfy V6 so each failure isolates the V8 receipt invariant.
        for (String invalid : new String[]{null, "", " \t\n "}) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                    "update return_requests set status='RECEIVED',reviewed_by='owner-1',reviewed_at=now(),"
                            + "received_by='owner-1',received_at=now(),receipt_reference=?,updated_at=now() where id=?",
                    invalid, returnId));
            assertEquals("REQUESTED", sql.queryForObject("select status from return_requests where id=?", String.class, returnId));
        }
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "update return_requests set receipt_reference='wrong-state' where id=?", returnId));

        assertEquals(1, sql.update("update return_requests set status='RECEIVED',reviewed_by='owner-1',reviewed_at=now(),"
                        + "received_by='owner-1',received_at=now(),receipt_reference='receipt-db-8',updated_at=now() where id=?",
                returnId));
        assertEquals("RECEIVED", sql.queryForObject("select status from return_requests where id=?", String.class, returnId));
    }

    // #155 RET-01/02/04/06: These tests run MockMvc + real PostgreSQL16 with StoreDirectory
    // mocked. They are NOT full Gateway/Inventory/React E2E or a refund authorization.
    private void delivered(String owner, SellerOrder order) throws Exception {
        for (String action : new String[]{"accept", "prepare", "ready", "dispatch", "deliver"}) {
            act(owner, order, action, null, 200);
        }
    }

    private org.springframework.test.web.servlet.ResultActions requestReturn(SellerOrder sellerOrder,
            String buyer, String key, String reason) throws Exception {
        return mvc.perform(post("/api/orders/{id}/store-orders/{storeId}/returns",
                order.getId(), sellerOrder.getId())
                .with(as(buyer, "CUSTOMER"))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions sellerReturn(String owner, UUID returnId,
            String action, String receiptReference) throws Exception {
        String body = receiptReference == null ? "{}" : "{\"receiptReference\":\"" + receiptReference + "\"}";
        return mvc.perform(post("/api/seller/returns/{id}/{action}", returnId, action)
                .with(as(owner, "SELLER")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int returnRows(SellerOrder sellerOrder) {
        return sql.queryForObject("select count(*) from return_requests where seller_order_id=?",
                Integer.class, sellerOrder.getId());
    }

    @Test
    void buyerReturnIsScopedToOneDeliveredStoreOrderAndReadBack() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, "wrong-buyer", "wrong-key", "Hang khong dung")
                .andExpect(status().isNotFound());
        requestReturn(so2, customer, "too-early", "Hang khong dung")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_ELIGIBLE"));
        mvc.perform(post("/api/orders/{id}/store-orders/{store}/returns", order.getId(), so1.getId())
                .with(as("owner-1", "SELLER")).header("Idempotency-Key", "not-a-buyer")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Wrong role\"}"))
                .andExpect(status().isForbidden());

        requestReturn(so1, customer, "key-one", "Hang khong dung")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.storeOrderId").value(so1.getId().toString()));
        assertEquals(1, returnRows(so1));
        assertEquals(0, returnRows(so2));
        assertEquals(0, outbox(so1, "\"RELEASE\""));
        assertEquals(1, outbox(so1, "RETURN_REQUESTED"));

        mvc.perform(get("/api/orders/{id}/returns", order.getId()).with(as(customer, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Hang khong dung"))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));
        mvc.perform(get("/api/orders/{id}/returns", order.getId())
                .with(as("wrong-buyer", "CUSTOMER"))).andExpect(status().isNotFound());
    }

    @Test
    void adminReadsReturnsButOnlyAdminAndUnknownIdIs404() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "admin-view", "Hang loi").andExpect(status().isCreated());
        UUID id = sql.queryForObject("select id from return_requests where seller_order_id=?", UUID.class, so1.getId());

        mvc.perform(get("/api/admin/returns").with(as("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.requestId=='" + id + "')].storeId").value(so1.getStoreId()))
                .andExpect(jsonPath("$[?(@.requestId=='" + id + "')].status").value("REQUESTED"));
        mvc.perform(get("/api/admin/returns?status=REJECTED").with(as("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.requestId=='" + id + "')]").isEmpty());
        mvc.perform(get("/api/admin/returns?status=BOGUS").with(as("admin", "ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/returns/{id}", id).with(as("admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Hang loi"));
        mvc.perform(get("/api/admin/returns/{id}", UUID.randomUUID()).with(as("admin", "ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
        mvc.perform(get("/api/admin/returns").with(as("owner-1", "SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/returns/{id}", id).with(as(customer, "CUSTOMER"))).andExpect(status().isForbidden());
        // Read-only: no Admin write route for Return exists.
        mvc.perform(post("/api/admin/returns/{id}", id).with(as("admin", "ADMIN")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void duplicateReturnKeyReplaysStableRequestWithoutExtraOutbox() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "same-key", "Sai kich co").andExpect(status().isCreated());
        UUID originalId = sql.queryForObject("select id from return_requests where seller_order_id=?",
                UUID.class, so1.getId());

        requestReturn(so1, customer, "same-key", "Sai kich co")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(originalId.toString()));
        requestReturn(so1, customer, "same-key", "Khac ly do")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_IDEMPOTENCY_MISMATCH"));
        requestReturn(so1, customer, "different-key", "Sai kich co")
                .andExpect(status().isConflict());
        delivered("owner-2", so2);
        requestReturn(so2, customer, "same-key", "Sai kich co")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_IDEMPOTENCY_MISMATCH"));
        assertEquals(1, returnRows(so1));
        assertEquals(0, returnRows(so2));
        assertEquals(1, outbox(so1, "RETURN_REQUESTED"));
    }

    @Test
    void returnTimeWindowAndValidationFailClosed() throws Exception {
        delivered("owner-1", so1);
        sql.update("update seller_orders set delivered_at=? where id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8))), so1.getId());
        requestReturn(so1, customer, "expired-key", "Qua ngay")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_ELIGIBLE"));
        assertEquals(0, returnRows(so1));
        requestReturn(so1, customer, "invalid-key", "")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/{id}/store-orders/{store}/returns", order.getId(), so1.getId())
                .with(as(customer, "CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, returnRows(so1));
    }

    @Test
    void pendingReturnBlocksSettlementButCollectCodStillRecordsCash() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "guard-key", "Tra hang")
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId())
                .with(as("admin", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_RETURN_OPEN"));
        assertEquals(0, outbox(so1, "\"SETTLED\""));
        delivered("owner-2", so2);
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so2.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so2.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());
        assertEquals(1, outbox(so2, "\"SETTLED\""));
    }

    @Test
    void concurrentReturnAndSettlementCannotBothCommit() throws Exception {
        delivered("owner-1", so1);
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> buyer = () -> {
                start.await();
                return requestReturn(so1, customer, "racing-key", "Tra hang")
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> finance = () -> {
                start.await();
                return mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId())
                        .with(as("admin", "ADMIN"))).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(buyer), second = pool.submit(finance);
            start.countDown();
            int resultBuyer = first.get(30, TimeUnit.SECONDS);
            int resultFinance = second.get(30, TimeUnit.SECONDS);
            assertTrue((resultBuyer == 201 && resultFinance == 409)
                    || (resultBuyer == 409 && resultFinance == 200),
                    "Return and settle cannot both win: " + resultBuyer + "/" + resultFinance);

            int active = sql.queryForObject("select count(*) from return_requests "
                    + "where seller_order_id=? and status='REQUESTED'", Integer.class, so1.getId());
            int settled = sql.queryForObject("select count(*) from seller_orders "
                    + "where id=? and settled_at is not null", Integer.class, so1.getId());
            assertEquals(1, active + settled);
            assertEquals(settled, outbox(so1, "\"SETTLED\""));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void utf16ReasonDatabaseBoundaryAndReplayAfterExpiry() throws Exception {
        assertEquals(2, sql.queryForObject("select hk5_order_return_utf16_length(?)",
                Integer.class, "😀"));
        assertEquals(1, sql.queryForObject("select hk5_order_return_utf16_length(?)",
                Integer.class, "á"));
        // Direct SQL must also reject >500 UTF-16 units, not only controller validation.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "insert into return_requests (id,order_id,seller_order_id,customer_id,idempotency_key,"
                + "request_fingerprint,reason,status,requested_at,updated_at) "
                + "values (?,?,?,?,?,?,?,'REQUESTED',now(),now())",
                UUID.randomUUID(), order.getId(), so1.getId(), customer, "direct-invalid",
                "a".repeat(64), "😀".repeat(251)));
        delivered("owner-1", so1);
        requestReturn(so1, customer, "expiry-replay", "Hop le").andExpect(status().isCreated());
        UUID id = sql.queryForObject("select id from return_requests where seller_order_id=?",
                UUID.class, so1.getId());
        // A previously accepted idempotency key must replay the same durable result
        // even after the delivery deadline; new keys must not create a Return then.
        sql.update("update seller_orders set delivered_at=? where id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(9))), so1.getId());
        requestReturn(so1, customer, "expiry-replay", "Hop le")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id.toString()));
        requestReturn(so1, customer, "late-new-key", "Hop le").andExpect(status().isConflict());
        assertEquals(1, returnRows(so1));
        mvc.perform(post("/api/orders/{id}/store-orders/{storeId}/returns", order.getId(), so1.getId())
                .with(as(customer, "CUSTOMER")).header("Idempotency-Key", "emoji-too-long")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "😀".repeat(251) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedReturnAllowsNewReturnAndReceivedStillBlocksSettlement() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "first-return", "Size").andExpect(status().isCreated());
        // TV4 will implement the authenticated Seller transitions. Only this isolated
        // DB test simulates a verified Seller's REJECTED/RECEIVED state for guard contract.
        sql.update("update return_requests set status='REJECTED', reviewed_by='owner-1', "
                + "reviewed_at=now(), updated_at=now() where seller_order_id=?", so1.getId());
        requestReturn(so1, customer, "second-return", "Wrong color")
                .andExpect(status().isCreated());
        assertEquals(2, returnRows(so1));
        sql.update("update return_requests set status='APPROVED', reviewed_by='owner-1', "
                + "reviewed_at=now(), updated_at=now() "
                + "where seller_order_id=? and status='REQUESTED'", so1.getId());
        sql.update("update return_requests set status='RECEIVED', received_by='owner-1', "
                + "received_at=now(), receipt_reference='fixture-receipt', updated_at=now() "
                + "where seller_order_id=? and status='APPROVED'", so1.getId());
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId())
                .with(as("admin", "ADMIN"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_RETURN_OPEN"));
        assertEquals(0, outbox(so1, "\"RELEASE\""));
        assertEquals(0, outbox(so1, "\"SETTLED\""));
    }

    @Test
    void sellerReturnWorkflowIsStoreScopedAndReceivedNeedsDurableReceiptEvidence() throws Exception {
        delivered("owner-1", so1);
        BigDecimal grossBeforeReturn = sql.queryForObject(
                "select gross from seller_orders where id=?", BigDecimal.class, so1.getId());
        BigDecimal feeBeforeReturn = sql.queryForObject(
                "select platform_fee from seller_orders where id=?", BigDecimal.class, so1.getId());
        requestReturn(so1, customer, "seller-workflow", "Sai kich co").andExpect(status().isCreated());
        UUID returnId = sql.queryForObject("select id from return_requests where seller_order_id=?", UUID.class, so1.getId());

        mvc.perform(get("/api/seller/returns").with(as("owner-1", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.requestId=='" + returnId + "')].reason").value("Sai kich co"));
        mvc.perform(get("/api/seller/returns").with(as("owner-2", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.requestId=='" + returnId + "')]").doesNotExist());
        sellerReturn("owner-2", returnId, "approve", null).andExpect(status().isNotFound());
        mvc.perform(post("/api/seller/returns/{id}/approve", returnId).with(as("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        sellerReturn("owner-1", returnId, "receive", "receipt-before-approval").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_INVALID_TRANSITION"));
        sellerReturn("owner-1", returnId, "approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        sellerReturn("owner-1", returnId, "approve", null).andExpect(status().isConflict());
        sellerReturn("owner-1", returnId, "receive", null).andExpect(status().isBadRequest());
        sellerReturn("owner-1", returnId, "receive", " receipt-123 ").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receiptReference").value("receipt-123"));
        assertEquals("RECEIVED", sql.queryForObject("select status from return_requests where id=?", String.class, returnId));
        assertEquals("receipt-123", sql.queryForObject("select receipt_reference from return_requests where id=?", String.class, returnId));
        assertEquals(0, outbox(so1, "\"INCREASE\""));
        assertEquals(0, outbox(so1, "RETURN_APPROVE"));
        assertEquals(0, outbox(so1, "RETURN_RECEIVE"));
        assertEquals(0, outbox(so1, "\"RELEASE\""));
        // Physical receipt is neither a money adjustment nor proof that the seller was paid.
        assertEquals(0, grossBeforeReturn.compareTo(sql.queryForObject(
                "select gross from seller_orders where id=?", BigDecimal.class, so1.getId())));
        assertEquals(0, feeBeforeReturn.compareTo(sql.queryForObject(
                "select platform_fee from seller_orders where id=?", BigDecimal.class, so1.getId())));
        assertEquals("PENDING", sql.queryForObject(
                "select cod_status from seller_orders where id=?", String.class, so1.getId()));
        assertNull(sql.queryForObject(
                "select settled_at from seller_orders where id=?", java.sql.Timestamp.class, so1.getId()));

        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId()).with(as("admin", "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId()).with(as("admin", "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETTLEMENT_RETURN_OPEN"));
        assertNull(sql.queryForObject(
                "select settled_at from seller_orders where id=?", java.sql.Timestamp.class, so1.getId()));
    }

    @Test
    void rejectedReturnIsTheOnlySellerCloseThatReopensSettlement() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "seller-reject", "Khong con nhu cau").andExpect(status().isCreated());
        UUID returnId = sql.queryForObject("select id from return_requests where seller_order_id=?", UUID.class, so1.getId());
        sellerReturn("owner-1", returnId, "reject", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/admin/store-orders/{id}/cod-collected", so1.getId()).with(as("admin", "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/store-orders/{id}/settle", so1.getId()).with(as("admin", "ADMIN")))
                .andExpect(status().isOk());
        assertEquals(1, outbox(so1, "\"SETTLED\""));
        assertEquals(0, outbox(so1, "\"INCREASE\""));
    }

    @Test
    void concurrentReceiveHasOneWinnerAndNeverCreatesAnInventoryMutation() throws Exception {
        delivered("owner-1", so1);
        requestReturn(so1, customer, "seller-concurrent-receive", "Bi loi").andExpect(status().isCreated());
        UUID returnId = sql.queryForObject("select id from return_requests where seller_order_id=?", UUID.class, so1.getId());
        sellerReturn("owner-1", returnId, "approve", null).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> receive = () -> {
                start.await();
                return sellerReturn("owner-1", returnId, "receive", "physical-receipt-9")
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(receive), b = pool.submit(receive);
            start.countDown();
            List<Integer> outcomes = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertTrue(outcomes.contains(200) && outcomes.contains(409), outcomes.toString());
            assertEquals("RECEIVED", sql.queryForObject("select status from return_requests where id=?", String.class, returnId));
            assertEquals(0, outbox(so1, "RETURN_RECEIVE"));
            assertEquals(0, outbox(so1, "\"INCREASE\""));
            assertEquals(0, outbox(so1, "\"RELEASE\""));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseRejectsWrongRootOrderAndDuplicateActiveReturn() throws Exception {
        Order unrelatedRoot = orders.saveAndFlush(new Order(UUID.randomUUID(), "another-customer"));
        // Both FK targets exist. The composite FK must still reject a cross-Order
        // SellerOrder reference, including from a future non-HTTP integration.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "insert into return_requests (id,order_id,seller_order_id,customer_id,idempotency_key,"
                + "request_fingerprint,reason,status,requested_at,updated_at) "
                + "values (?,?,?,?,?,?,?,'REQUESTED',now(),now())",
                UUID.randomUUID(), unrelatedRoot.getId(), so1.getId(), customer,
                "wrong-root", "a".repeat(64), "valid"));

        delivered("owner-1", so1);
        requestReturn(so1, customer, "primary-return", "Damaged item")
                .andExpect(status().isCreated());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "insert into return_requests (id,order_id,seller_order_id,customer_id,idempotency_key,"
                + "request_fingerprint,reason,status,requested_at,updated_at) "
                + "values (?,?,?,?,?,?,?,'REQUESTED',now(),now())",
                UUID.randomUUID(), order.getId(), so1.getId(), customer,
                "second-active", "b".repeat(64), "Second request"));
        assertEquals(1, returnRows(so1));
    }

    @Test
    void concurrentReuseOfOneCustomerKeyAcrossTwoStoresCreatesOnlyOneRequest() throws Exception {
        delivered("owner-1", so1);
        delivered("owner-2", so2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> one = () -> {
                start.await();
                return requestReturn(so1, customer, "cross-store-race", "Ly do")
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> two = () -> {
                start.await();
                return requestReturn(so2, customer, "cross-store-race", "Ly do")
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(one), b = pool.submit(two);
            start.countDown();
            int resultA = a.get(30, TimeUnit.SECONDS), resultB = b.get(30, TimeUnit.SECONDS);
            assertTrue((resultA == 201 && resultB == 409) || (resultA == 409 && resultB == 201),
                    "Global customer key must have one winner: " + resultA + "/" + resultB);
            assertEquals(1, returnRows(so1) + returnRows(so2));
            assertEquals(1, outbox(so1, "RETURN_REQUESTED") + outbox(so2, "RETURN_REQUESTED"));
        } finally {
            pool.shutdownNow();
        }
    }
}
