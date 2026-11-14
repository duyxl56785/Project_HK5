package com.projecthk5.productservice.promotion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import com.projecthk5.productservice.web.SellerStoreClient;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgresql")
@SpringBootTest
@AutoConfigureMockMvc
class PromotionHttpPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired PromotionService promotions;
    @Autowired com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter persistence;
    @MockBean SellerStoreClient stores;

    private record Fixture(UUID campaign, UUID product, UUID store) {
        String adminDecision() { return "/api/admin/promotions/" + campaign + "/products/" + product + "/decision"; }
        String sellerWithdraw() { return "/api/seller/promotions/" + campaign + "/products/" + product + "/withdraw"; }
    }

    private Fixture fixture() {
        var store = UUID.randomUUID();
        var category = UUID.randomUUID();
        var product = UUID.randomUUID();
        db.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Promo " + category);
        db.update("""
                INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status)
                VALUES (?,?,?,?,?,100000,'VND','ACTIVE')
                """, product, store, category, product.toString(), "Promotion product");
        when(stores.readStore(store)).thenReturn(new StoreAccess(store, UUID.randomUUID(), "owner", true, true, false));
        return campaign(product, store);
    }

    private Fixture campaign(UUID product, UUID store) {
        var campaign = UUID.randomUUID();
        db.update("""
                INSERT INTO promotion_campaigns(id,name,kind,status,store_id,starts_at,ends_at,submitted_at)
                VALUES (?,'Clearance','SELLER_CLEARANCE','APPROVED',?,
                        now()+interval '2 hours',now()+interval '4 hours',now())
                """, campaign, store);
        db.update("""
                INSERT INTO promotion_campaign_products(campaign_id,product_id,promotional_unit_price)
                VALUES (?,?,80000)
                """, campaign, product);
        return new Fixture(campaign, product, store);
    }

    static JwtRequestPostProcessor token(String actor, String role) {
        return jwt().jwt(j -> j.subject(actor).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void concurrentApprovalCommitsOnlyOneOfferAndAudit() throws Exception {
        var first = fixture();
        var second = campaign(first.product(), first.store());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> approve(first, "admin-a", ready, start));
            var b = pool.submit(() -> approve(second, "admin-b", ready, start));
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            assertEquals(List.of(200,409), List.of(a.get(20, TimeUnit.SECONDS),
                    b.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1, db.queryForObject("""
                SELECT count(*) FROM promotion_campaign_products WHERE product_id=? AND status='APPROVED'
                """, Integer.class, first.product()));
        assertEquals(1, db.queryForObject("""
                SELECT count(*) FROM promotion_campaign_audit WHERE product_id=? AND to_status='APPROVED'
                """, Integer.class, first.product()));
    }

    @Test
    void approvalAndBasePriceUpdateCannotCommitAnInvalidPair() throws Exception {
        var f = fixture();
        var product = persistence.findById(f.product()).orElseThrow().listing();
        var newBase = new com.projecthk5.productservice.product.ProductListing(product.id(), product.storeId(),
                product.categoryId(), product.sku(), product.name(), java.math.BigDecimal.valueOf(70000),
                product.currency(), product.status(), product.reviewReason());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var approval = pool.submit(() -> approve(f, "admin", ready, start));
            var edit = pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                try {
                    persistence.update(newBase, 0);
                    return 200;
                } catch (com.projecthk5.security.ApiException conflict) {
                    assertEquals("PROMOTION_PRICE_CONFLICT", conflict.code());
                    return conflict.status().value();
                }
            });
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            assertEquals(List.of(200,409), List.of(approval.get(20, TimeUnit.SECONDS),
                    edit.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(0, db.queryForObject("""
                SELECT count(*) FROM promotion_campaign_products r JOIN products p ON p.id=r.product_id
                WHERE p.id=? AND r.status='APPROVED' AND r.promotional_unit_price>=p.unit_price
                """, Integer.class, f.product()));
    }

    private int approve(Fixture f, String actor, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
        return http.perform(post(f.adminDecision()).with(token(actor,"ADMIN")).contentType("application/json")
                .content("{\"decision\":\"approve\",\"expectedVersion\":\"0\"}")).andReturn().getResponse().getStatus();
    }

    @Test
    void flashQuoteChangesAtVietnamBoundariesAndKeepsStoredBase() {
        var f = fixture();
        var flash = UUID.randomUUID();
        db.update("""
                INSERT INTO promotion_campaigns(id,name,kind,status,flash_date,starts_at,ends_at)
                VALUES (?,'Flash','FLASH_SALE','APPROVED','2026-11-14','2026-11-14T15:00:00Z','2026-11-14T17:00:00Z')
                """, flash);
        db.update("""
                INSERT INTO promotion_campaign_products(campaign_id,product_id,promotional_unit_price,status)
                VALUES (?,?,65000,'APPROVED')
                """, flash, f.product());
        var seller = stores.readStore(f.store()).sellerId();
        var base = java.math.BigDecimal.valueOf(100000);
        var start = java.time.Instant.parse("2026-11-14T15:00:00Z");
        var end = java.time.Instant.parse("2026-11-14T17:00:00Z");
        assertNull(promotions.quote(f.product(), f.store(), seller, base, true, start.minusSeconds(1)).promotion());
        var live = promotions.quote(f.product(), f.store(), seller, base, true, start);
        assertEquals(flash, live.promotion().campaignId());
        assertEquals(65000, live.unitPrice().intValueExact());
        assertNull(promotions.quote(f.product(), f.store(), seller, base, false, start).promotion());
        assertNull(promotions.quote(f.product(), f.store(), seller, base, true, end).promotion());
        assertEquals(100000, db.queryForObject("SELECT unit_price FROM products WHERE id=?",Integer.class,f.product()));
    }

    @Test
    void securityAndStrictPayloadGuardMutation() throws Exception {
        var f = fixture();
        String approval = "{\"decision\":\"approve\",\"expectedVersion\":\"0\"}";
        assertEquals(401, http.perform(post(f.adminDecision()).contentType("application/json").content(approval))
                .andReturn().getResponse().getStatus());
        assertEquals(403, http.perform(post(f.adminDecision()).with(token("owner","SELLER"))
                .contentType("application/json").content(approval)).andReturn().getResponse().getStatus());
        assertEquals(403, http.perform(post(f.adminDecision()).with(token("owner","ADMIN"))
                .contentType("application/json").content(approval)).andReturn().getResponse().getStatus());
        for (String invalid : List.of("{\"decision\":null}", "{\"decision\":\"approve\",\"extra\":\"x\"}",
                "{\"decision\":\"approve\",\"decision\":\"reject\",\"expectedVersion\":\"0\"}")) {
            assertEquals(400, http.perform(post(f.adminDecision()).with(token("admin","ADMIN"))
                    .contentType("application/json").content(invalid)).andReturn().getResponse().getStatus());
        }
        assertEquals(200, http.perform(post(f.adminDecision()).with(token("admin","ADMIN"))
                .contentType("application/json").content(approval)).andReturn().getResponse().getStatus());
        String withdrawal = "{\"expectedVersion\":\"1\"}";
        assertEquals(404, http.perform(post(f.sellerWithdraw()).with(token("unrelated","CUSTOMER"))
                .contentType("application/json").content(withdrawal)).andReturn().getResponse().getStatus());
        when(stores.readStore(f.store())).thenReturn(new StoreAccess(f.store(), UUID.randomUUID(), "owner", false, false, false));
        assertEquals(200, http.perform(post(f.sellerWithdraw()).with(token("owner","CUSTOMER"))
                .contentType("application/json").content(withdrawal)).andReturn().getResponse().getStatus());
        assertEquals("WITHDRAWN", db.queryForObject("""
                SELECT status FROM promotion_campaign_products WHERE campaign_id=? AND product_id=?
                """, String.class, f.campaign(), f.product()));
    }
}
