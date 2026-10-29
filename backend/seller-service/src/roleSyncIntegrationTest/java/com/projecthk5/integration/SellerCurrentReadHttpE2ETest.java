package com.projecthk5.integration;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.*;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.security.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class SellerCurrentReadHttpE2ETest {
    static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static String password=UUID.randomUUID().toString(),pair="test-only-seller-current-read-pair-0123456789";
    static ServletWebServerApplicationContext auth,seller;static JdbcTemplate authDb,sellerDb;
    static UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),profile=UUID.randomUUID(),store=UUID.randomUUID();
    static String base,token;static HttpClient http=HttpClient.newHttpClient();static ObjectMapper json=new ObjectMapper();
    @TestConfiguration static class AuthWiring {
        @Bean Hk5JwtCodec codec(){return new Hk5JwtCodec("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());}
        @Bean CurrentAccountAuthority authority(AccountRepository accounts){return new CurrentAccountAuthority(accounts,"ACTIVE");}
    }
    @BeforeAll static void start(){
        pg.start();var root=jdbc(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        root.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '"+password+"'");root.execute("CREATE ROLE auth_app LOGIN PASSWORD '"+password+"'");root.execute("CREATE DATABASE auth_db OWNER auth_migrator");root.execute("CREATE DATABASE seller_db");
        String au=pg.getJdbcUrl().replace("/test","/auth_db"),su=pg.getJdbcUrl().replace("/test","/seller_db");
        migrate(au,"auth_migrator",password,"auth-service");migrate(su,pg.getUsername(),pg.getPassword(),"seller-service");
        authDb=jdbc(au,pg.getUsername(),pg.getPassword());sellerDb=jdbc(su,pg.getUsername(),pg.getPassword());authDb.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        auth=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class,AuthWiring.class)
            .run("--server.port=0","--spring.datasource.url="+au,"--spring.datasource.username=auth_app","--spring.datasource.password="+password,"--spring.flyway.enabled=false",
                "--hk5.auth.current-account.enabled=true","--HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET="+pair);
        seller=boot(su,true);base="http://127.0.0.1:"+seller.getWebServer().getPort();
    }
    static ServletWebServerApplicationContext boot(String url,boolean enabled){
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.sellerservice.SellerServiceApplication.class)
            .run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username="+pg.getUsername(),"--spring.datasource.password="+pg.getPassword(),"--spring.flyway.enabled=false",
                "--hk5.seller.http.read.enabled="+enabled,"--HK5_AUTH_CURRENT_ACCOUNT_URL=http://127.0.0.1:"+auth.getWebServer().getPort()+"/internal/auth/current-account/seller",
                "--HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET="+pair,"--HK5_AUTH_JWT_ISSUER=https://issuer.example.invalid","--HK5_AUTH_JWT_AUDIENCE=aud",
                "--HK5_AUTH_JWT_CURRENT_KID=test","--HK5_AUTH_JWT_CURRENT_KEY_B64="+Base64.getEncoder().encodeToString(new byte[32]));
    }
    static JdbcTemplate jdbc(String url,String user,String pass){return new JdbcTemplate(new DriverManagerDataSource(url,user,pass));}
    static void migrate(String url,String user,String pass,String module){Flyway.configure().dataSource(url,user,pass).locations("filesystem:"+java.nio.file.Path.of("..").resolve(module).resolve("src/main/resources/db/migration").toAbsolutePath().normalize()).load().migrate();}
    @BeforeEach void seed(){
        authDb.execute("TRUNCATE accounts CASCADE");sellerDb.execute("TRUNCATE seller_role_delivery,seller_status_outbox,seller_profiles CASCADE");
        for(UUID id:List.of(owner,other)){
            authDb.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",id,id+"@example.invalid","test-hash","ACTIVE");authDb.update("INSERT INTO account_role_grants VALUES(?,'CUSTOMER'),(?,'SELLER')",id,id);
        }
        sellerDb.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,?,'APPROVED',2)",profile,owner.toString());
        sellerDb.update("INSERT INTO stores(id,seller_id,name,slug,status,version) VALUES(?,?,'Own','own','CLOSED',3)",store,profile);
        token=token(owner);
    }
    static String token(UUID who){return auth.getBean(Hk5JwtCodec.class).issue(auth.getBean(CurrentAccountAuthority.class).requireCurrentForIssue(who));}
    static HttpResponse<String> get(String target,String path,String bearer) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(target+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+bearer)
            .header("X-User-Id",other.toString()).header("X-Roles","ADMIN").GET().build();return http.send(request,HttpResponse.BodyHandlers.ofString());
    }
    @AfterAll static void stop(){if(seller!=null)seller.close();if(auth!=null)auth.close();pg.stop();}
    @Test void realJwtAndCurrentRpcReadOnlyOwnDataThroughJpaProxies() throws Exception {
        var own=get(base,"/api/sellers/me",token);assertEquals(200,own.statusCode(),own.body());assertEquals(profile.toString(),json.readTree(own.body()).get("id").textValue());assertFalse(own.body().contains("ownerReference"));
        var shop=get(base,"/api/stores/me",token);assertEquals(200,shop.statusCode(),shop.body());assertFalse(json.readTree(shop.body()).get("open").booleanValue());assertEquals("3",json.readTree(shop.body()).get("version").textValue());
        assertEquals(404,get(base,"/api/sellers/me",token(other)).statusCode());
        assertEquals(400,get(base,"/api/sellers/me?sellerId="+profile,token).statusCode());
    }
    @Test void oldTokenCannotKeepRevokedGrantOrReadDisabledAccount() throws Exception {
        authDb.update("DELETE FROM account_role_grants WHERE account_id=? AND role='SELLER'",owner);
        assertEquals(403,get(base,"/api/stores/me",token).statusCode());assertEquals(200,get(base,"/api/sellers/me",token).statusCode());
        authDb.update("UPDATE accounts SET status='DISABLED' WHERE id=?",owner);assertEquals(401,get(base,"/api/sellers/me",token).statusCode());
    }
    @Test void localSellerStatusRecheckedAndBusinessWritesDenied() throws Exception {
        sellerDb.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='private',version=3 WHERE id=?",profile);
        assertEquals(403,get(base,"/api/stores/me",token).statusCode());
        var request=HttpRequest.newBuilder(URI.create(base+"/api/sellers")).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(403,http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(1,sellerDb.queryForObject("SELECT count(*) FROM seller_profiles",Integer.class));
    }
    @Test void disabledConfigAndUnavailableAuthRemainClosed() throws Exception {
        try(var off=boot(pg.getJdbcUrl().replace("/test","/seller_db"),false)){
            assertEquals(403,get("http://127.0.0.1:"+off.getWebServer().getPort(),"/api/sellers/me",token).statusCode());
        }
        authDb.execute("REVOKE SELECT ON accounts FROM auth_app");try{assertEquals(503,get(base,"/api/sellers/me",token).statusCode());}finally{authDb.execute("GRANT SELECT ON accounts TO auth_app");}
    }
}
