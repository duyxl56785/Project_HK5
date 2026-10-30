package com.projecthk5.sellerservice.mutation;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.security.fencing.RevocationBarrier;
import com.projecthk5.security.fencing.RevocationBarrier.*;
import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.store.*;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Local DB protocol proof with explicit test-only policy; never an Auth/Seller live fencing proof. */
@SpringBootTest
@Testcontainers
@Tag("postgresql")
class SellerMutationQueuePostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE seller_mutation LOGIN PASSWORD 'local-fixture-only' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            statement.execute("CREATE ROLE seller_mutation_worker NOLOGIN");
            statement.execute("GRANT seller_mutation_worker TO seller_mutation");
            statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../../docs/tv1/sql/mutation-seller-grants.sql")));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "seller_mutation");
        properties.add("spring.datasource.password", () -> "local-fixture-only");
        properties.add("spring.flyway.enabled", () -> false);
    }
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired SellerProfileRepository sellers;
    @Autowired StoreRepository stores;
    @Autowired JdbcTemplate jdbc;
    SellerMutationQueue queue;
    RevocationBarrier barrier;
    UUID seller;
    UUID store;
    final SellerAccessPolicy localPolicy = new SellerAccessPolicy() {
        public void requireAuthenticated(SellerIdentity actor) {
            assertTrue(actor.reference().equals("admin") || actor.reference().equals("owner"));
        }
        public void requireSellerModerationPermission(SellerIdentity actor) {
            assertEquals("admin", actor.reference());
        }
    };

    @BeforeEach void setup() {
        assertEquals("seller_mutation", jdbc.queryForObject("SELECT current_user", String.class));
        assertEquals(false, jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class));
        admin().update("DELETE FROM seller_mutation_receipts");
        admin().update("DELETE FROM authority_pending_revocations");
        admin().update("DELETE FROM authority_write_reservations");
        admin().update("DELETE FROM stores");
        admin().update("DELETE FROM seller_profiles");
        seller = UUID.randomUUID(); store = UUID.randomUUID();
        jdbc.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,?,'APPROVED',0)", seller, "owner");
        jdbc.update("INSERT INTO stores(id,seller_id,name,slug,status,version,created_at,updated_at) VALUES(?,?,'Shop','shop','OPEN',0,now(),now())", store, seller);
        queue = new SellerMutationQueue(source, manager, em, sellers, stores);
        barrier = new RevocationBarrier(source);
    }

    SellerMutationQueue.Command suspend(long version) {
        return new SellerMutationQueue.Command(SellerMutationQueue.Operation.SUSPEND, seller, version, "admin", "local proof", null);
    }
    JdbcTemplate admin() {return new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));}
    Claim claim(UUID id) throws Exception {
        return barrier.claimMutation(Kind.SELLER_MUTATION, id, Duration.ofSeconds(30)).orElseThrow();
    }
    StoreAccessPolicy storePolicy() {
        return actor -> new SellerPersistenceService(sellers, localPolicy).findOwn(actor);
    }

    @Test void activeReservationRetainsPendingMutationAndPreventsDataAndReceiptWrites() throws Exception {
        UUID id = UUID.randomUUID();
        queue.enqueue(id, suspend(0));
        admin().update("INSERT INTO authority_write_reservations(request_id,binding_hash,epoch,state) VALUES(?, ?,1,'ACTIVE')", UUID.randomUUID(), "a".repeat(64));
        Claim claimed = claim(id);
        assertThrows(RuntimeException.class, () -> queue.applyLocal(claimed, localPolicy, storePolicy()));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM seller_profiles WHERE id=?", String.class, seller));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM seller_mutation_receipts", Integer.class));
        assertEquals(State.LEASED, queue.inspect(id).orElseThrow().state());
        // Simulate genuine reservation terminal release; no timeout or mutation worker force-unlock.
        admin().update("UPDATE authority_write_reservations SET state='RELEASED',terminal_state='ABORTED'");
        assertEquals("SUSPENDED", queue.applyLocal(claimed, localPolicy, storePolicy()).state());
        assertEquals(State.APPLIED, queue.inspect(id).orElseThrow().state());
    }

    @Test void crashAfterCommitBeforeAckReplaysReceiptWithoutSecondVersionOrOutbox() throws Exception {
        UUID id = UUID.randomUUID(); queue.enqueue(id, suspend(0));
        Claim first = claim(id);
        var committed = queue.applyLocalTransaction(first, localPolicy, storePolicy());
        assertEquals(State.LEASED, queue.inspect(id).orElseThrow().state());
        jdbc.update("UPDATE authority_pending_revocations SET lease_until=clock_timestamp()-interval '1 second' WHERE request_id=?", id);
        Claim recovered = claim(id);
        assertNotEquals(first.token(), recovered.token());
        assertEquals(committed, queue.applyLocal(recovered, localPolicy, storePolicy()));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM seller_profiles WHERE id=?", Long.class, seller));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM seller_status_outbox WHERE seller_id=?", Integer.class, seller));
        assertEquals(State.APPLIED, queue.inspect(id).orElseThrow().state());
    }

    @Test void identityConflictAndStaleVersionCannotApplyOrCreateReceipt() throws Exception {
        UUID id = UUID.randomUUID(); queue.enqueue(id, suspend(1));
        assertThrows(RevocationBarrier.Conflict.class, () -> queue.enqueue(id, suspend(0)));
        assertThrows(SellerPersistenceException.class, () -> queue.applyLocal(claim(id), localPolicy, storePolicy()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM seller_mutation_receipts", Integer.class));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM seller_profiles WHERE id=?", String.class, seller));
    }

    @Test void storeCloseUsesLifecycleAndActiveReservationGuardsDirectOwnershipSql() throws Exception {
        UUID id = UUID.randomUUID();
        queue.enqueue(id, new SellerMutationQueue.Command(SellerMutationQueue.Operation.SET_OPEN, store, 0, "owner", null, false));
        assertEquals("CLOSED", queue.applyLocal(claim(id), localPolicy, storePolicy()).state());
        assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM stores WHERE id=?", String.class, store));
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO seller_profiles(id,owner_reference,status,version) VALUES(?,'other','APPROVED',0)", other);
        admin().update("INSERT INTO authority_write_reservations(request_id,binding_hash,epoch,state) VALUES(?, ?,1,'ACTIVE')", UUID.randomUUID(), "a".repeat(64));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE seller_profiles SET owner_reference='replacement' WHERE id=?", seller));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE stores SET seller_id=? WHERE id=?", other, store));
        assertEquals("owner", jdbc.queryForObject("SELECT owner_reference FROM seller_profiles WHERE id=?", String.class, seller));
        assertEquals(seller, jdbc.queryForObject("SELECT seller_id FROM stores WHERE id=?", UUID.class, store));
        assertThrows(IllegalArgumentException.class, () -> SellerMutationQueue.Operation.valueOf("TRANSFER"));
    }
    @Test void mutationWorkerCannotReleaseReservationsOrRewriteReceipts() {
        for(String sql:java.util.List.of("UPDATE authority_write_reservations SET state='RELEASED',terminal_state='ABORTED'",
                "DELETE FROM seller_mutation_receipts","UPDATE authority_fence_clock SET epoch=epoch+1")) {
            var failure=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.execute(sql));
            assertEquals("42501",assertInstanceOf(java.sql.SQLException.class,failure.getMostSpecificCause()).getSQLState());
        }
    }
}
