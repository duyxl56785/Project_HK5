package com.projecthk5.productservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.sql.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.*;
import com.projecthk5.authservice.authority.AuthWriteReservations;
import com.projecthk5.sellerservice.storefacts.SellerWriteReservations;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;

class DurableReservationPostgresTest {
    AuthWriteReservations auth;
    SellerWriteReservations seller;
    DurableWriteLedger ledger;
    WriteIntent intent;
    @BeforeAll static void start() {
        ProductAuthorityHttpPostgresTest.start();
        Flyway.configure().dataSource(ProductAuthorityHttpPostgresTest.productDs)
            .baselineOnMigrate(true).baselineVersion("0")
            .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }
    @AfterAll static void stop() { ProductAuthorityHttpPostgresTest.stop(); }
    @BeforeEach void seed() {
        ProductAuthorityHttpPostgresTest.authDb.execute("TRUNCATE authority_write_reservations");
        ProductAuthorityHttpPostgresTest.sellerDb.execute("TRUNCATE authority_write_reservations");
        ProductAuthorityHttpPostgresTest.productProbe.execute("TRUNCATE authority_write_operations CASCADE");
        new ProductAuthorityHttpPostgresTest().seed();
        var verifier=new JwtAccessVerifier("https://issuer.example.invalid","aud","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),Clock.systemUTC());
        auth=new AuthWriteReservations(ProductAuthorityHttpPostgresTest.authDb.getDataSource(),verifier);
        seller=new SellerWriteReservations(ProductAuthorityHttpPostgresTest.sellerDb.getDataSource());
        ledger=new DurableWriteLedger(ProductAuthorityHttpPostgresTest.productDs);
        intent=new WriteIntent(UUID.randomUUID(),ProductAuthorityHttpPostgresTest.owner,ProductAuthorityHttpPostgresTest.store,
                verifier.verify(ProductAuthorityHttpPostgresTest.token).tokenId(),WriteIntent.Mode.MANAGE,2,4,0,"a".repeat(64));
    }
    @Test void productionMigrationsInstallDurableAuthorityAndOperationLedgers() {
        for(var db:List.of(ProductAuthorityHttpPostgresTest.authDb,ProductAuthorityHttpPostgresTest.sellerDb))
            assertEquals(1,db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='authority_write_reservations'",Integer.class));
        assertEquals(1,ProductAuthorityHttpPostgresTest.productProbe.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='authority_write_operations'",Integer.class));
    }
    record Admission(DurableWriteLedger.Operation operation,Grant auth,Grant seller) { }
    Admission admit() throws Exception {
        var operation=ledger.begin(intent);var a=auth.admit(intent,ProductAuthorityHttpPostgresTest.token);ledger.recordGrant(intent.requestId(),operation.epoch(),a);
        var s=seller.admit(intent);ledger.recordGrant(intent.requestId(),operation.epoch(),s);return new Admission(operation,a,s);
    }
    void release(Admission a) throws Exception {seller.release(a.seller(),ledger::terminal);auth.release(a.auth(),ledger::terminal);}
    void probe(Connection c) throws SQLException {try(var q=c.prepareStatement("INSERT INTO authority_write_probe VALUES(?)")){q.setObject(1,intent.requestId());q.executeUpdate();}}
    int writes(){return ProductAuthorityHttpPostgresTest.productProbe.queryForObject("SELECT count(*) FROM authority_write_probe",Integer.class);}
    void roleRevocation(){ProductAuthorityHttpPostgresTest.authDb.update("DELETE FROM account_role_grants WHERE account_id=?",intent.accountId());}
    void assertBarrier(Runnable mutation){
        var failure=assertThrows(org.springframework.dao.DataAccessException.class,mutation::run);
        assertEquals("55P03",assertInstanceOf(SQLException.class,failure.getMostSpecificCause()).getSQLState());
    }

    @Test void reservationsFenceEveryAuthorityMutationUntilAtomicCommitReceipt() throws Exception {
        var a=admit();
        assertBarrier(this::roleRevocation);
        assertBarrier(()->ProductAuthorityHttpPostgresTest.authDb.update("UPDATE accounts SET status='DISABLED' WHERE id=?",intent.accountId()));
        assertBarrier(()->ProductAuthorityHttpPostgresTest.sellerDb.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test',version=version+1"));
        assertBarrier(()->ProductAuthorityHttpPostgresTest.sellerDb.update("UPDATE stores SET status='CLOSED',version=version+1"));
        assertBarrier(()->ProductAuthorityHttpPostgresTest.sellerDb.update("UPDATE seller_profiles SET owner_reference=?",UUID.randomUUID().toString()));
        assertBarrier(()->ProductAuthorityHttpPostgresTest.sellerDb.execute("TRUNCATE stores CASCADE"));
        assertThrows(IllegalArgumentException.class,()->ledger.terminal(intent.requestId()));
        var result=ledger.commit(intent.requestId(),a.operation().epoch(),this::probe);
        assertEquals(DurableWriteLedger.State.COMMITTED,result.state());assertEquals(1,writes());
        assertBarrier(this::roleRevocation);release(a);roleRevocation();
        ProductAuthorityHttpPostgresTest.sellerDb.update("UPDATE stores SET status='CLOSED',version=version+1");
    }
    @Test void revocationBeforeAdmissionAndStaleStoreVersionCannotBecomeGrants() throws Exception {
        roleRevocation();assertThrows(SecurityException.class,()->auth.admit(intent,ProductAuthorityHttpPostgresTest.token));
        assertEquals(0,ProductAuthorityHttpPostgresTest.authDb.queryForObject("SELECT count(*) FROM authority_write_reservations",Integer.class));
        ProductAuthorityHttpPostgresTest.sellerDb.update("UPDATE stores SET version=5");
        assertThrows(SecurityException.class,()->seller.admit(intent));
        assertEquals(0,ProductAuthorityHttpPostgresTest.sellerDb.queryForObject("SELECT count(*) FROM authority_write_reservations",Integer.class));
    }
    @Test void crashBeforeRecordingAckCanBeCancelledAndLateAckNeverReadmits() throws Exception {
        var operation=ledger.begin(intent);var a=auth.admit(intent,ProductAuthorityHttpPostgresTest.token);
        assertEquals(DurableWriteLedger.State.ABORTED,ledger.abort(intent.requestId(),operation.epoch()).state());
        assertBarrier(this::roleRevocation);
        ledger.recordGrant(intent.requestId(),operation.epoch(),a);
        assertThrows(IllegalStateException.class,()->ledger.commit(intent.requestId(),operation.epoch(),this::probe));
        assertEquals(0,writes());auth.release(a,ledger::terminal);roleRevocation();
    }
    @Test void timeoutAndAgeNeverReleaseReservationsAndRecoveryIsIdempotent() throws Exception {
        var a=admit();
        assertThrows(java.net.SocketTimeoutException.class,()->auth.release(a.auth(),id->{throw new java.net.SocketTimeoutException();}));
        ProductAuthorityHttpPostgresTest.authDb.update("UPDATE authority_write_reservations SET created_at='2000-01-01T00:00:00Z'");
        assertBarrier(this::roleRevocation);
        var recovered=new DurableAuthorityReservations(ProductAuthorityHttpPostgresTest.authDb.getDataSource(),Participant.AUTH).lookup(intent).orElseThrow();
        assertEquals(a.auth(),recovered);
        ledger.abort(intent.requestId(),a.operation().epoch());release(a);release(a);roleRevocation();
    }
    @Test void requestReplayPayloadConflictAndStaleEpochCannotRepeatWrite() throws Exception {
        var a=admit();assertEquals(a.auth(),auth.admit(intent,ProductAuthorityHttpPostgresTest.token));
        assertThrows(IllegalStateException.class,()->ledger.begin(new WriteIntent(intent.requestId(),intent.accountId(),intent.storeId(),intent.tokenId(),intent.mode(),2,4,0,"b".repeat(64))));
        assertThrows(IllegalStateException.class,()->ledger.commit(intent.requestId(),a.operation().epoch()+1,this::probe));
        ledger.commit(intent.requestId(),a.operation().epoch(),this::probe);
        ledger.commit(intent.requestId(),a.operation().epoch(),c->{throw new AssertionError("Duplicate write executed");});
        assertEquals(1,writes());
        var stale=new Grant(a.auth().requestId(),a.auth().bindingHash(),Participant.AUTH,a.auth().epoch()+1);
        assertThrows(IllegalStateException.class,()->auth.release(stale,ledger::terminal));release(a);
    }
    @Test void localFailureRollsBackBusinessAndTerminalReceiptTogether() throws Exception {
        var a=admit();
        assertThrows(SQLException.class,()->ledger.commit(intent.requestId(),a.operation().epoch(),c->{probe(c);throw new SQLException("test rollback");}));
        assertEquals(0,writes());assertThrows(IllegalArgumentException.class,()->ledger.terminal(intent.requestId()));
        assertBarrier(this::roleRevocation);ledger.abort(intent.requestId(),a.operation().epoch());release(a);
    }
    @Test void concurrentAbortWaitsForCommitAndRevocationCannotSlipThrough() throws Exception {
        var a=admit();var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var writer=workers.submit(()->ledger.commit(intent.requestId(),a.operation().epoch(),c->{probe(c);entered.countDown();try{if(!proceed.await(10,TimeUnit.SECONDS))throw new SQLException("test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException(e);}}));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));assertBarrier(this::roleRevocation);
                var cancel=workers.submit(()->{attempted.countDown();return ledger.abort(intent.requestId(),a.operation().epoch());});
                assertTrue(attempted.await(10,TimeUnit.SECONDS));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline && !waiting) {
                    waiting=ProductAuthorityHttpPostgresTest.productProbe.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock')",Boolean.class);
                    if(!waiting)Thread.sleep(10);
                }
                assertTrue(waiting,"Recovery must serialize on the operation row");proceed.countDown();
                assertEquals(DurableWriteLedger.State.COMMITTED,writer.get(10,TimeUnit.SECONDS).state());
                assertEquals(DurableWriteLedger.State.COMMITTED,cancel.get(10,TimeUnit.SECONDS).state());
            } finally {proceed.countDown();}
        }
        assertEquals(1,writes());release(a);
    }
    @Test void repeatableReadSnapshotCannotBypassCommittedReservation() throws Exception {
        try(var connection=ProductAuthorityHttpPostgresTest.authDb.getDataSource().getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT count(*) FROM account_role_grants")){assertTrue(r.next());}
            var a=admit();
            try(var s=connection.prepareStatement("DELETE FROM account_role_grants WHERE account_id=?")) {
                s.setObject(1,intent.accountId());var failure=assertThrows(SQLException.class,s::executeUpdate);
                assertEquals("40001",failure.getSQLState());
            } finally {connection.rollback();}
            ledger.abort(intent.requestId(),a.operation().epoch());release(a);
        }
    }
    @Test void ambientTransactionCannotStartNetworkAdmission() throws Exception {
        var calls=new AtomicInteger();
        var port=new DurableWriteCoordinator.AuthorityPort(){
            public Grant admit(WriteIntent i){calls.incrementAndGet();throw new AssertionError();}
            public Optional<Grant> lookup(WriteIntent i){calls.incrementAndGet();throw new AssertionError();}
            public void release(Grant g){calls.incrementAndGet();throw new AssertionError();}
        };
        var coordinator=new DurableWriteCoordinator(ledger,port,port);
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {assertThrows(IllegalStateException.class,()->coordinator.admit(intent));assertEquals(0,calls.get());}
        finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
    }
    @Test void mutationStatementStartedBeforeAdmissionCommitStillSeesReservation() throws Exception {
        var inside=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var reservations=new DurableAuthorityReservations(ProductAuthorityHttpPostgresTest.authDb.getDataSource(),Participant.AUTH);
        var operation=ledger.begin(intent);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var admit=pool.submit(()->reservations.admit(intent,c->{inside.countDown();try{if(!proceed.await(10,TimeUnit.SECONDS))throw new SQLException("test timeout");}catch(InterruptedException e){throw new SQLException(e);}}));
            try {
                assertTrue(inside.await(10,TimeUnit.SECONDS));
                assertBarrier(()->ProductAuthorityHttpPostgresTest.authDb.execute("TRUNCATE accounts CASCADE"));
                var revoke=pool.submit(()->{roleRevocation();return true;});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline && !waiting) {
                    waiting=ProductAuthorityHttpPostgresTest.authDb.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory' AND wait_event_type='Lock')",Boolean.class);
                    if(!waiting)Thread.sleep(10);
                }
                assertTrue(waiting,"Mutation must wait for admission's exclusive guard");
                proceed.countDown();var grant=admit.get(10,TimeUnit.SECONDS);
                var failure=assertThrows(ExecutionException.class,()->revoke.get(10,TimeUnit.SECONDS));
                assertEquals("55P03",assertInstanceOf(SQLException.class,assertInstanceOf(org.springframework.dao.DataAccessException.class,failure.getCause()).getMostSpecificCause()).getSQLState());
                ledger.abort(intent.requestId(),operation.epoch());ledger.recordGrant(intent.requestId(),operation.epoch(),grant);reservations.release(grant,ledger::terminal);
            }finally{proceed.countDown();}
        }
    }
    @Test void lostHttpAckRetryAndTerminalDependencyFailureRetainBarrierUntilDurableAbort() throws Exception {
        var operation=ledger.begin(intent);var admitted=new CountDownLatch(1);var responseMayFinish=new CountDownLatch(1);var attempts=new AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var workers=Executors.newFixedThreadPool(4);server.setExecutor(workers);
        String key="test-only-auth-reservation-credential-0123456789";
        String path="/internal/auth/write-reservations";
        var verifier=new InternalPairVerifier("product-service","auth-service",Set.of(new InternalPairVerifier.Scope("POST",path,"AUTHORITY_RESERVE")),
                ()->new InternalPairVerifier.Credentials(key,null,null),Clock.systemUTC());
        final var guard=verifier;
        server.createContext(path,exchange->{
            int status=200;String body="";
            try {
                guard.verify(exchange.getRequestHeaders().getFirst("X-HK5-Service-Credential"),exchange.getRequestMethod(),exchange.getRequestURI().getPath());
                var grant=auth.admit(intent,exchange.getRequestHeaders().getFirst("Authorization").substring(7));
                if(attempts.incrementAndGet()==1) {admitted.countDown();if(!responseMayFinish.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}
                body=Long.toString(grant.epoch());
            }catch(InternalPairVerifier.Failure denied){status=denied.status();}
            catch(Exception failed){status=503;}
            try {byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}
            catch(java.io.IOException disconnected) { }
            finally {exchange.close();}
        });
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        server.createContext("/terminal",exchange->{
            try {byte[] bytes=json.writeValueAsBytes(ledger.terminal(intent.requestId()));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}
            catch(Exception unavailable){exchange.sendResponseHeaders(503,-1);}
            finally {exchange.close();}
        });
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();var http=java.net.http.HttpClient.newHttpClient();
        try {
            var wrong=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).timeout(Duration.ofSeconds(5))
                .header("X-HK5-Service-Credential","other-process-credential-0123456789").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(401,http.send(wrong,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(0,attempts.get());
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).timeout(Duration.ofMillis(500))
                .header("X-HK5-Service-Credential",key).header("Authorization","Bearer "+ProductAuthorityHttpPostgresTest.token)
                .header("X-User-Id",ProductAuthorityHttpPostgresTest.other.toString()).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            var lost=http.sendAsync(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertTrue(admitted.await(10,TimeUnit.SECONDS));
            assertEquals(0,ProductAuthorityHttpPostgresTest.authDb.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction'",Integer.class));
            var failure=assertThrows(ExecutionException.class,()->lost.get(10,TimeUnit.SECONDS));assertInstanceOf(java.net.http.HttpTimeoutException.class,failure.getCause());
            var retry=http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,retry.statusCode());
            var recovered=new DurableAuthorityReservations(ProductAuthorityHttpPostgresTest.authDb.getDataSource(),Participant.AUTH).lookup(intent).orElseThrow();
            assertEquals(Long.toString(recovered.epoch()),retry.body());assertBarrier(this::roleRevocation);
            assertThrows(java.io.IOException.class,()->auth.release(recovered,id->{
                var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/terminal")).timeout(Duration.ofSeconds(5)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(503,response.statusCode());throw new java.io.IOException("No durable terminal receipt");
            }));
            assertBarrier(this::roleRevocation);
            ledger.abort(intent.requestId(),operation.epoch());ledger.recordGrant(intent.requestId(),operation.epoch(),recovered);
            assertThrows(java.io.IOException.class,()->auth.release(recovered,id->{throw new java.io.IOException("test receipt source unavailable");}));
            assertBarrier(this::roleRevocation);
            auth.release(recovered,id->{
                var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/terminal")).timeout(Duration.ofSeconds(5)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode());return json.readValue(response.body(),DurableWriteLedger.Receipt.class);
            });
            roleRevocation();assertEquals(0,writes());
        } finally {responseMayFinish.countDown();server.stop(0);workers.shutdownNow();}
    }
}
