package com.projecthk5.sellerservice.storefacts;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import com.fasterxml.jackson.databind.*;

@Tag("postgresql")
class StoreFactsHttpPostgresTest {
    static final String PATH="/internal/seller/store-facts/batch";
    static final String KEY="test-only-product-seller-facts-credential-0123456789";
    static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static JdbcTemplate admin;static ServletWebServerApplicationContext app;
    static String password=UUID.randomUUID().toString(),base;
    static HttpClient http=HttpClient.newHttpClient();static ObjectMapper json=new ObjectMapper();
    UUID seller=UUID.randomUUID(),store=UUID.randomUUID();
    @BeforeAll static void start() {
        pg.start();Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).load().migrate();
        admin=new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
        admin.execute("CREATE ROLE facts_app LOGIN PASSWORD '"+password+"'");
        admin.execute("GRANT USAGE ON SCHEMA public TO facts_app");
        admin.execute("GRANT SELECT ON seller_profiles,stores TO facts_app");
        app=boot(true);base="http://127.0.0.1:"+app.getWebServer().getPort();
    }
    static ServletWebServerApplicationContext boot(boolean enabled) {
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.sellerservice.SellerServiceApplication.class)
            .initializers(c -> com.projecthk5.security.PairManifestFixture.install(c.getEnvironment())).run("--server.port=0","--spring.datasource.url="+pg.getJdbcUrl(),"--spring.datasource.username=facts_app",
                "--spring.datasource.password="+password,"--spring.flyway.enabled=false","--hk5.seller.store-facts.enabled="+enabled,
                "--HK5_PRODUCT_SELLER_STORE_FACTS_SECRET="+KEY,"--spring.jpa.properties.hibernate.generate_statistics=true");
    }
    @AfterAll static void stop(){if(app!=null)app.close();pg.stop();}
    @BeforeEach void seed(){
        admin.execute("TRUNCATE seller_profiles CASCADE");
        admin.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,?,'APPROVED',7)",seller," Owner-A ");
        admin.update("INSERT INTO stores(id,seller_id,name,slug,status,version) VALUES(?,?,'Store','store','OPEN',9)",store,seller);
    }
    String body(UUID...ids) throws Exception {return json.writeValueAsString(Map.of("storeIds",Arrays.stream(ids).map(UUID::toString).toList()));}
    HttpResponse<String> call(String credential,String body) throws Exception {return send(base,credential,body);}
    HttpResponse<String> send(String target,String credential,String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(target+PATH)).timeout(Duration.ofSeconds(8))
            .header("Content-Type","application/json").header("X-HK5-Service-Credential",credential)
            .header("X-Caller-Service","product-service").header("X-User-Id","forged-owner").header("X-Roles","ADMIN")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void batchFoundAbsentOwnerAndSingleStatementSnapshot() throws Exception {
        UUID absent=UUID.randomUUID();
        var statistics=app.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();statistics.clear();
        var result=call(KEY,body(absent,store));assertEquals(200,result.statusCode(),result.body());
        var rows=json.readTree(result.body()).get("results");assertEquals(2,rows.size());
        assertEquals("ABSENT",rows.get(0).get("result").textValue());assertEquals(absent.toString(),rows.get(0).get("storeId").textValue());
        var found=rows.get(1);assertEquals("FOUND",found.get("result").textValue());assertEquals(store.toString(),found.get("storeId").textValue());
        assertEquals(seller.toString(),found.get("sellerId").textValue());assertEquals(" Owner-A ",found.get("ownerReference").textValue());
        assertEquals("OPEN",found.get("storeStatus").textValue());assertEquals("APPROVED",found.get("sellerStatus").textValue());
        assertEquals("9",found.get("storeVersion").textValue());assertEquals("7",found.get("sellerVersion").textValue());
        assertEquals(1,statistics.getPrepareStatementCount());
        assertFalse(result.body().contains("house"));assertFalse(result.body().contains("reviewReason"));assertFalse(result.body().contains("accountStatus"));
        assertEquals("no-store",result.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void strictInputAndSizeLimits() throws Exception {
        for(String value:List.of(body(store,store),"{\"storeIds\":[]}","{\"storeIds\":[\"bad\"]}","{\"storeIds\":null}",
            "{\"storeIds\":[],\"storeIds\":[]}",body(store)+" {}",body(store).replace("}",",\"extra\":true}"))) {
            assertEquals(400,call(KEY,value).statusCode(),value);
        }
        var tooMany=new ArrayList<String>();for(int i=0;i<101;i++)tooMany.add(UUID.randomUUID().toString());
        assertEquals(400,call(KEY,json.writeValueAsString(Map.of("storeIds",tooMany))).statusCode());
        assertEquals(413,call(KEY,"x".repeat(8193)).statusCode());
    }
    @Test void wrongCredentialsCannotBeReplacedByForgedHeaders() throws Exception {
        assertEquals(401,call("wrong",body(store)).statusCode());
        var request=HttpRequest.newBuilder(URI.create(base+PATH)).header("Content-Type","application/json")
            .header("X-Caller-Service","product-service").POST(HttpRequest.BodyPublishers.ofString(body(store))).build();
        assertEquals(401,http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void databaseFailureRejectsWholeBatchWithoutUsableSubset() throws Exception {
        admin.execute("REVOKE SELECT ON stores FROM facts_app");
        try{var result=call(KEY,body(store,UUID.randomUUID()));assertEquals(503,result.statusCode());assertFalse(result.body().contains("results"));}
        finally{admin.execute("GRANT SELECT ON stores TO facts_app");}
    }
    @Test void inconsistentOrphanIs503RatherThanConfirmedAbsent() throws Exception {
        UUID orphan=UUID.randomUUID();
        try(var connection=admin.getDataSource().getConnection();var statement=connection.createStatement()) {
            statement.execute("SET session_replication_role=replica");
            try{statement.execute("INSERT INTO stores(id,seller_id,name,slug,status) VALUES('"+orphan+"','"+UUID.randomUUID()+"','Orphan','orphan','OPEN')");}
            finally{statement.execute("SET session_replication_role=origin");}
        }
        var result=call(KEY,body(store,orphan));assertEquals(503,result.statusCode());assertFalse(result.body().contains("results"));
    }
    @Test void defaultOffAndBusinessEndpointsRemainDenied() throws Exception {
        try(var closed=boot(false)) {
            assertEquals(403,send("http://127.0.0.1:"+closed.getWebServer().getPort(),KEY,body(store)).statusCode());
        }
        var request=HttpRequest.newBuilder(URI.create(base+"/api/sellers")).header("X-HK5-Service-Credential",KEY).GET().build();
        assertEquals(403,http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void readsCurrentStatusesAndVersionsWithoutInventingEligibility() throws Exception {
        call(KEY,body(store));
        admin.update("UPDATE stores SET status='CLOSED',version=10 WHERE id=?",store);
        admin.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test',version=8 WHERE id=?",seller);
        var result=call(KEY,body(store));assertEquals(200,result.statusCode());var found=json.readTree(result.body()).get("results").get(0);
        assertEquals("CLOSED",found.get("storeStatus").textValue());assertEquals("SUSPENDED",found.get("sellerStatus").textValue());
        assertEquals("10",found.get("storeVersion").textValue());assertEquals("8",found.get("sellerVersion").textValue());
    }
    @Test void rotationExpiryAndUnavailableCredentialsFailClosed() throws Exception {
        String previous="test-only-previous-product-facts-credential-0123456789";
        var values=new HashMap<String,Object>();values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_SECRET",previous);
        values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());
        app.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("rotation-test",values));
        try {
            assertEquals(200,call(previous,body(store)).statusCode());
            values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_UNTIL",Instant.now().minusSeconds(1).toString());
            assertEquals(401,call(previous,body(store)).statusCode());
            assertEquals(401,call("test-only-seller-auth-other-pair-credential-0123456789",body(store)).statusCode());
            values.put("HK5_PRODUCT_SELLER_STORE_FACTS_SECRET","");
            assertEquals(503,call(KEY,body(store)).statusCode());
        }finally{app.getEnvironment().getPropertySources().remove("rotation-test");}
    }
    @Test void duplicateCredentialsMalformedUtf8AndWrongMethodAreRejected() throws Exception {
        var duplicate=HttpRequest.newBuilder(URI.create(base+PATH)).header("X-HK5-Service-Credential",KEY)
            .header("X-HK5-Service-Credential",KEY).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body(store))).build();
        assertEquals(401,http.send(duplicate,HttpResponse.BodyHandlers.ofString()).statusCode());
        var get=HttpRequest.newBuilder(URI.create(base+PATH)).header("X-HK5-Service-Credential",KEY).GET().build();
        assertEquals(403,http.send(get,HttpResponse.BodyHandlers.ofString()).statusCode());
        var invalid=HttpRequest.newBuilder(URI.create(base+PATH)).header("X-HK5-Service-Credential",KEY)
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{(byte)0xc3,0x28})).build();
        assertEquals(400,http.send(invalid,HttpResponse.BodyHandlers.ofString()).statusCode());
        var otherPath=HttpRequest.newBuilder(URI.create(base+PATH+"/other")).header("X-HK5-Service-Credential",KEY)
            .POST(HttpRequest.BodyPublishers.ofString(body(store))).build();
        assertEquals(403,http.send(otherPath,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void maximumBatchAndDeletedStoreAreAuthoritative() throws Exception {
        var ids=new ArrayList<UUID>();ids.add(store);for(int i=1;i<100;i++)ids.add(UUID.randomUUID());
        var result=call(KEY,body(ids.toArray(UUID[]::new)));assertEquals(200,result.statusCode());
        var rows=json.readTree(result.body()).get("results");assertEquals(100,rows.size());
        for(int i=0;i<100;i++)assertEquals(ids.get(i).toString(),rows.get(i).get("storeId").textValue());
        admin.update("DELETE FROM stores WHERE id=?",store);
        var absent=call(KEY,body(store));assertEquals(200,absent.statusCode());
        assertEquals("ABSENT",json.readTree(absent.body()).get("results").get(0).get("result").textValue());
    }
    @Test void crossPairAliasesNeverDiscloseOwnerFacts() throws Exception {
        String previous="old-test-only-product-facts-credential-0123456789";
        var stats=app.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        for(String foreign:List.of("HK5_SELLER_AUTH_ROLE_SYNC_","HK5_SELLER_AUTH_CURRENT_ACCOUNT_"))
        for(String own:List.of(KEY,previous))for(String slot:List.of("SECRET","PREVIOUS_SECRET")) {
            var values=new HashMap<String,Object>();values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_SECRET",previous);
            values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());
            values.put(foreign+slot,own);values.put(foreign+"PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());
            app.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("cross-alias",values));
            try {var result=call(own,body(store));assertEquals(503,result.statusCode());assertFalse(result.body().contains("ownerReference"));}
            finally{app.getEnvironment().getPropertySources().remove("cross-alias");}
        }
        assertEquals(0,stats.getPrepareStatementCount());
    }
}
