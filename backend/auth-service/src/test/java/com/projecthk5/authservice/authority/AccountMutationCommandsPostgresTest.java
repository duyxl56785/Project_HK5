package com.projecthk5.authservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.projecthk5.authservice.persistence.*;
import com.projecthk5.authservice.security.*;
import com.projecthk5.authservice.rolesync.*;
import com.projecthk5.security.fencing.RevocationBarrier;
import com.projecthk5.security.fencing.DurableAuthorityReservations;
import com.projecthk5.security.fencing.WriteIntent;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class AccountMutationCommandsPostgresTest {
    static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static JdbcTemplate jdbc;
    static DriverManagerDataSource source;
    static TransactionTemplate tx;
    static RevocationBarrier barrier;
    static String url;
    AccountMutationCommands commands;
    UUID actor=UUID.randomUUID(), target=UUID.randomUUID();
    @BeforeAll static void setup() {
        pg.start();
        var root=new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
        root.execute("CREATE ROLE auth_app LOGIN PASSWORD 'test-only-password'");
        root.execute("CREATE ROLE auth_migrator LOGIN PASSWORD 'test-only-migration-password'");
        root.execute("CREATE DATABASE auth_db OWNER auth_migrator");
        url=pg.getJdbcUrl().replace("/test","/auth_db");
        Flyway.configure().dataSource(url,"auth_migrator","test-only-migration-password").load().migrate();
        root=new JdbcTemplate(new DriverManagerDataSource(url,pg.getUsername(),pg.getPassword()));
        root.execute("GRANT USAGE ON SCHEMA public TO auth_app");
        source=new DriverManagerDataSource(url,"auth_app","test-only-password");
        jdbc=new JdbcTemplate(source); tx=new TransactionTemplate(new DataSourceTransactionManager(source)); barrier=new RevocationBarrier(source);
    }
    @AfterAll static void close() {pg.stop();}
    @BeforeEach void fixture() {
        var root=new JdbcTemplate(new DriverManagerDataSource(url,pg.getUsername(),pg.getPassword()));
        root.execute("TRUNCATE accounts,authority_write_reservations,authority_pending_revocations CASCADE");
        jdbc.update("INSERT INTO accounts VALUES(?,?,?,?,clock_timestamp()),(?,?,?,?,clock_timestamp())",actor,actor+"@example.invalid","hash","ACTIVE",target,target+"@example.invalid","hash","ACTIVE");
        jdbc.update("INSERT INTO account_role_grants VALUES(?,'ADMIN'),(?,'CUSTOMER')",actor,target);
        commands=new AccountMutationCommands(barrier,jdbc,tx,"ACTIVE");
    }
    Hk5JwtCodec.VerifiedPrincipal principal(UUID id,Account.Role role,Clock clock) {
        var repo=mock(AccountRepository.class);
        when(repo.findById(id)).thenReturn(Optional.of(new Account(id,id+"@example.invalid","hash","ACTIVE",Set.of(role),Instant.now())));
        var codec=new Hk5JwtCodec("https://test.invalid","test","test",Map.of("test",new SecretKeySpec(new byte[32],"HmacSHA256")),clock);
        return codec.verify(codec.issue(new CurrentAccountAuthority(repo,"ACTIVE").requireCurrentForIssue(id)));
    }
    AccountMutationCommands.Change change(UUID id) {
        long version=jdbc.queryForObject("SELECT version FROM account_authority_versions WHERE account_id=?",Long.class,target);
        return new AccountMutationCommands.Change(id,target,version,"ACTIVE",Set.of(Account.Role.CUSTOMER),"DISABLED",Set.of(Account.Role.CUSTOMER));
    }
    String status() {return jdbc.queryForObject("SELECT status FROM accounts WHERE id=?",String.class,target);}
    @Test void durableMutationAppliesAndReplaysWithoutDuplicateVersionAdvance() throws Exception {
        UUID id=UUID.randomUUID(); var principal=principal(actor,Account.Role.ADMIN,Clock.systemUTC()); var change=change(id);
        commands.enqueue(principal,change); assertTrue(commands.tick()); assertEquals("DISABLED",status());
        long version=jdbc.queryForObject("SELECT version FROM account_authority_versions WHERE account_id=?",Long.class,target);
        assertEquals(RevocationBarrier.State.APPLIED,commands.enqueue(principal,change).state()); assertFalse(commands.tick());
        assertEquals(version,jdbc.queryForObject("SELECT version FROM account_authority_versions WHERE account_id=?",Long.class,target));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM account_mutation_receipts",Long.class));
    }
    @Test void revokedCurrentActorAndUnsignedAdminClaimsCannotAuthorize() throws Exception {
        assertThrows(AccountMutationCommands.Denied.class,()->commands.enqueue(principal(actor,Account.Role.CUSTOMER,Clock.systemUTC()),change(UUID.randomUUID())));
        UUID id=UUID.randomUUID(); commands.enqueue(principal(actor,Account.Role.ADMIN,Clock.systemUTC()),change(id));
        jdbc.update("DELETE FROM account_role_grants WHERE account_id=? AND role='ADMIN'",actor);
        commands.tick(); assertEquals("ACTIVE",status()); assertEquals(RevocationBarrier.State.REJECTED,barrier.inspect(id).orElseThrow().state());
    }
    @Test void activeReservationBlocksMutationAndNewAdmissionsUntilRetry() throws Exception {
        UUID active=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO authority_write_reservations(request_id,binding_hash,epoch,state) VALUES(?,?,1,'ACTIVE')",active,"a".repeat(64));
        commands.enqueue(principal(actor,Account.Role.ADMIN,Clock.systemUTC()),change(id));
        assertThrows(RuntimeException.class,()->commands.tick()); assertEquals("ACTIVE",status());
        assertEquals(RevocationBarrier.State.PENDING,barrier.inspect(id).orElseThrow().state());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM account_mutation_receipts",Long.class));
        jdbc.update("UPDATE authority_write_reservations SET state='RELEASED',terminal_state='ABORTED' WHERE request_id=?",active);
        var reservations=new DurableAuthorityReservations(source,DurableAuthorityReservations.Participant.AUTH);
        var intent=new WriteIntent(UUID.randomUUID(),actor,UUID.randomUUID(),"test-token",WriteIntent.Mode.MANAGE,0,0,0,"c".repeat(64));
        assertEquals("FENCE_REVOCATION_PENDING",assertThrows(IllegalStateException.class,()->reservations.admit(intent,c->fail("Pending mutation must precede new admission"))).getMessage());
        jdbc.update("UPDATE authority_pending_revocations SET due_at=clock_timestamp() WHERE request_id=?",id);
        assertTrue(commands.tick()); assertEquals("DISABLED",status());
    }
    @Test void staleEvenAfterAbaAndChangedBindingAreRejected() throws Exception {
        UUID id=UUID.randomUUID(); var principal=principal(actor,Account.Role.ADMIN,Clock.systemUTC()); var change=change(id);
        commands.enqueue(principal,change);
        jdbc.update("UPDATE accounts SET status='DISABLED' WHERE id=?",target); jdbc.update("UPDATE accounts SET status='ACTIVE' WHERE id=?",target);
        commands.tick(); assertEquals("ACTIVE",status()); assertEquals(RevocationBarrier.State.REJECTED,barrier.inspect(id).orElseThrow().state());
        assertThrows(RevocationBarrier.Conflict.class,()->commands.enqueue(principal,change(id)));
    }
    @Test void databaseFailureRollsBackStatusRolesVersionAndReceipt() throws Exception {
        var root=new JdbcTemplate(new DriverManagerDataSource(url,pg.getUsername(),pg.getPassword()));
        UUID id=UUID.randomUUID(); var change=change(id); commands.enqueue(principal(actor,Account.Role.ADMIN,Clock.systemUTC()),change);
        root.execute("ALTER TABLE account_mutation_receipts ADD CONSTRAINT test_failure CHECK(false) NOT VALID");
        try {assertThrows(RuntimeException.class,()->commands.tick());}
        finally {root.execute("ALTER TABLE account_mutation_receipts DROP CONSTRAINT test_failure");}
        assertEquals("ACTIVE",status()); assertEquals(change.expectedVersion(),jdbc.queryForObject("SELECT version FROM account_authority_versions WHERE account_id=?",Long.class,target));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM account_mutation_receipts",Long.class));
    }
    @Test void expiredVerifiedAuthorizationRejectsWithoutStoringJwt() throws Exception {
        UUID id=UUID.randomUUID(); var old=Clock.offset(Clock.systemUTC(),Duration.ofHours(-1));
        commands.enqueue(principal(actor,Account.Role.ADMIN,old),change(id)); commands.tick();
        assertEquals("ACTIVE",status()); assertEquals(RevocationBarrier.State.REJECTED,barrier.inspect(id).orElseThrow().state());
        String payload=barrier.inspect(id).orElseThrow().payload(); assertFalse(payload.contains("password")); assertFalse(payload.contains("Bearer"));
    }
    @Test void staleClaimCannotMutateAfterLeaseReassignment() throws Exception {
        UUID id=UUID.randomUUID(); commands.enqueue(principal(actor,Account.Role.ADMIN,Clock.systemUTC()),change(id));
        var stale=barrier.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,id,Duration.ofSeconds(30)).orElseThrow();
        jdbc.update("UPDATE authority_pending_revocations SET lease_until=clock_timestamp()-interval '1 second' WHERE request_id=?",id);
        var current=barrier.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,id,Duration.ofSeconds(30)).orElseThrow();
        assertThrows(IllegalStateException.class,()->commands.executeClaim(stale)); assertEquals("ACTIVE",status());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM account_mutation_receipts",Long.class));
        commands.executeClaim(current); assertEquals("DISABLED",status()); assertTrue(barrier.complete(current));
    }
    @Test void versionTriggerDoesNotWriteToTemporaryShadowTables() {
        tx.executeWithoutResult(ignored->{
            long before=jdbc.queryForObject("SELECT version FROM public.account_authority_versions WHERE account_id=?",Long.class,target);
            jdbc.execute("CREATE TEMP TABLE account_authority_versions(account_id uuid PRIMARY KEY,version bigint)");
            try {
                jdbc.update("UPDATE accounts SET status='DISABLED' WHERE id=?",target);
                assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM pg_temp.account_authority_versions",Long.class));
                assertEquals(before+1,jdbc.queryForObject("SELECT version FROM public.account_authority_versions WHERE account_id=?",Long.class,target));
            } finally {jdbc.execute("DROP TABLE pg_temp.account_authority_versions");}
        });
    }
    @Test void approvedRoleSyncAlsoQueuesAndRetriesBehindActiveReservation() throws Exception {
        var proxy=new ProxyFactory(new SellerRoleSyncService(jdbc)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx.getTransactionManager(),new AnnotationTransactionAttributeSource()));
        var recovery=new SellerRoleRevocationRecovery(barrier,(SellerRoleSyncService)proxy.getProxy());
        UUID active=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO authority_write_reservations(request_id,binding_hash,epoch,state) VALUES(?,?,1,'ACTIVE')",active,"b".repeat(64));
        var event=new SellerRoleSyncService.Command(id,UUID.randomUUID(),target.toString(),SellerRoleSyncService.Status.APPROVED,1);
        assertThrows(RuntimeException.class,()->recovery.apply(event)); assertEquals(RevocationBarrier.Kind.ROLE_SYNC,barrier.inspect(id).orElseThrow().kind());
        jdbc.update("UPDATE authority_write_reservations SET state='RELEASED',terminal_state='ABORTED' WHERE request_id=?",active);
        jdbc.update("UPDATE authority_pending_revocations SET due_at=clock_timestamp() WHERE request_id=?",id);
        assertTrue(recovery.tick()); assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM account_role_grants WHERE account_id=? AND role='SELLER'",Long.class,target));
    }
}
