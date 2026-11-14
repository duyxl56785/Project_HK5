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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
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
        UUID paused=draft("PAUSED1","seller-a");
        db.update("UPDATE vouchers SET status='PAUSED' WHERE id=?",paused);
        assertEquals(19,count());
        assertEquals(200,publish(first,"seller-a","SELLER"));
        MvcResult denied=send(post("/api/stores/me/vouchers/"+second+"/publish")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}"),"seller-a","SELLER");
        assertEquals(409,denied.getResponse().getStatus());
        assertTrue(denied.getResponse().getContentAsString().contains("VOUCHER_QUOTA_EXCEEDED"));
        assertEquals(20,count());
        assertEquals(409,send(post("/api/stores/me/vouchers/"+paused+"/resume")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}"),"seller-a","SELLER")
            .getResponse().getStatus());
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
        return "{\"requestedLimit\":30,\"reason\":\"Đẩy tồn kho\",\"requestedFrom\":\""
            +Instant.now().minusSeconds(3600).toString()
            +"\",\"requestedUntil\":\""+end()+"\"}";
    }
    @Test void idempotentRequestAndPartialAdminApprovalGrant() throws Exception {
        String fixedPayload=requestBody();
        var req=post("/api/stores/me/voucher-limit-requests")
            .header("Idempotency-Key","same-key").contentType(MediaType.APPLICATION_JSON).content(fixedPayload);
        MvcResult first=send(req,"seller-a","SELLER");
        assertEquals(201,first.getResponse().getStatus());
        assertEquals(20,json.readTree(send(get("/api/stores/me/voucher-limit"),"seller-a","SELLER")
            .getResponse().getContentAsString()).get("limit").asInt());
        assertEquals("IDEMPOTENCY_KEY_REUSED",json.readTree(send(post("/api/stores/me/voucher-limit-requests")
            .header("Idempotency-Key","same-key").contentType(MediaType.APPLICATION_JSON)
            .content(fixedPayload.replace("\"requestedLimit\":30","\"requestedLimit\":31")),"seller-a","SELLER")
            .getResponse().getContentAsString()).get("code").asText());
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
        assertEquals(0,db.queryForObject("SELECT count(*) FROM vouchers",Integer.class));
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
        db.update("UPDATE vouchers SET status='PAUSED' WHERE id=?",last);
        assertEquals(409,send(post("/api/stores/me/vouchers/"+last+"/resume").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"0\"}"),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(24,count());
    }
    private JsonNode createRequest(String key,String payload) throws Exception {
        MvcResult created=send(post("/api/stores/me/voucher-limit-requests")
            .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(payload),
            "seller-a","SELLER");
        assertEquals(201,created.getResponse().getStatus(),created.getResponse().getContentAsString());
        return json.readTree(created.getResponse().getContentAsString());
    }

    private MvcResult approve(JsonNode request) throws Exception {
        String body=json.createObjectNode().put("expectedVersion","0").put("approvedLimit",30)
            .put("approvedFrom",request.get("requestedFrom").asText())
            .put("approvedUntil",request.get("requestedUntil").asText()).toString();
        return send(post("/api/admin/voucher-limit-requests/"+request.get("id").asText()+"/approve")
            .contentType(MediaType.APPLICATION_JSON).content(body),"admin-moderator","ADMIN");
    }

    private static List<Integer> race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            Future<Integer> a=pool.submit(()->{ready.countDown();start.await();return first.call();});
            Future<Integer> b=pool.submit(()->{ready.countDown();start.await();return second.call();});
            try {
                assertTrue(ready.await(10,TimeUnit.SECONDS));
                start.countDown();
                return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            } finally {
                a.cancel(true);
                b.cancel(true);
            }
        }
    }

    @Test void concurrentPublishAndApprovalSerializeOnStore() throws Exception {
        JsonNode request=createRequest("approve-publish",requestBody());
        seed(20);
        UUID extra=draft("EXTRA1","seller-a");
        List<Integer> result=race(()->publish(extra,"seller-a","SELLER"),
            ()->approve(request).getResponse().getStatus());
        assertEquals(200,result.get(1));
        assertTrue(result.get(0)==200||result.get(0)==409);
        assertEquals(result.get(0)==200?21:20,count());
        if(result.get(0)==409) assertEquals(200,publish(extra,"seller-a","SELLER"));
        assertEquals(21,count());
    }

    @Test void concurrentCancelAndApprovalHaveOneWinnerAndConsistentGrant() throws Exception {
        JsonNode request=createRequest("approve-cancel",requestBody());
        String id=request.get("id").asText();
        List<Integer> result=race(()->send(post("/api/stores/me/voucher-limit-requests/"+id+"/cancel")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}"),
            "seller-a","SELLER").getResponse().getStatus(),()->approve(request).getResponse().getStatus());
        assertEquals(List.of(200,409),result.stream().sorted().toList());
        UUID requestId=UUID.fromString(id);
        boolean approved=result.get(1)==200;
        assertEquals(approved?"APPROVED":"CANCELLED",db.queryForObject(
            "SELECT status FROM voucher_limit_requests WHERE id=?",String.class,requestId));
        assertEquals(approved?1:0,db.queryForObject(
            "SELECT count(*) FROM store_voucher_limits WHERE source_request_id=?",Integer.class,requestId));
        assertEquals(1,db.queryForObject(
            "SELECT count(*) FROM voucher_limit_request_history WHERE request_id=? AND from_status='PENDING'",
            Integer.class,requestId));
    }

    @Test void flywayUpgradesPopulatedV8SchemaThroughV10() {
        String schema="voucher_upgrade";
        var config=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
            .schemas(schema).locations("classpath:db/migration");
        assertEquals(8,config.target("8").load().migrate().migrationsExecuted);
        db.update("INSERT INTO "+schema+".seller_profiles (id,owner_reference,status) VALUES (?,?,'APPROVED')",
            SELLER_A,"upgrade-owner");
        db.update("INSERT INTO "+schema+".stores (id,seller_id,name,slug,status) VALUES (?,?,?,?,'OPEN')",
            STORE_A,SELLER_A,"Upgrade store","upgrade-store");
        Flyway upgraded=config.target("10").load();
        assertEquals(2,upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals(1,db.queryForObject("SELECT count(*) FROM "+schema+".stores WHERE id=?",Integer.class,STORE_A));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM "+schema+".vouchers",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM "+schema+".store_voucher_limits",Integer.class));
    }

    @Test void v9RejectsCrossStoreGrantAndInvalidDecisionData() throws Exception {
        String payload=requestBody().replace("\"requestedLimit\":30",
            "\"requestedLimit\":30,\"budgetNote\":\"\"");
        JsonNode request=createRequest("v9-constraints",payload);
        UUID id=UUID.fromString(request.get("id").asText());
        assertTrue(request.get("budgetNote").isNull());
        assertThrows(DataIntegrityViolationException.class,()->db.update(
            "UPDATE voucher_limit_requests SET budget_note='' WHERE id=?",id));
        assertThrows(DataIntegrityViolationException.class,()->db.update(
            "UPDATE voucher_limit_requests SET status='REJECTED',decided_by='admin',decided_at=now() WHERE id=?",id));
        assertThrows(DataIntegrityViolationException.class,()->db.update(
            "UPDATE voucher_limit_requests SET status='APPROVED',approved_limit=25,"
            +"approved_from=requested_from,approved_until=requested_until,decided_by='admin',decided_at=now() WHERE id=?",id));
        assertEquals(200,approve(request).getResponse().getStatus());
        assertThrows(DataIntegrityViolationException.class,()->db.update(
            "INSERT INTO store_voucher_limits (store_id,granted_limit,valid_from,valid_until,"
            +"source_request_id,decided_by,decided_at) VALUES (?,30,now(),now()+interval '1 day',?,'admin',now())",
            STORE_B,id));
    }

    @Test void decisionsRequireReasonAndRejectCrossStoreRequests() throws Exception {
        JsonNode request=createRequest("decision-permissions",requestBody());
        String id=request.get("id").asText();
        String adminPath="/api/admin/voucher-limit-requests/"+id;
        String approval=json.createObjectNode().put("expectedVersion","0").put("approvedLimit",25)
            .put("approvedFrom",request.get("requestedFrom").asText())
            .put("approvedUntil",request.get("requestedUntil").asText()).toString();
        String version="{\"expectedVersion\":\"0\"}";
        assertEquals(400,send(post(adminPath+"/approve").contentType(MediaType.APPLICATION_JSON)
            .content(approval),"admin-moderator","ADMIN").getResponse().getStatus());
        assertEquals(400,send(post(adminPath+"/reject").contentType(MediaType.APPLICATION_JSON)
            .content(version),"admin-moderator","ADMIN").getResponse().getStatus());
        assertEquals(403,send(get(adminPath),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(404,send(post("/api/stores/me/voucher-limit-requests/"+id+"/cancel")
            .contentType(MediaType.APPLICATION_JSON).content(version),"seller-b","SELLER").getResponse().getStatus());
        assertEquals(200,send(post(adminPath+"/reject").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"0\",\"reason\":\"Review completed\"}"),
            "admin-moderator","ADMIN").getResponse().getStatus());
        assertEquals("Review completed",db.queryForObject(
            "SELECT reason FROM voucher_limit_request_history WHERE request_id=? AND to_status='REJECTED'",
            String.class,UUID.fromString(id)));
    }

    @Test void futureGrantReplacesCurrentGrantWithoutIncreasingEffectiveQuota() throws Exception {
        assertEquals(200,approve(createRequest("active-grant",requestBody())).getResponse().getStatus());
        assertEquals(30,json.readTree(send(get("/api/stores/me/voucher-limit"),"seller-a","SELLER")
            .getResponse().getContentAsString()).get("limit").asInt());
        String future=json.createObjectNode().put("requestedLimit",30).put("reason","Future grant")
            .put("requestedFrom",start()).put("requestedUntil",end()).toString();
        JsonNode replacement=createRequest("future-grant",future);
        assertEquals(200,approve(replacement).getResponse().getStatus());
        JsonNode quota=json.readTree(send(get("/api/stores/me/voucher-limit"),"seller-a","SELLER")
            .getResponse().getContentAsString());
        assertEquals(20,quota.get("limit").asInt());
        assertEquals(replacement.get("id").asText(),quota.get("grant").get("sourceRequestId").asText());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM store_voucher_limits",Integer.class));
    }

    @Test void voucherStartingAtGrantExpiryCannotUseExtendedSlot() throws Exception {
        String from=Instant.now().minusSeconds(3600).toString(), expires=start();
        JsonNode request=createRequest("grant-boundary",json.createObjectNode().put("requestedLimit",30)
            .put("reason","Boundary").put("requestedFrom",from).put("requestedUntil",expires).toString());
        assertEquals(200,approve(request).getResponse().getStatus());
        seed(20);
        UUID voucher=draft("BOUNDARY1","seller-a");
        db.update("UPDATE vouchers SET starts_at=? WHERE id=?",
            OffsetDateTime.parse(request.get("requestedUntil").asText()),voucher);
        assertEquals(409,publish(voucher,"seller-a","SELLER"));
        assertEquals(20,count());
    }

    @Test void closedStoreCanRetractAndRejectButCannotExpand() throws Exception {
        JsonNode pending=createRequest("closed-store",requestBody());
        UUID live=draft("CLOSED1","seller-a"), unpublished=draft("CLOSED2","seller-a");
        assertEquals(200,publish(live,"seller-a","SELLER"));
        db.update("UPDATE stores SET status='CLOSED' WHERE id=?",STORE_A);
        assertEquals(409,publish(unpublished,"seller-a","SELLER"));
        assertEquals(409,approve(pending).getResponse().getStatus());
        assertEquals(409,send(post("/api/stores/me/voucher-limit-requests").header("Idempotency-Key","closed-new")
            .contentType(MediaType.APPLICATION_JSON).content(requestBody()),"seller-a","SELLER")
            .getResponse().getStatus());
        assertEquals(200,send(post("/api/stores/me/vouchers/"+live+"/pause").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"1\"}"),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(409,send(post("/api/stores/me/vouchers/"+live+"/resume").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"2\"}"),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(200,send(post("/api/stores/me/vouchers/"+live+"/cancel").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":\"2\"}"),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(200,send(post("/api/admin/voucher-limit-requests/"+pending.get("id").asText()+"/reject")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\",\"reason\":\"Closed Store\"}"),
            "admin-moderator","ADMIN").getResponse().getStatus());
    }

    @Test void voucherRoutesRequireAuthenticationAndDenyUnrelatedCustomers() throws Exception {
        UUID id=draft("AUTH01","seller-a");
        String vouchers="/api/stores/me/vouchers";
        String requests="/api/stores/me/voucher-limit-requests";
        String admin="/api/admin/voucher-limit-requests";
        String version="{\"expectedVersion\":\"0\"}";
        List<java.util.function.Supplier<MockHttpServletRequestBuilder>> routes=List.of(
            ()->get(vouchers), ()->get(vouchers+"/"+id),
            ()->post(vouchers).contentType(MediaType.APPLICATION_JSON).content(body("AUTH02")),
            ()->put(vouchers+"/"+id).contentType(MediaType.APPLICATION_JSON)
                .content(body("AUTH01").replace("{","{\"expectedVersion\":\"0\",")),
            ()->post(vouchers+"/"+id+"/publish").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->post(vouchers+"/"+id+"/pause").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->post(vouchers+"/"+id+"/resume").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->post(vouchers+"/"+id+"/cancel").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->get("/api/stores/me/voucher-limit"), ()->get(requests),
            ()->post(requests).header("Idempotency-Key","auth-test").contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()),
            ()->post(requests+"/"+id+"/cancel").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->get(admin), ()->get(admin+"/"+id),
            ()->post(admin+"/"+id+"/approve").contentType(MediaType.APPLICATION_JSON).content(version),
            ()->post(admin+"/"+id+"/reject").contentType(MediaType.APPLICATION_JSON).content(version));
        for(var route:routes){
            var anonymous=http.perform(route.get()).andReturn();
            assertEquals(401,anonymous.getResponse().getStatus(),anonymous.getRequest().getRequestURI());
            var unrelated=send(route.get(),"unrelated-customer","CUSTOMER");
            assertTrue(List.of(403,404).contains(unrelated.getResponse().getStatus()),
                unrelated.getRequest().getRequestURI()+": "+unrelated.getResponse().getStatus());
        }
    }

    @Test void suspendedSellerCannotPublishButMayRetractPriorVoucher() throws Exception {
        UUID draft=draft("PAUSE01","seller-a");
        assertEquals(200,publish(draft,"seller-a","SELLER"));
        db.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='Suspended' WHERE id=?",SELLER_A);
        assertEquals(200,send(get("/api/stores/me/vouchers"),"seller-a","CUSTOMER").getResponse().getStatus());
        assertEquals(200,send(get("/api/stores/me/vouchers/"+draft),"seller-a","CUSTOMER").getResponse().getStatus());
        assertEquals(403,send(get("/api/stores/me/vouchers"),"customer","CUSTOMER").getResponse().getStatus());
        assertEquals(404,send(get("/api/stores/me/vouchers/"+draft),"seller-b","SELLER").getResponse().getStatus());
        assertEquals(403,send(post("/api/stores/me/vouchers").contentType(MediaType.APPLICATION_JSON)
            .content(body("OTHER02")),"seller-a","SELLER").getResponse().getStatus());
        assertEquals(200,send(post("/api/stores/me/vouchers/"+draft+"/pause")
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"1\"}"),
            "seller-a","CUSTOMER").getResponse().getStatus());
    }
}
