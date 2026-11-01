package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.*;
import com.projecthk5.productservice.catalog.CurrentStoreFactsSource.StoreFacts;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual production Flyway/JPA/PostgreSQL. Store/Seller/House authority is fixture-only, not a live service pair. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class ProductCatalogSnapshotSourceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID STORE_A = UUID.fromString("80000000-0000-4000-8000-000000000002");
    private static final UUID STORE_B = UUID.fromString("80000000-0000-4000-8000-000000000003");
    private static final UUID SELLER_A = UUID.fromString("80000000-0000-4000-8000-000000000004");
    private static final UUID SELLER_B = UUID.fromString("80000000-0000-4000-8000-000000000005");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    }
    @Autowired ProductRepository products;
    @Autowired ProductPersistenceAdapter persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;
    @Autowired MockMvc http;
    private ProductCatalogSnapshotSource source;
    private CatalogBatchValidator validator;
    private final AtomicInteger storeCalls = new AtomicInteger();
    private Set<UUID> requestedStores;
    private String mode;
    private Runnable storeHook;

    @BeforeEach void resetDisposableDatabaseAndFixtures() {
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "Batch category");
        mode = "normal"; storeCalls.set(0); requestedStores = Set.of(); storeHook = null;
        source = withStores(this::currentStores);
        validator = new CatalogBatchValidator(source);
    }

    @Test void aValidProductIsReadFromProductionDatabaseWithExactFields() {
        ProductListing product = insert(STORE_A, "Exact-SKU", ProductStatus.ACTIVE);
        var found = source.load(Set.of(product.id())).getFirst();
        assertEquals(LookupStatus.FOUND, found.status());
        assertEquals(product, found.facts().product());
        assertEquals("VND", found.facts().currency());
        assertTrue(found.facts().categoryExists());
        Result result = validator.validate(List.of(expected(product)));
        assertTrue(result.valid());
        assertEquals("Exact-SKU", result.items().getFirst().snapshot().inventorySku());
        assertFalse(result.items().getFirst().snapshot().houseStore());
        assertEquals(SELLER_A, result.items().getFirst().snapshot().sellerId());
    }

    @Test void twoStoresHaveDistinctSellerAndExplicitHouseFactsAndOrderedResults() {
        var first = insert(STORE_A, "first", ProductStatus.ACTIVE);
        var second = insert(STORE_B, "second", ProductStatus.ACTIVE);
        var result = validator.validate(List.of(expected(second), expected(first)));
        assertTrue(result.valid());
        assertEquals(List.of(second.id(), first.id()), result.items().stream().map(ItemResult::productId).toList());
        assertTrue(result.items().getFirst().snapshot().houseStore());
        assertEquals(SELLER_B, result.items().getFirst().snapshot().sellerId());
        assertEquals(Set.of(STORE_A, STORE_B), requestedStores);
        assertEquals(1, storeCalls.get());
    }

    @Test void maximumBatchUsesOneProductCategoryQueryAndOneUniqueStoreBatch() {
        var listings = IntStream.range(0, 100).mapToObj(i -> insert(i % 2 == 0 ? STORE_A : STORE_B,
                "batch-" + i, ProductStatus.ACTIVE)).toList();
        var statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = validator.validate(listings.stream().map(this::expected).toList());
        assertTrue(result.valid());
        assertEquals(100, result.items().size());
        assertEquals(1, statistics.getPrepareStatementCount(), "No Product/Category per-ID SELECTs");
        assertEquals(1, storeCalls.get());
        assertEquals(Set.of(STORE_A, STORE_B), requestedStores);
    }

    @Test void multipleProductsFromOneStoreShareOneTrustedStoreFact() {
        var a = insert(STORE_A, "a", ProductStatus.ACTIVE);
        var b = insert(STORE_A, "b", ProductStatus.ACTIVE);
        assertTrue(validator.validate(List.of(expected(a), expected(b))).valid());
        assertEquals(Set.of(STORE_A), requestedStores);
        assertEquals(1, storeCalls.get());
    }

    @Test void onlySuccessfulProductLookupConfirmsAbsenceAndDoesNotCallStoreDependency() {
        UUID missing = UUID.randomUUID();
        var result = source.load(Set.of(missing)).getFirst();
        assertEquals(missing, result.productId());
        assertEquals(LookupStatus.ABSENT, result.status());
        assertNull(result.facts());
        assertEquals(0, storeCalls.get());
        var rejected = validator.validate(List.of(new ExpectedItem(missing, STORE_A, SELLER_A, BigDecimal.TEN, "VND")));
        assertEquals(ItemCode.PRODUCT_NOT_FOUND, rejected.items().getFirst().code());
    }

    @Test void confirmedAbsenceIsPreservedAlongsideUnavailableExistingProductButValidatorAborts() {
        var product = insert(STORE_A, "present", ProductStatus.ACTIVE);
        UUID missing = UUID.randomUUID();
        mode = "store-unavailable";
        var loaded = source.load(Set.of(product.id(), missing));
        assertEquals(LookupStatus.ABSENT, lookup(loaded, missing).status());
        assertEquals(LookupStatus.UNAVAILABLE, lookup(loaded, product.id()).status());
        unavailable(() -> validator.validate(List.of(expected(product),
                new ExpectedItem(missing, STORE_A, SELLER_A, BigDecimal.TEN, "VND"))));
    }

    @Test void actualPostgresLockTimeoutIsUnavailableNeverAuthoritativeAbsence() throws Exception {
        var product = insert(STORE_A, "timeout", ProductStatus.ACTIVE);
        UUID missing = UUID.randomUUID();
        try (Connection blocker = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            blocker.setAutoCommit(false);
            try {
                blocker.createStatement().execute("LOCK TABLE products IN ACCESS EXCLUSIVE MODE");
                transaction().executeWithoutResult(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout='150ms'");
                    var statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
                    statistics.clear();
                    var loaded = source.load(Set.of(product.id(), missing));
                    assertEquals(1, statistics.getPrepareStatementCount(), "Must exercise an actual timed-out SQL query");
                    assertEquals(2, loaded.size());
                    assertTrue(loaded.stream().allMatch(r -> r.status() == LookupStatus.UNAVAILABLE && r.facts() == null));
                    tx.setRollbackOnly();
                });
            } finally { blocker.rollback(); }
        }
        assertEquals(0, storeCalls.get());
        assertTrue(validator.validate(List.of(expected(product))).valid());
    }

    @ParameterizedTest @ValueSource(strings={"seller-unavailable","store-unavailable"})
    void dependencyOutageIsNotReportedAsNotSellableOrMissing(String outage) {
        var product = insert(STORE_A, "outage", ProductStatus.ACTIVE);
        mode = outage;
        assertEquals(LookupStatus.UNAVAILABLE, source.load(Set.of(product.id())).getFirst().status());
        unavailable(() -> validator.validate(List.of(expected(product))));
    }

    @ParameterizedTest @EnumSource(value=ProductStatus.class, names="ACTIVE", mode=EnumSource.Mode.EXCLUDE)
    void nonActiveProductsNeverSupplyPublicSnapshots(ProductStatus status) {
        var product = insert(STORE_A, "status", status);
        var result = validator.validate(List.of(expected(product)));
        assertFalse(result.valid());
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, result.items().getFirst().code());
        assertNull(result.items().getFirst().snapshot());
    }

    @ParameterizedTest @ValueSource(strings={"unapproved","closed","missing-category"})
    void eligibilityUsesCurrentDependencyAndLocalCategory(String invalid) {
        var product = insert(STORE_A, "eligibility", ProductStatus.ACTIVE);
        mode = invalid;
        if (invalid.equals("missing-category")) {
            // Corruption injection only in this disposable DB; ordinary deletion is prevented by the production FK.
            transaction().executeWithoutResult(tx -> {
                jdbc.execute("SET LOCAL session_replication_role=replica");
                jdbc.update("DELETE FROM categories WHERE id=?", CATEGORY);
            });
        }
        var result = validator.validate(List.of(expected(product)));
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, result.items().getFirst().code());
        assertNull(result.items().getFirst().snapshot());
    }

    @ParameterizedTest @ValueSource(strings={"price","currency","store","seller"})
    void expectedClientFieldsOnlyCompareWithTrustedFacts(String mismatch) {
        var p = insert(STORE_A, "comparison", ProductStatus.ACTIVE);
        var expected = new ExpectedItem(p.id(), mismatch.equals("store") ? STORE_B : STORE_A,
                mismatch.equals("seller") ? SELLER_B : SELLER_A,
                mismatch.equals("price") ? BigDecimal.ONE : p.unitPrice(), mismatch.equals("currency") ? "USD" : "VND");
        var result = validator.validate(List.of(expected));
        ItemCode code = switch(mismatch) {
            case "price" -> ItemCode.PRICE_CHANGED;
            case "currency" -> ItemCode.CURRENCY_MISMATCH;
            default -> ItemCode.OWNERSHIP_MISMATCH;
        };
        assertEquals(code, result.items().getFirst().code());
        assertNull(result.items().getFirst().snapshot());
    }

    @Test void caseNfcNfdAndSupplementarySkuIdentityIsUnchanged() {
        var listings = List.of("Sku-A", "sku-a", "\u00e9", "e\u0301", "SKU-\ud83d\ude80")
                .stream().map(sku -> insert(STORE_A, sku, ProductStatus.ACTIVE)).toList();
        var result = validator.validate(listings.stream().map(this::expected).toList());
        assertTrue(result.valid());
        assertEquals(listings.stream().map(ProductListing::sku).toList(),
                result.items().stream().map(i -> i.snapshot().inventorySku()).toList());
    }

    @Test void consumerOutputDoesNotExposePrivateOwnerOrModerationReasonOrRejectedDetails() throws Exception {
        var active = insert(STORE_A, "public-sku", ProductStatus.ACTIVE);
        String ready = new ObjectMapper().writeValueAsString(validator.validate(List.of(expected(active))));
        assertFalse(ready.contains("private-owner"));
        assertFalse(ready.contains("ownerReference"));
        assertFalse(ready.contains("reviewReason"));
        var rejected = insert(STORE_B, "private-sku", ProductStatus.REJECTED);
        var result = validator.validate(List.of(expected(active), expected(rejected)));
        assertTrue(result.items().stream().allMatch(i -> i.snapshot() == null));
        String failed = new ObjectMapper().writeValueAsString(result);
        assertFalse(failed.contains("private moderation reason"));
        assertFalse(failed.contains("private-sku"));
        assertFalse(failed.contains("public-sku"));
        assertFalse(failed.contains("ownerReference"));
    }

    @ParameterizedTest @ValueSource(strings={"missing","extra","duplicate","conflict","seller-conflict",
            "stale","unknown-house","unknown-seller","unknown-store","unknown-owner","unknown-seller-id",
            "wrong-store","null-batch","null-fact"})
    void incompleteUnverifiedOrConflictingStoreFactsAreUnavailable(String invalid) {
        var a = insert(STORE_A, "a", ProductStatus.ACTIVE);
        var b = insert(STORE_B, "b", ProductStatus.ACTIVE);
        mode = invalid;
        var loaded = source.load(Set.of(a.id(), b.id()));
        assertTrue(loaded.stream().allMatch(r -> r.status() == LookupStatus.UNAVAILABLE && r.facts() == null));
        unavailable(() -> validator.validate(List.of(expected(a), expected(b))));
    }

    @Test void oneNonReadyProductRemovesEveryUsableSnapshotInEitherRequestOrder() {
        var a = insert(STORE_A, "a", ProductStatus.ACTIVE);
        var b = insert(STORE_B, "b", ProductStatus.SUSPENDED);
        for (var request : List.of(List.of(expected(a), expected(b)), List.of(expected(b), expected(a)))) {
            var result = validator.validate(request);
            assertFalse(result.valid());
            assertTrue(result.items().stream().allMatch(i -> i.snapshot() == null));
        }
    }

    @Test void queryProjectionBypassesCachedEntityWithoutClaimingDistributedFreshness() {
        var p = insert(STORE_A, "fresh", ProductStatus.ACTIVE);
        transaction().executeWithoutResult(tx -> {
            persistence.findById(p.id()).orElseThrow();
            requiresNew().executeWithoutResult(other ->
                    jdbc.update("UPDATE products SET unit_price=20,version=version+1 WHERE id=?", p.id()));
            var loaded = source.load(Set.of(p.id())).getFirst();
            assertEquals(new BigDecimal("20"), loaded.facts().product().unitPrice());
        });
        assertEquals(ItemCode.PRICE_CHANGED, validator.validate(List.of(expected(p))).items().getFirst().code());
    }

    @ParameterizedTest @ValueSource(strings={"price","status"})
    void concurrentChangesAfterProductReadRequireRevalidationRatherThanAPriceOrStatusLease(String changed) {
        var p = insert(STORE_A, "observation", ProductStatus.ACTIVE);
        storeHook = () -> requiresNew().executeWithoutResult(tx ->
                jdbc.update(changed.equals("price")
                        ? "UPDATE products SET unit_price=20,version=version+1 WHERE id=?"
                        : "UPDATE products SET status='INACTIVE',version=version+1 WHERE id=?", p.id()));
        assertTrue(validator.validate(List.of(expected(p))).valid(), "Statement observation, not a distributed lease");
        assertEquals(changed.equals("price") ? ItemCode.PRICE_CHANGED : ItemCode.PRODUCT_NOT_SELLABLE,
                validator.validate(List.of(expected(p))).items().getFirst().code());
        jdbc.update("UPDATE products SET status='INACTIVE',version=version+1 WHERE id=?", p.id());
        var current = new ExpectedItem(p.id(), STORE_A, SELLER_A, new BigDecimal("20"), "VND");
        assertEquals(ItemCode.PRODUCT_NOT_SELLABLE, validator.validate(List.of(current)).items().getFirst().code());
    }

    @Test void retainedRepeatableReadSnapshotCannotClaimCurrentFacts() {
        var p = insert(STORE_A, "retained", ProductStatus.ACTIVE);
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> {
            assertEquals(LookupStatus.UNAVAILABLE, source.load(Set.of(p.id())).getFirst().status());
            unavailable(() -> validator.validate(List.of(expected(p))));
        });
        assertEquals(0, storeCalls.get());
    }

    @Test void anAmbientTransactionWithUnreportedIsolationAlsoFailsClosed() {
        var p = insert(STORE_A, "unknown-isolation", ProductStatus.ACTIVE);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var statistics = entityManagers.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
            assertEquals(LookupStatus.UNAVAILABLE, source.load(Set.of(p.id())).getFirst().status());
            assertEquals(0, statistics.getPrepareStatementCount());
        });
        assertEquals(0, storeCalls.get());
    }

    @Test void unconfiguredDefaultProductionAuthorityNeverCreatesFoundOrReady() {
        var production = new ProductCatalogSnapshotSource(products, new StaticListableBeanFactory().getBeanProvider(CurrentStoreFactsSource.class));
        var p = insert(STORE_A, "closed", ProductStatus.ACTIVE);
        UUID missing = UUID.randomUUID();
        var loaded = production.load(Set.of(p.id(), missing));
        assertEquals(LookupStatus.UNAVAILABLE, lookup(loaded, p.id()).status());
        assertEquals(LookupStatus.ABSENT, lookup(loaded, missing).status());
        unavailable(() -> new CatalogBatchValidator(production).validate(List.of(expected(p))));
    }

    @Test void ambiguousDependenciesDoNotSelectAnAuthorityOrReturnFound() {
        var p = insert(STORE_A, "ambiguous", ProductStatus.ACTIVE);
        var factory = new StaticListableBeanFactory();
        factory.addBean("one", (CurrentStoreFactsSource) this::currentStores);
        factory.addBean("two", (CurrentStoreFactsSource) this::currentStores);
        var ambiguous = new ProductCatalogSnapshotSource(products, factory.getBeanProvider(CurrentStoreFactsSource.class));
        assertEquals(LookupStatus.UNAVAILABLE, ambiguous.load(Set.of(p.id())).getFirst().status());
        assertEquals(0, storeCalls.get());
    }

    @Test void productAndCatalogHttpRoutesRemainDenyDefaultEvenWithForgedCredentials() throws Exception {
        for (String route : List.of("/api/products", "/api/catalog", "/internal/catalog/checkout/validate")) {
            var response = http.perform(MockMvcRequestBuilders.post(route).header("X-User-Id","owner")
                    .header("X-Roles","ADMIN,SELLER").header("Authorization","Bearer forged")
                    .contentType("application/json").content("{}")).andReturn().getResponse();
            if (route.equals("/api/products")) {
                assertEquals(403, response.getStatus());
                assertTrue(response.getContentAsString().contains("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
            } else assertEquals(401, response.getStatus());
        }
    }

    private ProductListing insert(UUID store, String sku, ProductStatus status) {
        String reason = status==ProductStatus.REJECTED || status==ProductStatus.SUSPENDED ? "private moderation reason" : null;
        return persistence.create(new ProductListing(UUID.randomUUID(), store, CATEGORY, sku, "Product",
                new BigDecimal("10.0000"), "VND", status, reason)).listing();
    }
    private ExpectedItem expected(ProductListing p) {
        return new ExpectedItem(p.id(), p.storeId(), p.storeId().equals(STORE_A) ? SELLER_A : SELLER_B, p.unitPrice(), p.currency());
    }
    private ProductCatalogSnapshotSource withStores(CurrentStoreFactsSource dependency) {
        var factory = new StaticListableBeanFactory(); factory.addBean("fixture", dependency);
        return new ProductCatalogSnapshotSource(products, factory.getBeanProvider(CurrentStoreFactsSource.class));
    }
    private List<StoreFacts> currentStores(Set<UUID> ids) {
        storeCalls.incrementAndGet(); requestedStores = ids;
        Runnable hook = storeHook; storeHook = null; if (hook != null) hook.run();
        if (mode.endsWith("-unavailable")) throw new IllegalStateException("Fixture "+mode);
        if (mode.equals("null-batch")) return null;
        var facts = new ArrayList<StoreFacts>();
        for (UUID id : ids) facts.add(new StoreFacts(id, mode.equals("unknown-seller-id") ? null : mode.equals("seller-conflict") || id.equals(STORE_A) ? SELLER_A : SELLER_B,
                mode.equals("unknown-owner") ? null : "private-owner-"+id, mode.equals("unknown-seller") ? null : !mode.equals("unapproved"),
                mode.equals("unknown-store") ? null : !mode.equals("closed"),
                mode.equals("unknown-house") ? null : id.equals(STORE_B), !mode.equals("stale")));
        if (mode.equals("missing")) facts.removeFirst();
        if (mode.equals("extra")) facts.add(new StoreFacts(UUID.randomUUID(), UUID.randomUUID(), "private-owner",true,true,false,true));
        if (mode.equals("duplicate")) facts.add(facts.getFirst());
        if (mode.equals("conflict")) { var f=facts.getFirst(); facts.add(new StoreFacts(f.storeId(),f.sellerId(),f.ownerReference(),true,true,!f.houseStore(),true)); }
        if (mode.equals("wrong-store")) { var f=facts.removeFirst(); facts.add(new StoreFacts(UUID.randomUUID(),f.sellerId(),f.ownerReference(),true,true,false,true)); }
        if (mode.equals("null-fact")) facts.add(null);
        return facts;
    }
    private LookupResult lookup(List<LookupResult> results, UUID id) { return results.stream().filter(r -> r.productId().equals(id)).findFirst().orElseThrow(); }
    private TransactionTemplate transaction() { var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return tx; }
    private TransactionTemplate requiresNew() { var tx=transaction();tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx; }
    private void unavailable(Runnable operation) { assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE,assertThrows(CatalogBatchException.class,operation::run).getCode()); }
}
