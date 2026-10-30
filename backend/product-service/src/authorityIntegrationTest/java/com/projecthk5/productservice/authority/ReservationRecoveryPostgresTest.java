package com.projecthk5.productservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import com.projecthk5.authservice.rolesync.SellerRoleRevocationRecovery;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReservationRecoveryPostgresTest {
    static DataSource recoverySource,operatorSource;
    static ServletWebServerApplicationContext revoker;
    ReservationRpcPostgresTest fixture;
    ProductReservationRecoveryStore producerStore,recoveryStore;
    ProductReservationRecoveryWorker worker;
    @BeforeAll static void start() throws Exception {
        ReservationRpcPostgresTest.start(true);
        var db=ReservationRpcPostgresTest.productDb;
        for(String group:List.of("product_recovery_worker","product_recovery_operator"))db.execute("CREATE ROLE "+group+" NOLOGIN");
        for(String role:List.of("recovery_app","operator_app")) {
            db.execute("CREATE ROLE "+role+" LOGIN PASSWORD '"+ReservationRpcPostgresTest.password+"'");db.execute("GRANT CONNECT ON DATABASE product_db TO "+role);
        }
        db.execute("GRANT product_recovery_worker TO recovery_app");db.execute("GRANT product_recovery_operator TO operator_app");
        db.execute(Files.readString(Path.of("../../docs/tv1/sql/reservation-recovery-product-grants.sql")));
        recoverySource=boundedSource("recovery_app");operatorSource=boundedSource("operator_app");
        String prefix="HK5_SELLER_AUTH_ROLE_SYNC_";
        var values=new HashMap<String,Object>();values.put(prefix+"SECRET",ReservationRpcPostgresTest.secrets.get(prefix+"SECRET"));values.put(PairCredentialManifest.PROPERTY,ReservationRpcPostgresTest.manifest());
        revoker=(ServletWebServerApplicationContext)new SpringApplicationBuilder(com.projecthk5.authservice.AuthServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("revoker",values)))
            .properties("hk5.auth.role-sync.enabled=true","hk5.auth.role-sync.revocation-recovery.enabled=true")
            .run(ReservationRpcPostgresTest.args("auth","auth_app"));
    }
    static DataSource boundedSource(String role) {
        var source=new DriverManagerDataSource(ReservationRpcPostgresTest.url("product"),role,ReservationRpcPostgresTest.password);
        var properties=new Properties();properties.setProperty("options","-c lock_timeout=200ms -c statement_timeout=5000ms");source.setConnectionProperties(properties);return source;
    }
    @BeforeEach void seed() throws Exception {
        fixture=new ReservationRpcPostgresTest();fixture.seed();
        ReservationRpcPostgresTest.authDb.execute("TRUNCATE account_mutation_receipts,authority_pending_revocations");ReservationRpcPostgresTest.sellerDb.execute("TRUNCATE seller_mutation_receipts,authority_pending_revocations");
        ReservationRpcPostgresTest.authConfig.put("hk5.auth.reservations.recovery.enabled",true);
        ReservationRpcPostgresTest.sellerConfig.put("hk5.seller.reservations.recovery.enabled",true);
        producerStore=new ProductReservationRecoveryStore(ReservationRpcPostgresTest.worker,Duration.ZERO);
        recoveryStore=new ProductReservationRecoveryStore(recoverySource,Duration.ZERO);
        worker=new ProductReservationRecoveryWorker(recoveryStore,new DurableWriteLedger(recoverySource),fixture.authClient(),fixture.sellerClient(),Duration.ofSeconds(20),8);
    }
    @AfterAll static void stop(){if(revoker!=null)revoker.close();ReservationRpcPostgresTest.stop();}
    DurableWriteLedger.Operation begin() throws Exception {return producerStore.begin(fixture.intent);}
    DurableWriteLedger.Operation admitted() throws Exception {
        var op=begin();var a=fixture.authClient().admit(fixture.intent);ReservationRpcPostgresTest.ledger.recordGrant(fixture.intent.requestId(),op.epoch(),a);
        var s=fixture.sellerClient().admit(fixture.intent);ReservationRpcPostgresTest.ledger.recordGrant(fixture.intent.requestId(),op.epoch(),s);return op;
    }
    String state() throws Exception {return recoveryStore.inspect(fixture.intent.requestId()).orElseThrow().state();}
    void immediate() {ReservationRpcPostgresTest.productDb.update("UPDATE product_reservation_recovery SET due_at=clock_timestamp() WHERE request_id=?",fixture.intent.requestId());}
    void assertClosed() throws Exception {
        assertEquals("COMPLETE",state());
        for(var db:List.of(ReservationRpcPostgresTest.authDb,ReservationRpcPostgresTest.sellerDb))
            assertEquals(1,db.queryForObject("SELECT count(*) FROM authority_reservation_closures WHERE request_id=?",Integer.class,fixture.intent.requestId()));
        ReservationRpcPostgresTest.failStatus(409,()->fixture.authClient().admit(fixture.intent));
    }
    @Test void abandonedBeforeAdmissionSealsAbsenceAndRejectsFutureLateAdmission() throws Exception {
        var op=begin();assertTrue(worker.tick());assertClosed();assertFalse(worker.tick());
        assertEquals(DurableWriteLedger.State.ABORTED,ReservationRpcPostgresTest.ledger.terminal(fixture.intent.requestId()).state());
        assertThrows(IllegalStateException.class,()->ReservationRpcPostgresTest.ledger.commit(fixture.intent.requestId(),op.epoch(),c->{throw new AssertionError();}));
    }
    @Test void enlistPermissionFailureRollsBackLedgerAndAtomicRetryIsIdempotent() throws Exception {
        var db=ReservationRpcPostgresTest.productDb;
        db.execute("REVOKE INSERT ON product_reservation_recovery FROM product_reservation_writer");
        try {
            assertEquals("42501",assertThrows(SQLException.class,()->producerStore.begin(fixture.intent)).getSQLState());
            assertEquals(0,db.queryForObject("SELECT count(*) FROM authority_write_operations WHERE request_id=?",Integer.class,fixture.intent.requestId()));
            assertTrue(recoveryStore.inspect(fixture.intent.requestId()).isEmpty());
        } finally {db.execute("GRANT INSERT(request_id,binding_hash,operation_epoch,intent,due_at) ON product_reservation_recovery TO product_reservation_writer");}
        var first=producerStore.begin(fixture.intent);var retry=producerStore.begin(fixture.intent);
        assertEquals(first,retry);
        assertEquals(1,db.queryForObject("SELECT count(*) FROM product_reservation_recovery WHERE request_id=?",Integer.class,fixture.intent.requestId()));
        assertTrue(worker.tick());assertClosed();
    }
    @Test void operatorCanPagePastUnknownLegacyOrphansWithoutMutation() throws Exception {
        var i=fixture.intent;
        for(int n=0;n<101;n++)ReservationRpcPostgresTest.ledger.begin(new WriteIntent(UUID.randomUUID(),i.accountId(),i.storeId(),i.tokenId(),i.mode(),i.sellerVersion(),i.storeVersion(),i.productVersion(),i.commandHash()));
        var operator=new ProductReservationRecoveryStore(operatorSource,Duration.ZERO);
        var first=operator.orphans();assertEquals(100,first.size());
        long cursor=first.getLast().operationEpoch();var next=operator.orphans(cursor);
        assertEquals(1,next.size());assertTrue(next.getFirst().operationEpoch()>cursor);
        assertTrue(ProductReservationRecoveryOperator.execute(new String[]{"orphans",Long.toString(cursor)},operatorSource,true).contains(next.getFirst().requestId().toString()));
        assertEquals(101,ReservationRpcPostgresTest.productDb.queryForObject("SELECT count(*) FROM authority_write_operations WHERE state='PENDING'",Integer.class));
        assertEquals(0,ReservationRpcPostgresTest.productDb.queryForObject("SELECT count(*) FROM product_reservation_recovery",Integer.class));
    }
    @Test void lostHttpAdmissionAckIsRecoveredWithoutRecordingUntrustedReceipt() throws Exception {
        var op=begin();ReservationRpcPostgresTest.admission.mode.set(ReservationRpcPostgresTest.FaultProxy.Mode.HOLD);
        var client=new ReservationRpcClient(URI.create(ReservationRpcPostgresTest.admission.base()+"/internal/auth/write-reservations/product/"),()->ReservationRpcPostgresTest.secrets.get(ReservationRpcPostgresTest.AUTH+"SECRET"),Participant.AUTH,()->fixture.token);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var request=pool.submit(()->client.admit(fixture.intent));
            try {assertTrue(ReservationRpcPostgresTest.admission.captured.await(10,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->request.get(8,TimeUnit.SECONDS));fixture.held();assertTrue(worker.tick());assertClosed();}
            finally {ReservationRpcPostgresTest.admission.proceed.countDown();}
        }
        var late=fixture.authClient().lookup(fixture.intent).grant().orElseThrow();ReservationRpcPostgresTest.ledger.recordGrant(fixture.intent.requestId(),op.epoch(),late);
        assertEquals(DurableWriteLedger.State.ABORTED,ReservationRpcPostgresTest.ledger.terminal(fixture.intent.requestId()).state());
        assertThrows(IllegalStateException.class,()->ReservationRpcPostgresTest.ledger.commit(fixture.intent.requestId(),op.epoch(),c->{throw new AssertionError();}));
    }
    @Test void restartAfterCommitKeepsWriteAndRepeatedRecoveryCannotApplyItTwice() throws Exception {
        var op=admitted();ReservationRpcPostgresTest.ledger.commit(fixture.intent.requestId(),op.epoch(),c->{try(var q=c.prepareStatement("INSERT INTO rpc_write_probe VALUES(?)")){q.setObject(1,fixture.intent.requestId());q.executeUpdate();}});
        var restarted=new ProductReservationRecoveryWorker(new ProductReservationRecoveryStore(boundedSource("recovery_app"),Duration.ZERO),new DurableWriteLedger(boundedSource("recovery_app")),fixture.authClient(),fixture.sellerClient(),Duration.ofSeconds(20),8);
        assertTrue(restarted.tick());assertClosed();assertFalse(restarted.tick());
        fixture.authClient().seal(fixture.intent,op.epoch());fixture.sellerClient().seal(fixture.intent,op.epoch());
        assertEquals(1,ReservationRpcPostgresTest.productDb.queryForObject("SELECT count(*) FROM rpc_write_probe",Integer.class));
        assertEquals(DurableWriteLedger.State.COMMITTED,ReservationRpcPostgresTest.ledger.terminal(fixture.intent.requestId()).state());
    }
    @Test void unavailableCallbackAndStaleEpochNeverReleaseOrComplete() throws Exception {
        var op=admitted();ReservationRpcPostgresTest.productDb.execute("REVOKE SELECT ON authority_write_operations FROM product_terminal_reader");
        try {assertTrue(worker.tick());assertEquals("READY",state());fixture.held();}
        finally {ReservationRpcPostgresTest.productDb.execute("GRANT SELECT ON authority_write_operations TO product_terminal_reader");}
        ReservationRpcPostgresTest.failStatus(503,()->fixture.authClient().seal(fixture.intent,op.epoch()+1));fixture.held();
        immediate();assertTrue(worker.tick());assertClosed();
    }
    @Test void leaseExpiryOnlyTransfersRecoveryWorkAndOldWorkerCannotAcknowledge() throws Exception {
        var op=admitted();var first=recoveryStore.claim(Duration.ofSeconds(20)).orElseThrow();
        ReservationRpcPostgresTest.productDb.update("UPDATE product_reservation_recovery SET lease_until=clock_timestamp()-interval '1 second' WHERE request_id=?",fixture.intent.requestId());
        fixture.held();var second=recoveryStore.claim(Duration.ofSeconds(20)).orElseThrow();assertNotEquals(first.token(),second.token());
        assertFalse(recoveryStore.complete(first));assertFalse(recoveryStore.retry(first,"RPC_503",8));fixture.held();
        ReservationRpcPostgresTest.ledger.abort(fixture.intent.requestId(),op.epoch());fixture.sellerClient().seal(fixture.intent,op.epoch());fixture.authClient().seal(fixture.intent,op.epoch());
        assertTrue(recoveryStore.complete(second));assertClosed();assertEquals(2,recoveryStore.inspect(fixture.intent.requestId()).orElseThrow().attempts());
    }
    @Test void operatorQueuesCancellationButCannotForceAnInflightWriteToAbort() throws Exception {
        var op=admitted();var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var writing=pool.submit(()->ReservationRpcPostgresTest.ledger.commit(fixture.intent.requestId(),op.epoch(),c->{
                try(var q=c.prepareStatement("INSERT INTO rpc_write_probe VALUES(?)")){q.setObject(1,fixture.intent.requestId());q.executeUpdate();}
                entered.countDown();try{if(!finish.await(10,TimeUnit.SECONDS))throw new SQLException("test timeout");}catch(InterruptedException e){throw new SQLException(e);}
            }));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));assertTrue(worker.tick());assertEquals("READY",state());fixture.held();
                assertThrows(IllegalStateException.class,()->ProductReservationRecoveryOperator.execute(new String[]{"cancel",fixture.intent.requestId().toString(),Long.toString(op.epoch()+1)},operatorSource,true));
                assertEquals("QUEUED",ProductReservationRecoveryOperator.execute(new String[]{"cancel",fixture.intent.requestId().toString(),Long.toString(op.epoch())},operatorSource,true));
                ReservationRpcPostgresTest.assertDenied(operatorSource,"UPDATE authority_write_operations SET state='ABORTED'");
                assertEquals("operator_app",ReservationRpcPostgresTest.productDb.queryForObject("SELECT database_actor FROM product_reservation_recovery_audit WHERE request_id=?",String.class,fixture.intent.requestId()));
                finish.countDown();assertEquals(DurableWriteLedger.State.COMMITTED,writing.get(8,TimeUnit.SECONDS).state());
            } finally {finish.countDown();}
        }
        assertTrue(worker.tick());assertClosed();assertEquals(1,ReservationRpcPostgresTest.productDb.queryForObject("SELECT count(*) FROM rpc_write_probe",Integer.class));
    }
    @Test void concurrentRecoveryWorkersClaimOneJobAndCompleteIdempotently() throws Exception {
        admitted();try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->worker.tick());var b=pool.submit(()->worker.tick());int ran=(a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0);assertEquals(1,ran);
        }assertClosed();
    }
    @Test void pendingRoleRevocationStopsNewAdmissionsUntilRecoveryDrainsOldWork() throws Exception {
        ReservationRpcPostgresTest.authDb.update("INSERT INTO account_role_grants VALUES(?,'SELLER')",ReservationRpcPostgresTest.other);
        var op=begin();fixture.authClient().admit(fixture.intent);
        ReservationRpcPostgresTest.sellerDb.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test',version=3 WHERE id=?",ReservationRpcPostgresTest.profile);
        UUID event=UUID.randomUUID();String payload="{\"eventId\":\""+event+"\",\"sellerId\":\""+ReservationRpcPostgresTest.profile+"\",\"accountId\":\""+ReservationRpcPostgresTest.owner+"\",\"sellerStatus\":\"SUSPENDED\",\"sourceVersion\":\"3\"}";
        var response=fixture.post(ReservationRpcPostgresTest.base(revoker),"/internal/auth/seller-role-sync",ReservationRpcPostgresTest.secrets.get("HK5_SELLER_AUTH_ROLE_SYNC_SECRET"),payload,null);assertEquals(503,response.statusCode());
        String jti=UUID.randomUUID().toString(),otherToken=ReservationRpcPostgresTest.jwt(ReservationRpcPostgresTest.other,jti,Set.of("SELLER"),false);
        var otherIntent=new WriteIntent(UUID.randomUUID(),ReservationRpcPostgresTest.other,fixture.intent.storeId(),jti,WriteIntent.Mode.MANAGE,2,4,0,"b".repeat(64));
        var otherClient=new ReservationRpcClient(URI.create(ReservationRpcPostgresTest.base(ReservationRpcPostgresTest.auth)+"/internal/auth/write-reservations/product/"),()->ReservationRpcPostgresTest.secrets.get(ReservationRpcPostgresTest.AUTH+"SECRET"),Participant.AUTH,()->otherToken);
        ReservationRpcPostgresTest.failStatus(503,()->otherClient.admit(otherIntent));assertTrue(worker.tick());assertClosed();
        ReservationRpcPostgresTest.authDb.update("UPDATE authority_pending_revocations SET due_at=clock_timestamp() WHERE request_id=?",event);
        assertTrue(revoker.getBean(SellerRoleRevocationRecovery.class).tick());
        assertEquals("APPLIED",ReservationRpcPostgresTest.authDb.queryForObject("SELECT state FROM authority_pending_revocations WHERE request_id=?",String.class,event));
        assertEquals(0,ReservationRpcPostgresTest.authDb.queryForObject("SELECT count(*) FROM account_role_grants WHERE account_id=? AND role='SELLER'",Integer.class,ReservationRpcPostgresTest.owner));
        assertNotNull(otherClient.admit(otherIntent));
    }
    @Test void boundedFailuresRemainBlockedUntilExplicitAuditedRetry() throws Exception {
        var op=admitted();ReservationRpcPostgresTest.productDb.execute("REVOKE SELECT ON authority_write_operations FROM product_terminal_reader");
        var bounded=new ProductReservationRecoveryWorker(recoveryStore,new DurableWriteLedger(recoverySource),fixture.authClient(),fixture.sellerClient(),Duration.ofSeconds(20),2);
        try {assertTrue(bounded.tick());immediate();assertTrue(bounded.tick());assertEquals("BLOCKED",state());assertFalse(bounded.tick());fixture.held();}
        finally {ReservationRpcPostgresTest.productDb.execute("GRANT SELECT ON authority_write_operations TO product_terminal_reader");}
        assertEquals("QUEUED",ProductReservationRecoveryOperator.execute(new String[]{"retry",fixture.intent.requestId().toString(),Long.toString(op.epoch())},operatorSource,true));
        assertTrue(worker.tick());assertClosed();
        ReservationRpcPostgresTest.assertDenied(recoverySource,"INSERT INTO rpc_write_probe VALUES('00000000-0000-0000-0000-000000000001')");
    }
    @Test void roleSyncWorkerCannotRejectAnUnrelatedManualRevocation() throws Exception {
        var barrier=new RevocationBarrier(ReservationRpcPostgresTest.authDb.getDataSource());var id=UUID.randomUUID();
        barrier.enqueue(id,"c".repeat(64),null);
        assertFalse(revoker.getBean(SellerRoleRevocationRecovery.class).tick());
        assertEquals(RevocationBarrier.State.PENDING,barrier.inspect(id).orElseThrow().state());
        ReservationRpcPostgresTest.failStatus(503,()->fixture.authClient().admit(fixture.intent));
        var claim=barrier.claim(Duration.ofSeconds(20)).orElseThrow();
        ReservationRpcPostgresTest.authMutation.update("UPDATE accounts SET status='DISABLED' WHERE id=?",ReservationRpcPostgresTest.owner);
        assertTrue(barrier.complete(claim));ReservationRpcPostgresTest.failStatus(403,()->fixture.authClient().admit(fixture.intent));
    }
    @Test void actualSchedulerIsOffByDefaultAndUsesDedicatedRecoveryIdentityWhenEnabledInTest() throws Exception {
        assertTrue(ReservationRpcPostgresTest.productWorker.getBeansOfType(ProductReservationRecoveryWorker.class).isEmpty());
        admitted();var values=new HashMap<String,Object>(ReservationRpcPostgresTest.workerConfig);
        values.put("HK5_PRODUCT_RECOVERY_DB_URL",ReservationRpcPostgresTest.url("product"));values.put("HK5_PRODUCT_RECOVERY_DB_USERNAME","recovery_app");values.put("HK5_PRODUCT_RECOVERY_DB_PASSWORD",ReservationRpcPostgresTest.password);
        try(var app=new SpringApplicationBuilder(com.projecthk5.productservice.ProductServiceApplication.class)
            .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("scheduled-recovery",values)))
            .properties("hk5.product.reservations.recovery.enabled=true","hk5.product.reservations.recovery.poll-ms=100")
            .run(ReservationRpcPostgresTest.args("product","product_receipt_app"))) {
            assertEquals(1,app.getBeansOfType(ProductReservationRecoveryWorker.class).size());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!"COMPLETE".equals(state()) && System.nanoTime()<deadline)Thread.sleep(25);
            assertClosed();
        }
    }
}
