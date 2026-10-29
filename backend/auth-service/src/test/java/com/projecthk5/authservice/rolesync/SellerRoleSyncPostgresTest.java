package com.projecthk5.authservice.rolesync;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import com.projecthk5.authservice.rolesync.SellerRoleSyncService.*;

class SellerRoleSyncPostgresTest {
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16.6-alpine");
    static final String password = UUID.randomUUID().toString();
    static JdbcTemplate admin, jdbc;
    static DataSourceTransactionManager transactions;
    static SellerRoleSyncService service;
    UUID account = UUID.randomUUID(), seller = UUID.randomUUID();
    @BeforeAll static void setup() {
        pg.start();
        var bootstrap = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        bootstrap.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '" + password + "'");
        bootstrap.execute("CREATE ROLE auth_app LOGIN PASSWORD '" + password + "'");
        bootstrap.execute("CREATE DATABASE auth_db OWNER auth_migrator");
        String url = pg.getJdbcUrl().replace("/test", "/auth_db");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));
        var v2 = Flyway.configure().dataSource(url,"auth_migrator",password).target("2").load();
        v2.migrate();
        var v3 = Flyway.configure().dataSource(url,"auth_migrator",password).load();
        assertEquals(1, v3.migrate().migrationsExecuted);
        v3.validate();
        assertEquals("3", v3.info().current().getVersion().toString());
        admin.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        var ds = new DriverManagerDataSource(url,"auth_app",password);
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        var proxy = new ProxyFactory(new SellerRoleSyncService(jdbc));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        service = (SellerRoleSyncService)proxy.getProxy();
    }
    @AfterAll static void stop() { pg.stop(); }
    @BeforeEach void reset() {
        admin.execute("TRUNCATE seller_role_sync_receipts,seller_role_sync_versions,seller_role_sync_state,accounts CASCADE");
        admin.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",account,account+"@example.invalid","fixture-hash","ACTIVE");
        admin.update("INSERT INTO account_role_grants VALUES(?,'CUSTOMER'),(?,'ADMIN')",account,account);
    }
    Command event(long version, Status status) {return new Command(UUID.randomUUID(),seller,account.toString(),status,version);}
    boolean sellerGranted() {return jdbc.queryForObject("SELECT count(*) FROM account_role_grants WHERE account_id=? AND role='SELLER'",Long.class,account)==1;}
    long latest() {return jdbc.queryForObject("SELECT latest_version FROM seller_role_sync_state WHERE seller_id=?",Long.class,seller);}
    @Test void firstDuplicateAndRetryAreDurableWithoutTouchingAdmin() {
        var event = event(1,Status.APPROVED);
        assertEquals(Result.APPLIED,service.apply(event));
        assertEquals(Result.DUPLICATE,service.apply(event));
        assertEquals(Result.DUPLICATE,service.apply(event(1,Status.APPROVED)));
        assertTrue(sellerGranted()); assertEquals(1,latest());
        assertEquals(3L,jdbc.queryForObject("SELECT count(*) FROM account_role_grants",Long.class));
        assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM seller_role_sync_receipts",Long.class));
    }
    @ParameterizedTest @EnumSource(Status.class)
    void onlyApprovedGrantsSeller(Status status) {
        service.apply(event(0,Status.APPROVED));
        service.apply(event(1,status));
        assertEquals(status==Status.APPROVED,sellerGranted());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM account_role_grants WHERE role='ADMIN'",Long.class));
    }
    @Test void outOfOrderCannotRestoreRevokedRoleAndStaleHistoryRejectsConflicts() {
        service.apply(event(9,Status.SUSPENDED));
        var stale=event(2,Status.APPROVED);
        assertEquals(Result.STALE_IGNORED,service.apply(stale));
        assertEquals(Result.DUPLICATE,service.apply(stale));
        assertThrows(Conflict.class,()->service.apply(event(2,Status.REJECTED)));
        assertFalse(sellerGranted()); assertEquals(9,latest());
    }
    @Test void eventIdentityAndSellerVersionConflictsDoNotMutateState() {
        var first=event(3,Status.APPROVED); service.apply(first);
        assertThrows(Conflict.class,()->service.apply(new Command(first.eventId(),seller,account.toString(),Status.SUSPENDED,4)));
        assertThrows(Conflict.class,()->service.apply(event(3,Status.SUSPENDED)));
        assertTrue(sellerGranted()); assertEquals(3,latest());
    }
    @Test void unknownAccountAndChangedBindingAreRejectedWithoutImplicitProvisioning() {
        var missing=new Command(UUID.randomUUID(),seller,UUID.randomUUID().toString(),Status.APPROVED,0);
        assertEquals("ROLE_SYNC_ACCOUNT_NOT_FOUND",assertThrows(Conflict.class,()->service.apply(missing)).code());
        service.apply(event(1,Status.APPROVED));
        assertThrows(Conflict.class,()->service.apply(new Command(UUID.randomUUID(),UUID.randomUUID(),account.toString(),Status.APPROVED,2)));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM accounts",Long.class));
    }
    @Test void requiredRollbackIncludesReceiptBindingHistoryAndGrant() {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{service.apply(event(1,Status.APPROVED)); tx.setRollbackOnly();});
        assertFalse(sellerGranted());
        for(String table:List.of("seller_role_sync_receipts","seller_role_sync_versions","seller_role_sync_state"))
            assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class));
    }
    @Test void databaseFailureRollsBackRoleAndLatestVersion() {
        service.apply(event(1,Status.APPROVED));
        admin.execute("ALTER TABLE seller_role_sync_receipts ADD CONSTRAINT test_fail CHECK (source_version<2)");
        try {assertThrows(RuntimeException.class,()->service.apply(event(2,Status.SUSPENDED)));}
        finally {admin.execute("ALTER TABLE seller_role_sync_receipts DROP CONSTRAINT test_fail");}
        assertTrue(sellerGranted()); assertEquals(1,latest());
    }
    @Test void concurrentConflictingVersionHasExactlyOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await(); return service.apply(event(1,Status.APPROVED));});
            var b=pool.submit(()->{start.await(); return service.apply(event(1,Status.SUSPENDED));});
            start.countDown(); int applied=0,conflicts=0;
            for(var future:List.of(a,b))try {assertEquals(Result.APPLIED,future.get(15,TimeUnit.SECONDS));applied++;}
            catch(ExecutionException failure){assertInstanceOf(Conflict.class,failure.getCause());conflicts++;}
            assertEquals(1,applied);assertEquals(1,conflicts);
            assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM seller_role_sync_receipts",Long.class));
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"123","opaque-Ä"," owner ","1-1-1-1-1"})
    void opaqueUnresolvableAccountsAreNeverNormalizedOrProvisioned(String reference) {
        var command=new Command(UUID.randomUUID(),seller,reference,Status.APPROVED,1);
        assertEquals(reference,command.accountId());
        assertEquals("ROLE_SYNC_ACCOUNT_NOT_FOUND",assertThrows(Conflict.class,()->service.apply(command)).code());
        assertFalse(sellerGranted());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM seller_role_sync_state",Long.class));
    }
    @Test void commitFailureAfterRoleMutationNeverReturnsAcknowledgmentAndRollsBackAllRows() {
        admin.execute("CREATE FUNCTION test_fail_sync_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test commit failure' USING ERRCODE='23514'; END $$");
        admin.execute("CREATE CONSTRAINT TRIGGER test_sync_commit AFTER INSERT ON seller_role_sync_receipts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_fail_sync_commit()");
        try {assertThrows(RuntimeException.class,()->service.apply(event(1,Status.APPROVED)));}
        finally {admin.execute("DROP TRIGGER test_sync_commit ON seller_role_sync_receipts");admin.execute("DROP FUNCTION test_fail_sync_commit()");}
        assertFalse(sellerGranted());
        for(String table:List.of("seller_role_sync_receipts","seller_role_sync_versions","seller_role_sync_state"))
            assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class));
    }
    @Test void concurrentDifferentAccountsCannotBindTheSameSeller() throws Exception {
        UUID other=UUID.randomUUID();
        admin.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp())",other,other+"@example.invalid","fixture-hash","ACTIVE");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return service.apply(event(1,Status.APPROVED));});
            var b=pool.submit(()->{start.await();return service.apply(new Command(UUID.randomUUID(),seller,other.toString(),Status.APPROVED,1));});
            start.countDown();int applied=0,conflicts=0;
            for(var f:List.of(a,b))try{f.get(15,TimeUnit.SECONDS);applied++;}catch(ExecutionException failure){assertInstanceOf(Conflict.class,failure.getCause());conflicts++;}
            assertEquals(1,applied);assertEquals(1,conflicts);
            assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM account_role_grants WHERE role='SELLER'",Long.class));
        }
    }
}
