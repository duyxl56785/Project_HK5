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

    private record Route(String method, String path, String body) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.valueOf(method), path)
                    .contentType("application/json").content(body);
        }
    }

    @Test
    void routeMatrixRejectsAnonymousAndUnauthorizedRoles() throws Exception {
        var f = fixture();
        var seller = "/api/seller/promotions";
        var admin = "/api/admin/promotions";
        var sellerCampaign = seller + "/" + f.campaign();
        var adminCampaign = admin + "/" + f.campaign();
        var sellerRoutes = List.of(new Route("GET", seller, ""),
                new Route("GET", sellerCampaign + "/products", ""),
                new Route("POST", seller, "{}"), new Route("POST", sellerCampaign + "/submit", "{}"),
                new Route("POST", sellerCampaign + "/products", "{}"),
                new Route("POST", f.sellerWithdraw(), "{\"expectedVersion\":\"0\"}"));
        var adminRoutes = List.of(new Route("GET", admin, ""),
                new Route("GET", adminCampaign + "/products", ""),
                new Route("POST", admin, "{}"), new Route("PATCH", adminCampaign + "/schedule", "{}"),
                new Route("POST", adminCampaign + "/actions", "{}"), new Route("POST", f.adminDecision(), "{}"));
        for (var route : java.util.stream.Stream.concat(sellerRoutes.stream(), adminRoutes.stream()).toList()) {
            assertEquals(401, http.perform(route.request()).andReturn().getResponse().getStatus(), route.path());
        }
        for (var route : adminRoutes) {
            assertEquals(403, http.perform(route.request().with(token("owner","SELLER")))
                    .andReturn().getResponse().getStatus(), route.path());
        }
        when(stores.managedBy("customer")).thenThrow(new com.projecthk5.productservice.product.ProductRuleException(
                com.projecthk5.productservice.product.ProductRuleException.Code.ACCESS_DENIED, "No Store"));
        for (var route : sellerRoutes) {
            int expected = route.path().endsWith("/withdraw") ? 404 : 403;
            assertEquals(expected, http.perform(route.request().with(token("customer","CUSTOMER")))
                    .andReturn().getResponse().getStatus(), route.path());
        }
    }

    @Test
    void sellerCannotReadOrWriteOtherStoreAndRegistrationPayloadIsStrict() throws Exception {
        var f = fixture();
        UUID outsider = UUID.randomUUID();
        when(stores.managedBy("outsider")).thenReturn(
                new SellerStoreClient.Facts(outsider, UUID.randomUUID(), "outsider", "Other", "other", true, true, false));
        when(stores.currentManagementStore("outsider")).thenReturn(outsider);
        var path = "/api/seller/promotions/" + f.campaign();
        String registration = "{\"productId\":\""+f.product()+"\",\"promotionalUnitPrice\":\"80000\",\"expectedCampaignVersion\":\"0\"}";
        assertEquals(404, http.perform(get(path + "/products").with(token("outsider","SELLER")))
                .andReturn().getResponse().getStatus());
        assertEquals(404, http.perform(post(path + "/submit").with(token("outsider","SELLER"))
                .contentType("application/json").content("{\"expectedVersion\":\"0\"}"))
                .andReturn().getResponse().getStatus());
        assertEquals(404, http.perform(post(path + "/products").with(token("outsider","SELLER"))
                .contentType("application/json").content(registration)).andReturn().getResponse().getStatus());
        for (String payload : List.of(registration.replace("\"80000\"", "80000"),
                registration.replace("80000","1.5"), registration.replace("\"80000\"","null"),
                registration.replace("{","{\"storeId\":\""+outsider+"\","))) {
            assertEquals(400, http.perform(post(path + "/products").with(token("owner","SELLER"))
                    .contentType("application/json").content(payload)).andReturn().getResponse().getStatus());
        }
        for (var route : List.of(new Route("POST", "/api/seller/promotions", "{}"),
                new Route("POST", path+"/submit", "{}"), new Route("POST", path+"/products", registration))) {
            assertEquals(403, http.perform(route.request().with(token("owner","CUSTOMER")))
                    .andReturn().getResponse().getStatus());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"STORE_CLOSED", "SELLER_SUSPENDED", "PRODUCT_INACTIVE"})
    void rejectsIneligibleRegistrationApprovalAndResumeWithoutWritingAudit(String fault) throws Exception {
        var f = fixture();
        var registration = campaign(f.product(), f.store());
        db.update("DELETE FROM promotion_campaign_products WHERE campaign_id=?", registration.campaign());
        var paused = campaign(f.product(), f.store());
        db.update("UPDATE promotion_campaigns SET status='PAUSED' WHERE id=?", paused.campaign());
        db.update("UPDATE promotion_campaign_products SET status='APPROVED' WHERE campaign_id=?", paused.campaign());
        if (fault.equals("PRODUCT_INACTIVE")) db.update("UPDATE products SET status='INACTIVE' WHERE id=?", f.product());
        when(stores.readStore(f.store())).thenReturn(new StoreAccess(f.store(), UUID.randomUUID(), "owner",
                !fault.equals("SELLER_SUSPENDED"), !fault.equals("STORE_CLOSED"), false));
        var requests = List.of(new Route("POST", "/api/seller/promotions/"+registration.campaign()+"/products",
                        "{\"productId\":\""+f.product()+"\",\"promotionalUnitPrice\":\"80000\",\"expectedCampaignVersion\":\"0\"}"),
                new Route("POST", f.adminDecision(), "{\"decision\":\"approve\",\"expectedVersion\":\"0\"}"),
                new Route("POST", "/api/admin/promotions/"+paused.campaign()+"/actions",
                        "{\"action\":\"resume\",\"expectedVersion\":\"0\"}"));
        for (var route : requests) {
            boolean seller = route.path().startsWith("/api/seller/");
            assertEquals(409, http.perform(route.request().with(token(seller ? "owner" : "admin", seller ? "SELLER" : "ADMIN")))
                    .andReturn().getResponse().getStatus(), route.path());
        }
        assertEquals(0, db.queryForObject("SELECT count(*) FROM promotion_campaign_audit WHERE campaign_id IN (?,?,?)",
                Integer.class, f.campaign(), registration.campaign(), paused.campaign()));
        assertEquals("PAUSED", db.queryForObject("SELECT status FROM promotion_campaigns WHERE id=?", String.class, paused.campaign()));
    }

    @Test
    void approvesAdjacentFlashAndSuperSaleForSameProduct() throws Exception {
        var f = fixture();
        var saturday = java.time.LocalDate.now(PromotionPriceRules.VIETNAM_ZONE).plusYears(2)
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
        for (var kind : List.of("FLASH_SALE", "SUPER_SALE")) {
            boolean flash = kind.equals("FLASH_SALE");
            var day = flash ? saturday : saturday.plusDays(1);
            var campaign = UUID.randomUUID();
            db.update("""
                    INSERT INTO promotion_campaigns(id,name,kind,status,flash_date,super_sale_date,starts_at,ends_at)
                    VALUES (?,'Adjacent sale',?,'APPROVED',?,?,?,?)
                    """, campaign, kind, flash ? day : null, flash ? null : day,
                    day.atTime(flash ? 22 : 0, 0).atZone(PromotionPriceRules.VIETNAM_ZONE).toOffsetDateTime(),
                    day.plusDays(1).atStartOfDay(PromotionPriceRules.VIETNAM_ZONE).toOffsetDateTime());
            db.update("INSERT INTO promotion_campaign_products(campaign_id,product_id,promotional_unit_price) VALUES (?,?,80000)",
                    campaign, f.product());
            var route = new Fixture(campaign, f.product(), f.store());
            assertEquals(200, approve(route, "admin", new CountDownLatch(0), new CountDownLatch(0)));
        }
        assertEquals(2, db.queryForObject("""
                SELECT count(*) FROM promotion_campaign_audit
                WHERE product_id=? AND to_status='APPROVED'
                """, Integer.class, f.product()));
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
