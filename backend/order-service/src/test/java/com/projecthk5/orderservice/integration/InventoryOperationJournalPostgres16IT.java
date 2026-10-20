package com.projecthk5.orderservice.integration;

import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.domain.InventoryOperationIntent.Line;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import com.projecthk5.orderservice.service.InventoryOperationJournal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {"app.checkout.reconcile-scan-ms=86400000",
                               "app.inventory.journal-scan-ms=86400000"})
class InventoryOperationJournalPostgres16IT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("app.security.issuer",()->"https://test-issuer.example.invalid");
        r.add("app.security.audience",()->"ecommerce");
        r.add("app.security.current-kid",()->"test-kid");
        r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired CheckoutAttemptRepository attempts;
    @Autowired InventoryOperationJournal journal;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager txManager;

    private InventoryOperationIntent freshIntent() {
        CheckoutAttempt attempt=attempts.saveAndFlush(new CheckoutAttempt(
                "journal-"+UUID.randomUUID(), "key-"+UUID.randomUUID(), "test-fingerprint"));
        return InventoryOperationIntent.prepare(attempt.getId(),"reserve-"+UUID.randomUUID(),
                InventoryOperationIntent.Type.RESERVE,
                List.of(new Line("sku-b",1),new Line("sku-a",2)));
    }

    @Test void flywayT1PersistsImmutableCanonicalPayloadAndSeparateT2Outcome() {
        var intent=freshIntent();
        journal.prepare(intent);
        var t1=journal.load(intent.operationId());
        assertEquals(InventoryOperationJournal.State.PENDING,t1.state());
        assertEquals(List.of(new Line("sku-a",2),new Line("sku-b",1)),t1.intent().lines());
        assertEquals(intent.fingerprint(),t1.intent().fingerprint());
        journal.prepare(intent); // Exact step replay retains state and one DB row.
        assertEquals(1,db.queryForObject(
                "select count(*) from inventory_operation_journal where operation_id=?",Integer.class,intent.operationId()));
        assertTrue(journal.claimForSend(intent.operationId()));
        journal.unknown(intent.operationId(),"TRANSPORT_OUTCOME_UNKNOWN");
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
        assertTrue(journal.claimForSend(intent.operationId())); // same ID, no second INSERT
        journal.confirm(intent.operationId());
        var t2=journal.load(intent.operationId());
        assertEquals(InventoryOperationJournal.State.CONFIRMED,t2.state());
        assertEquals(2,t2.sends());
        assertFalse(journal.claimForSend(intent.operationId()));
        assertThrows(IllegalStateException.class,()->journal.unknown(intent.operationId(),"STALE"));
    }

    @Test void t1JournalCommitSurvivesRollbackOfAlreadyCommittedAttemptCaller() {
        var intent = freshIntent(); // CheckoutAttempt must be committed before journal FK insert.
        new TransactionTemplate(txManager).execute(status -> {
            journal.prepare(intent); // REQUIRES_NEW commits while caller transaction is suspended.
            assertEquals(InventoryOperationJournal.State.PENDING, journal.load(intent.operationId()).state());
            status.setRollbackOnly();
            return null;
        });
        assertEquals(InventoryOperationJournal.State.PENDING, journal.load(intent.operationId()).state());
        assertEquals(1, db.queryForObject("select count(*) from inventory_operation_journal where operation_id=?",
                Integer.class, intent.operationId()));
    }

    @Test void t2ConfirmedOutcomeSurvivesCallerTransactionRollback() {
        var intent = freshIntent();
        journal.prepare(intent);
        assertTrue(journal.claimForSend(intent.operationId()));
        new TransactionTemplate(txManager).execute(status -> {
            journal.confirm(intent.operationId()); // Independent T2 commit, even if outer transaction rolls back.
            status.setRollbackOnly();
            return null;
        });
        assertEquals(InventoryOperationJournal.State.CONFIRMED, journal.load(intent.operationId()).state());
        assertEquals(1, journal.load(intent.operationId()).sends());
    }

    @Test void lateT2AfterCrashScannerMustNotOverwriteUnknown() {
        var intent = freshIntent();
        journal.prepare(intent);
        assertTrue(journal.claimForSend(intent.operationId()));
        db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",
                intent.operationId());
        assertEquals(1, journal.flagStaleInFlight(Duration.ofMinutes(10)));
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId()));
        assertEquals(InventoryOperationJournal.State.UNKNOWN, journal.load(intent.operationId()).state());
        assertEquals(1, journal.load(intent.operationId()).sends());
    }

    @Test void differentPayloadOnSameOperationIdIsRejectedWithoutMutation() {
        var intent=freshIntent();
        journal.prepare(intent);
        var changed=InventoryOperationIntent.prepare(intent.checkoutAttemptId(),intent.operationId(),
                InventoryOperationIntent.Type.RESERVE,List.of(new Line("sku-a",3),new Line("sku-b",1)));
        assertThrows(IllegalArgumentException.class,()->journal.prepare(changed));
        assertEquals(intent.fingerprint(),journal.load(intent.operationId()).intent().fingerprint());
    }

    @Test void twoWorkersClaimOneJournalRowOnlyOnce() throws Exception {
        var intent=freshIntent();
        journal.prepare(intent);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch go=new CountDownLatch(1);
        try {
            Callable<Boolean> send=()->{go.await();return journal.claimForSend(intent.operationId());};
            Future<Boolean> first=pool.submit(send),second=pool.submit(send);
            go.countDown();
            assertNotEquals(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,journal.load(intent.operationId()).sends());
            assertEquals(InventoryOperationJournal.State.IN_FLIGHT,journal.load(intent.operationId()).state());
        } finally {pool.shutdownNow();}
    }

    @Test void crashedInFlightIsUnknownAndNeverAutomaticallyCompensated() {
        var intent=freshIntent();
        journal.prepare(intent);
        assertTrue(journal.claimForSend(intent.operationId()));
        db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",
                intent.operationId());
        assertEquals(1,journal.flagStaleInFlight(Duration.ofMinutes(10)));
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
        assertEquals(1,db.queryForObject("select count(*) from inventory_operation_journal where operation_id=?",
                Integer.class,intent.operationId()));
    }
}
