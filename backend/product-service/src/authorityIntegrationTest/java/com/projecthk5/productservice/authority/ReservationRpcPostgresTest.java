package com.projecthk5.productservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class ReservationRpcPostgresTest {
    static final String AUTH="HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_",SELLER="HK5_PRODUCT_SELLER_STORE_FACTS_",VERIFY="HK5_SELLER_AUTH_CURRENT_ACCOUNT_";
    static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static final String password=UUID.randomUUID().toString();
    static final Map<String,String> secrets=new HashMap<>();
    static ServletWebServerApplicationContext auth,seller,product,productWorker;
    static final Map<String,Object> authConfig=new HashMap<>(),sellerConfig=new HashMap<>(),productConfig=new HashMap<>(),workerConfig=new HashMap<>();
    static JdbcTemplate authDb,sellerDb,productDb,authMutation;
    static DataSource worker,receiptReader,authRuntime,sellerRuntime;
    static DurableWriteLedger ledger;
    static FaultProxy callback,admission;
    static final UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),profile=UUID.randomUUID(),store=UUID.randomUUID();
    static final HttpClient http=HttpClient.newHttpClient();
    String token;WriteIntent intent;

    @BeforeAll static void start() throws Exception {
        pg.start();var root=db(pg.getJdbcUrl(),pg.getUsername());
        for(String role:List.of("auth_migrator","auth_app","seller_migrator","product_migrator","auth_rpc_app","seller_rpc_app","product_receipt_app","product_worker_app"))
            root.execute("CREATE ROLE "+role+" LOGIN PASSWORD '"+password+"'");
        for(String role:List.of("auth_reservation_rpc","seller_reservation_rpc","product_terminal_reader","product_reservation_writer"))root.execute("CREATE ROLE "+role+" NOLOGIN");
        root.execute("GRANT auth_reservation_rpc TO auth_rpc_app");root.execute("GRANT seller_reservation_rpc TO seller_rpc_app");
        root.execute("GRANT product_terminal_reader TO product_receipt_app");root.execute("GRANT product_reservation_writer TO product_worker_app");
        for(String module:List.of("auth","seller","product")) {
            root.execute("CREATE DATABASE "+module+"_db OWNER "+module+"_migrator");
            root.execute("REVOKE ALL ON DATABASE "+module+"_db FROM PUBLIC");
            String roles=switch(module){case "auth"->"auth_app,auth_rpc_app";case "seller"->"seller_rpc_app";default->"product_receipt_app,product_worker_app";};
            root.execute("GRANT CONNECT ON DATABASE "+module+"_db TO "+roles);
            migrate(module,module+"_migrator");
            var admin=db(url(module),pg.getUsername());admin.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            String grants=Files.readString(Path.of("../../docs/tv1/sql/reservation-"+module+"-grants.sql"));admin.execute(grants);
        }
        authDb=db(url("auth"),pg.getUsername());sellerDb=db(url("seller"),pg.getUsername());productDb=db(url("product"),pg.getUsername());
        authDb.execute("GRANT USAGE ON SCHEMA public TO auth_app");authMutation=db(url("auth"),"auth_app");
        productDb.execute("CREATE TABLE rpc_write_probe(id UUID PRIMARY KEY)");productDb.execute("GRANT SELECT,INSERT ON rpc_write_probe TO product_reservation_writer");
        worker=source(url("product"),"product_worker_app");receiptReader=source(url("product"),"product_receipt_app");
        authRuntime=source(url("auth"),"auth_rpc_app");sellerRuntime=source(url("seller"),"seller_rpc_app");ledger=new DurableWriteLedger(worker);
        defaults();product=bootProduct(true);
        callback=new FaultProxy(base(product));auth=bootAuth(true);admission=new FaultProxy(base(auth));seller=bootSeller(true);
        workerConfig.put("HK5_PRODUCT_AUTH_RESERVATION_URL",base(auth)+"/internal/auth/write-reservations/product/");
        workerConfig.put("HK5_PRODUCT_SELLER_RESERVATION_URL",base(seller)+"/internal/seller/write-reservations/product/");
        productWorker=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.productservice.ProductServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rpc-process",workerConfig)))
            .properties("hk5.product.reservations.client.enabled=true").run(args("product","product_worker_app"));
    }
    static void migrate(String module,String user){Flyway.configure().dataSource(url(module),user,password).locations("filesystem:"+Path.of("../"+module+"-service/src/main/resources/db/migration").toAbsolutePath()).load().migrate();}
    static String url(String module){return pg.getJdbcUrl().replace("/test","/"+module+"_db");}
    static DataSource source(String url,String role){return new DriverManagerDataSource(url,role,role.equals(pg.getUsername())?pg.getPassword():password);}
    static JdbcTemplate db(String url,String role){return new JdbcTemplate(source(url,role));}
    static String base(ServletWebServerApplicationContext app){return "http://127.0.0.1:"+app.getWebServer().getPort();}
    static String manifest(){return PairCredentialManifest.provision(secrets::get);}
    static void defaults(){secrets.clear();for(String pair:PairCredentialManifest.PAIRS)secrets.put(pair+"SECRET","test-only-reservation-key-0123456789-"+pair);refresh();}
    static void config(Map<String,Object> target,List<String> pairs){
        target.clear();target.put(PairCredentialManifest.PROPERTY,manifest());
        for(String p:pairs)for(String slot:List.of("SECRET","PREVIOUS_SECRET","PREVIOUS_UNTIL")){String value=secrets.get(p+slot);if(value!=null)target.put(p+slot,value);}
    }
    static void refresh(){config(authConfig,List.of(AUTH,VERIFY));config(sellerConfig,List.of(SELLER,VERIFY));config(productConfig,List.of(AUTH,SELLER));config(workerConfig,List.of(AUTH,SELLER));}
    static String[] args(String module,String role){return new String[]{"--server.port=0","--spring.datasource.url="+url(module),"--spring.datasource.username="+role,"--spring.datasource.password="+password,"--spring.flyway.enabled=false","--spring.main.banner-mode=off"};}
    static ServletWebServerApplicationContext bootProduct(boolean enabled){
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.productservice.ProductServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rpc-process",productConfig)))
            .properties("hk5.product.receipts.enabled="+enabled).run(args("product","product_receipt_app"));
    }
    static ServletWebServerApplicationContext bootAuth(boolean enabled){
        authConfig.put("HK5_AUTH_PRODUCT_TERMINAL_URL",callback.base()+"/internal/product/write-receipts/auth");
        authConfig.put("HK5_AUTH_JWT_ISSUER","https://issuer.example.invalid");authConfig.put("HK5_AUTH_JWT_AUDIENCE","aud");
        authConfig.put("HK5_AUTH_JWT_CURRENT_KID","test");authConfig.put("HK5_AUTH_JWT_CURRENT_KEY_B64",Base64.getEncoder().encodeToString(new byte[32]));
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rpc-process",authConfig)))
            .properties("hk5.auth.reservations.enabled="+enabled).run(args("auth","auth_rpc_app"));
    }
    static ServletWebServerApplicationContext bootSeller(boolean enabled){
        sellerConfig.put("HK5_SELLER_PRODUCT_TERMINAL_URL",base(product)+"/internal/product/write-receipts/seller");
        sellerConfig.put("HK5_SELLER_AUTH_RESERVATION_URL",base(auth)+"/internal/auth/write-reservations/seller/");
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.sellerservice.SellerServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rpc-process",sellerConfig)))
            .properties("hk5.seller.reservations.enabled="+enabled).run(args("seller","seller_rpc_app"));
    }
    @BeforeEach void seed() throws Exception {
        defaults();
        authConfig.put("HK5_AUTH_PRODUCT_TERMINAL_URL",callback.base()+"/internal/product/write-receipts/auth");
        authConfig.put("HK5_AUTH_JWT_ISSUER","https://issuer.example.invalid");authConfig.put("HK5_AUTH_JWT_AUDIENCE","aud");authConfig.put("HK5_AUTH_JWT_CURRENT_KID","test");authConfig.put("HK5_AUTH_JWT_CURRENT_KEY_B64",Base64.getEncoder().encodeToString(new byte[32]));
        sellerConfig.put("HK5_SELLER_PRODUCT_TERMINAL_URL",base(product)+"/internal/product/write-receipts/seller");sellerConfig.put("HK5_SELLER_AUTH_RESERVATION_URL",base(auth)+"/internal/auth/write-reservations/seller/");
        workerConfig.put("HK5_PRODUCT_AUTH_RESERVATION_URL",base(auth)+"/internal/auth/write-reservations/product/");workerConfig.put("HK5_PRODUCT_SELLER_RESERVATION_URL",base(seller)+"/internal/seller/write-reservations/product/");
        callback.reset();admission.reset();
        authDb.execute("TRUNCATE authority_write_reservations");sellerDb.execute("TRUNCATE authority_write_reservations");productDb.execute("TRUNCATE authority_write_operations,rpc_write_probe CASCADE");
        authDb.execute("TRUNCATE accounts CASCADE");sellerDb.execute("TRUNCATE seller_profiles CASCADE");
        for(UUID id:List.of(owner,other)) {
            authDb.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",id,id+"@example.invalid","test-only-hash","ACTIVE");
            authDb.update("INSERT INTO account_role_grants VALUES(?,?)",id,id.equals(owner)?"SELLER":"CUSTOMER");
        }
        sellerDb.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,?,'APPROVED',2)",profile,owner.toString());
        sellerDb.update("INSERT INTO stores(id,seller_id,name,slug,status,version) VALUES(?,?,'Own','own','OPEN',4)",store,profile);
        String jti=UUID.randomUUID().toString();token=jwt(owner,jti,Set.of("SELLER"),false);
        intent=new WriteIntent(UUID.randomUUID(),owner,store,jti,WriteIntent.Mode.MANAGE,2,4,0,"a".repeat(64));
    }
    static String jwt(UUID id,String jti,Set<String> roles,boolean expired) throws Exception {
        var now=Instant.now().minusSeconds(expired?2000:0).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var signed=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test").type(JOSEObjectType.JWT).build(),new JWTClaimsSet.Builder().issuer("https://issuer.example.invalid").audience("aud").subject(id.toString()).jwtID(jti).issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).claim("roles",new ArrayList<>(roles)).build());
        signed.sign(new MACSigner(new byte[32]));return signed.serialize();
    }
    ReservationRpcClient authClient(){return new ReservationRpcClient(URI.create(base(auth)+"/internal/auth/write-reservations/product/"),()->secrets.get(AUTH+"SECRET"),Participant.AUTH,()->token);}
    ReservationRpcClient sellerClient(){return new ReservationRpcClient(URI.create(base(seller)+"/internal/seller/write-reservations/product/"),()->secrets.get(SELLER+"SECRET"),Participant.SELLER,()->null);}
    HttpResponse<String> post(String root,String path,String key,String body,String bearer) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(root+path)).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").header("X-HK5-Service-Credential",key).header("X-User-Id",other.toString()).header("X-Roles","ADMIN");
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    static void failStatus(int expected,Runnable run){assertEquals(expected,assertThrows(ReservationRpcClient.Failure.class,run::run).status());}
    static int count(JdbcTemplate db){return db.queryForObject("SELECT count(*) FROM authority_write_reservations",Integer.class);}
    void held(){var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->authMutation.update("DELETE FROM account_role_grants WHERE account_id=?",owner));assertEquals("55P03",assertInstanceOf(SQLException.class,failure.getMostSpecificCause()).getSQLState());}

    @Test void realThreeServiceCommitAndReceiptReleaseUseIndependentLeastPrivilegeLogins() throws Exception {
        var coordinator=productWorker.getBean(ProductReservationPorts.class).coordinatorFor(intent,token);
        var op=coordinator.admit(intent);var a=authClient().lookup(intent).grant().orElseThrow();var s=sellerClient().lookup(intent).grant().orElseThrow();held();
        failStatus(503,()->authClient().release(intent,a.epoch()));held();
        coordinator.commit(intent.requestId(),op.epoch(),c->{try(var q=c.prepareStatement("INSERT INTO rpc_write_probe VALUES(?)")){q.setObject(1,intent.requestId());q.executeUpdate();}});
        coordinator.reconcile(intent);authClient().release(intent,a.epoch());
        assertEquals("RELEASED",authClient().lookup(intent).state());assertEquals(1,productDb.queryForObject("SELECT count(*) FROM rpc_write_probe",Integer.class));
        authMutation.update("DELETE FROM account_role_grants WHERE account_id=?",owner);
        assertEquals("auth_rpc_app",new JdbcTemplate(authRuntime).queryForObject("SELECT current_user",String.class));
        assertEquals("seller_rpc_app",new JdbcTemplate(sellerRuntime).queryForObject("SELECT current_user",String.class));
        assertEquals("product_receipt_app",new JdbcTemplate(receiptReader).queryForObject("SELECT current_user",String.class));
        assertDenied(authRuntime,"UPDATE accounts SET status='DISABLED'");assertDenied(authRuntime,"DELETE FROM account_role_grants");
        assertDenied(authRuntime,"SELECT password_hash FROM accounts");assertDenied(sellerRuntime,"SELECT review_reason FROM seller_profiles");
        assertDenied(sellerRuntime,"UPDATE seller_profiles SET status='SUSPENDED'");assertDenied(receiptReader,"UPDATE authority_write_operations SET state='COMMITTED'");
        assertDenied(receiptReader,"SELECT * FROM categories");assertDenied(authRuntime,"CREATE TABLE rpc_forbidden(id int)");
        assertThrows(SQLException.class,()->source(url("seller"),"auth_rpc_app").getConnection());
    }
    static void assertDenied(DataSource source,String sql){var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->new JdbcTemplate(source).execute(sql));assertEquals("42501",assertInstanceOf(SQLException.class,failure.getMostSpecificCause()).getSQLState());}

    @Test void wrongPairLeakedCredentialForgedHeadersAndUntrustedEvidenceCannotGrantOrRelease() throws Exception {
        String request=ReservationWire.request("admit",UUID.randomUUID(),intent,null);
        assertEquals(401,post(base(auth),"/internal/auth/write-reservations/product/admit",secrets.get(SELLER+"SECRET"),request,token).statusCode());
        assertEquals(401,post(base(auth),"/internal/auth/write-reservations/product/admit",secrets.get(AUTH+"SECRET"),request,null).statusCode());
        assertEquals(403,post(base(seller),"/internal/seller/write-reservations/product/admit",secrets.get(SELLER+"SECRET"),request,null).statusCode());
        assertEquals(0,count(authDb));assertEquals(0,count(sellerDb));
        var op=ledger.begin(intent);var a=authClient().admit(intent);ledger.recordGrant(intent.requestId(),op.epoch(),a);
        String release=ReservationWire.request("release",UUID.randomUUID(),intent,a.epoch());
        assertEquals(400,post(base(auth),"/internal/auth/write-reservations/product/release",secrets.get(AUTH+"SECRET"),release.substring(0,release.length()-1)+",\"receipt\":{\"state\":\"COMMITTED\"}}",null).statusCode());
        failStatus(503,()->authClient().release(intent,a.epoch()));held();
        assertEquals(401,post(base(product),"/internal/product/write-receipts/auth",secrets.get(SELLER+"SECRET"),ReservationWire.request("receipt",UUID.randomUUID(),intent,a.epoch()),null).statusCode());
        assertEquals(403,post(base(auth),"/internal/auth/write-reservations/seller/admit",secrets.get(VERIFY+"SECRET"),request,token).statusCode());
    }

    @Test void strictJsonCanonicalBindingAndEpochConflictsFailBeforeMutation() throws Exception {
        String body=ReservationWire.request("admit",UUID.randomUUID(),intent,null);
        for(String invalid:List.of(body.replace("\"sellerVersion\":\"2\"","\"sellerVersion\":2"),body.replace("\"sellerVersion\":\"2\"","\"sellerVersion\":\"02\""),body+"{}",body.substring(0,body.length()-1)+",\"nonce\":\""+UUID.randomUUID()+"\"}"))
            assertEquals(400,post(base(auth),"/internal/auth/write-reservations/product/admit",secrets.get(AUTH+"SECRET"),invalid,token).statusCode());
        assertEquals(413,post(base(auth),"/internal/auth/write-reservations/product/admit",secrets.get(AUTH+"SECRET"),"x".repeat(16385),token).statusCode());
        assertEquals(0,count(authDb));var a=authClient().admit(intent);assertEquals(a,authClient().admit(intent));assertEquals(1,count(authDb));
        var conflict=new WriteIntent(intent.requestId(),owner,store,intent.tokenId(),intent.mode(),2,4,0,"b".repeat(64));failStatus(409,()->authClient().admit(conflict));
        failStatus(409,()->authClient().release(intent,a.epoch()+1));held();
    }

    @Test void receiptMediaTypeIsCheckedAfterIdentityAndBeforeDatabaseAccess() throws Exception {
        var op=ledger.begin(intent);var grant=authClient().admit(intent);ledger.recordGrant(intent.requestId(),op.epoch(),grant);ledger.abort(intent.requestId(),op.epoch());
        String body=ReservationWire.request("receipt",UUID.randomUUID(),intent,grant.epoch());
        var root=URI.create(base(product)+"/internal/product/write-receipts/auth");
        for(String type:List.of("text/plain","")) {
            var request=HttpRequest.newBuilder(root).header("X-HK5-Service-Credential",secrets.get(AUTH+"SECRET"));
            if(!type.isEmpty())request.header("Content-Type",type);
            assertEquals(415,http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        productDb.execute("REVOKE SELECT ON authority_write_operations FROM product_terminal_reader");
        try {
            var request=HttpRequest.newBuilder(root).header("X-HK5-Service-Credential",secrets.get(AUTH+"SECRET")).header("Content-Type","text/plain");
            assertEquals(415,http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {productDb.execute("GRANT SELECT ON authority_write_operations TO product_terminal_reader");}
        var valid=HttpRequest.newBuilder(root).header("X-HK5-Service-Credential",secrets.get(AUTH+"SECRET")).header("Content-Type","application/json; charset=UTF-8");
        assertEquals(200,http.send(valid.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var foreign=HttpRequest.newBuilder(root).header("X-HK5-Service-Credential",secrets.get(SELLER+"SECRET")).header("Content-Type","text/plain");
        assertEquals(401,http.send(foreign.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void revokedDisabledExpiredAndWrongOwnershipDenyCurrentAdmission() throws Exception {
        token=jwt(owner,intent.tokenId(),Set.of("SELLER"),true);failStatus(401,()->authClient().admit(intent));
        token=jwt(owner,intent.tokenId(),Set.of("SELLER"),false);authMutation.update("DELETE FROM account_role_grants WHERE account_id=?",owner);failStatus(403,()->authClient().admit(intent));
        authDb.update("INSERT INTO account_role_grants VALUES(?,'SELLER')",owner);authDb.update("UPDATE accounts SET status='DISABLED' WHERE id=?",owner);failStatus(403,()->authClient().admit(intent));
        authDb.update("UPDATE accounts SET status='ACTIVE' WHERE id=?",owner);authClient().admit(intent);
        sellerDb.update("UPDATE seller_profiles SET owner_reference=?,version=3 WHERE id=?",other.toString(),profile);failStatus(403,()->sellerClient().admit(intent));assertEquals(0,count(sellerDb));
    }

    @Test void actualHttpAdmissionWaitsForRevocationCommitThenDenies() throws Exception {
        try(var connection=source(url("auth"),"auth_app").getConnection();var pool=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try(var update=connection.prepareStatement("DELETE FROM account_role_grants WHERE account_id=?")){update.setObject(1,owner);assertEquals(1,update.executeUpdate());}
            var request=pool.submit(()->authClient().admit(intent));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);boolean waiting=false;
                while(System.nanoTime()<deadline && !waiting) {
                    waiting=authDb.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory')",Boolean.class);
                    if(!waiting)Thread.sleep(10);
                }
                assertTrue(waiting,"HTTP admission must wait for the authority mutation transaction");connection.commit();
                var failure=assertThrows(ExecutionException.class,()->request.get(5,TimeUnit.SECONDS));
                assertEquals(403,assertInstanceOf(ReservationRpcClient.Failure.class,failure.getCause()).status());assertEquals(0,count(authDb));
            } finally {connection.rollback();}
        }
    }

    @Test void missingLowerProductMigrationsAreADeploymentGateNotSilentlyApplied(@org.junit.jupiter.api.io.TempDir Path folder) throws Exception {
        assertEquals("1,6",productDb.queryForObject("SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL",String.class));
        Files.writeString(folder.resolve("V2__unreviewed_lower_version.sql"),"SELECT 1;");
        var migration=Flyway.configure().dataSource(url("product"),"product_migrator",password)
            .locations("filesystem:src/main/resources/db/migration","filesystem:"+folder).outOfOrder(false).load();
        assertFalse(migration.validateWithResult().validationSuccessful);
        assertThrows(org.flywaydb.core.api.FlywayException.class,migration::migrate);
        assertEquals("1,6",productDb.queryForObject("SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL",String.class));
    }

    @Test void rotationAndCrossProcessAliasStayIsolated() throws Exception {
        String old=secrets.get(AUTH+"SECRET");secrets.put(AUTH+"SECRET","new-test-only-product-auth-secret-0123456789");secrets.put(AUTH+"PREVIOUS_SECRET",old);secrets.put(AUTH+"PREVIOUS_UNTIL",Instant.now().plusSeconds(60).toString());refresh();restoreEndpoints();
        var previous=new ReservationRpcClient(URI.create(base(auth)+"/internal/auth/write-reservations/product/"),()->old,Participant.AUTH,()->token);
        var grant=previous.admit(intent);assertEquals(grant,authClient().admit(intent));
        secrets.put(AUTH+"PREVIOUS_UNTIL",Instant.now().minusSeconds(1).toString());refresh();restoreEndpoints();failStatus(401,()->previous.lookup(intent));
        authConfig.put(AUTH+"SECRET",secrets.get(SELLER+"SECRET"));
        var leaked=new ReservationRpcClient(URI.create(base(auth)+"/internal/auth/write-reservations/product/"),()->secrets.get(SELLER+"SECRET"),Participant.AUTH,()->token);failStatus(503,()->leaked.lookup(intent));
        assertNull(auth.getEnvironment().getProperty(SELLER+"SECRET"));assertNull(seller.getEnvironment().getProperty(AUTH+"SECRET"));held();
    }
    static void restoreEndpoints(){
        authConfig.put("HK5_AUTH_PRODUCT_TERMINAL_URL",callback.base()+"/internal/product/write-receipts/auth");authConfig.put("HK5_AUTH_JWT_ISSUER","https://issuer.example.invalid");authConfig.put("HK5_AUTH_JWT_AUDIENCE","aud");authConfig.put("HK5_AUTH_JWT_CURRENT_KID","test");authConfig.put("HK5_AUTH_JWT_CURRENT_KEY_B64",Base64.getEncoder().encodeToString(new byte[32]));
        sellerConfig.put("HK5_SELLER_PRODUCT_TERMINAL_URL",base(product)+"/internal/product/write-receipts/seller");sellerConfig.put("HK5_SELLER_AUTH_RESERVATION_URL",base(auth)+"/internal/auth/write-reservations/seller/");
    }

    @Test void callbackReplayAndTimeoutCannotSubstituteForVerifiedTerminalReceipt() throws Exception {
        var op=ledger.begin(intent);var a=authClient().admit(intent);ledger.recordGrant(intent.requestId(),op.epoch(),a);ledger.abort(intent.requestId(),op.epoch());
        new TerminalReceiptClient(URI.create(callback.base()+"/internal/product/write-receipts/auth"),()->secrets.get(AUTH+"SECRET"),Participant.AUTH).read(intent,a.epoch());
        callback.mode.set(FaultProxy.Mode.REPLAY);failStatus(503,()->authClient().release(intent,a.epoch()));held();
        callback.mode.set(FaultProxy.Mode.HOLD);var pool=Executors.newSingleThreadExecutor();
        try {
            var call=pool.submit(()->{authClient().release(intent,a.epoch());return true;});assertTrue(callback.captured.await(10,TimeUnit.SECONDS));
            assertEquals(0,authDb.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction'",Integer.class));
            var failure=assertThrows(ExecutionException.class,()->call.get(8,TimeUnit.SECONDS));assertEquals(503,assertInstanceOf(ReservationRpcClient.Failure.class,failure.getCause()).status());held();
        } finally {callback.proceed.countDown();pool.shutdownNow();}
        callback.mode.set(FaultProxy.Mode.FORWARD);authClient().release(intent,a.epoch());
        assertEquals("RELEASED",authClient().lookup(intent).state());
    }

    @Test void lostAdmissionAckAndRestartRecoverOnlyThroughDurableCancelledReceipt() throws Exception {
        var op=ledger.begin(intent);admission.mode.set(FaultProxy.Mode.HOLD);
        var client=new ReservationRpcClient(URI.create(admission.base()+"/internal/auth/write-reservations/product/"),()->secrets.get(AUTH+"SECRET"),Participant.AUTH,()->token);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var call=pool.submit(()->client.admit(intent));assertTrue(admission.captured.await(10,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->call.get(8,TimeUnit.SECONDS));held();
        } finally {admission.proceed.countDown();pool.shutdownNow();}
        seller.close();auth.close();auth=bootAuth(true);seller=bootSeller(true);
        var recovered=authClient().lookup(intent).grant().orElseThrow();assertEquals("ACTIVE",authClient().lookup(intent).state());
        ledger.abort(intent.requestId(),op.epoch());ledger.recordGrant(intent.requestId(),op.epoch(),recovered);
        authClient().release(intent,recovered.epoch());assertEquals("RELEASED",authClient().lookup(intent).state());
        assertThrows(IllegalStateException.class,()->ledger.commit(intent.requestId(),op.epoch(),c->{throw new AssertionError();}));
    }

    @Test void defaultOffDoesNotExposeReservationOrReceiptRoutes() throws Exception {
        try(var off=bootProduct(false)) {
            assertEquals(403,post(base(off),"/internal/product/write-receipts/auth",secrets.get(AUTH+"SECRET"),"{}",null).statusCode());
        }
        try(var off=bootAuth(false)) {
            assertEquals(403,post(base(off),"/internal/auth/write-reservations/product/admit",secrets.get(AUTH+"SECRET"),"{}",token).statusCode());
        }
        try(var off=bootSeller(false)) {
            assertEquals(403,post(base(off),"/internal/seller/write-reservations/product/admit",secrets.get(SELLER+"SECRET"),"{}",null).statusCode());
        }
        assertEquals(403,post(base(product),"/api/products",secrets.get(AUTH+"SECRET"),"{}",token).statusCode());
    }

    static final class FaultProxy implements AutoCloseable {
        enum Mode { FORWARD,HOLD,REPLAY }
        final AtomicReference<Mode> mode=new AtomicReference<>(Mode.FORWARD);
        volatile CountDownLatch captured=new CountDownLatch(1),proceed=new CountDownLatch(1);
        final AtomicReference<HttpResponse<byte[]>> previous=new AtomicReference<>();
        final com.sun.net.httpserver.HttpServer server;
        final ExecutorService workers=Executors.newCachedThreadPool();
        FaultProxy(String target) throws Exception {
            server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);
            server.createContext("/",exchange->{
                try {
                    Mode selected=mode.get();HttpResponse<byte[]> response;
                    if(selected==Mode.REPLAY)response=previous.get();
                    else {
                        var request=HttpRequest.newBuilder(URI.create(target+exchange.getRequestURI().getPath())).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json");
                        for(String h:List.of("X-HK5-Service-Credential","Authorization")){String v=exchange.getRequestHeaders().getFirst(h);if(v!=null)request.header(h,v);}
                        response=http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());previous.set(response);
                    }
                    if(selected==Mode.HOLD && mode.compareAndSet(Mode.HOLD,Mode.FORWARD)){captured.countDown();proceed.await(10,TimeUnit.SECONDS);}
                    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Cache-Control","no-store");
                    exchange.sendResponseHeaders(response.statusCode(),response.body().length);exchange.getResponseBody().write(response.body());
                }catch(Exception failed){try{exchange.sendResponseHeaders(503,-1);}catch(Exception ignored){}}
                finally{exchange.close();}
            });server.start();
        }
        String base(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        void reset(){proceed.countDown();captured=new CountDownLatch(1);proceed=new CountDownLatch(1);previous.set(null);mode.set(Mode.FORWARD);}
        public void close(){proceed.countDown();server.stop(0);workers.shutdownNow();}
    }
    @AfterAll static void stop(){if(productWorker!=null)productWorker.close();if(seller!=null)seller.close();if(auth!=null)auth.close();if(product!=null)product.close();if(callback!=null)callback.close();if(admission!=null)admission.close();pg.stop();}
}
