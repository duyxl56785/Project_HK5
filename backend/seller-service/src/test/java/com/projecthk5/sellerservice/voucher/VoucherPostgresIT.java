package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * C1 real PostgreSQL tests, NOT runnable until TV2 V9 exists in the same branch.
 * No Docker runtime is available in the artifact-generation environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class VoucherPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String OWNER="voucher-test-owner";
    private static final String OTHER="voucher-test-other";
    private final UUID seller=UUID.fromString("11000000-0000-4000-8000-000000000001");
    private final UUID store=UUID.fromString("12000000-0000-4000-8000-000000000001");
    private final UUID otherSeller=UUID.fromString("11000000-0000-4000-8000-000000000002");
    private final UUID otherStore=UUID.fromString("12000000-0000-4000-8000-000000000002");
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username",POSTGRES::getUsername);
        p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("spring.datasource.hikari.maximum-pool-size",()->12);
    }
    @BeforeEach void reset() {
        db.update("DELETE FROM voucher_status_history");
        db.update("DELETE FROM vouchers");
        db.update("DELETE FROM store_voucher_limits"); // TV2 V9 prerequisite
        db.update("DELETE FROM voucher_limit_request_history");
        db.update("DELETE FROM voucher_limit_requests");
        db.update("DELETE FROM seller_outbox");
        db.update("DELETE FROM seller_status_history");
        db.update("DELETE FROM stores");
        db.update("DELETE FROM seller_profiles");
        db.update("INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED')",seller,OWNER);
        db.update("INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED')",otherSeller,OTHER);
        db.update("INSERT INTO stores(id,seller_id,name,slug,status) VALUES (?,?,'Voucher Store','voucher-store','OPEN')",store,seller);
        db.update("INSERT INTO stores(id,seller_id,name,slug,status) VALUES (?,?,'Other Store','other-store','OPEN')",otherStore,otherSeller);
    }

    private int call(MockHttpServletRequestBuilder req,String principal,String role) throws Exception {
        JwtRequestPostProcessor token=jwt().jwt(j->j.subject(principal).claim("roles",List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_"+role));
        return mvc.perform(req.with(token)).andReturn().getResponse().getStatus();
    }
    private static MockHttpServletRequestBuilder action(UUID id,String verb) {
        return post("/api/stores/me/vouchers/"+id+"/"+verb)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}");
    }
    private UUID seed(String code,String status,UUID storeId) {
        UUID id=UUID.randomUUID();
        Instant begin=Instant.now().minusSeconds(600),end=Instant.now().plusSeconds(3600);
        db.update("INSERT INTO vouchers(id,store_id,code,name,discount_type,discount_value,max_discount,"
                +"min_order_amount,usage_limit,starts_at,ends_at,status,version) "
                +"VALUES (?,?,?,'Voucher','FIXED',100,NULL,100,10,?,?,?,0)",
                id,storeId,code,OffsetDateTime.ofInstant(begin,ZoneOffset.UTC),
                OffsetDateTime.ofInstant(end,ZoneOffset.UTC),status);
        return id;
    }

    @Test void vl01LimitAtTwentyAndPausedDoesNotConsumeSlot() throws Exception {
        for(int i=0;i<19;i++)seed("FIX"+String.format("%04d",i),"PUBLISHED",store);
        UUID twentieth=seed("FIX2000","DRAFT",store), twentyFirst=seed("FIX2100","DRAFT",store);
        assertEquals(200,call(action(twentieth,"publish"),OWNER,"SELLER"));
        assertEquals(409,call(action(twentyFirst,"publish"),OWNER,"SELLER"));
        assertEquals(20,db.queryForObject("SELECT count(*) FROM vouchers WHERE store_id=? AND status='PUBLISHED'",Integer.class,store));
        assertEquals(200,call(post("/api/stores/me/vouchers/"+twentieth+"/pause")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"1\"}"),OWNER,"SELLER"));
        assertEquals(200,call(action(twentyFirst,"publish"),OWNER,"SELLER"));
    }

    @Test void vl02ConcurrentTwentyFirstPublishExactlyOnceRepeatedTwentyRounds() throws Exception {
        for(int round=0;round<20;round++) {
            db.update("DELETE FROM voucher_status_history"); db.update("DELETE FROM vouchers");
            for(int i=0;i<19;i++) seed("FIX"+String.format("%04d",i),"PUBLISHED",store);
            UUID a=seed("A200","DRAFT",store),b=seed("B200","DRAFT",store);
            CountDownLatch start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                Future<Integer> f1=pool.submit(()->{start.await();return call(action(a,"publish"),OWNER,"SELLER");});
                Future<Integer> f2=pool.submit(()->{start.await();return call(action(b,"publish"),OWNER,"SELLER");});
                start.countDown();
                int x=f1.get(20,TimeUnit.SECONDS),y=f2.get(20,TimeUnit.SECONDS);
                assertEquals(1,(x==200?1:0)+(y==200?1:0),"round "+round);
                assertEquals(1,(x==409?1:0)+(y==409?1:0),"round "+round);
                assertEquals(20,db.queryForObject("SELECT count(*) FROM vouchers WHERE store_id=? AND status='PUBLISHED'",Integer.class,store));
            }
        }
    }

    @Test void vl09PausedCannotResumeWhileQuotaFull() throws Exception {
        for (int i=0;i<20;i++) seed("LIVE"+String.format("%04d",i),"PUBLISHED",store);
        UUID paused=seed("PAUS5678","PAUSED",store);
        assertEquals(409,call(action(paused,"resume"),OWNER,"SELLER"));
        assertEquals("PAUSED",db.queryForObject("SELECT status FROM vouchers WHERE id=?",String.class,paused));
    }

    @Test void vl05ExpiredGrantGrandfathersExistingTwentyFour() throws Exception {
        Instant now=Instant.now();
        UUID req=UUID.randomUUID();
        db.update("INSERT INTO voucher_limit_requests(id,store_id,idempotency_key,request_fingerprint,"
            +"requested_limit,reason,requested_from,requested_until,status,approved_limit,"
            +"approved_from,approved_until,decided_by,decided_at) "
            +"VALUES (?,?,?,?,25,'clearance',?,?,'APPROVED',25,?,?,?,?)",
            req,store,"itest-"+req,"a".repeat(64),
            OffsetDateTime.ofInstant(now.minusSeconds(86400),ZoneOffset.UTC),
            OffsetDateTime.ofInstant(now.plusSeconds(86400),ZoneOffset.UTC),
            OffsetDateTime.ofInstant(now.minusSeconds(3600),ZoneOffset.UTC),
            OffsetDateTime.ofInstant(now.plusSeconds(60),ZoneOffset.UTC),"admin-test",
            OffsetDateTime.ofInstant(now,ZoneOffset.UTC));
        db.update("INSERT INTO store_voucher_limits(store_id,granted_limit,valid_from,valid_until,source_request_id,decided_by,decided_at) "
            +"VALUES (?,25,?,?,?,?,?)",store,
            OffsetDateTime.ofInstant(now.minusSeconds(3600),ZoneOffset.UTC),
            OffsetDateTime.ofInstant(now.minusSeconds(1),ZoneOffset.UTC),req,"admin-test",
            OffsetDateTime.ofInstant(now,ZoneOffset.UTC));
        for (int i=0;i<24;i++) seed("EXT"+String.format("%04d",i),"PUBLISHED",store);
        UUID fresh=seed("NEW1234","DRAFT",store);
        assertEquals(409,call(action(fresh,"publish"),OWNER,"SELLER"));
        assertEquals(24,db.queryForObject("SELECT count(*) FROM vouchers WHERE store_id=? AND status='PUBLISHED'",
            Integer.class,store));
    }

    @Test void ownershipRoleAndClosedStoreAreEnforced() throws Exception {
        UUID privateVoucher=seed("PRIV123","DRAFT",otherStore);
        assertEquals(401,mvc.perform(get("/api/stores/me/vouchers")).andReturn().getResponse().getStatus());
        assertEquals(403,call(get("/api/stores/me/vouchers"),OWNER,"CUSTOMER"));
        assertEquals(404,call(get("/api/stores/me/vouchers/"+privateVoucher),OWNER,"SELLER"));
        UUID own=seed("OWN1234","DRAFT",store);
        db.update("UPDATE stores SET status='CLOSED' WHERE id=?",store);
        assertEquals(409,call(action(own,"publish"),OWNER,"SELLER"));
    }

    @Test void suspendedSellerMayPauseButNotPublish() throws Exception {
        UUID published=seed("PUB1234","PUBLISHED",store), draft=seed("DRF1234","DRAFT",store);
        db.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='integration fixture' WHERE id=?",seller);
        assertEquals(200,call(action(published,"pause"),OWNER,"SELLER"));
        assertEquals(403,call(action(draft,"publish"),OWNER,"SELLER"));
    }
}
