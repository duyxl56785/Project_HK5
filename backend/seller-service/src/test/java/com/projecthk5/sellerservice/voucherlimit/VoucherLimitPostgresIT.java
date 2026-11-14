package com.projecthk5.sellerservice.voucherlimit;

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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class VoucherLimitPostgresIT {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",PG::getJdbcUrl);
        p.add("spring.datasource.username",PG::getUsername);
        p.add("spring.datasource.password",PG::getPassword);
        p.add("spring.datasource.hikari.maximum-pool-size",()->12);
    }
    private static final UUID SELLER=UUID.fromString("00000001-0000-4000-8000-000000000001");
    private static final UUID STORE=UUID.fromString("00000002-0000-4000-8000-000000000001");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void reset(){
        for(String table:List.of("voucher_status_history","vouchers","store_voucher_limits",
                "voucher_limit_request_history","voucher_limit_requests","seller_outbox","seller_status_history",
                "stores","seller_profiles"))jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED')",SELLER,"owner-vc");
        jdbc.update("INSERT INTO stores(id,seller_id,name,slug,status) VALUES (?,?,'VC Store','vc-store','OPEN')",STORE,SELLER);
    }
    private MvcResult as(MockHttpServletRequestBuilder req,String actor,String role)throws Exception{
        JwtRequestPostProcessor token=jwt().jwt(j->j.subject(actor).claim("roles",List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_"+role));
        return mvc.perform(req.with(token)).andReturn();
    }
    private String submitBody(){
        Instant now=Instant.now();
        return "{\"requestedLimit\":30,\"reason\":\"xả kho lô cuối\",\"requestedFrom\":\""
                +now.minusSeconds(60).toString()+"\",\"requestedUntil\":\""
                +now.plusSeconds(86400).toString()+"\"}";
    }
    private MvcResult submit(String body,String key)throws Exception{
        return as(post("/api/stores/me/voucher-limit-requests")
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body),
                "owner-vc","SELLER");
    }
    @Test void vl03ApproveDoesNotIssueVouchersAndIdempotencyReplayIs200()throws Exception{
        String body=submitBody(),key=UUID.randomUUID().toString();
        MvcResult first=submit(body,key);
        assertEquals(201,first.getResponse().getStatus());
        String raw=first.getResponse().getContentAsString();
        assertEquals(200,submit(body,key).getResponse().getStatus());
        String id=json.readTree(raw).get("id").asText();
        assertEquals(409,submit(submitBody(),key).getResponse().getStatus());
        assertEquals(20,json.readTree(as(get("/api/stores/me/voucher-limit"),"owner-vc","SELLER")
                .getResponse().getContentAsString()).get("limit").asInt());
        JsonNode old=json.readTree(raw);
        String approved="{\"expectedVersion\":\"0\",\"approvedLimit\":25,\"approvedFrom\":\""
                +old.get("requestedFrom").asText()+"\",\"approvedUntil\":\""
                +old.get("requestedUntil").asText()+"\",\"reason\":\"cấp 25\"}";
        assertEquals(200,as(post("/api/admin/voucher-limit-requests/"+id+"/approve")
                .contentType(MediaType.APPLICATION_JSON).content(approved),"admin-vc","ADMIN")
                .getResponse().getStatus());
        assertEquals(25,json.readTree(as(get("/api/stores/me/voucher-limit"),"owner-vc","SELLER")
                .getResponse().getContentAsString()).get("limit").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM vouchers",Integer.class));
    }
    @Test void vl04AndVl06RolesSelfApprovalAndSuspendedSeller()throws Exception{
        String body=submitBody();
        assertEquals(401,mvc.perform(get("/api/admin/voucher-limit-requests")).andReturn().getResponse().getStatus());
        assertEquals(403,as(get("/api/admin/voucher-limit-requests"),"owner-vc","SELLER").getResponse().getStatus());
        MvcResult r=submit(body,UUID.randomUUID().toString());
        assertEquals(201,r.getResponse().getStatus());
        JsonNode request=json.readTree(r.getResponse().getContentAsString());
        String id=request.get("id").asText();
        String approve="{\"expectedVersion\":\"0\",\"approvedLimit\":30,\"approvedFrom\":\""
                +request.get("requestedFrom").asText()+"\",\"approvedUntil\":\""
                +request.get("requestedUntil").asText()+"\"}";
        assertEquals(403,as(post("/api/admin/voucher-limit-requests/"+id+"/approve")
                .contentType(MediaType.APPLICATION_JSON).content(approve),"owner-vc","ADMIN").getResponse().getStatus());
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test' WHERE id=?",SELLER);
        assertEquals(409,as(post("/api/admin/voucher-limit-requests/"+id+"/approve")
                .contentType(MediaType.APPLICATION_JSON).content(approve),"admin-vc","ADMIN").getResponse().getStatus());
        assertEquals(200,as(post("/api/admin/voucher-limit-requests/"+id+"/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\",\"reason\":\"Seller suspended\"}"),
                "admin-vc","ADMIN").getResponse().getStatus());
    }
    @Test void vl10CancelVersusApproveCannotBothCommit()throws Exception{
        MvcResult r=submit(submitBody(),UUID.randomUUID().toString());
        JsonNode row=json.readTree(r.getResponse().getContentAsString());
        String id=row.get("id").asText();
        String body="{\"expectedVersion\":\"0\",\"approvedLimit\":30,\"approvedFrom\":\""
                +row.get("requestedFrom").asText()+"\",\"approvedUntil\":\""
                +row.get("requestedUntil").asText()+"\"}";
        CountDownLatch ready=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Future<Integer> cancel=pool.submit(()->{ready.await();return as(post("/api/stores/me/voucher-limit-requests/"+id+"/cancel")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":\"0\"}"),
                    "owner-vc","SELLER").getResponse().getStatus();});
            Future<Integer> approve=pool.submit(()->{ready.await();return as(post("/api/admin/voucher-limit-requests/"+id+"/approve")
                    .contentType(MediaType.APPLICATION_JSON).content(body),"admin-vc","ADMIN")
                    .getResponse().getStatus();});
            ready.countDown();
            int a=cancel.get(20,TimeUnit.SECONDS), b=approve.get(20,TimeUnit.SECONDS);
            assertEquals(1,(a==200?1:0)+(b==200?1:0));
            assertEquals(1,(a==409?1:0)+(b==409?1:0));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM voucher_limit_requests WHERE id=?",Integer.class,UUID.fromString(id)));
        }
    }
}
