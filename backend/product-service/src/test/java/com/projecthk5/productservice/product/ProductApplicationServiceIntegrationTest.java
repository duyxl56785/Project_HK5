package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real production orchestration/JPA/PostgreSQL; authority facts are test fixtures, not JWT/live Store acceptance. */
@SpringBootTest
@Import(ProductApplicationServiceIntegrationTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgresql")
class ProductApplicationServiceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID STORE_A = UUID.fromString("70000000-0000-4000-8000-000000000002");
    private static final UUID STORE_B = UUID.fromString("70000000-0000-4000-8000-000000000003");
    private static final String OWNER_A = "account-a", OWNER_B = "account-b", ADMIN = "verified-admin-fixture";

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @Autowired private ProductApplicationService service;
    @Autowired private ProductPersistenceAdapter persistence;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private FixturePolicy policy;

    @BeforeEach void resetDisposableDatabaseAndAuthorityFixtures() {
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "Lifecycle category");
        policy.reset();
    }

    @Test void completeLifecyclePersistsEveryTransitionVersionPriceCurrencyAndCanonicalReason() {
        StoredProduct current = create("Exact-SKU");
        assertEquals(ProductStatus.DRAFT, current.listing().status());
        assertEquals(0, current.version());
        assertEquals(new BigDecimal("1.0000"), current.listing().unitPrice());
        current = service.edit(OWNER_A, current.listing().id(), current.version(), CATEGORY, "Edited", new BigDecimal("25.00"), "VND");
        assertEquals(1, current.version());
        current = service.submit(OWNER_A, current.listing().id(), current.version());
        assertEquals(ProductStatus.PENDING_REVIEW, current.listing().status());
        current = service.reject(ADMIN, current.listing().id(), current.version(), " Fix details ");
        assertEquals(ProductStatus.REJECTED, current.listing().status());
        assertEquals("Fix details", current.listing().reviewReason());
        current = service.edit(OWNER_A, current.listing().id(), current.version(), CATEGORY, "Corrected", new BigDecimal("50.000"), "VND");
        current = service.submit(OWNER_A, current.listing().id(), current.version());
        assertNull(current.listing().reviewReason());
        current = service.approve(ADMIN, current.listing().id(), current.version());
        assertEquals(ProductStatus.ACTIVE, current.listing().status());
        current = service.deactivate(OWNER_A, current.listing().id(), current.version());
        assertEquals(ProductStatus.INACTIVE, current.listing().status());
        current = service.submit(OWNER_A, current.listing().id(), current.version());
        current = service.approve(ADMIN, current.listing().id(), current.version());
        current = service.suspend(ADMIN, current.listing().id(), current.version(), " Policy issue ");
        assertEquals(ProductStatus.SUSPENDED, current.listing().status());
        assertEquals("Policy issue", current.listing().reviewReason());
        assertEquals(10, current.version());
        assertEquals("Exact-SKU", current.listing().sku());
        assertEquals(STORE_A, current.listing().storeId());
        assertEquals("VND", current.listing().currency());
        assertEquals(new BigDecimal("50.000"), current.listing().unitPrice());
        assertEquals(current, service.findById(OWNER_A, current.listing().id()));
        assertEquals("Policy issue", jdbc.queryForObject("SELECT review_reason FROM products WHERE id=?", String.class, current.listing().id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "edit", "submit", "approve", "reject", "suspend", "deactivate", "read"})
    void crossSellerAndUnprivilegedActorCannotReadOrWrite(String command) {
        StoredProduct original = create("owned-a");
        rule(ProductRuleException.Code.ACCESS_DENIED, () -> invoke(command, OWNER_B, original));
        assertEquals(original, persistence.findById(original.listing().id()).orElseThrow());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "suspend"})
    void evenAnAdminSellerCannotModerateTheirOwnProduct(String command) {
        StoredProduct current = create("self-moderation");
        current = service.submit(OWNER_A, current.listing().id(), current.version());
        if (command.equals("suspend")) current = service.approve(ADMIN, current.listing().id(), current.version());
        StoredProduct original = current;
        policy.admins.add(OWNER_A);
        rule(ProductRuleException.Code.ACCESS_DENIED, () -> invoke(command, OWNER_A, original));
        assertEquals(original, persistence.findById(original.listing().id()).orElseThrow());
    }

    @ParameterizedTest @ValueSource(strings = {"edit", "submit", "approve", "reject", "suspend", "deactivate"})
    void invalidStateNeverChangesStoredProduct(String command) {
        StoredProduct current = create("invalid-state");
        if (Set.of("edit", "submit").contains(command)) {
            current = service.submit(OWNER_A, current.listing().id(), current.version());
            current = service.approve(ADMIN, current.listing().id(), current.version());
        }
        StoredProduct original = current;
        String actor = Set.of("approve", "reject", "suspend").contains(command) ? ADMIN : OWNER_A;
        rule(ProductRuleException.Code.INVALID_STATE, () -> invoke(command, actor, original));
        assertEquals(original, persistence.findById(original.listing().id()).orElseThrow());
    }

    @ParameterizedTest @ValueSource(strings = {"closed", "unapproved", "missing", "mismatched", "unavailable"})
    void currentStoreSellerFactsAreRequiredAndFailClosed(String mode) {
        StoredProduct original = create("current-facts");
        policy.mode = mode;
        ProductRuleException.Code expected = Set.of("closed", "unapproved").contains(mode)
                ? ProductRuleException.Code.ACCESS_DENIED : ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE;
        for (Runnable operation : List.<Runnable>of(() -> service.findById(OWNER_A, original.listing().id()),
                () -> service.edit(OWNER_A, original.listing().id(), original.version(), CATEGORY, "Edit", BigDecimal.TEN, "VND"),
                () -> create("another"))) rule(expected, operation);
        assertEquals(original, persistence.findById(original.listing().id()).orElseThrow());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test void exactMoneySkuAndCategoryRulesSurviveApplicationOrchestration() {
        StoredProduct original = create("Immutable-SKU");
        rule(ProductRuleException.Code.INVALID_INPUT,
                () -> service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "Fraction", new BigDecimal("1.5"), "VND"));
        rule(ProductRuleException.Code.INVALID_INPUT,
                () -> service.create(OWNER_A, STORE_A, CATEGORY, "wrong-currency", "Name", BigDecimal.ONE, null));
        rule(ProductRuleException.Code.CATEGORY_NOT_FOUND,
                () -> service.edit(OWNER_A, original.listing().id(), 0, UUID.randomUUID(), "Bad category", BigDecimal.ONE, "VND"));
        assertEquals(original, persistence.findById(original.listing().id()).orElseThrow());
        failure(ProductPersistenceException.Code.DUPLICATE_SKU,
                () -> service.create(OWNER_B, STORE_B, CATEGORY, "Immutable-SKU", "Other Store", BigDecimal.TEN, "VND"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test void missingProductReturnsNotFoundAfterAuthorityIsAvailable() {
        failure(ProductPersistenceException.Code.NOT_FOUND, () -> service.findById(OWNER_A, UUID.randomUUID()));
        failure(ProductPersistenceException.Code.NOT_FOUND, () -> service.submit(OWNER_A, UUID.randomUUID(), 0));
    }

    @Test void staleExpectedVersionDoesNotOverwriteWinnerAndNoOpStaleEditFails() {
        StoredProduct original = create("stale");
        StoredProduct winner = service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "Winner", BigDecimal.TEN, "VND");
        failure(ProductPersistenceException.Code.STALE_VERSION,
                () -> service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "Winner", BigDecimal.TEN, "VND"));
        assertEquals(winner, service.findById(OWNER_A, original.listing().id()));
    }

    @Test void cachedOldEntityUsesCurrentStateAndVersionBeforeTransition() {
        StoredProduct original = create("cached-state");
        transaction().executeWithoutResult(tx -> {
            persistence.findById(original.listing().id()).orElseThrow();
            var winner = requiresNew().execute(other -> service.submit(OWNER_A, original.listing().id(), original.version()));
            assertNotNull(winner);
            // Version1 is PENDING_REVIEW: authorize/approve using current facts, not cached DRAFT version0.
            var approved = service.approve(ADMIN, original.listing().id(), winner.version());
            assertEquals(ProductStatus.ACTIVE, approved.listing().status());
            assertEquals(2, approved.version());
        });
        assertEquals(ProductStatus.ACTIVE, service.findById(OWNER_A, original.listing().id()).listing().status());
    }

    @Test void cachedOldDraftCannotAuthorizeEditOfCurrentPendingProduct() {
        StoredProduct original = create("cached-edit");
        transaction().executeWithoutResult(tx -> {
            persistence.findById(original.listing().id()).orElseThrow();
            var pending = requiresNew().execute(other -> service.submit(OWNER_A, original.listing().id(), original.version()));
            assertNotNull(pending);
            rule(ProductRuleException.Code.INVALID_STATE, () -> service.edit(OWNER_A, original.listing().id(),
                    pending.version(), CATEGORY, "Forbidden", BigDecimal.TEN, "VND"));
            tx.setRollbackOnly();
        });
        assertEquals(ProductStatus.PENDING_REVIEW, service.findById(OWNER_A, original.listing().id()).listing().status());
    }

    @Test void aVersionFromAfterTheReadCannotValidateATransitionComputedFromAnOlderSnapshot() {
        StoredProduct original = create("snapshot-version");
        policy.managementHook = () -> requiresNew().executeWithoutResult(other ->
                service.submit(OWNER_A, original.listing().id(), original.version()));
        // During authorization a second transaction submits version1, after the outer command read DRAFT0.
        failure(ProductPersistenceException.Code.STALE_VERSION, () -> service.edit(OWNER_A, original.listing().id(),
                1, CATEGORY, "Forbidden stale draft", BigDecimal.TEN, "VND"));
        StoredProduct current = service.findById(OWNER_A, original.listing().id());
        assertEquals(ProductStatus.PENDING_REVIEW, current.listing().status());
        assertEquals("Product", current.listing().name());
        assertEquals(1, current.version());
    }

    @Test void aCachedDeletedProductCannotBeReturnedAsAnOwnedManagementRead() {
        StoredProduct original = create("deleted-cache");
        transaction().executeWithoutResult(tx -> {
            persistence.findById(original.listing().id()).orElseThrow();
            requiresNew().executeWithoutResult(other -> jdbc.update("DELETE FROM products WHERE id=?", original.listing().id()));
            failure(ProductPersistenceException.Code.NOT_FOUND, () -> service.findById(OWNER_A, original.listing().id()));
            tx.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test void concurrentEditsFromOneVersionHaveExactlyOneWinner() throws Exception {
        StoredProduct original = create("concurrent");
        var workers = Executors.newFixedThreadPool(2);
        policy.writeBarrier = new CyclicBarrier(2);
        try {
            var first = workers.submit(() -> service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "First", BigDecimal.TEN, "VND"));
            var second = workers.submit(() -> service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "Second", new BigDecimal("20"), "VND"));
            int successes = 0, stale = 0;
            StoredProduct winner = null;
            for (var result : List.of(first, second)) {
                try { winner = result.get(20, TimeUnit.SECONDS); successes++; }
                catch (ExecutionException failure) {
                    assertEquals(ProductPersistenceException.Code.STALE_VERSION,
                            assertInstanceOf(ProductPersistenceException.class, failure.getCause()).getCode());
                    stale++;
                }
            }
            policy.writeBarrier = null;
            assertEquals(1, successes);
            assertEquals(1, stale);
            assertNotNull(winner);
            assertEquals(1, winner.version());
            assertEquals(winner, service.findById(OWNER_A, original.listing().id()));
        } finally {
            policy.writeBarrier = null;
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void requiredOuterRollbackRevertsMultipleLifecycleWrites() {
        StoredProduct original = create("rollback-existing");
        transaction().executeWithoutResult(tx -> {
            service.edit(OWNER_A, original.listing().id(), 0, CATEGORY, "Rolled back", BigDecimal.TEN, "VND");
            create("rollback-new");
            tx.setRollbackOnly();
        });
        assertEquals(original, service.findById(OWNER_A, original.listing().id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @ParameterizedTest @ValueSource(strings = {"duplicate", "invalid-state", "authorization", "stale"})
    void failuresRollBackEarlierCompanionWritesInRequiredTransaction(String mode) {
        StoredProduct original = create("atomic-original");
        if (mode.equals("duplicate")) failure(ProductPersistenceException.Code.DUPLICATE_SKU,
                () -> transaction().executeWithoutResult(tx -> { create("companion"); create(original.listing().sku()); }));
        if (mode.equals("invalid-state")) rule(ProductRuleException.Code.INVALID_STATE,
                () -> transaction().executeWithoutResult(tx -> { create("companion"); service.approve(ADMIN, original.listing().id(), 0); }));
        if (mode.equals("authorization")) rule(ProductRuleException.Code.ACCESS_DENIED,
                () -> transaction().executeWithoutResult(tx -> { create("companion"); service.findById(OWNER_B, original.listing().id()); }));
        if (mode.equals("stale")) failure(ProductPersistenceException.Code.STALE_VERSION,
                () -> transaction().executeWithoutResult(tx -> { create("companion");
                    service.edit(OWNER_A, original.listing().id(), 1, CATEGORY, "Stale", BigDecimal.TEN, "VND"); }));
        assertEquals(original, service.findById(OWNER_A, original.listing().id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    private StoredProduct create(String sku) {
        return service.create(OWNER_A, STORE_A, CATEGORY, sku, "Product", new BigDecimal("1.0000"), "VND");
    }

    private void invoke(String command, String actor, StoredProduct current) {
        UUID id = current.listing().id(); long version = current.version();
        switch (command) {
            case "create" -> service.create(actor, STORE_A, CATEGORY, "another", "Name", BigDecimal.ONE, "VND");
            case "edit" -> service.edit(actor, id, version, CATEGORY, "Edited", BigDecimal.TEN, "VND");
            case "submit" -> service.submit(actor, id, version);
            case "approve" -> service.approve(actor, id, version);
            case "reject" -> service.reject(actor, id, version, "Reason");
            case "suspend" -> service.suspend(actor, id, version, "Reason");
            case "deactivate" -> service.deactivate(actor, id, version);
            case "read" -> service.findById(actor, id);
            default -> throw new IllegalArgumentException(command);
        }
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }
    private TransactionTemplate requiresNew() {
        var template = transaction(); template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); return template;
    }
    private void rule(ProductRuleException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(ProductRuleException.class, operation::run).getCode());
    }
    private void failure(ProductPersistenceException.Code code, Runnable operation) {
        assertEquals(code, assertThrows(ProductPersistenceException.class, operation::run).getCode());
    }

    @TestConfiguration static class AuthorityFixture {
        @Bean FixturePolicy policy() { return new FixturePolicy(); }
    }

    static class FixturePolicy implements ProductAccessPolicy {
        private final Map<UUID, StoreAccess> stores = new ConcurrentHashMap<>();
        private final Set<String> admins = ConcurrentHashMap.newKeySet();
        private volatile String mode;
        private volatile CyclicBarrier writeBarrier;
        private volatile Runnable managementHook;
        void reset() {
            mode = "normal"; writeBarrier = null; managementHook = null; admins.clear(); admins.add(ADMIN); stores.clear();
            stores.put(STORE_A, new StoreAccess(STORE_A, UUID.randomUUID(), OWNER_A, true, true, false));
            stores.put(STORE_B, new StoreAccess(STORE_B, UUID.randomUUID(), OWNER_B, true, true, false));
        }
        @Override public StoreAccess requireSellerManagement(String actor, UUID storeId) {
            if (!Set.of(OWNER_A, OWNER_B).contains(actor)) throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture role denied");
            Runnable hook = managementHook;
            managementHook = null;
            if (hook != null) hook.run();
            CyclicBarrier barrier = writeBarrier;
            if (barrier != null) try { barrier.await(10, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new IllegalStateException("Fixture barrier failed", failure); }
            return readStore(storeId);
        }
        @Override public StoreAccess requireModeration(String actor, UUID storeId) {
            if (!admins.contains(actor)) throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture admin denied");
            return readStore(storeId);
        }
        @Override public StoreAccess readStore(UUID storeId) {
            if (mode.equals("unavailable")) throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Fixture unavailable");
            if (mode.equals("missing")) return null;
            if (mode.equals("mismatched")) return stores.get(storeId.equals(STORE_A) ? STORE_B : STORE_A);
            StoreAccess current = stores.get(storeId);
            if (current == null) return null;
            return new StoreAccess(current.storeId(), current.sellerId(), current.ownerReference(),
                    !mode.equals("unapproved"), !mode.equals("closed"), current.houseStore());
        }
    }
}
