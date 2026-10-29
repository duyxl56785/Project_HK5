package com.projecthk5.sellerservice.seller;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.SellerAccessPolicy;
import com.projecthk5.sellerservice.http.SellerStoreResponses;
import com.projecthk5.sellerservice.seller.service.SellerInputs;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceException;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(SellerPersistenceIntegrationTest.Wiring.class)
@Testcontainers
@Tag("postgresql")
class SellerPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static final SellerIdentity OWNER = new SellerIdentity("d1f87152-b93c-4eab-b1af-cc9dab67ff60");
    private static final SellerIdentity ADMIN = new SellerIdentity("a74c9f53-c66c-4247-85a3-fc7387f06543");
    private static final SellerIdentity OTHER = new SellerIdentity("173eb068-238b-4f6b-ae7d-875502b8bf27");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "ApplicationName=seller-persistence-test");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
    }

    @Autowired private SellerPersistenceService service;
    @Autowired private SellerProfileRepository repository;
    @Autowired private FixturePolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM seller_status_outbox");
        jdbc.update("DELETE FROM seller_profiles");
        policy.known.clear();
        policy.known.addAll(Set.of(OWNER.reference(), ADMIN.reference(), OTHER.reference()));
    }

    @Test
    void privateResponseMappingFollowsGuardedOwnReadAndDoesNotGrantModeration() {
        PersistedSeller first = service.register(OWNER);
        PersistedSeller second = service.register(OTHER);
        var own = SellerStoreResponses.sellerPrivate(service.findOwn(OWNER));
        var otherOwn = SellerStoreResponses.sellerPrivate(service.findOwn(OTHER));
        assertEquals(first.id(), own.id());
        assertEquals(second.id(), otherOwn.id());
        assertNotEquals(own.id(), otherOwn.id());
        assertThrows(SellerRuleException.class, () -> SellerStoreResponses.sellerPrivate(
                service.findForModeration(second.id(), OWNER)));
        policy.known.remove(OWNER.reference());
        assertThrows(SellerRuleException.class, () -> SellerStoreResponses.sellerPrivate(service.findOwn(OWNER)));
        // Fixture policy plus real DB verifies ordering/isolation, not production JWT or current ADMIN.
    }

    @Test
    void typedCommandsPreserveServerIdentityVersionPolicyAndRequiredRollback() {
        PersistedSeller created = service.register(OWNER);
        var rejected = service.reject(created.id(), new SellerInputs.Moderation(created.version(), "  Revise  "), ADMIN);
        assertEquals("Revise", rejected.application().reviewReason());
        var resubmitted = service.resubmit(created.id(), new SellerInputs.Version(rejected.version()), OWNER);
        assertEquals(created.id(), resubmitted.id());
        assertEquals(OWNER, resubmitted.application().owner());
        assertNull(resubmitted.application().reviewReason());
        assertCode(SellerPersistenceException.Code.SELLER_VERSION_CONFLICT,
                assertThrows(SellerPersistenceException.class,
                        () -> service.approve(created.id(), new SellerInputs.Version(created.version()), ADMIN)));
        var approved = service.approve(created.id(), new SellerInputs.Version(resubmitted.version()), ADMIN);
        assertThrows(SellerRuleException.class,
                () -> service.suspend(created.id(), new SellerInputs.Moderation(approved.version(), "No"), OTHER));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.suspend(created.id(), new SellerInputs.Moderation(approved.version(), "Hold"), ADMIN);
            tx.setRollbackOnly();
        });
        assertEquals(approved, service.findOwn(OWNER));
        var suspended = service.suspend(created.id(), new SellerInputs.Moderation(approved.version(), "Hold"), ADMIN);
        var reinstated = service.reinstate(created.id(), new SellerInputs.Version(suspended.version()), ADMIN);
        assertEquals(created.id(), reinstated.id());
        assertEquals(SellerStatus.APPROVED, reinstated.application().status());
        assertNull(reinstated.application().reviewReason());
    }

    @Test
    void ownReadUsesExactIdentityAndCannotSelectAnotherOwnersId() {
        PersistedSeller first = service.register(OWNER);
        PersistedSeller second = service.register(OTHER);
        assertEquals(first, service.findOwn(OWNER));
        assertEquals(second, service.findOwn(OTHER));
        assertCode(SellerPersistenceException.Code.SELLER_NOT_FOUND,
                assertThrows(SellerPersistenceException.class, () -> service.findOwn(ADMIN)));
        assertThrows(SellerRuleException.class, () -> service.findOwn(new SellerIdentity("unknown")));
        assertEquals(2, repository.count());
    }

    @Test
    void moderationReadChecksPermissionAndReturnsCommittedVersionWithoutWriting() {
        PersistedSeller seller = service.register(OWNER);
        PersistedSeller approved = service.approve(seller.id(), seller.version(), ADMIN);
        assertThrows(SellerRuleException.class, () -> service.findForModeration(seller.id(), OTHER));
        assertEquals(approved, service.findForModeration(seller.id(), ADMIN));
        assertEquals(approved.version(), repository.findById(seller.id()).orElseThrow().getVersion());
        assertCode(SellerPersistenceException.Code.SELLER_NOT_FOUND,
                assertThrows(SellerPersistenceException.class,
                        () -> service.findForModeration(UUID.randomUUID(), ADMIN)));
    }

    @Test
    void ownReadDoesNotNormalizeOpaqueIdentity() {
        for (String owner : new String[]{"OpaqueRead", "opaqueread", "OpaqueRead "}) {
            policy.known.add(owner);
            PersistedSeller seller = service.register(new SellerIdentity(owner));
            assertEquals(seller, service.findOwn(new SellerIdentity(owner)));
        }
        assertEquals(3, repository.count());
    }

    @Test
    void migrationAndJpaPersistASeparateSellerIdentity() {
        PersistedSeller seller = service.register(OWNER);
        assertNotEquals(UUID.fromString(OWNER.reference()), seller.id());
        assertEquals(0, seller.version());
        var stored = repository.findByOwnerReference(OWNER.reference()).orElseThrow();
        assertEquals(seller.id(), stored.getId());
        assertEquals(SellerStatus.PENDING_REVIEW, stored.getStatus());
        assertNull(stored.getReviewReason());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version='1'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conrelid='seller_profiles'::regclass AND contype='f'",
                Integer.class));
    }

    @Test
    void duplicateOwnerIsRejectedWithoutCreatingAnotherSeller() {
        PersistedSeller original = service.register(OWNER);
        assertCode(SellerPersistenceException.Code.SELLER_ALREADY_EXISTS,
                assertThrows(SellerPersistenceException.class, () -> service.register(OWNER)));
        assertEquals(1, repository.count());
        assertEquals(original.id(), repository.findByOwnerReference(OWNER.reference()).orElseThrow().getId());
    }

    @Test
    void databaseOwnerComparisonDoesNotTrimOrFoldCase() {
        for (String reference : new String[] {"OpaqueOwner", "opaqueowner", "OpaqueOwner "}) {
            policy.known.add(reference);
            service.register(new SellerIdentity(reference));
            assertEquals(reference, repository.findByOwnerReference(reference).orElseThrow().getOwnerReference());
        }
        assertEquals(3, repository.count());
    }

    @Test
    void resubmitKeepsIdAndLifecyclePersistsReasonsAndVersions() {
        PersistedSeller seller = service.register(OWNER);
        PersistedSeller rejected = service.reject(seller.id(), seller.version(), ADMIN, " Needs review ");
        assertEquals("Needs review", rejected.application().reviewReason());
        PersistedSeller resubmitted = service.resubmit(seller.id(), rejected.version(), OWNER);
        assertEquals(seller.id(), resubmitted.id());
        assertEquals(2, resubmitted.version());
        assertEquals(SellerStatus.PENDING_REVIEW, resubmitted.application().status());
        assertNull(resubmitted.application().reviewReason());
        PersistedSeller approved = service.approve(seller.id(), resubmitted.version(), ADMIN);
        PersistedSeller suspended = service.suspend(seller.id(), approved.version(), ADMIN, " Policy reason ");
        assertEquals("Policy reason", suspended.application().reviewReason());
        PersistedSeller reinstated = service.reinstate(seller.id(), suspended.version(), ADMIN);
        assertEquals(5, reinstated.version());
        assertEquals(SellerStatus.APPROVED, reinstated.application().status());
        assertNull(repository.findById(seller.id()).orElseThrow().getReviewReason());
    }

    @Test
    void requiredRegistrationRollsBackWithItsCaller() {
        AtomicReference<UUID> id = new AtomicReference<>();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            id.set(service.register(OWNER).id());
            transaction.setRollbackOnly();
        });
        assertFalse(repository.existsById(id.get()));
        assertEquals(0, repository.count());
    }

    @Test
    void requiredModerationRollsBackStateAndVersionWithItsCaller() {
        PersistedSeller seller = service.register(OWNER);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            service.approve(seller.id(), seller.version(), ADMIN);
            transaction.setRollbackOnly();
        });
        var stored = repository.findById(seller.id()).orElseThrow();
        assertEquals(SellerStatus.PENDING_REVIEW, stored.getStatus());
        assertEquals(seller.version(), stored.getVersion());
    }

    @Test
    void staleExpectedVersionCannotApproveAfterRejectAndResubmit() {
        PersistedSeller seller = service.register(OWNER);
        PersistedSeller rejected = service.reject(seller.id(), seller.version(), ADMIN, "reason");
        service.resubmit(seller.id(), rejected.version(), OWNER);
        assertCode(SellerPersistenceException.Code.SELLER_VERSION_CONFLICT,
                assertThrows(SellerPersistenceException.class,
                        () -> service.approve(seller.id(), seller.version(), ADMIN)));
        assertEquals(SellerStatus.PENDING_REVIEW, repository.findById(seller.id()).orElseThrow().getStatus());
    }

    @Test
    void permissionsAndInvalidInputLeaveDatabaseUnchanged() {
        PersistedSeller seller = service.register(OWNER);
        assertThrows(SellerRuleException.class, () -> service.approve(seller.id(), 0, OTHER));
        assertThrows(SellerRuleException.class,
                () -> service.reject(seller.id(), 0, ADMIN, "R".repeat(501)));
        assertEquals(SellerStatus.PENDING_REVIEW, repository.findById(seller.id()).orElseThrow().getStatus());
        PersistedSeller rejected = service.reject(seller.id(), 0, ADMIN, "reason");
        assertThrows(SellerRuleException.class, () -> service.resubmit(seller.id(), rejected.version(), OTHER));
        assertEquals("reason", repository.findById(seller.id()).orElseThrow().getReviewReason());
        PersistedSeller ownAdminProfile = service.register(ADMIN);
        assertThrows(SellerRuleException.class, () -> service.approve(ownAdminProfile.id(), 0, ADMIN));
        assertEquals(SellerStatus.PENDING_REVIEW,
                repository.findById(ownAdminProfile.id()).orElseThrow().getStatus());
    }

    @ParameterizedTest(name = "database rejects {0}")
    @MethodSource("invalidRows")
    void databaseConstraintsRejectInvalidRows(String label, String owner, String state, String reason, Long version) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO seller_profiles(id,owner_reference,status,review_reason,version) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), owner, state, reason, version));
        assertEquals(0, repository.count());
    }

    static Stream<Arguments> invalidRows() {
        return Stream.of(
                Arguments.of("null owner", null, "PENDING_REVIEW", null, 0L),
                Arguments.of("blank owner", " \t\n", "PENDING_REVIEW", null, 0L),
                Arguments.of("long owner", "O".repeat(129), "PENDING_REVIEW", null, 0L),
                Arguments.of("null status", "owner", null, null, 0L),
                Arguments.of("unknown status", "owner", "UNKNOWN", null, 0L),
                Arguments.of("missing reason", "owner", "REJECTED", null, 0L),
                Arguments.of("blank reason", "owner", "SUSPENDED", " \t", 0L),
                Arguments.of("long reason", "owner", "REJECTED", "R".repeat(501), 0L),
                Arguments.of("obsolete reason", "owner", "APPROVED", "reason", 0L),
                Arguments.of("null version", "owner", "PENDING_REVIEW", null, null),
                Arguments.of("negative version", "owner", "PENDING_REVIEW", null, -1L));
    }

    @Test
    void concurrentRegistrationWaitsForTheUniqueConstraintAndOnlyOneCommits() throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PersistedSeller> first = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                PersistedSeller created = service.register(OWNER);
                firstInserted.countDown();
                await(releaseFirst);
                return created;
            }));
            assertTrue(firstInserted.await(15, TimeUnit.SECONDS));
            Future<PersistedSeller> second = workers.submit(() -> service.register(OWNER));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database() "
                        + "AND application_name='seller-persistence-test' AND wait_event_type='Lock' "
                        + "AND lower(query) LIKE '%insert into seller_profiles%')", Boolean.class))) {
                    Thread.sleep(25);
                }
            });
            releaseFirst.countDown();
            PersistedSeller created = first.get(15, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> second.get(15, TimeUnit.SECONDS));
            assertCode(SellerPersistenceException.Code.SELLER_ALREADY_EXISTS,
                    assertInstanceOf(SellerPersistenceException.class, rejected.getCause()));
            assertEquals(1, repository.count());
            assertEquals(created.id(), repository.findByOwnerReference(OWNER.reference()).orElseThrow().getId());
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    @Test
    void twoRealTransactionsWithTheSameLoadedVersionCannotBothModerate() throws Exception {
        PersistedSeller seller = service.register(OWNER);
        CountDownLatch loaded = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PersistedSeller> approve = workers.submit(() -> moderateAfterBothLoad(seller, loaded, true));
            Future<PersistedSeller> reject = workers.submit(() -> moderateAfterBothLoad(seller, loaded, false));
            int successes = 0;
            int conflicts = 0;
            for (Future<PersistedSeller> result : List.of(approve, reject)) {
                try {
                    result.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertCode(SellerPersistenceException.Code.SELLER_VERSION_CONFLICT,
                            assertInstanceOf(SellerPersistenceException.class, exception.getCause()));
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, repository.findById(seller.id()).orElseThrow().getVersion());
            var snapshots = statusSnapshots(seller.id());
            assertEquals(2, snapshots.size());
            assertEquals(service.findOwn(OWNER).application().status().name(), snapshots.getLast().get("seller_status"));
        } finally {
            stop(workers);
        }
    }

    private PersistedSeller moderateAfterBothLoad(PersistedSeller seller, CountDownLatch loaded, boolean approve) {
        return new TransactionTemplate(transactions).execute(tx -> {
            assertEquals(seller.version(), repository.findById(seller.id()).orElseThrow().getVersion());
            loaded.countDown();
            await(loaded);
            return approve ? service.approve(seller.id(), seller.version(), ADMIN)
                    : service.reject(seller.id(), seller.version(), ADMIN, "reason");
        });
    }

    private static void assertCode(SellerPersistenceException.Code expected, SellerPersistenceException exception) {
        assertEquals(expected, exception.getCode());
    }

    @Test
    void roleSyncRegistrationSnapshotIsDurableAndPreservesExactOwner() {
        SellerIdentity exact = new SellerIdentity(" Owner-opaque-Ä ");
        policy.known.add(exact.reference());
        var seller = service.register(exact);
        var row = statusSnapshots(seller.id()).getFirst();
        assertEquals(seller.id(), row.get("seller_id"));
        assertEquals(exact.reference(), row.get("owner_reference"));
        assertEquals("PENDING_REVIEW", row.get("seller_status"));
        assertEquals(seller.version(), ((Number) row.get("source_version")).longValue());
        assertInstanceOf(UUID.class, row.get("event_id"));
        assertNotNull(row.get("recorded_at"));
        assertEquals(List.of(row), statusSnapshots(seller.id()));
    }

    @Test
    void roleSyncEveryLifecycleStateHasOneOrderedPersistedSnapshot() {
        var seller = service.register(OWNER);
        var rejected = service.reject(seller.id(), seller.version(), ADMIN, "Fix");
        var resubmitted = service.resubmit(seller.id(), rejected.version(), OWNER);
        var approved = service.approve(seller.id(), resubmitted.version(), ADMIN);
        var suspended = service.suspend(seller.id(), approved.version(), ADMIN, "Hold");
        var reinstated = service.reinstate(seller.id(), suspended.version(), ADMIN);
        var rows = statusSnapshots(seller.id());
        assertEquals(List.of("PENDING_REVIEW", "REJECTED", "PENDING_REVIEW", "APPROVED", "SUSPENDED", "APPROVED"),
                rows.stream().map(row -> row.get("seller_status")).toList());
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L), rows.stream()
                .map(row -> ((Number) row.get("source_version")).longValue()).toList());
        assertEquals(6, rows.stream().map(row -> row.get("event_id")).distinct().count());
        assertEquals(reinstated.version(), ((Number) rows.getLast().get("source_version")).longValue());
        assertTrue(rows.stream().allMatch(row -> OWNER.reference().equals(row.get("owner_reference"))));
        assertFalse(rows.getFirst().containsKey("review_reason"));
    }

    @Test
    void roleSyncUncommittedRegistrationIsInvisibleToAnotherConnection() {
        var outside = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        var id = new TransactionTemplate(transactions).execute(tx -> {
            var seller = service.register(OWNER);
            assertEquals(1, statusSnapshots(seller.id()).size());
            assertEquals(0L, outside.queryForObject(
                    "SELECT count(*) FROM seller_status_outbox WHERE seller_id=?", Long.class, seller.id()));
            return seller.id();
        });
        assertEquals(1L, outside.queryForObject(
                "SELECT count(*) FROM seller_status_outbox WHERE seller_id=?", Long.class, id));
    }

    @Test
    void roleSyncRegistrationRollbackLeavesNeitherSellerNorSnapshot() {
        UUID id = new TransactionTemplate(transactions).execute(tx -> {
            var seller = service.register(OWNER);
            assertEquals(1, statusSnapshots(seller.id()).size());
            tx.setRollbackOnly();
            return seller.id();
        });
        assertFalse(repository.existsById(id));
        assertTrue(statusSnapshots(id).isEmpty());
    }

    @Test
    void roleSyncModerationRollbackRestoresStateVersionAndSnapshotCount() {
        var seller = service.register(OWNER);
        var before = statusSnapshots(seller.id());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.approve(seller.id(), seller.version(), ADMIN);
            assertEquals(2, statusSnapshots(seller.id()).size());
            tx.setRollbackOnly();
        });
        assertEquals(seller, service.findOwn(OWNER));
        assertEquals(before, statusSnapshots(seller.id()));
    }

    @Test
    void roleSyncOutboxFailureRollsBackOtherwiseValidApproval() {
        var seller = service.register(OWNER);
        var before = statusSnapshots(seller.id());
        jdbc.execute("ALTER TABLE seller_status_outbox ADD CONSTRAINT test_refuse_approved CHECK (seller_status <> 'APPROVED')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> service.approve(seller.id(), seller.version(), ADMIN));
            assertEquals(seller, service.findOwn(OWNER));
            assertEquals(before, statusSnapshots(seller.id()));
        } finally {
            jdbc.execute("ALTER TABLE seller_status_outbox DROP CONSTRAINT test_refuse_approved");
        }
    }

    @Test
    void roleSyncDeniedOrStaleMutationCannotAppendSnapshot() {
        var seller = service.register(OWNER);
        assertThrows(SellerRuleException.class, () -> service.approve(seller.id(), seller.version(), OTHER));
        var approved = service.approve(seller.id(), seller.version(), ADMIN);
        assertThrows(SellerPersistenceException.class, () -> service.suspend(seller.id(), seller.version(), ADMIN, "Hold"));
        assertEquals(2, statusSnapshots(seller.id()).size());
        assertEquals(approved, service.findOwn(OWNER));
    }

    @Test
    void roleSyncDatabaseRejectsDuplicateSellerVersionAndInvalidSnapshot() {
        var seller = service.register(OWNER);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO seller_status_outbox(event_id,seller_id,owner_reference,seller_status,source_version) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), seller.id(), OWNER.reference(), "PENDING_REVIEW", seller.version()));
        for (String status : List.of("ADMIN", "SELLER", "ACTIVE", "approved")) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO seller_status_outbox(event_id,seller_id,owner_reference,seller_status,source_version) VALUES(?,?,?,?,?)",
                    UUID.randomUUID(), seller.id(), OWNER.reference(), status, 5L));
        }
        assertEquals(1, statusSnapshots(seller.id()).size());
    }

    private List<java.util.Map<String, Object>> statusSnapshots(UUID sellerId) {
        return jdbc.queryForList("SELECT * FROM seller_status_outbox WHERE seller_id=? ORDER BY source_version", sellerId);
    }

    @Test
    void roleSyncMigrationBackfillsExistingSellersWithoutPublishingOrChangingThem() {
        String schema = "outbox_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        UUID id = UUID.randomUUID();
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("2").load();
        try {
            flyway.migrate();
            jdbc.update("INSERT INTO " + schema + ".seller_profiles(id,owner_reference,status,version) VALUES(?,?,?,?)",
                    id, " Legacy-OWNER ", "APPROVED", 9L);
            var upgraded = org.flywaydb.core.Flyway.configure().dataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).load();
            upgraded.migrate();
            upgraded.validate();
            var row = jdbc.queryForMap("SELECT * FROM " + schema + ".seller_status_outbox WHERE seller_id=?", id);
            assertEquals(" Legacy-OWNER ", row.get("owner_reference"));
            assertEquals("APPROVED", row.get("seller_status"));
            assertEquals(9L, ((Number) row.get("source_version")).longValue());
            assertEquals(9L, jdbc.queryForObject("SELECT version FROM " + schema + ".seller_profiles WHERE id=?", Long.class, id));
            assertEquals("5", upgraded.info().current().getVersion().toString());
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not reach its barrier");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", exception);
        }
    }

    private static void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Wiring {
        @Bean FixturePolicy policy() { return new FixturePolicy(); }
        @Bean SellerPersistenceService persistenceService(SellerProfileRepository repository, FixturePolicy policy) {
            return new SellerPersistenceService(repository, policy);
        }
    }

    // Explicit test identities and Admin grant; never a JWT verifier or a production policy bean.
    static class FixturePolicy implements SellerAccessPolicy {
        private final Set<String> known = ConcurrentHashMap.newKeySet();

        @Override
        public void requireAuthenticated(SellerIdentity actor) {
            if (actor == null || !known.contains(actor.reference())) {
                throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Unknown test identity");
            }
        }

        @Override
        public void requireSellerModerationPermission(SellerIdentity actor) {
            requireAuthenticated(actor);
            if (!ADMIN.equals(actor)) {
                throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Admin fixture required");
            }
        }
    }
}
