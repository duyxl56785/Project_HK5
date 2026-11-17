package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Voucher C2 usage (MD18/MD19/MD22) on real Flyway V1..V11 and PostgreSQL row locks. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class VoucherC2RedemptionPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String TOKEN = "order-seller-test-token";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> 12);
        r.add("ORDER_SELLER_TOKEN", () -> TOKEN);
    }
    private static final UUID SELLER = UUID.fromString("e3000000-0000-4000-8000-000000000001");
    private static final UUID STORE = UUID.fromString("e4000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_STORE = UUID.fromString("e4000000-0000-4000-8000-000000000002");
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void reset() {
        db.update("DELETE FROM voucher_redemptions");
        db.update("DELETE FROM voucher_status_history");
        db.update("DELETE FROM store_voucher_limits");
        db.update("DELETE FROM voucher_limit_request_history");
        db.update("DELETE FROM voucher_limit_requests");
        db.update("DELETE FROM vouchers");
        db.update("DELETE FROM seller_outbox");
        db.update("DELETE FROM seller_status_history");
        db.update("DELETE FROM stores");
        db.update("DELETE FROM seller_profiles");
        db.update("INSERT INTO seller_profiles (id,owner_reference,status,review_reason,version) VALUES (?,?,'APPROVED',NULL,0)",
                SELLER, "seller-c2");
        db.update("INSERT INTO stores(id,seller_id,name,slug,status,version) VALUES (?,?,?,?,'OPEN',0)",
                STORE, SELLER, "c2-store", "c2-store");
    }

    private UUID voucher(String code, String status, int usageLimit, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO vouchers (id,store_id,code,name,discount_type,discount_value,max_discount,"
                + "min_order_amount,usage_limit,starts_at,ends_at,status,version) VALUES (?,?,?,?,'PERCENT',10,30000,150000,?,?,?,?,0)",
                id, STORE, code, "Voucher " + code, usageLimit,
                OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(end, ZoneOffset.UTC), status);
        return id;
    }
    private UUID live(String code, int usageLimit) {
        return voucher(code, "PUBLISHED", usageLimit, Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400));
    }
    private static String key(UUID order) { return "order-" + order + "-store-" + STORE; }

    private MvcResult call(String path, String body, String token) throws Exception {
        var request = post("/internal/vouchers/" + path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request = request.header("X-Internal-Token", token);
        return http.perform(request).andReturn();
    }
    private MvcResult quote(String code, String customer) throws Exception {
        return call("quote", "{\"storeId\":\"" + STORE + "\",\"code\":\"" + code + "\",\"customerId\":\"" + customer + "\"}", TOKEN);
    }
    private MvcResult redeem(UUID voucher, String code, String customer, UUID order) throws Exception {
        return call("redeem", "{\"redemptionKey\":\"" + key(order) + "\",\"voucherId\":\"" + voucher + "\",\"storeId\":\""
                + STORE + "\",\"code\":\"" + code + "\",\"customerId\":\"" + customer + "\",\"orderId\":\"" + order + "\"}", TOKEN);
    }
    private MvcResult release(UUID order) throws Exception {
        return call("release", "{\"redemptionKey\":\"" + key(order) + "\",\"orderId\":\"" + order + "\"}", TOKEN);
    }
    private int used(UUID voucher) {
        return db.queryForObject("SELECT used_count FROM vouchers WHERE id=?", Integer.class, voucher);
    }
    private static String code(MvcResult r) throws Exception { return r.getResponse().getContentAsString(); }

    @Test void quoteReturnsFrozenTermsAndRequiresOrderToken() throws Exception {
        UUID id = live("SALE10", 5);
        MvcResult ok = quote("sale10", "customer-1");
        assertEquals(200, ok.getResponse().getStatus(), code(ok));
        JsonNode terms = json.readTree(code(ok));
        assertEquals(id.toString(), terms.get("voucherId").asText());
        assertEquals("PERCENT", terms.get("discountType").asText());
        assertEquals(10, terms.get("discountValue").asLong());
        assertEquals(30000, terms.get("maxDiscount").asLong());
        assertEquals(150000, terms.get("minOrderAmount").asLong());
        assertEquals(401, call("quote", "{\"storeId\":\"" + STORE + "\",\"code\":\"SALE10\",\"customerId\":\"c\"}", "wrong")
                .getResponse().getStatus());
        assertEquals(401, call("quote", "{\"storeId\":\"" + STORE + "\",\"code\":\"SALE10\",\"customerId\":\"c\"}", null)
                .getResponse().getStatus());
        // A code of another Store is not visible through this Store.
        assertEquals(404, call("quote", "{\"storeId\":\"" + OTHER_STORE + "\",\"code\":\"SALE10\",\"customerId\":\"c\"}", TOKEN)
                .getResponse().getStatus());
    }

    @Test void inactiveVouchersAreRefused() throws Exception {
        voucher("DRAFT01", "DRAFT", 5, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        voucher("PAUSE01", "PAUSED", 5, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        voucher("SOON01", "PUBLISHED", 5, Instant.now().plusSeconds(600), Instant.now().plusSeconds(3600));
        voucher("OLD001", "PUBLISHED", 5, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60));
        for (String c : List.of("DRAFT01", "PAUSE01", "SOON01", "OLD001")) {
            MvcResult r = quote(c, "customer-1");
            assertEquals(409, r.getResponse().getStatus(), c);
            assertTrue(code(r).contains("VOUCHER_NOT_ACTIVE"), c);
        }
        live("CLOSED1", 5);
        db.update("UPDATE stores SET status='CLOSED' WHERE id=?", STORE);
        assertTrue(code(quote("CLOSED1", "customer-1")).contains("VOUCHER_NOT_ACTIVE"));
        assertEquals(404, quote("NOPE01", "customer-1").getResponse().getStatus());
    }

    @Test void redeemIsIdempotentLimitedPerCustomerAndByUsageLimit() throws Exception {
        UUID id = live("LIMIT2", 2);
        UUID first = UUID.randomUUID();
        assertEquals(200, redeem(id, "LIMIT2", "customer-1", first).getResponse().getStatus());
        assertEquals(200, redeem(id, "LIMIT2", "customer-1", first).getResponse().getStatus());
        assertEquals(1, used(id));
        MvcResult twice = redeem(id, "LIMIT2", "customer-1", UUID.randomUUID());
        assertEquals(409, twice.getResponse().getStatus());
        assertTrue(code(twice).contains("VOUCHER_ALREADY_USED"));
        assertTrue(code(quote("LIMIT2", "customer-1")).contains("VOUCHER_ALREADY_USED"));
        assertEquals(200, redeem(id, "LIMIT2", "customer-2", UUID.randomUUID()).getResponse().getStatus());
        assertEquals(2, used(id));
        MvcResult exhausted = redeem(id, "LIMIT2", "customer-3", UUID.randomUUID());
        assertEquals(409, exhausted.getResponse().getStatus());
        assertTrue(code(exhausted).contains("VOUCHER_USAGE_EXHAUSTED"));
        assertEquals(2, used(id));
        // Same key, different customer: refused, never a second use.
        MvcResult mismatch = redeem(id, "LIMIT2", "customer-9", first);
        assertTrue(code(mismatch).contains("VOUCHER_REDEMPTION_MISMATCH"));
    }

    @Test void releaseRestoresUsageOnceAndReleasedKeyIsNeverReused() throws Exception {
        UUID id = live("BACK01", 1);
        UUID order = UUID.randomUUID();
        assertEquals(200, redeem(id, "BACK01", "customer-1", order).getResponse().getStatus());
        assertEquals(1, used(id));
        assertEquals(200, release(order).getResponse().getStatus());
        assertEquals(200, release(order).getResponse().getStatus());
        assertEquals(0, used(id));
        MvcResult late = redeem(id, "BACK01", "customer-1", order);
        assertEquals(409, late.getResponse().getStatus());
        assertTrue(code(late).contains("VOUCHER_REDEMPTION_RELEASED"));
        assertEquals(0, used(id));
        // MD19: the restored use is available again, including to the same customer.
        assertEquals(200, redeem(id, "BACK01", "customer-1", UUID.randomUUID()).getResponse().getStatus());
        assertEquals(1, used(id));
    }

    @Test void releaseBeforeRedeemLeavesTombstone() throws Exception {
        UUID id = live("RACE01", 3);
        UUID order = UUID.randomUUID();
        assertEquals(200, release(order).getResponse().getStatus());
        MvcResult late = redeem(id, "RACE01", "customer-1", order);
        assertEquals(409, late.getResponse().getStatus());
        assertEquals(0, used(id));
        assertEquals(400, call("release", "{\"redemptionKey\":\"" + key(order) + "\",\"orderId\":\"" + UUID.randomUUID() + "\"}",
                TOKEN).getResponse().getStatus());
    }

    @Test void concurrentRedeemNeverExceedsUsageLimit() throws Exception {
        UUID id = live("RUSH01", 3);
        int callers = 12;
        var pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            String customer = "rush-" + i;
            Callable<Integer> task = () -> { go.await(); return redeem(id, "RUSH01", customer, UUID.randomUUID()).getResponse().getStatus(); };
            results.add(pool.submit(task));
        }
        go.countDown();
        int ok = 0, refused = 0;
        for (Future<Integer> f : results) {
            int status = f.get(30, TimeUnit.SECONDS);
            if (status == 200) ok++; else if (status == 409) refused++;
        }
        pool.shutdown();
        assertEquals(3, ok);
        assertEquals(callers - 3, refused);
        assertEquals(3, used(id));
        assertEquals(3, db.queryForObject("SELECT count(*) FROM voucher_redemptions WHERE voucher_id=? AND status='ACTIVE'",
                Integer.class, id));
    }

    @Test void flywayUpgradesPopulatedV10VoucherToV11WithZeroUsage() {
        String schema = "voucher_c2_upgrade";
        var config = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).locations("classpath:db/migration");
        assertEquals(10, config.target("10").load().migrate().migrationsExecuted);
        db.update("INSERT INTO " + schema + ".seller_profiles (id,owner_reference,status) VALUES (?,?,'APPROVED')", SELLER, "upgrade");
        db.update("INSERT INTO " + schema + ".stores (id,seller_id,name,slug,status) VALUES (?,?,?,?,'OPEN')", STORE, SELLER, "u", "u");
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO " + schema + ".vouchers (id,store_id,code,name,discount_type,discount_value,max_discount,"
                + "min_order_amount,usage_limit,starts_at,ends_at,status,version) VALUES (?,?,'UPG001','u','FIXED',100,NULL,200,1,now(),now()+interval '1 day','PUBLISHED',0)",
                id, STORE);
        Flyway upgraded = config.target("11").load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals(0, db.queryForObject("SELECT used_count FROM " + schema + ".vouchers WHERE id=?", Integer.class, id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("UPDATE " + schema + ".vouchers SET used_count=2 WHERE id=?", id));
    }
}
