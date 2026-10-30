package com.projecthk5.productservice.authority;

import static org.junit.jupiter.api.Assertions.*;
import com.projecthk5.security.fencing.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class MutationPathFencePostgresTest {
    ReservationRpcPostgresTest fixture;
    JdbcTemplate sellerWriter;
    @BeforeAll static void start() throws Exception {
        ReservationRecoveryPostgresTest.start();
        var db=ReservationRpcPostgresTest.sellerDb;
        db.execute("CREATE ROLE mutation_path_writer LOGIN PASSWORD '"+ReservationRpcPostgresTest.password+"'");
        db.execute("GRANT CONNECT ON DATABASE seller_db TO mutation_path_writer");
        db.execute("GRANT USAGE ON SCHEMA public TO mutation_path_writer");
        db.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON seller_profiles,stores TO mutation_path_writer");
        db.execute("GRANT SELECT ON authority_write_reservations,authority_fence_clock TO mutation_path_writer");
    }
    @AfterAll static void stop(){ReservationRecoveryPostgresTest.stop();}
    @BeforeEach void seed() throws Exception {
        var recovery=new ReservationRecoveryPostgresTest();recovery.seed();fixture=recovery.fixture;
        sellerWriter=ReservationRpcPostgresTest.db(ReservationRpcPostgresTest.url("seller"),"mutation_path_writer");
    }
    @Test void activeHttpReservationsBlockAllExistingAccountRoleAndStatusDml() {
        fixture.authClient().admit(fixture.intent);
        var db=ReservationRpcPostgresTest.authMutation;
        for(String sql:List.of("UPDATE accounts SET status='DISABLED' WHERE id=?",
                "DELETE FROM account_role_grants WHERE account_id=?",
                "INSERT INTO account_role_grants(account_id,role) VALUES(?,'ADMIN')"))
            assertBlocked(()->db.update(sql,ReservationRpcPostgresTest.owner));
        for(String sql:List.of("DELETE FROM accounts WHERE id=?","UPDATE account_role_grants SET role='ADMIN' WHERE account_id=?")) {
            var denied=assertThrows(DataAccessException.class,()->db.update(sql,ReservationRpcPostgresTest.owner));
            assertEquals("42501",assertInstanceOf(SQLException.class,denied.getMostSpecificCause()).getSQLState());
            assertBlocked(()->ReservationRpcPostgresTest.authDb.update(sql,ReservationRpcPostgresTest.owner));
        }
        assertEquals("ACTIVE",db.queryForObject("SELECT status FROM accounts WHERE id=?",String.class,ReservationRpcPostgresTest.owner));
        assertEquals(List.of("SELLER"),db.queryForList("SELECT role FROM account_role_grants WHERE account_id=?",String.class,ReservationRpcPostgresTest.owner));
    }
    @Test void activeHttpReservationsBlockSellerAndStoreStatusDeleteAndRevision() {
        fixture.authClient().admit(fixture.intent);fixture.sellerClient().admit(fixture.intent);
        assertBlocked(()->sellerWriter.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='test',version=version+1 WHERE id=?",ReservationRpcPostgresTest.profile));
        assertBlocked(()->sellerWriter.update("DELETE FROM seller_profiles WHERE id=?",ReservationRpcPostgresTest.profile));
        assertBlocked(()->sellerWriter.update("UPDATE stores SET status='CLOSED',version=version+1 WHERE id=?",ReservationRpcPostgresTest.store));
        assertBlocked(()->sellerWriter.update("UPDATE stores SET name='changed',version=version+1 WHERE id=?",ReservationRpcPostgresTest.store));
        assertBlocked(()->sellerWriter.update("DELETE FROM stores WHERE id=?",ReservationRpcPostgresTest.store));
        assertEquals("OPEN",sellerWriter.queryForObject("SELECT status FROM stores WHERE id=?",String.class,ReservationRpcPostgresTest.store));
    }
    @Test void typedPriorityTicketSurvivesNewInstanceAndCannotBeTakenByManualOrRoleSyncWorker() throws Exception {
        var source=ReservationRpcPostgresTest.authMutation.getDataSource();var barrier=new RevocationBarrier(source);
        UUID request=UUID.randomUUID();var ticket=barrier.enqueueMutation(request,"a".repeat(64),"test-no-authority",RevocationBarrier.Kind.AUTH_MUTATION);
        ReservationRpcPostgresTest.failStatus(503,()->fixture.authClient().admit(fixture.intent));
        assertTrue(barrier.claim(Duration.ofSeconds(10)).isEmpty());assertTrue(barrier.claimRoleSync(Duration.ofSeconds(10)).isEmpty());
        var restarted=new RevocationBarrier(source);
        assertEquals(ticket,restarted.inspect(request).orElseThrow());
        var claim=restarted.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,request,Duration.ofSeconds(10)).orElseThrow();
        assertTrue(restarted.reject(claim,"TEST_NO_MUTATION"));
        assertNotNull(fixture.authClient().admit(fixture.intent));
    }
    @Test void killedClaimantLeavesDurableBarrierAndRestartCannotAdmitUntilExplicitOutcome() throws Exception {
        UUID request=UUID.randomUUID();var properties=new Properties();
        properties.setProperty("url",ReservationRpcPostgresTest.url("auth"));properties.setProperty("password",ReservationRpcPostgresTest.password);properties.setProperty("request",request.toString());
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",ReservationProcessCrashPostgresTest.childClasspath(),MutationPathFencePostgresTest.class.getName()).redirectErrorStream(true).start();
        try(var child=new ReservationProcessCrashPostgresTest.Child(process)) {
            try(var input=process.getOutputStream()){properties.store(input,null);}
            child.await("CRASH_POINT");child.kill();
        }
        Thread.sleep(150);
        var restarted=new RevocationBarrier(ReservationRpcPostgresTest.authMutation.getDataSource());
        assertEquals(RevocationBarrier.State.LEASED,restarted.inspect(request).orElseThrow().state());
        ReservationRpcPostgresTest.failStatus(503,()->fixture.authClient().admit(fixture.intent));
        var claim=restarted.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,request,Duration.ofSeconds(10)).orElseThrow();
        assertEquals(2,claim.ticket().attempts());assertTrue(restarted.reject(claim,"TEST_NO_MUTATION"));
        assertNotNull(fixture.authClient().admit(fixture.intent));
    }
    public static void main(String[] args) {
        try {
            var properties=new Properties();properties.load(System.in);
            var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(properties.getProperty("url"),"auth_app",properties.getProperty("password"));
            var barrier=new RevocationBarrier(source);UUID request=UUID.fromString(properties.getProperty("request"));
            barrier.enqueueMutation(request,"a".repeat(64),"test-no-authority",RevocationBarrier.Kind.AUTH_MUTATION);
            barrier.claimMutation(RevocationBarrier.Kind.AUTH_MUTATION,request,Duration.ofMillis(100)).orElseThrow();
            ReservationProcessCrashPostgresTest.park();
        } catch(Throwable failure){System.out.println("CHILD_FAILURE "+failure.getClass().getName());System.out.flush();System.exit(2);}
    }
    private static void assertBlocked(Runnable action) {
        var failure=assertThrows(DataAccessException.class,action::run);
        assertEquals("55P03",assertInstanceOf(SQLException.class,failure.getMostSpecificCause()).getSQLState());
    }
}
