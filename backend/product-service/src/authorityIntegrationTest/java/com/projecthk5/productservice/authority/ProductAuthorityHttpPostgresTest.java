package com.projecthk5.productservice.authority;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.security.*;
import com.projecthk5.security.*;
import com.projecthk5.productservice.product.*;

@Tag("postgresql")
class ProductAuthorityHttpPostgresTest {
    static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static String password=UUID.randomUUID().toString();
    static String authKey="test-only-product-auth-pair-credential-0123456789",factsKey="test-only-product-seller-pair-credential-0123456789";
    static ServletWebServerApplicationContext auth,seller;
    static JdbcTemplate authDb,sellerDb,productProbe;
    static DriverManagerDataSource productDs;
    static UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),sellerId=UUID.randomUUID(),store=UUID.randomUUID();
    static ProductCurrentAuthority factory;static StoreFactsClient facts;static String token,otherToken;
    static AtomicInteger networkCalls=new AtomicInteger();
    @TestConfiguration static class AuthWiring {
        @Bean Hk5JwtCodec codec(){return new Hk5JwtCodec("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());}
        @Bean CurrentAccountAuthority authority(AccountRepository accounts){return new CurrentAccountAuthority(accounts,"ACTIVE");}
    }
    @BeforeAll static void start() {
        pg.start();var root=jdbc(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        root.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '"+password+"'");root.execute("CREATE ROLE auth_app LOGIN PASSWORD '"+password+"'");
        root.execute("CREATE DATABASE auth_db OWNER auth_migrator");root.execute("CREATE DATABASE seller_db");root.execute("CREATE DATABASE product_db");
        String authUrl=pg.getJdbcUrl().replace("/test","/auth_db"),sellerUrl=pg.getJdbcUrl().replace("/test","/seller_db");
        // Each service's migrations are selected explicitly despite the two-service test classpath.
        migrate(authUrl,"auth_migrator",password,"auth-service");migrate(sellerUrl,pg.getUsername(),pg.getPassword(),"seller-service");
        authDb=jdbc(authUrl,pg.getUsername(),pg.getPassword());sellerDb=jdbc(sellerUrl,pg.getUsername(),pg.getPassword());
        authDb.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        sellerDb.execute("CREATE ROLE facts_app LOGIN PASSWORD '"+password+"'");sellerDb.execute("GRANT USAGE ON SCHEMA public TO facts_app");sellerDb.execute("GRANT SELECT ON seller_profiles,stores TO facts_app");
        productDs=new DriverManagerDataSource(pg.getJdbcUrl().replace("/test","/product_db"),pg.getUsername(),pg.getPassword());
        productProbe=new JdbcTemplate(productDs);productProbe.execute("CREATE TABLE authority_write_probe(id UUID PRIMARY KEY)");
        auth=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class,AuthWiring.class)
            .initializers(c -> com.projecthk5.security.PairManifestFixture.install(c.getEnvironment())).run("--server.port=0","--spring.application.name=auth-service","--spring.datasource.url="+authUrl,"--spring.datasource.username=auth_app","--spring.datasource.password="+password,
                "--spring.flyway.enabled=false","--hk5.auth.current-account.enabled=true","--HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_SECRET="+authKey);
        seller=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.sellerservice.SellerServiceApplication.class)
            .initializers(c -> com.projecthk5.security.PairManifestFixture.install(c.getEnvironment())).run("--server.port=0","--spring.application.name=seller-service","--spring.datasource.url="+sellerUrl,"--spring.datasource.username=facts_app","--spring.datasource.password="+password,
                "--spring.flyway.enabled=false","--hk5.seller.store-facts.enabled=true","--HK5_PRODUCT_SELLER_STORE_FACTS_SECRET="+factsKey);
        var verifier=new JwtAccessVerifier("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());
        facts=new StoreFactsClient(URI.create(base(seller)+"/internal/seller/store-facts/batch"),()->factsKey);
        factory=new ProductCurrentAuthority(new CurrentAccountClient(URI.create(base(auth)+"/internal/auth/current-account/product"),verifier,()->{networkCalls.incrementAndGet();return authKey;}),facts,Clock.systemUTC());
    }
    static JdbcTemplate jdbc(String url,String user,String pass){return new JdbcTemplate(new DriverManagerDataSource(url,user,pass));}
    static String base(ServletWebServerApplicationContext app){return "http://127.0.0.1:"+app.getWebServer().getPort();}
    static void migrate(String url,String user,String pass,String module){
        var root=java.nio.file.Path.of("..").resolve(module).resolve("src/main/resources/db/migration").toAbsolutePath().normalize();
        Flyway.configure().dataSource(url,user,pass).locations("filesystem:"+root).load().migrate();
    }
    @BeforeEach void seed(){
        authDb.execute("TRUNCATE accounts CASCADE");sellerDb.execute("TRUNCATE seller_profiles CASCADE");productProbe.execute("TRUNCATE authority_write_probe");
        for(UUID id:List.of(owner,other)){
            authDb.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",id,id+"@example.invalid","test-hash","ACTIVE");
            authDb.update("INSERT INTO account_role_grants VALUES(?,'SELLER')",id);
        }
        sellerDb.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,?,'APPROVED',2)",sellerId,owner.toString());
        sellerDb.update("INSERT INTO stores(id,seller_id,name,slug,status,version) VALUES(?,?,'Store','store','OPEN',4)",store,sellerId);
        var codec=auth.getBean(Hk5JwtCodec.class);var current=auth.getBean(CurrentAccountAuthority.class);
        token=codec.issue(current.requireCurrentForIssue(owner));otherToken=codec.issue(current.requireCurrentForIssue(other));
    }
    @AfterAll static void stop(){if(seller!=null)seller.close();if(auth!=null)auth.close();pg.stop();}
    @Test void actualRpcPreflightCannotAuthorizeEvenUnchangedWrite() {
        var policy=factory.prepareSellerManagement(token,store);
        assertEquals(owner.toString(),policy.readStore(store).ownerReference());
        assertWriteDenied(policy);
    }
    @Test void concurrentRevocationAfterActualHttpPreflightCannotCommitProductWrite() throws Exception {
        var workers=Executors.newSingleThreadExecutor();
        try {
            List<Runnable> revocations=List.of(
                ()->authDb.update("DELETE FROM account_role_grants WHERE account_id=?",owner),
                ()->authDb.update("UPDATE accounts SET status='DISABLED' WHERE id=?",owner),
                ()->sellerDb.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test revocation',version=version+1 WHERE id=?",sellerId),
                ()->sellerDb.update("UPDATE stores SET status='CLOSED',version=version+1 WHERE id=?",store));
            for(var revoke:revocations) {
                seed();var policy=factory.prepareSellerManagement(token,store);
                workers.submit(revoke).get(10,TimeUnit.SECONDS);
                assertWriteDenied(policy);
            }
        } finally {workers.shutdownNow();}
    }
    private void assertWriteDenied(ProductAccessPolicy policy) {
        int calls=networkCalls.get();
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
            assertThrows(ProductRuleException.class,()->policy.requireSellerManagement(owner.toString(),store)).getCode());
        var tx=new TransactionTemplate(new DataSourceTransactionManager(productDs));
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,assertThrows(ProductRuleException.class,()->
            tx.executeWithoutResult(status->{
                productProbe.update("INSERT INTO authority_write_probe VALUES(?)",UUID.randomUUID());
                policy.requireSellerManagement(owner.toString(),store);
            })).getCode());
        assertEquals(calls,networkCalls.get());
        assertEquals(0,productProbe.queryForObject("SELECT count(*) FROM authority_write_probe",Integer.class));
    }
    @Test void revokedDisabledAndStoreTransitionsRejectNewRequests(){
        assertNotNull(factory.prepareSellerManagement(token,store));
        sellerDb.update("UPDATE stores SET status='CLOSED',version=5 WHERE id=?",store);
        assertThrows(ProductRuleException.class,()->factory.prepareSellerManagement(token,store));
        sellerDb.update("UPDATE stores SET status='OPEN',version=6 WHERE id=?",store);
        authDb.update("DELETE FROM account_role_grants WHERE account_id=?",owner);
        assertThrows(CurrentAccountClient.Failure.class,()->factory.prepareSellerManagement(token,store));
        authDb.update("INSERT INTO account_role_grants VALUES(?,'SELLER')",owner);authDb.update("UPDATE accounts SET status='DISABLED' WHERE id=?",owner);
        assertThrows(CurrentAccountClient.Failure.class,()->factory.prepareSellerManagement(token,store));
    }
    @Test void concurrentRequestsCannotShareOwnerAuthority() throws Exception {
        var workers=Executors.newFixedThreadPool(4);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<12;i++){final boolean own=i%2==0;tasks.add(()->{
                try{factory.prepareSellerManagement(own?token:otherToken,store);return own;}catch(ProductRuleException denied){return !own;}
            });}
            for(var result:workers.invokeAll(tasks))assertTrue(result.get(15,TimeUnit.SECONDS));
            assertEquals(0,productProbe.queryForObject("SELECT count(*) FROM authority_write_probe",Integer.class));
        }finally{workers.shutdownNow();}
    }
    @Test void wrongCredentialAndUnavailableSellerAreNotAbsent(){
        var wrong=new StoreFactsClient(URI.create(base(seller)+"/internal/seller/store-facts/batch"),()->"wrong-pair-test-credential-0123456789");
        assertEquals(503,assertThrows(StoreFactsClient.Failure.class,()->wrong.read(List.of(store))).status());
        sellerDb.execute("REVOKE SELECT ON stores FROM facts_app");
        try{assertEquals(503,assertThrows(StoreFactsClient.Failure.class,()->factory.prepareSellerManagement(token,store)).status());}
        finally{sellerDb.execute("GRANT SELECT ON stores TO facts_app");}
    }
    @Test void scopedCredentialRotationWorksAcrossTheActualProvider(){
        String rotated="rotated-test-only-product-seller-pair-credential-0123456789";
        var values=new HashMap<String,Object>();values.put("HK5_PRODUCT_SELLER_STORE_FACTS_SECRET",rotated);
        values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_SECRET",factsKey);
        values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());
        seller.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("consumer-rotation",values));
        try {
            assertTrue(facts.read(List.of(store)).get(store).isPresent());
            var latest=new StoreFactsClient(URI.create(base(seller)+"/internal/seller/store-facts/batch"),()->rotated);
            assertTrue(latest.read(List.of(store)).get(store).isPresent());
            values.put("HK5_PRODUCT_SELLER_STORE_FACTS_PREVIOUS_UNTIL",Instant.now().minusSeconds(1).toString());
            assertThrows(StoreFactsClient.Failure.class,()->facts.read(List.of(store)));
        }finally{seller.getEnvironment().getPropertySources().remove("consumer-rotation");}
    }
}
