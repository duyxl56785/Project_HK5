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
        assertEquals(1, journal.claimForSend(intent.operationId()));
        journal.unknown(intent.operationId(),1,"TRANSPORT_OUTCOME_UNKNOWN");
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
        assertEquals(2, journal.claimForSend(intent.operationId())); // same ID, new send generation
        journal.confirm(intent.operationId(),2);
        var t2=journal.load(intent.operationId());
        assertEquals(InventoryOperationJournal.State.CONFIRMED,t2.state());
        assertEquals(2,t2.sends());
        assertNull(journal.claimForSend(intent.operationId()));
        assertThrows(IllegalStateException.class,()->journal.unknown(intent.operationId(),1,"STALE"));
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
        assertEquals(1, journal.claimForSend(intent.operationId()));
        new TransactionTemplate(txManager).execute(status -> {
            journal.confirm(intent.operationId(),1); // Independent T2 commit, even if outer transaction rolls back.
            status.setRollbackOnly();
            return null;
        });
        assertEquals(InventoryOperationJournal.State.CONFIRMED, journal.load(intent.operationId()).state());
        assertEquals(1, journal.load(intent.operationId()).sends());
    }

    @Test void lateT2AfterCrashScannerMustNotOverwriteUnknown() {
        var intent = freshIntent();
        journal.prepare(intent);
        assertEquals(1, journal.claimForSend(intent.operationId()));
        db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",
                intent.operationId());
        assertEquals(1, journal.flagStaleInFlight(Duration.ofMinutes(10)));
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId(),1));
        assertEquals(InventoryOperationJournal.State.UNKNOWN, journal.load(intent.operationId()).state());
        assertEquals(1, journal.load(intent.operationId()).sends());
    }


    @Test void staleResponseFromFirstSendCannotMutateReclaimedSecondSend() {
        var intent = freshIntent();
        journal.prepare(intent);
        int first = journal.claimForSend(intent.operationId());
        assertEquals(1, first);
        // Simulate sender A losing contact and reconciliation marking its claim stale.
        db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",
                intent.operationId());
        assertEquals(1, journal.flagStaleInFlight(Duration.ofMinutes(10)));
        int second = journal.claimForSend(intent.operationId());
        assertEquals(2, second);
        // Sender A returns late while sender B is working the same persisted operationId.
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId(), first));
        assertThrows(IllegalStateException.class, () -> journal.reject(intent.operationId(), first, "OLD_REJECTION"));
        assertThrows(IllegalStateException.class, () -> journal.unknown(intent.operationId(), first, "OLD_TIMEOUT"));
        assertThrows(IllegalStateException.class, () -> journal.notSent(intent.operationId(), first, "OLD_NOT_SENT"));
        var stillOwnedByB = journal.load(intent.operationId());
        assertEquals(InventoryOperationJournal.State.IN_FLIGHT, stillOwnedByB.state());
        assertEquals(second, stillOwnedByB.sends());
        assertNull(stillOwnedByB.errorCode());
        journal.confirm(intent.operationId(), second);
        assertEquals(InventoryOperationJournal.State.CONFIRMED, journal.load(intent.operationId()).state());
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId(), first));
        assertNull(journal.claimForSend(intent.operationId()));
    }

    @Test void completedOrUnknownAttemptCannotReuseOldGeneration() {
        var intent = freshIntent();
        journal.prepare(intent);
        int first = journal.claimForSend(intent.operationId());
        journal.unknown(intent.operationId(), first, "TIMEOUT");
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId(), first));
        int second = journal.claimForSend(intent.operationId());
        assertEquals(first + 1, second);
        journal.reject(intent.operationId(), second, "DEFINITE_NONCOMMIT");
        assertThrows(IllegalStateException.class, () -> journal.confirm(intent.operationId(), second));
        assertEquals(InventoryOperationJournal.State.REJECTED, journal.load(intent.operationId()).state());
    }

    @Test void multiSkuReleaseCandidateRetainsExactLongQuantitiesAndIndependentOperationId() {
        // Journal/storage test only: preparing a RELEASE does NOT prove authorization,
        // physical held stock, current SellerOrder state or a live Inventory mutation.
        var committedAttempt = freshIntent();
        var lines = List.of(
                new Line("SKU-Z", 3L),
                new Line("SKU-A", 2_147_483_648L),
                new Line("SKU-M", 17L));
        var reserve = InventoryOperationIntent.prepare(committedAttempt.checkoutAttemptId(),
                "reserve-multi-" + UUID.randomUUID(), InventoryOperationIntent.Type.RESERVE, lines);
        var release = InventoryOperationIntent.prepare(committedAttempt.checkoutAttemptId(),
                "release-multi-" + UUID.randomUUID(), InventoryOperationIntent.Type.RELEASE, lines);
        journal.prepare(reserve);
        var reserveClaim = journal.claimForSend(reserve.operationId());
        assertEquals(1, reserveClaim);
        journal.confirm(reserve.operationId(), reserveClaim);
        journal.prepare(release);
        var storedReserve = journal.load(reserve.operationId());
        var storedRelease = journal.load(release.operationId());
        var ordered = List.of(new Line("SKU-A", 2_147_483_648L),
                new Line("SKU-M", 17L), new Line("SKU-Z", 3L));
        assertEquals(ordered, storedReserve.intent().lines());
        assertEquals(ordered, storedRelease.intent().lines());
        assertEquals(InventoryOperationIntent.Type.RESERVE, storedReserve.intent().type());
        assertEquals(InventoryOperationIntent.Type.RELEASE, storedRelease.intent().type());
        assertNotEquals(storedReserve.intent().operationId(), storedRelease.intent().operationId());
        assertNotEquals(storedReserve.intent().fingerprint(), storedRelease.intent().fingerprint());
        assertEquals(InventoryOperationJournal.State.CONFIRMED, storedReserve.state());
        assertEquals(InventoryOperationJournal.State.PENDING, storedRelease.state());
        journal.prepare(release); // exactly the same RELEASE operationId + immutable lines is replay-safe
        assertEquals(1, db.queryForObject(
                "select count(*) from inventory_operation_journal where operation_id=?",
                Integer.class, release.operationId()));
        assertThrows(IllegalArgumentException.class, () -> journal.prepare(
                InventoryOperationIntent.prepare(release.checkoutAttemptId(), release.operationId(),
                        InventoryOperationIntent.Type.RELEASE, List.of(
                                new Line("SKU-A", 2_147_483_648L),
                                new Line("SKU-M", 18L), new Line("SKU-Z", 3L)))));
        assertEquals(ordered, journal.load(release.operationId()).intent().lines());
    }

    @Test void unknownMultiSkuReservePreservesStableIdAndNeverCreatesImplicitRelease() {
        var committedAttempt = freshIntent();
        var reserve = InventoryOperationIntent.prepare(committedAttempt.checkoutAttemptId(),
                "unknown-multi-" + UUID.randomUUID(), InventoryOperationIntent.Type.RESERVE,
                List.of(new Line("SKU-C", 5), new Line("SKU-A", 11), new Line("SKU-B", 2)));
        journal.prepare(reserve);
        Integer first = journal.claimForSend(reserve.operationId());
        assertEquals(1, first);
        journal.unknown(reserve.operationId(), first, "TRANSPORT_OUTCOME_UNKNOWN");
        var unknown = journal.load(reserve.operationId());
        assertEquals(InventoryOperationJournal.State.UNKNOWN, unknown.state());
        assertEquals(reserve.operationId(), unknown.intent().operationId());
        assertEquals(reserve.fingerprint(), unknown.intent().fingerprint());
        assertEquals(reserve.lines(), unknown.intent().lines());
        // No RELEASE or replacement RESERVE side effect is inferred from UNKNOWN.
        assertEquals(1, db.queryForObject(
                "select count(*) from inventory_operation_journal where checkout_attempt_id=?",
                Integer.class, committedAttempt.checkoutAttemptId()));
        Integer second = journal.claimForSend(reserve.operationId());
        assertEquals(2, second);
        journal.unknown(reserve.operationId(), second, "SECOND_RESPONSE_MISSING");
        assertEquals(1, db.queryForObject(
                "select count(*) from inventory_operation_journal where checkout_attempt_id=?",
                Integer.class, committedAttempt.checkoutAttemptId()));
        assertEquals(reserve.operationId(), journal.load(reserve.operationId()).intent().operationId());
        assertEquals(reserve.lines(), journal.load(reserve.operationId()).intent().lines());
        assertEquals(2, journal.load(reserve.operationId()).sends());
    }


    @Test void multiSkuUnknownIntentRejectsSameIdDifferentTypeOrCheckoutAttempt() {
        var existingAttempt = freshIntent();
        var lines = List.of(new Line("SKU-C", 1L), new Line("SKU-A", 3L), new Line("SKU-B", 7L));
        var reserve = InventoryOperationIntent.prepare(existingAttempt.checkoutAttemptId(),
                "multi-conflict-" + UUID.randomUUID(), InventoryOperationIntent.Type.RESERVE, lines);
        journal.prepare(reserve);
        int first = journal.claimForSend(reserve.operationId());
        assertEquals(1, first);
        journal.unknown(reserve.operationId(), first, "TRANSPORT_OUTCOME_UNKNOWN");

        var wrongType = InventoryOperationIntent.prepare(existingAttempt.checkoutAttemptId(),
                reserve.operationId(), InventoryOperationIntent.Type.RELEASE, lines);
        assertThrows(IllegalArgumentException.class, () -> journal.prepare(wrongType));

        var differentAttempt = freshIntent();
        var wrongOwner = InventoryOperationIntent.prepare(differentAttempt.checkoutAttemptId(),
                reserve.operationId(), InventoryOperationIntent.Type.RESERVE, lines);
        assertThrows(IllegalArgumentException.class, () -> journal.prepare(wrongOwner));

        var stored = journal.load(reserve.operationId());
        assertEquals(InventoryOperationJournal.State.UNKNOWN, stored.state());
        assertEquals(reserve.checkoutAttemptId(), stored.intent().checkoutAttemptId());
        assertEquals(InventoryOperationIntent.Type.RESERVE, stored.intent().type());
        assertEquals(reserve.fingerprint(), stored.intent().fingerprint());
        assertEquals(reserve.lines(), stored.intent().lines());
        assertEquals(1, db.queryForObject(
                "select count(*) from inventory_operation_journal where operation_id=?",
                Integer.class, reserve.operationId()));
    }

    @Test void twoWorkersReclaimOneUnknownMultiSkuOperationOnlyOnce() throws Exception {
        var committedAttempt = freshIntent();
        var intent = InventoryOperationIntent.prepare(committedAttempt.checkoutAttemptId(),
                "multi-reclaim-" + UUID.randomUUID(), InventoryOperationIntent.Type.RESERVE,
                List.of(new Line("SKU-C", 5L), new Line("SKU-A", 11L), new Line("SKU-B", 2L)));
        journal.prepare(intent);
        int first = journal.claimForSend(intent.operationId());
        assertEquals(1, first);
        journal.unknown(intent.operationId(), first, "TIMEOUT");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> { go.await(); return journal.claimForSend(intent.operationId()); };
            Future<Integer> a = pool.submit(task), b = pool.submit(task);
            go.countDown();
            Integer aa = a.get(30, TimeUnit.SECONDS), bb = b.get(30, TimeUnit.SECONDS);
            assertTrue((Integer.valueOf(2).equals(aa) && bb == null)
                    || (Integer.valueOf(2).equals(bb) && aa == null));
            var claimed = journal.load(intent.operationId());
            assertEquals(InventoryOperationJournal.State.IN_FLIGHT, claimed.state());
            assertEquals(2, claimed.sends());
            assertEquals(intent.fingerprint(), claimed.intent().fingerprint());
            assertEquals(intent.lines(), claimed.intent().lines());
            journal.confirm(intent.operationId(), 2);
            assertEquals(InventoryOperationJournal.State.CONFIRMED, journal.load(intent.operationId()).state());
            assertNull(journal.claimForSend(intent.operationId()));
        } finally {
            pool.shutdownNow();
        }
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
            Callable<Integer> send=()->{go.await();return journal.claimForSend(intent.operationId());};
            Future<Integer> first=pool.submit(send),second=pool.submit(send);
            go.countDown();
            Integer firstAttempt=first.get(30,TimeUnit.SECONDS);
            Integer secondAttempt=second.get(30,TimeUnit.SECONDS);
            assertTrue((Integer.valueOf(1).equals(firstAttempt) && secondAttempt==null)
                    || (Integer.valueOf(1).equals(secondAttempt) && firstAttempt==null));
            assertEquals(1,journal.load(intent.operationId()).sends());
            assertEquals(InventoryOperationJournal.State.IN_FLIGHT,journal.load(intent.operationId()).state());
        } finally {pool.shutdownNow();}
    }

    @Test void crashedInFlightIsUnknownAndNeverAutomaticallyCompensated() {
        var intent=freshIntent();
        journal.prepare(intent);
        assertEquals(1, journal.claimForSend(intent.operationId()));
        db.update("update inventory_operation_journal set updated_at=now()-interval '20 minutes' where operation_id=?",
                intent.operationId());
        assertEquals(1,journal.flagStaleInFlight(Duration.ofMinutes(10)));
        assertEquals(InventoryOperationJournal.State.UNKNOWN,journal.load(intent.operationId()).state());
        assertEquals(1,db.queryForObject("select count(*) from inventory_operation_journal where operation_id=?",
                Integer.class,intent.operationId()));
    }
}
