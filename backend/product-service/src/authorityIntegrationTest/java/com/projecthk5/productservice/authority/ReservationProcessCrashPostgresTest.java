package com.projecthk5.productservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The producer/recovery worker really die in separate JVMs; HTTP authorities stay in the fixture JVM. */
class ReservationProcessCrashPostgresTest {
    ReservationRpcPostgresTest fixture;
    @BeforeAll static void start() throws Exception { ReservationRecoveryPostgresTest.start(); }
    @AfterAll static void stop() { ReservationRecoveryPostgresTest.stop(); }
    @BeforeEach void seed() throws Exception {
        var recovery=new ReservationRecoveryPostgresTest();recovery.seed();fixture=recovery.fixture;
    }

    @ParameterizedTest
    @ValueSource(strings={"before_begin","inside_before_enlist","inside_begin","legacy_begin","after_begin","before_commit","inside_commit","after_commit","during_callback","after_callback","before_ack","after_ack"})
    void killedJvmRestartsFromOnlyDurableState(String boundary) throws Exception {
        if(boundary.equals("during_callback"))ReservationRpcPostgresTest.callback.mode.set(ReservationRpcPostgresTest.FaultProxy.Mode.HOLD);
        try(var child=launch(boundary)) {
            child.await("CRASH_POINT");
            if(boundary.equals("during_callback"))assertTrue(ReservationRpcPostgresTest.callback.captured.await(10,TimeUnit.SECONDS));
            // The transaction has both rows, but neither is externally visible before its commit.
            if(Set.of("inside_before_enlist","inside_begin").contains(boundary)) {
                assertEquals(0,count("authority_write_operations"));assertEquals(0,count("product_reservation_recovery"));
            }
            child.kill();
        } finally {ReservationRpcPostgresTest.callback.proceed.countDown();}
        boolean empty=Set.of("before_begin","inside_before_enlist","inside_begin").contains(boundary);
        assertEquals(empty?0:1,count("authority_write_operations"));
        assertEquals(empty || boundary.equals("legacy_begin")?0:1,count("product_reservation_recovery"));
        if(boundary.equals("legacy_begin")) {
            try(var unattended=launch("recover")) {unattended.await("DONE");unattended.success();}
            assertEquals(0,count("product_reservation_recovery"));
            assertEquals("PENDING",ReservationRpcPostgresTest.productDb.queryForObject("SELECT state FROM authority_write_operations WHERE request_id=?",String.class,fixture.intent.requestId()));
            assertTrue(ProductReservationRecoveryOperator.execute(new String[]{"orphans"},ReservationRecoveryPostgresTest.operatorSource,true).contains(fixture.intent.requestId().toString()));
            long epoch=ReservationRpcPostgresTest.productDb.queryForObject("SELECT epoch FROM authority_write_operations WHERE request_id=?",Long.class,fixture.intent.requestId());
            // Legacy rows lack the intent: an operator must supply the original binding; never guess it.
            new ProductReservationRecoveryStore(ReservationRpcPostgresTest.worker,Duration.ZERO).enlist(fixture.intent,epoch);
        }
        if(empty) {
            try(var restarted=launch("restart_begin")) {restarted.await("DONE");restarted.success();}
        }
        // A lease survives process death, so recovery must wait for expiry rather than steal it.
        var row=ReservationRpcPostgresTest.productDb.queryForMap("SELECT state FROM product_reservation_recovery WHERE request_id=?",fixture.intent.requestId());
        if("LEASED".equals(row.get("state")))Thread.sleep(5200);
        try(var restarted=launch("recover")) {restarted.await("DONE");restarted.success();}
        assertEquals("COMPLETE",new ProductReservationRecoveryStore(ReservationRecoveryPostgresTest.recoverySource,Duration.ZERO).inspect(fixture.intent.requestId()).orElseThrow().state());
        boolean committed=Set.of("after_commit","during_callback","after_callback","before_ack","after_ack").contains(boundary);
        assertEquals(committed?DurableWriteLedger.State.COMMITTED:DurableWriteLedger.State.ABORTED,ReservationRpcPostgresTest.ledger.terminal(fixture.intent.requestId()).state());
        assertEquals(committed?1:0,count("rpc_write_probe"));
        for(var db:List.of(ReservationRpcPostgresTest.authDb,ReservationRpcPostgresTest.sellerDb))
            assertEquals(1,db.queryForObject("SELECT count(*) FROM authority_reservation_closures WHERE request_id=?",Integer.class,fixture.intent.requestId()));
        // Replay from another fresh process cannot duplicate the business write or closures.
        try(var restarted=launch("recover")) {restarted.await("DONE");restarted.success();}
        assertEquals(committed?1:0,count("rpc_write_probe"));
        ReservationRpcPostgresTest.failStatus(409,()->fixture.authClient().admit(fixture.intent));
    }
    int count(String table) {return ReservationRpcPostgresTest.productDb.queryForObject("SELECT count(*) FROM "+table+" WHERE "+(table.equals("rpc_write_probe")?"id":"request_id")+"=?",Integer.class,fixture.intent.requestId());}

    Child launch(String mode) throws Exception {
        var p=new Properties();
        p.setProperty("mode",mode);p.setProperty("url",ReservationRpcPostgresTest.url("product"));
        p.setProperty("password",ReservationRpcPostgresTest.password);
        p.setProperty("intent",ReservationWire.request("lookup",fixture.intent.requestId(),fixture.intent,null));
        p.setProperty("auth",ReservationRpcPostgresTest.base(ReservationRpcPostgresTest.auth)+"/internal/auth/write-reservations/product/");
        p.setProperty("seller",ReservationRpcPostgresTest.base(ReservationRpcPostgresTest.seller)+"/internal/seller/write-reservations/product/");
        p.setProperty("authKey",ReservationRpcPostgresTest.secrets.get(ReservationRpcPostgresTest.AUTH+"SECRET"));
        p.setProperty("sellerKey",ReservationRpcPostgresTest.secrets.get(ReservationRpcPostgresTest.SELLER+"SECRET"));p.setProperty("token",fixture.token);
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",childClasspath(),ReservationProcessCrashPostgresTest.class.getName()).redirectErrorStream(true).start();
        var child=new Child(process);
        try(var input=process.getOutputStream()) {p.store(input,null);}
        return child;
    }
    static String childClasspath() throws Exception {
        var entries=new LinkedHashSet<String>();
        entries.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))));
        for(ClassLoader loader=ReservationProcessCrashPostgresTest.class.getClassLoader();loader!=null;loader=loader.getParent())
            if(loader instanceof URLClassLoader urls)for(URL url:urls.getURLs())if("file".equals(url.getProtocol()))entries.add(Path.of(url.toURI()).toString());
        return String.join(File.pathSeparator,entries);
    }
    static final class Child implements AutoCloseable {
        final Process process;final BlockingQueue<String> signals=new LinkedBlockingQueue<>();final Thread reader;
        Child(Process process) {
            this.process=process;
            reader=Thread.ofPlatform().daemon().start(()->{
                try(var lines=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;while((line=lines.readLine())!=null)if(Set.of("CRASH_POINT","DONE").contains(line) || line.startsWith("CHILD_FAILURE"))signals.add(line);
                }catch(IOException ignored){}finally{signals.add("EXIT");}
            });
        }
        void await(String expected) throws Exception {assertEquals(expected,signals.poll(30,TimeUnit.SECONDS),"Child failed or timed out; output intentionally excludes credentials");}
        void kill() throws Exception {process.destroyForcibly();assertTrue(process.waitFor(10,TimeUnit.SECONDS));}
        void success() throws Exception {assertTrue(process.waitFor(10,TimeUnit.SECONDS));assertEquals(0,process.exitValue());}
        @Override public void close() throws Exception {if(process.isAlive())kill();reader.join(1000);}
    }

    /** Secrets travel only over stdin, never process arguments, files, or output. */
    public static void main(String[] ignored) {
        try {runChild();}
        catch(Throwable failure) {System.out.println("CHILD_FAILURE "+failure.getClass().getName());System.out.flush();System.exit(2);}
    }
    static void runChild() throws Exception {
        var p=new Properties();p.load(System.in);String mode=p.getProperty("mode");
        var intent=ReservationWire.readRequest("lookup",p.getProperty("intent").getBytes(StandardCharsets.UTF_8)).intent();
        DataSource producer=new DriverManagerDataSource(p.getProperty("url"),"product_worker_app",p.getProperty("password"));
        DataSource recovery=new DriverManagerDataSource(p.getProperty("url"),"recovery_app",p.getProperty("password"));
        var ledger=new DurableWriteLedger(producer);
        var auth=new ReservationRpcClient(URI.create(p.getProperty("auth")),()->p.getProperty("authKey"),Participant.AUTH,()->p.getProperty("token"));
        var seller=new ReservationRpcClient(URI.create(p.getProperty("seller")),()->p.getProperty("sellerKey"),Participant.SELLER,()->null);
        if(mode.equals("recover")) {
            new ProductReservationRecoveryWorker(new ProductReservationRecoveryStore(recovery,Duration.ZERO),new DurableWriteLedger(recovery),auth,seller,Duration.ofSeconds(1),8).tick();done();return;
        }
        if(mode.equals("before_begin"))park();
        if(mode.equals("legacy_begin")){ledger.begin(intent);park();}
        if(Set.of("inside_begin","inside_before_enlist").contains(mode))producer=pauseTransaction(producer,mode);
        var store=new ProductReservationRecoveryStore(producer,Duration.ZERO);
        var operation=store.begin(intent);
        if(mode.equals("restart_begin")){done();return;}
        if(mode.equals("after_begin"))park();
        ledger.recordGrant(intent.requestId(),operation.epoch(),auth.admit(intent));
        ledger.recordGrant(intent.requestId(),operation.epoch(),seller.admit(intent));
        if(mode.equals("before_commit"))park();
        ledger.commit(intent.requestId(),operation.epoch(),connection->{
            try(var q=connection.prepareStatement("INSERT INTO rpc_write_probe VALUES(?)")){q.setObject(1,intent.requestId());q.executeUpdate();}
            if(mode.equals("inside_commit"))park();
        });
        if(mode.equals("after_commit"))park();
        // Exercise production worker ordering: abort/read terminal, seller seal, auth seal, queue ACK.
        if(Set.of("after_callback","before_ack").contains(mode))recovery=pauseCompletion(recovery,mode);
        var recoveryStore=new ProductReservationRecoveryStore(recovery,Duration.ZERO);
        if(mode.equals("during_callback")){System.out.println("CRASH_POINT");System.out.flush();}
        if(!new ProductReservationRecoveryWorker(recoveryStore,new DurableWriteLedger(recovery),auth,seller,Duration.ofSeconds(5),8).tick())
            throw new IllegalStateException("Missing recovery work");
        if(mode.equals("after_ack")) {
            if(!"COMPLETE".equals(recoveryStore.inspect(intent.requestId()).orElseThrow().state()))throw new IllegalStateException("Recovery not acknowledged");
            park();
        }
        throw new IllegalArgumentException("Unknown boundary or missing crash checkpoint");
    }
    /** Observe the actual worker's JDBC completion without replacing its store, RPCs, or SQL. */
    static DataSource pauseCompletion(DataSource delegate,String mode) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                var connection=delegate.getConnection();
                return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try {
                        Object result=method.invoke(connection,args);
                        if(method.getName().equals("prepareStatement") && args!=null && args[0] instanceof String sql
                                && sql.startsWith("UPDATE product_reservation_recovery SET state=?")) {
                            var statement=(PreparedStatement)result;
                            boolean[] complete={false};
                            return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                                if(m.getName().equals("setString") && a!=null && Integer.valueOf(1).equals(a[0])) {
                                    complete[0]="COMPLETE".equals(a[1]);
                                    // The worker has received both real HTTP ACKs and now starts its completion statement.
                                    if(complete[0] && mode.equals("after_callback"))park();
                                }
                                if(complete[0] && m.getName().equals("executeUpdate") && mode.equals("before_ack"))park();
                                try{return m.invoke(statement,a);}catch(InvocationTargetException e){throw e.getCause();}
                            });
                        }
                        return result;
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
            }
            @Override public Connection getConnection(String user,String password)throws SQLException {throw new SQLFeatureNotSupportedException();}
        };
    }
    static DataSource pauseTransaction(DataSource delegate,String mode) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                var connection=delegate.getConnection();
                return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    if(mode.equals("inside_begin") && method.getName().equals("commit"))park();
                    if(mode.equals("inside_before_enlist") && method.getName().equals("prepareStatement") && args!=null && args[0] instanceof String sql && sql.startsWith("INSERT INTO product_reservation_recovery"))park();
                    try{return method.invoke(connection,args);}catch(InvocationTargetException e){throw e.getCause();}
                });
            }
            @Override public Connection getConnection(String user,String password)throws SQLException {throw new SQLFeatureNotSupportedException();}
        };
    }
    static void park() throws SQLException {
        System.out.println("CRASH_POINT");System.out.flush();
        try{new CountDownLatch(1).await();}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new SQLException("Child interrupted",failure);}
    }
    static void done(){System.out.println("DONE");System.out.flush();}
}
