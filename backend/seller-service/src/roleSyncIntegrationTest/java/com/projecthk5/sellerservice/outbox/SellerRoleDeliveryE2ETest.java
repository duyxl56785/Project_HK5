package com.projecthk5.sellerservice.outbox;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;

class SellerRoleDeliveryE2ETest {
    static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static String password=UUID.randomUUID().toString(),key="test-only-seller-auth-role-sync-key-0123456789";
    static ServletWebServerApplicationContext auth,seller;static JdbcTemplate authDb,sellerDb;
    static DriverManagerDataSource sellerDs;static DeliveryStore store;static RoleSyncClient client;static SellerRoleDispatcher dispatcher;
    static UUID owner=UUID.randomUUID();static SellerIdentity actor=new SellerIdentity(owner.toString()),admin=new SellerIdentity(UUID.randomUUID().toString());
    static SellerPersistenceService service;
    @TestConfiguration @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="test.role-sync.producer-fixture",havingValue="true") static class Wiring {
        @Bean SellerPersistenceService service(SellerProfileRepository repository){return new SellerPersistenceService(repository,new SellerAccessPolicy(){
            public void requireAuthenticated(SellerIdentity who){if(who==null)throw new IllegalArgumentException();}
            public void requireSellerModerationPermission(SellerIdentity who){if(!admin.equals(who))throw new IllegalArgumentException();}
        });}
    }
    @BeforeAll static void start(){
        pg.start();var root=jdbc(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());
        root.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '"+password+"'");root.execute("CREATE ROLE auth_app LOGIN PASSWORD '"+password+"'");
        root.execute("CREATE DATABASE auth_db OWNER auth_migrator");root.execute("CREATE DATABASE seller_db");
        String au=pg.getJdbcUrl().replace("/test","/auth_db"),su=pg.getJdbcUrl().replace("/test","/seller_db");
        migrate(au,"auth_migrator",password,"auth-service");migrate(su,pg.getUsername(),pg.getPassword(),"seller-service");
        authDb=jdbc(au,pg.getUsername(),pg.getPassword());sellerDs=new DriverManagerDataSource(su,pg.getUsername(),pg.getPassword());sellerDb=new JdbcTemplate(sellerDs);
        authDb.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        auth=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class)
            .initializers(c -> com.projecthk5.security.PairManifestFixture.install(c.getEnvironment())).run("--server.port=0","--spring.datasource.url="+au,"--spring.datasource.username=auth_app","--spring.datasource.password="+password,
                "--spring.flyway.enabled=false","--hk5.auth.role-sync.enabled=true","--hk5.auth.role-sync.revocation-recovery.enabled=true","--HK5_SELLER_AUTH_ROLE_SYNC_SECRET="+key);
        seller=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.sellerservice.SellerServiceApplication.class,Wiring.class)
            .initializers(c -> com.projecthk5.security.PairManifestFixture.install(c.getEnvironment())).run("--server.port=0","--spring.datasource.url="+su,"--spring.datasource.username="+pg.getUsername(),"--spring.datasource.password="+pg.getPassword(),"--spring.flyway.enabled=false","--test.role-sync.producer-fixture=true");
        service=seller.getBean(SellerPersistenceService.class);store=new DeliveryStore(sellerDs);
        client=new RoleSyncClient(URI.create("http://127.0.0.1:"+auth.getWebServer().getPort()+"/internal/auth/seller-role-sync"),()->key);
        dispatcher=new SellerRoleDispatcher(store,client);
    }
    static JdbcTemplate jdbc(String url,String user,String pass){return new JdbcTemplate(new DriverManagerDataSource(url,user,pass));}
    static void migrate(String url,String user,String pass,String module){Flyway.configure().dataSource(url,user,pass).locations("filesystem:"+java.nio.file.Path.of("..").resolve(module).resolve("src/main/resources/db/migration").toAbsolutePath().normalize()).load().migrate();}
    @BeforeEach void reset(){
        sellerDb.execute("TRUNCATE seller_role_delivery,seller_status_outbox,seller_profiles CASCADE");
        authDb.execute("TRUNCATE seller_role_sync_receipts,seller_role_sync_versions,seller_role_sync_state,accounts CASCADE");
        authDb.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",owner,owner+"@example.invalid","test-hash","ACTIVE");
        authDb.update("INSERT INTO account_role_grants VALUES(?,'CUSTOMER'),(?,'ADMIN')",owner,owner);
    }
    @AfterAll static void close(){if(seller!=null)seller.close();if(auth!=null)auth.close();pg.stop();}
    boolean granted(){return authDb.queryForObject("SELECT count(*) FROM account_role_grants WHERE account_id=? AND role='SELLER'",Integer.class,owner)==1;}
    void drain(){for(int i=0;i<10;i++)if(!dispatcher.runOnce())break;}
    void expire(){sellerDb.update("UPDATE seller_role_delivery SET lease_until=clock_timestamp()-interval '1 second' WHERE state='LEASED'");}
    void due(){sellerDb.update("UPDATE seller_role_delivery SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE state='PENDING'");}
    @Test void committedApproveAndSuspendGrantAndRevokeOnlySeller(){
        var first=service.register(actor);drain();assertFalse(granted());
        var approved=service.approve(first.id(),first.version(),admin);drain();assertTrue(granted());
        service.suspend(approved.id(),approved.version(),admin,"test");drain();assertFalse(granted());
        assertEquals(2,authDb.queryForObject("SELECT count(*) FROM account_role_grants",Integer.class));
        assertEquals(3,sellerDb.queryForObject("SELECT count(*) FROM seller_role_delivery WHERE state='DELIVERED'",Integer.class));
    }
    @Test void rollbackAndUncommittedEventsNeverSend(){
        var tx=new TransactionTemplate(seller.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            service.register(actor);assertThrows(IllegalStateException.class,dispatcher::runOnce);throw new IllegalStateException("rollback");
        }));
        assertEquals(0,sellerDb.queryForObject("SELECT count(*) FROM seller_status_outbox",Integer.class));assertFalse(dispatcher.runOnce());assertFalse(granted());
    }
    @Test void crashAfterAuthCommitRecoversWithDuplicateAck(){
        var first=service.register(actor);drain();service.approve(first.id(),first.version(),admin);
        var crashed=new SellerRoleDispatcher(store,event->{client.send(event);throw new AssertionError("process crash before local ACK");});
        assertThrows(AssertionError.class,crashed::runOnce);assertTrue(granted());
        expire();assertTrue(dispatcher.runOnce());
        assertEquals("DUPLICATE",sellerDb.queryForObject("SELECT ack_result FROM seller_role_delivery WHERE attempts=2",String.class));
    }
    @Test void boundedRetryForWrongCredentialThenRotationRecovery(){
        service.register(actor);var wrong=new RoleSyncClient(client.endpoint(),()->"wrong-role-sync-pair-credential-0123456789");
        var broken=new SellerRoleDispatcher(store,wrong);assertTrue(broken.runOnce());assertFalse(granted());
        assertEquals("PENDING",sellerDb.queryForObject("SELECT state FROM seller_role_delivery",String.class));
        due();assertTrue(dispatcher.runOnce());assertEquals("DELIVERED",sellerDb.queryForObject("SELECT state FROM seller_role_delivery",String.class));
    }
    @Test void leaseFencesLateWorkersAndConcurrentDispatchIsSafe() throws Exception {
        service.register(actor);var old=store.claim().orElseThrow();expire();var fresh=store.claim().orElseThrow();
        assertFalse(store.delivered(old,"APPLIED"));assertTrue(store.delivered(fresh,client.send(fresh).name()));
        var first=service.findOwn(actor);service.approve(first.id(),first.version(),admin);
        var pool=Executors.newFixedThreadPool(4);try{var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<4;i++)tasks.add(dispatcher::runOnce);for(var future:pool.invokeAll(tasks))future.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertTrue(granted());assertEquals(2,authDb.queryForObject("SELECT count(*) FROM seller_role_sync_receipts",Integer.class));
    }
    @Test void reconciliationCreatesMissingStateAndExhaustionIsBounded(){
        service.register(actor);assertEquals(1,store.reconcile().pending());
        var broken=new SellerRoleDispatcher(store,event->{throw new IllegalStateException("Auth unavailable");});
        for(int i=0;i<8;i++){due();assertTrue(broken.runOnce());}
        assertFalse(broken.runOnce());assertEquals(1,store.reconcile().exhausted());assertFalse(granted());
    }
    @Test void queuedSuspensionIsDeliveredBeforeHistoricalApprovals(){
        var first=service.register(actor);var approved=service.approve(first.id(),first.version(),admin);service.suspend(approved.id(),approved.version(),admin,"test");
        assertTrue(dispatcher.runOnce());
        assertEquals(2L,authDb.queryForObject("SELECT latest_version FROM seller_role_sync_state",Long.class));
        assertFalse(granted());drain();assertFalse(granted());
    }
    @Test void deliveryConstraintsRejectPartialLeasesAndSnapshotsCannotChange(){
        service.register(actor);store.reconcile();
        assertThrows(org.springframework.dao.DataAccessException.class,()->sellerDb.update("UPDATE seller_role_delivery SET claim_token=?",UUID.randomUUID()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->sellerDb.update("UPDATE seller_status_outbox SET seller_status='APPROVED'"));
    }    @Test void conflictIsTerminalAndEventReplayCannotOverwriteRevoke(){
        var first=service.register(actor);drain();service.approve(first.id(),first.version(),admin);
        var claimed=store.claim().orElseThrow();
        var conflicting=new DeliveryStore.Claim(claimed.eventId(),claimed.sellerId(),claimed.accountId(),"SUSPENDED",claimed.version(),claimed.attempt(),claimed.token());
        client.send(conflicting);assertFalse(granted());expire();
        assertTrue(dispatcher.runOnce());assertEquals(1,store.reconcile().blocked());assertFalse(dispatcher.runOnce());assertFalse(granted());
    }
    @Test void outOfOrderAndDuplicateAcksNeverRegrantOlderApproval(){
        var first=service.register(actor);var approved=service.approve(first.id(),first.version(),admin);service.suspend(approved.id(),approved.version(),admin,"test");
        var latest=store.claim().orElseThrow();
        assertEquals(SellerRoleDispatcher.Ack.APPLIED,client.send(latest));store.delivered(latest,"APPLIED");
        var claims=new ArrayList<DeliveryStore.Claim>();claims.add(latest);for(int i=0;i<2;i++)claims.add(store.claim().orElseThrow());
        claims.sort(Comparator.comparingLong(DeliveryStore.Claim::version).reversed());
        assertEquals(SellerRoleDispatcher.Ack.DUPLICATE,client.send(claims.getFirst()));
        assertEquals(SellerRoleDispatcher.Ack.DUPLICATE,client.send(claims.getFirst()));
        assertEquals(SellerRoleDispatcher.Ack.STALE_IGNORED,client.send(claims.get(1)));assertFalse(granted());
    }
    @Test void timeoutAfterRemoteCommitIsRecoveredBySameEventRetry() throws Exception {
        var first=service.register(actor);drain();service.approve(first.id(),first.version(),admin);
        try(var connection=authDb.getDataSource().getConnection()){
            connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.executeQuery("SELECT id FROM seller_role_sync_lock WHERE id=1 FOR UPDATE");}
            assertTrue(dispatcher.runOnce());assertEquals("PENDING",sellerDb.queryForObject("SELECT state FROM seller_role_delivery WHERE attempts=1 AND state<>'DELIVERED'",String.class));
            connection.commit();
        }
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!granted() && System.nanoTime()<deadline)Thread.sleep(25);
        assertTrue(granted());due();assertTrue(dispatcher.runOnce());
        assertEquals("DUPLICATE",sellerDb.queryForObject("SELECT ack_result FROM seller_role_delivery WHERE attempts=2",String.class));
    }
    @Test void sourceGapIsReportedRatherThanInventingAnEvent(){
        var first=service.register(actor);
        sellerDb.update("UPDATE seller_profiles SET status='APPROVED',version=version+1 WHERE id=?",first.id());
        assertEquals(1,store.reconcile().missingCurrentSnapshots());assertFalse(dispatcher.runOnce());assertFalse(granted());
    }    @Test void malformedOrMismatchedAckIsNeverDelivered() throws Exception {
        service.register(actor);var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/auth/seller-role-sync",exchange->{
            exchange.getRequestBody().readAllBytes();byte[] bytes="{\"eventId\":\"wrong\",\"sellerId\":\"wrong\",\"sourceVersion\":\"0\",\"result\":\"APPLIED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.getResponseHeaders().add("Cache-Control","no-store");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var invalid=new SellerRoleDispatcher(store,new RoleSyncClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/internal/auth/seller-role-sync"),()->key));
            assertTrue(invalid.runOnce());assertEquals("PENDING",sellerDb.queryForObject("SELECT state FROM seller_role_delivery",String.class));
            assertEquals(0,authDb.queryForObject("SELECT count(*) FROM seller_role_sync_receipts",Integer.class));
            due();assertTrue(dispatcher.runOnce());assertEquals(1,store.reconcile().delivered());
        }finally{server.stop(0);}
    }
    @Test void credentialRotationUsesCurrentSupplierAndPreviousDeadline(){
        var outbound=new java.util.concurrent.atomic.AtomicReference<>(key);
        var rotating=new SellerRoleDispatcher(store,new RoleSyncClient(client.endpoint(),outbound::get));
        String next="rotated-test-only-seller-auth-role-sync-key-0123456789";
        var values=new HashMap<String,Object>();values.put("HK5_SELLER_AUTH_ROLE_SYNC_SECRET",next);values.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_SECRET",key);
        values.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL",java.time.Instant.now().plusSeconds(60).toString());
        auth.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("sender-rotation",values));
        try {
            var first=service.register(actor);assertTrue(rotating.runOnce());service.approve(first.id(),first.version(),admin);
            values.put("HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL",java.time.Instant.now().minusSeconds(1).toString());
            assertTrue(rotating.runOnce());assertFalse(granted());
            outbound.set(next);due();assertTrue(rotating.runOnce());assertTrue(granted());
        }finally{auth.getEnvironment().getPropertySources().remove("sender-rotation");}
    }
    @Test void separatePollCannotSeeUncommittedSnapshotAndWorkerIsDefaultOff() throws Exception {
        assertFalse(seller.containsBean("sellerRoleWorker"));
        var tx=new TransactionTemplate(seller.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var worker=Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status->{
                service.register(actor);
                try{assertFalse(worker.submit(dispatcher::runOnce).get(5,TimeUnit.SECONDS));}catch(Exception failure){throw new IllegalStateException(failure);}
                status.setRollbackOnly();
            });
            assertEquals(0,sellerDb.queryForObject("SELECT count(*) FROM seller_status_outbox",Integer.class));assertFalse(granted());
        }finally{worker.shutdownNow();}
    }    @Test void latestBackoffOrLeaseBlocksHistoricalGrant(){
        var first=service.register(actor);var approved=service.approve(first.id(),first.version(),admin);service.suspend(approved.id(),approved.version(),admin,"test");
        var failed=new SellerRoleDispatcher(store,event->{throw new IllegalStateException("Auth offline");});
        assertTrue(failed.runOnce());assertFalse(dispatcher.runOnce());assertFalse(granted());
        due();var held=store.claim().orElseThrow();assertEquals(2,held.version());assertTrue(store.claim().isEmpty());
        store.delivered(held,client.send(held).name());drain();assertFalse(granted());
    }
    @Test void discoveryBeyondHundredRowsStillSendsLatestBeforeHistory(){
        var first=service.register(actor);
        for(int version=1;version<=102;version++)sellerDb.update("INSERT INTO seller_status_outbox(event_id,seller_id,owner_reference,seller_status,source_version) VALUES(?,?,?,?,?)",
            UUID.randomUUID(),first.id(),owner.toString(),version%2==0?"SUSPENDED":"APPROVED",version);
        sellerDb.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test',version=102 WHERE id=?",first.id());
        assertTrue(dispatcher.runOnce());assertEquals(102L,authDb.queryForObject("SELECT latest_version FROM seller_role_sync_state",Long.class));assertFalse(granted());
    }
}
