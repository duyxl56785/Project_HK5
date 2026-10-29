package com.projecthk5.authservice.authority;
import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.authservice.security.*;
import com.projecthk5.authservice.persistence.AccountRepository;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

class CurrentAccountRpcPostgresTest {
    static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static String password=UUID.randomUUID().toString(),key="test-only-current-account-pair-key-0123456789";
    static ServletWebServerApplicationContext app;
    static JdbcTemplate admin;
    static UUID account=UUID.randomUUID();
    static HttpClient http=HttpClient.newHttpClient();
    static Hk5JwtCodec codec;
    static CurrentAccountAuthority authority;
    static String base;
    @TestConfiguration static class Wiring {
        @Bean Hk5JwtCodec codec() {return new Hk5JwtCodec("https://issuer.example.invalid","test-audience","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());}
        @Bean CurrentAccountAuthority authority(AccountRepository repository) {return new CurrentAccountAuthority(repository,"ACTIVE");}
    }
    @BeforeAll static void setup() {
        pg.start();
        var root=new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
        root.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '"+password+"'");
        root.execute("CREATE ROLE auth_app LOGIN PASSWORD '"+password+"'");
        root.execute("CREATE DATABASE auth_db OWNER auth_migrator");
        String url=pg.getJdbcUrl().replace("/test","/auth_db");
        Flyway.configure().dataSource(url,"auth_migrator",password).load().migrate();
        admin=new JdbcTemplate(new DriverManagerDataSource(url,pg.getUsername(),pg.getPassword()));
        admin.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        app=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class,Wiring.class)
            .run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username=auth_app","--spring.datasource.password="+password,
                "--hk5.auth.current-account.enabled=true","--HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET="+key,
                "--HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_SECRET=product-test-only-current-account-key-0123456789");
        base="http://127.0.0.1:"+app.getWebServer().getPort();
        codec=app.getBean(Hk5JwtCodec.class); authority=app.getBean(CurrentAccountAuthority.class);
    }
    @AfterAll static void close() {if(app!=null)app.close();pg.stop();}
    @BeforeEach void reset() {
        admin.execute("TRUNCATE accounts CASCADE");
        admin.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",account,account+"@example.invalid","test-only-hash","ACTIVE");
        admin.update("INSERT INTO account_role_grants VALUES(?,'CUSTOMER')",account);
    }
    String token(){return codec.issue(authority.requireCurrentForIssue(account));}
    HttpResponse<String> call(String pair,String token,String body,String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10))
            .header("X-HK5-Service-Credential",pair).header("Authorization","Bearer "+token)
            .header("X-User-Id",UUID.randomUUID().toString()).header("X-Roles","ADMIN")
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String body(){return "{\"requestId\":\""+UUID.randomUUID()+"\"}";}
    static final String PATH="/internal/auth/current-account/seller";
    @Test void effectiveRolesNeverPromoteAndReplaysReadCurrentGrants() throws Exception {
        String token=token(),body=body();
        admin.update("INSERT INTO account_role_grants VALUES(?,'ADMIN')",account);
        var result=call(key,token,body,PATH); assertEquals(200,result.statusCode(),result.body());
        assertTrue(result.body().contains(account.toString()));assertFalse(result.body().contains("ADMIN"));
        assertFalse(result.body().contains("email"));assertFalse(result.body().contains("password"));
        admin.update("DELETE FROM account_role_grants WHERE account_id=? AND role='CUSTOMER'",account);
        assertEquals(403,call(key,token,body,PATH).statusCode());
    }
    @Test void disabledBadJwtWrongPairAndStrictBodyFailClosed() throws Exception {
        String token=token();
        assertEquals(401,call(key,"invalid",body(),PATH).statusCode());
        assertEquals(401,call(key,token.substring(0,token.lastIndexOf('.')+1)+"a".repeat(43),body(),PATH).statusCode());
        assertEquals(401,call("wrong",token,body(),PATH).statusCode());
        assertEquals(401,call(key,token,body(),"/internal/auth/current-account/product").statusCode());
        assertEquals(400,call(key,token,"{\"accountId\":\"forged\"}",PATH).statusCode());
        admin.update("UPDATE accounts SET status='DISABLED' WHERE id=?",account);
        assertEquals(401,call(key,token,body(),PATH).statusCode());
    }
    @Test void expiredJwtIsRejectedUsingExistingVerifier() throws Exception {
        var old=new Hk5JwtCodec("https://issuer.example.invalid","test-audience","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.offset(Clock.systemUTC(),Duration.ofHours(-1)));
        assertEquals(401,call(key,old.issue(authority.requireCurrentForIssue(account)),body(),PATH).statusCode());
    }
    @Test void databaseFailureReturns503NotIdentityOrPermissionDenial() throws Exception {
        String token=token(); admin.execute("REVOKE SELECT ON account_role_grants FROM auth_app");
        try {assertEquals(503,call(key,token,body(),PATH).statusCode());}
        finally {admin.execute("GRANT SELECT ON account_role_grants TO auth_app");}
    }
    @Test void scopedClientAgainstRealRpcRechecksRevocation() {
        var verifier=new com.projecthk5.security.JwtAccessVerifier("https://issuer.example.invalid","test-audience","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());
        var client=new com.projecthk5.security.CurrentAccountClient(URI.create(base+PATH),verifier,()->key);
        String token=token();assertEquals(Set.of("CUSTOMER"),client.resolve(token).roles());
        admin.update("UPDATE accounts SET status='DISABLED' WHERE id=?",account);
        assertEquals(401,assertThrows(com.projecthk5.security.CurrentAccountClient.Failure.class,()->client.resolve(token)).status());
    }
    @Test void credentialRotationDuplicatesAndMethodScope() throws Exception {
        String token=token(),previous="test-only-previous-current-account-key-0123456789";
        var values=new HashMap<String,Object>();values.put("HK5_SELLER_AUTH_CURRENT_ACCOUNT_PREVIOUS_SECRET",previous);
        values.put("HK5_SELLER_AUTH_CURRENT_ACCOUNT_PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());
        app.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("rotation-test",values));
        try {
            assertEquals(200,call(previous,token,body(),PATH).statusCode());
            values.put("HK5_SELLER_AUTH_CURRENT_ACCOUNT_PREVIOUS_UNTIL",Instant.now().minusSeconds(1).toString());
            assertEquals(401,call(previous,token,body(),PATH).statusCode());
            assertEquals(200,call("product-test-only-current-account-key-0123456789",token,body(),"/internal/auth/current-account/product").statusCode());
            var duplicate=HttpRequest.newBuilder(URI.create(base+PATH)).header("X-HK5-Service-Credential",key)
                .header("X-HK5-Service-Credential",key).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body())).build();
            assertEquals(401,http.send(duplicate,HttpResponse.BodyHandlers.ofString()).statusCode());
            var wrongMethod=HttpRequest.newBuilder(URI.create(base+PATH)).header("X-HK5-Service-Credential",key).GET().build();
            assertEquals(403,http.send(wrongMethod,HttpResponse.BodyHandlers.ofString()).statusCode());
            values.put("HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_SECRET",key);
            assertEquals(503,call(key,token,body(),PATH).statusCode());
        }finally {app.getEnvironment().getPropertySources().remove("rotation-test");}
    }
    @Test void defaultOffAndMissingBootstrapRemainClosed() throws Exception {
        String url=pg.getJdbcUrl().replace("/test","/auth_db");
        for(boolean enabled:List.of(false,true)) {
            try(var other=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class)
                    .run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username=auth_app","--spring.datasource.password="+password,
                        "--hk5.auth.current-account.enabled="+enabled,"--HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET="+key)) {
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+other.getWebServer().getPort()+PATH))
                    .header("X-HK5-Service-Credential",key).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body())).build();
                assertEquals(enabled?503:403,http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
            }
        }
    }    @Test void strictDtoRejectsDuplicateUnknownAndOversizedBodies() throws Exception {
        String token=token();
        for(String body:List.of("{}",body().replace("}",",\"extra\":true}"),
            body().replace("}",",\"requestId\":\""+UUID.randomUUID()+"\"}"),"[]")) {
            assertEquals(400,call(key,token,body,PATH).statusCode());
        }
        assertEquals(413,call(key,token,"x".repeat(1025),PATH).statusCode());
    }}
