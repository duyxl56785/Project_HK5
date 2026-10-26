package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real local Seller/Store DB facts, not JWT, HTTP, House or cross-service freshness acceptance. */
@SpringBootTest
@Testcontainers
@Tag("postgresql")
@Import(StoreProductFactsIntegrationTest.InspectionFixture.class)
class StoreProductFactsIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID SELLER_A = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID SELLER_B = UUID.fromString("50000000-0000-4000-8000-000000000002");
    private static final UUID STORE_A = UUID.fromString("50000000-0000-4000-8000-000000000003");
    private static final UUID STORE_B = UUID.fromString("50000000-0000-4000-8000-000000000004");
    private static final String OWNER_A = " Exact-Owner ";
    private static final String OWNER_B = "exact-owner";

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    }

    @Autowired StoreCurrentSource source;
    @Autowired StoreRepository stores;
    @Autowired SellerProfileRepository sellers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired DataSource dataSource;
    @Autowired ReadInspector inspector;

    @BeforeEach void resetDisposableDatabase() {
        inspector.beforeRead.set(null);
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM seller_profiles");
        jdbc.update("INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED'),(?,?,'APPROVED')",
                SELLER_A, OWNER_A, SELLER_B, OWNER_B);
        jdbc.update("INSERT INTO stores(id,seller_id,name,slug,status) VALUES (?,?,'First','first','OPEN'),(?,?,'Second','second','OPEN')",
                STORE_A, SELLER_A, STORE_B, SELLER_B);
    }

    @Test void batchUsesOneScalarCurrentReadAndPreservesPrivatePersistedOwnerAndVersions() {
        jdbc.update("UPDATE stores SET version=3 WHERE id=?", STORE_A);
        jdbc.update("UPDATE seller_profiles SET version=7 WHERE id=?", SELLER_A);
        AtomicInteger observations = new AtomicInteger();
        inspector.beforeRead.set(() -> {
            assertEquals("read committed", jdbc.queryForObject("SHOW transaction_isolation", String.class));
            assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
            observations.incrementAndGet();
        });
        var statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var results = source.findOwnedByIds(Set.of(STORE_A, STORE_B));
        assertEquals(1, observations.get());
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(0, statistics.getEntityLoadCount());
        var byId = indexed(results);
        assertEquals(Set.of(STORE_A, STORE_B), byId.keySet());
        var a = byId.get(STORE_A);
        assertEquals(SELLER_A, a.sellerId());
        assertEquals(OWNER_A, a.ownerReference());
        assertEquals(3, a.storeVersion());
        assertEquals(7, a.sellerVersion());
        assertEquals(SELLER_B, byId.get(STORE_B).sellerId());
        assertEquals(OWNER_B, byId.get(STORE_B).ownerReference());
        assertThrows(UnsupportedOperationException.class, results::clear);
    }

    static Stream<Arguments> states() {
        return Stream.of(SellerStatus.values()).flatMap(s -> Stream.of(StoreStatus.values()).map(t -> Arguments.of(s, t)));
    }

    @ParameterizedTest @MethodSource("states")
    void ineligibleStatusesRemainExplicitFactsRatherThanDisappearingOrBecomingApproved(SellerStatus seller, StoreStatus store) {
        jdbc.update("UPDATE seller_profiles SET status=?,review_reason=?,version=version+1 WHERE id=?", seller.name(),
                seller == SellerStatus.REJECTED || seller == SellerStatus.SUSPENDED ? "Private reason" : null, SELLER_A);
        jdbc.update("UPDATE stores SET status=?,version=version+1 WHERE id=?", store.name(), STORE_A);
        var result = source.findOwnedByIds(Set.of(STORE_A)).getFirst();
        assertEquals(STORE_A, result.storeId());
        assertEquals(SELLER_A, result.sellerId());
        assertEquals(seller, result.sellerStatus());
        assertEquals(store, result.storeStatus());
        assertEquals(1, result.storeVersion());
        assertEquals(1, result.sellerVersion());
        assertEquals(OWNER_A, result.ownerReference());
    }

    @Test void unknownLocalStoreIdsAreOmittedOnlyAfterSuccessfulQuery() {
        UUID missing = UUID.randomUUID();
        assertEquals(Set.of(STORE_A), indexed(source.findOwnedByIds(Set.of(STORE_A, missing))).keySet());
        assertTrue(source.findOwnedByIds(Set.of(missing)).isEmpty());
        var hundred = IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());
        assertTrue(source.findOwnedByIds(hundred).isEmpty());
    }

    @Test void nullEmptyNullMemberAndOversizedBatchesNeverQueryTheDatabase() {
        var statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        invalid(() -> source.findOwnedByIds(null));
        invalid(() -> source.findOwnedByIds(Set.of()));
        Set<UUID> withNull = new HashSet<>(); withNull.add(STORE_A); withNull.add(null);
        invalid(() -> source.findOwnedByIds(withNull));
        var oversized = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());
        invalid(() -> source.findOwnedByIds(oversized));
        assertEquals(0, statistics.getPrepareStatementCount());
    }

    @Test void scalarBatchBypassesStaleSellerAndStoreEntitiesAfterAnotherCommit() {
        currentTransaction().executeWithoutResult(tx -> {
            var cachedStore = stores.findById(STORE_A).orElseThrow();
            var cachedSeller = sellers.findById(SELLER_A).orElseThrow();
            requiresNew().executeWithoutResult(other -> closeAndSuspend());
            assertEquals(StoreStatus.OPEN, cachedStore.getStatus());
            assertEquals(SellerStatus.APPROVED, cachedSeller.getStatus());
            var facts = source.findOwnedByIds(Set.of(STORE_A)).getFirst();
            assertEquals(StoreStatus.CLOSED, facts.storeStatus());
            assertEquals(SellerStatus.SUSPENDED, facts.sellerStatus());
            assertEquals(1, facts.storeVersion());
            assertEquals(1, facts.sellerVersion());
            assertEquals(OWNER_A, facts.ownerReference());
        });
    }

    @Test void deletedRowsAreNotResurrectedFromCachedJpaEntities() {
        currentTransaction().executeWithoutResult(tx -> {
            stores.findById(STORE_A).orElseThrow();
            sellers.findById(SELLER_A).orElseThrow();
            requiresNew().executeWithoutResult(other -> deleteFirstStoreAndSeller());
            assertTrue(source.findOwnedByIds(Set.of(STORE_A)).isEmpty());
            assertEquals(Set.of(STORE_B), indexed(source.findOwnedByIds(Set.of(STORE_A, STORE_B))).keySet());
        });
    }

    @ParameterizedTest @ValueSource(strings = {"status", "delete"})
    void concurrentUncommittedChangesNeverPublishMixedOrDirtyFacts(String mutation) throws Exception {
        var written = new CountDownLatch(1); var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var future = worker.submit(() -> currentTransaction().executeWithoutResult(tx -> {
                if (mutation.equals("status")) closeAndSuspend(); else deleteFirstStoreAndSeller();
                written.countDown(); await(release);
            }));
            assertTrue(written.await(10, TimeUnit.SECONDS));
            var before = source.findOwnedByIds(Set.of(STORE_A)).getFirst();
            assertEquals(StoreStatus.OPEN, before.storeStatus());
            assertEquals(SellerStatus.APPROVED, before.sellerStatus());
            assertEquals(0, before.storeVersion()); assertEquals(0, before.sellerVersion());
            release.countDown(); future.get(10, TimeUnit.SECONDS);
            var after = source.findOwnedByIds(Set.of(STORE_A));
            if (mutation.equals("delete")) assertTrue(after.isEmpty());
            else {
                assertEquals(StoreStatus.CLOSED, after.getFirst().storeStatus());
                assertEquals(SellerStatus.SUSPENDED, after.getFirst().sellerStatus());
                assertEquals(1, after.getFirst().storeVersion()); assertEquals(1, after.getFirst().sellerVersion());
            }
        } finally {
            release.countDown(); worker.shutdownNow(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"default", "repeatable-read", "serializable"})
    void unknownOrRetainedAmbientSnapshotCannotClaimCurrentOwnedFacts(String isolation) {
        var tx = new TransactionTemplate(transactions);
        if (isolation.equals("repeatable-read")) tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        if (isolation.equals("serializable")) tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        tx.executeWithoutResult(status -> {
            unavailable(() -> source.findOwnedByIds(Set.of(STORE_A)));
            status.setRollbackOnly();
        });
    }

    @Test void bypassingTransactionProxyCannotReadAuthoritativeOwnedFacts() {
        unavailable(() -> new StoreCurrentSource(stores).findOwnedByIds(Set.of(STORE_A)));
    }

    @Test void databaseTimeoutIsUnavailableNeverAnEmptyOrPartialBatch() throws Exception {
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                blocker.createStatement().execute("LOCK TABLE stores IN ACCESS EXCLUSIVE MODE");
                currentTransaction().executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='150ms'");
                    unavailable(() -> source.findOwnedByIds(Set.of(STORE_A, STORE_B)));
                    status.setRollbackOnly();
                });
            } finally { blocker.rollback(); }
        }
        assertEquals(2, source.findOwnedByIds(Set.of(STORE_A, STORE_B)).size());
    }

    @Test void localForeignKeyPreventsSellerDisappearingWhileStoreExists() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM seller_profiles WHERE id=?", SELLER_A));
        assertEquals(SELLER_A, source.findOwnedByIds(Set.of(STORE_A)).getFirst().sellerId());
    }

    private void closeAndSuspend() {
        jdbc.update("UPDATE stores SET status='CLOSED',version=version+1 WHERE id=?", STORE_A);
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='Private reason',version=version+1 WHERE id=?", SELLER_A);
    }

    private void deleteFirstStoreAndSeller() {
        jdbc.update("DELETE FROM stores WHERE id=?", STORE_A);
        jdbc.update("DELETE FROM seller_profiles WHERE id=?", SELLER_A);
    }

    private TransactionTemplate currentTransaction() {
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); return tx;
    }

    private TransactionTemplate requiresNew() {
        var tx = currentTransaction(); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); return tx;
    }

    private Map<UUID, StoreCurrentSource.OwnedFacts> indexed(List<StoreCurrentSource.OwnedFacts> facts) {
        return facts.stream().collect(Collectors.toMap(StoreCurrentSource.OwnedFacts::storeId, Function.identity()));
    }

    private void invalid(Runnable action) {
        assertEquals(StoreRuleException.Code.INVALID_INPUT, assertThrows(StoreRuleException.class, action::run).getCode());
    }

    private void unavailable(Runnable action) {
        assertEquals(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE, assertThrows(StoreRuleException.class, action::run).getCode());
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture barrier timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @TestConfiguration static class InspectionFixture {
        @Bean ReadInspector readInspector() { return new ReadInspector(); }
        @Bean HibernatePropertiesCustomizer inspection(ReadInspector inspector) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", inspector);
        }
    }

    static class ReadInspector implements StatementInspector {
        final AtomicReference<Runnable> beforeRead = new AtomicReference<>();
        @Override public String inspect(String statement) {
            String sql = statement.toLowerCase(Locale.ROOT);
            if (sql.startsWith("select") && sql.contains("from stores")) {
                Runnable hook = beforeRead.getAndSet(null); if (hook != null) hook.run();
            }
            return statement;
        }
    }
}
