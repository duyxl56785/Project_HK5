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
        for (String a : new String[]{"accept", "prepare", "ready", "dispatch", "deliver"}) act("owner-1", so1, a, null, 200);
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
}
