package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Flyway V1..V10, PostgreSQL 16 row-locking, Spring HTTP and verified-JWT boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class VoucherC1HttpPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size",()->10);
    }
    private static final UUID SELLER_A=UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID SELLER_B=UUID.fromString("e1000000-0000-4000-8000-000000000002");
    private static final UUID STORE_A=UUID.fromString("e2000000-0000-4000-8000-000000000001");
    private static final UUID STORE_B=UUID.fromString("e2000000-0000-4000-8000-000000000002");
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    private final ObjectMapper json=new ObjectMapper();
    private static String start(){return Instant.now().plusSeconds(3600).toString();}
    private static String end(){return Instant.now().plusSeconds(86400).toString();}

    @BeforeEach void reset() {
        db.update("DELETE FROM voucher_status_history");
        db.update("DELETE FROM store_voucher_limits");
        db.update("DELETE FROM voucher_limit_request_history");
        db.update("DELETE FROM voucher_limit_requests");
        db.update("DELETE FROM vouchers");
        db.update("DELETE FROM seller_outbox");
        db.update("DELETE FROM seller_status_history");
        db.update("DELETE FROM stores");
        db.update("DELETE FROM seller_profiles");
        seller(SELLER_A,"seller-a","APPROVED");
        seller(SELLER_B,"seller-b","APPROVED");
        store(STORE_A,SELLER_A,"store-a");
        store(STORE_B,SELLER_B,"store-b");
    }
    private void seller(UUID id,String owner,String status){
        db.update("INSERT INTO seller_profiles (id,owner_reference,status,review_reason,version)"
            +" VALUES (?,?,?,NULL,0)",id,owner,status);
    }
    private void store(UUID id,UUID seller,String slug){
        db.update("INSERT INTO stores(id,seller_id,name,slug,status,version)"
            +" VALUES (?,?,?,?,'OPEN',0)",id,seller,slug,slug);
    }
    private static JwtRequestPostProcessor identity(String actor,String role) {
        return jwt().jwt(j->j.subject(actor).claim("roles",List.of(role)))
            .authorities(new SimpleGrantedAuthority("ROLE_"+role));
    }
    private MvcResult send(MockHttpServletRequestBuilder request,String user,String role) throws Exception {
        return http.perform(request.with(identity(user,role))).andReturn();
    }
    private String body(String code){
        return "{\"code\":\""+code+"\",\"name\":\"Voucher "+code+"\","
          +"\"discountType\":\"FIXED\",\"discountValue\":100,\"minOrderAmount\":200,"
          +"\"usageLimit\":100,\"startsAt\":\""+start()+"\",\"endsAt\":\""+end()+"\"}";
    }
    private UUID draft(String code,String user) throws Exception {
        MvcResult result=send(post("/api/stores/me/vouchers").contentType(MediaType.APPLICATION_JSON)
            .content(body(code)),user,"SELLER");
        assertEquals(201,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
    private int publish(UUID id,String user,String role) throws Exception {
        return send(post("/api/stores/me/vouchers/"+id+"/publish").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"0\"}"),user,role).getResponse().getStatus();
    }
    private int count(){return db.queryForObject(
        "SELECT count(*) FROM vouchers WHERE store_id=? AND status='PUBLISHED' AND ends_at>now()",
        Integer.class,STORE_A);}
    private void seed(int howMany){
        Instant t=Instant.now();
        for(int i=0;i<howMany;i++){
            db.update("INSERT INTO vouchers (id,store_id,code,name,discount_type,discount_value,max_discount,"
                +"min_order_amount,usage_limit,starts_at,ends_at,status,version)"
                +" VALUES (?,?,?,?,'FIXED',100,NULL,200,100,?,?,'PUBLISHED',0)",
                UUID.randomUUID(),STORE_A,String.format("SEED%04d",i),"seed",
                OffsetDateTime.ofInstant(t.minusSeconds(60),ZoneOffset.UTC),
                OffsetDateTime.ofInstant(t.plusSeconds(86400),ZoneOffset.UTC));
        }
    }
    @Test void quotaBoundaryAndCrossStoreIsolation() throws Exception {
        seed(19);
        UUID first=draft("ONE001","seller-a"),second=draft("TWO002","seller-a");
        assertEquals(200,publish(first,"seller-a","SELLER"));
        MvcResult denied=send(post("/api/stores/me/vouchers/"+second+"/publish")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}"),"seller-a","SELLER");
        assertEquals(409,denied.getResponse().getStatus());
        assertTrue(denied.getResponse().getContentAsString().contains("VOUCHER_QUOTA_EXCEEDED"));
        assertEquals(20,count());
        assertEquals(404,send(get("/api/stores/me/vouchers/"+first),"seller-b","SELLER").getResponse().getStatus());
        assertEquals(401,http.perform(get("/api/stores/me/vouchers/"+first)).andReturn().getResponse().getStatus());
    }
    @Test void concurrentPublishAtNineteenHasOneWinnerInTwentyReplays() throws Exception {
        for(int round=0;round<20;round++){
            db.update("DELETE FROM voucher_status_history");
            db.update("DELETE FROM vouchers");
            seed(19);
            UUID a=draft("FIRST1","seller-a"),b=draft("SECOND2","seller-a");
            try(var pool=Executors.newFixedThreadPool(2)) {
                CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
                Future<Integer> first=pool.submit(()->{ready.countDown();go.await();
                    return publish(a,"seller-a","SELLER");});
                Future<Integer> second=pool.submit(()->{ready.countDown();go.await();
                    return publish(b,"seller-a","SELLER");});
                ready.await();go.countDown();
                int x=first.get(),y=second.get();
                assertEquals(1,(x==200?1:0)+(y==200?1:0),"round="+round+" x="+x+" y="+y);
                assertEquals(1,(x==409?1:0)+(y==409?1:0));
                assertEquals(20,count());
            }
        }
    }
    private String requestBody(){
        return "{\"requestedLimit\":30,\"reason\":\"Đẩy tồn kho\",\"requestedFrom\":\""+start()
            +"\",\"requestedUntil\":\""+end()+"\"}";
    }
    @Test void idempotentRequestAndPartialAdminApprovalGrant() throws Exception {
        String fixedPayload=requestBody();
        var req=post("/api/stores/me/voucher-limit-requests")
            .header("Idempotency-Key","same-key").contentType(MediaType.APPLICATION_JSON).content(fixedPayload);
        MvcResult first=send(req,"seller-a","SELLER");
        assertEquals(201,first.getResponse().getStatus());
        // Same logical request and same key must return 200 with original ID.
        String payload=first.getResponse().getContentAsString();
        MvcResult again=send(post("/api/stores/me/voucher-limit-requests").header("Idempotency-Key","same-key")
            .contentType(MediaType.APPLICATION_JSON).content(fixedPayload),"seller-a","SELLER");
        assertEquals(200,again.getResponse().getStatus(),again.getResponse().getContentAsString());
        assertEquals(json.readTree(payload).get("id").asText(),json.readTree(again.getResponse().getContentAsString()).get("id").asText());
        UUID id=UUID.fromString(json.readTree(payload).get("id").asText());
        String from=json.readTree(payload).get("requestedFrom").asText();
        String until=json.readTree(payload).get("requestedUntil").asText();
        String approval="{\"expectedVersion\":\"0\",\"approvedLimit\":25,"
            +"\"approvedFrom\":\""+from+"\",\"approvedUntil\":\""+until+"\",\"reason\":\"Duyệt 25\"}";
        MvcResult done=send(post("/api/admin/voucher-limit-requests/"+id+"/approve")
            .contentType(MediaType.APPLICATION_JSON).content(approval),"admin-moderator","ADMIN");
        assertEquals(200,done.getResponse().getStatus(),done.getResponse().getContentAsString());
        MvcResult limit=send(get("/api/stores/me/voucher-limit"),"seller-a","SELLER");
        assertTrue(limit.getResponse().getContentAsString().contains("\"limit\":25"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM voucher_limit_request_history WHERE request_id=? AND to_status='APPROVED'",
            Integer.class,id));
    }
    @Test void expiredGrantGrandfathersExistingButBlocksAnotherPublish() throws Exception {
        String req=requestBody();
        MvcResult created=send(post("/api/stores/me/voucher-limit-requests")
            .header("Idempotency-Key","grant-1").contentType(MediaType.APPLICATION_JSON).content(req),
            "seller-a","SELLER");
        JsonNode j=json.readTree(created.getResponse().getContentAsString());
        UUID id=UUID.fromString(j.get("id").asText());
        String approval="{\"expectedVersion\":\"0\",\"approvedLimit\":25,\"approvedFrom\":\""
            +j.get("requestedFrom").asText()+"\",\"approvedUntil\":\""+j.get("requestedUntil").asText()
            +"\",\"reason\":\"Test\"}";
        assertEquals(200,send(post("/api/admin/voucher-limit-requests/"+id+"/approve")
            .contentType(MediaType.APPLICATION_JSON).content(approval),"admin-moderator","ADMIN")
            .getResponse().getStatus());
        seed(24);
        db.update("UPDATE store_voucher_limits SET valid_from=now()-interval '2 days',"
            +"valid_until=now()-interval '1 day' WHERE store_id=?",STORE_A);
        UUID last=draft("LAST001","seller-a");
        assertEquals(409,publish(last,"seller-a","SELLER"));
        assertEquals(24,count());
    }
    @Test void suspendedSellerCannotPublishButMayRetractPriorVoucher() throws Exception {
        UUID draft=draft("PAUSE01","seller-a");
        assertEquals(200,publish(draft,"seller-a","SELLER"));
        db.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='Suspended' WHERE id=?",SELLER_A);
        UUID newDraft=UUID.randomUUID();
        assertEquals(403,send(post("/api/stores/me/vouchers").contentType(MediaType.APPLICATION_JSON)
            .content(body("OTHER02")),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(200,send(post("/api/stores/me/vouchers/"+draft+"/pause")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"1\"}"),
            "seller-a","CUSTOMER").getResponse().getStatus());
    }
}
