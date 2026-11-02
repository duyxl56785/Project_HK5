package com.projecthk5.tv5integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.catalog.CatalogCheckoutBatchPreflight;
import com.projecthk5.productservice.ProductServiceApplication;
import com.projecthk5.productservice.catalog.*;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual Product writes/reads -> explicit TEST mapping -> actual PURE Order preflight, not Checkout wiring. */
@SpringBootTest(classes = ProductServiceApplication.class)
@Import(ProductCatalogCompositionPostgresTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgres-integration")
class ProductCatalogCheckoutPreflightPostgresTest {
    @Container static final PostgreSQLContainer<?> PRODUCT = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @Container static final PostgreSQLContainer<?> ORDER = new PostgreSQLContainer<>("postgres:16.6-alpine")
            .withDatabaseName("tv5_order_observation");
    private static final JsonNode FIXTURE = fixture();
    private static final JsonNode A = FIXTURE.get("stores").get(0), B = FIXTURE.get("stores").get(1);
    private static final UUID CATEGORY = id(FIXTURE, "categoryId");
    private static final List<String> ORDER_TABLES = List.of("inventory_operation_journal", "outbox_events",
            "order_items", "seller_orders", "checkout_attempts", "orders");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PRODUCT::getJdbcUrl);
        r.add("spring.datasource.username", PRODUCT::getUsername);
        r.add("spring.datasource.password", PRODUCT::getPassword);
    }
    @Autowired ProductApplicationService application;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate productDb;
    @Autowired PlatformTransactionManager transactions;
    private JdbcTemplate orderDb;
    private CatalogBatchValidator catalog;
    private String dependencyMode;
    private int consumerCalls;

    @BeforeEach void resetOnlyTheTwoDisposableDatabases() {
        assertTrue(PRODUCT.isRunning() && ORDER.isRunning());
        productDb.update("DELETE FROM products");
        productDb.update("UPDATE categories SET parent_id=NULL");
        productDb.update("DELETE FROM categories");
        productDb.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "TV5 preflight fixture");
        var dataSource = new DriverManagerDataSource(ORDER.getJdbcUrl(), ORDER.getUsername(), ORDER.getPassword());
        String migrations = Objects.requireNonNull(System.getProperty("tv5.order.migrations"));
        Flyway.configure().dataSource(dataSource).locations("filesystem:" + migrations.replace('\\', '/')).load().migrate();
        orderDb = new JdbcTemplate(dataSource);
        for (String table : ORDER_TABLES) orderDb.update("DELETE FROM " + table);
        assertEquals(2, orderDb.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        dependencyMode = "complete"; consumerCalls = 0;
        CurrentStoreFactsSource storeFixture = stores -> stores.stream()
                .filter(store -> !(dependencyMode.equals("missing-store") && store.equals(id(B, "storeId"))))
                .map(store -> {
                    JsonNode f = store.equals(id(A, "storeId")) ? A : store.equals(id(B, "storeId")) ? B : null;
                    if (f == null) throw new IllegalStateException("No synthetic Store facts");
                    Boolean house = dependencyMode.equals("unknown-house") && f == B ? null : f.get("houseStore").booleanValue();
                    UUID seller = dependencyMode.equals("same-seller-two-stores") ? id(B, "sellerId") : id(f, "sellerId");
                    return new CurrentStoreFactsSource.StoreFacts(store, seller, actor(f), true, true, house,
                            !dependencyMode.equals("stale-store"));
                }).toList();
        var beans = new StaticListableBeanFactory(); beans.addBean("storeFixture", storeFixture);
        catalog = new CatalogBatchValidator(new ProductCatalogSnapshotSource(products,
                beans.getBeanProvider(CurrentStoreFactsSource.class)));
    }

    @Test void dTwoStoreReadyControlPreservesProviderSkuAndRequestOrderWithoutWrites() {
        var first = active(A); var second = active(B);
        var wanted = List.of(expected(second, B), expected(first, A));
        var before = witness();
        var ready = evaluate(wanted);
        assertEquals(1, consumerCalls);
        assertEquals(List.of(second.listing().id().toString(), first.listing().id().toString()),
                ready.stream().map(CatalogCheckoutBatchPreflight.VerifiedProduct::productId).toList());
        assertEquals(List.of(B.get("inventorySku").textValue(), A.get("inventorySku").textValue()),
                ready.stream().map(CatalogCheckoutBatchPreflight.VerifiedProduct::inventorySku).toList());
        assertEquals(id(B, "storeId").toString(), ready.getFirst().storeId());
        assertEquals(id(B, "sellerId").toString(), ready.getFirst().sellerId());
        assertEquals("VND", ready.getFirst().currency());
        assertNotEquals(ready.getFirst().productId(), ready.getFirst().inventorySku());
        assertThrows(UnsupportedOperationException.class, () -> ready.clear());
        assertEquals(before, witness());
    }

    @Test void aCommittedRepriceRejectsStaleExpectationThenAcceptsFreshExactPrice() {
        var first = active(A); var second = active(B);
        var oldExpected = expected(first, A);
        first = application.deactivate(actor(A), first.listing().id(), first.version());
        first = application.edit(actor(A), first.listing().id(), first.version(), CATEGORY, "Repriced",
                new BigDecimal("1000000000"), "VND");
        first = application.submit(actor(A), first.listing().id(), first.version());
        first = application.approve(admin(), first.listing().id(), first.version());
        var before = witness();
        rejected(List.of(oldExpected, expected(second, B)), ItemCode.PRICE_CHANGED);
        var fresh = evaluate(List.of(expected(first, A), expected(second, B)));
        assertEquals(0, new BigDecimal("1000000000").compareTo(fresh.getFirst().unitPrice()));
        assertEquals(0, new BigDecimal("10000").compareTo(oldExpected.expectedUnitPrice()));
        assertEquals(before, witness());
    }

    @ParameterizedTest @ValueSource(strings = {"deactivate", "suspend"})
    void bCommittedUnsellableLifecycleCannotPassPreflight(String operation) {
        var first = active(A); var second = active(B);
        var wanted = List.of(expected(first, A), expected(second, B));
        if (operation.equals("deactivate")) application.deactivate(actor(A), first.listing().id(), first.version());
        else application.suspend(admin(), first.listing().id(), first.version(), "Fixture suspension");
        var before = witness();
        rejected(wanted, ItemCode.PRODUCT_NOT_SELLABLE);
        assertEquals(before, witness());
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "missing-store", "unknown-house", "same-seller-two-stores", "stale-store"})
    void cInvalidOrUnverifiedMemberNeverSuppliesAPartialReadySubset(String mode) {
        var first = active(A); var second = active(B);
        var wanted = List.of(expected(first, A), expected(second, B));
        if (mode.equals("absent")) wanted = List.of(new ExpectedItem(UUID.randomUUID(), id(A, "storeId"),
                id(A, "sellerId"), BigDecimal.ONE, "VND"), expected(second, B));
        dependencyMode = mode;
        var before = witness();
        if (mode.equals("absent")) rejected(wanted, ItemCode.PRODUCT_NOT_FOUND);
        else {
            var input = wanted;
            var failure = assertThrows(CatalogBatchException.class, () -> evaluate(input));
            assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, failure.getCode());
            assertEquals(0, consumerCalls, "Unverified provider cannot yield a consumer response");
            assertFalse(failure.getMessage().contains(actor(B)));
        }
        assertEquals(before, witness());
    }

    @ParameterizedTest @ValueSource(strings = {"store", "seller"})
    void dCallerExpectationCannotReassignProviderStoreOrSeller(String field) {
        var first = active(A); var second = active(B);
        var forged = new ExpectedItem(first.listing().id(), field.equals("store") ? id(B, "storeId") : id(A, "storeId"),
                field.equals("seller") ? id(B, "sellerId") : id(A, "sellerId"), first.listing().unitPrice(), "VND");
        var before = witness();
        rejected(List.of(forged, expected(second, B)), ItemCode.OWNERSHIP_MISMATCH);
        assertEquals(before, witness());
    }

    @Test void eRejectedPreflightRollsBackAnEarlierProductWriteAndLeavesOrderTablesUntouched() {
        var first = active(A); var second = active(B);
        var wanted = List.of(expected(first, A), expected(second, B));
        var before = witness();
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            application.deactivate(actor(A), first.listing().id(), first.version());
            evaluate(wanted);
        }));
        assertEquals(1, consumerCalls);
        assertEquals(before, witness(), "Product rollback and separate Order DB observations must remain unchanged");
        assertEquals(first, application.findById(actor(A), first.listing().id()));
    }

    private void rejected(List<ExpectedItem> wanted, ItemCode code) {
        Result result = catalog.validate(wanted);
        assertFalse(result.valid()); assertEquals(code, result.items().getFirst().code());
        assertEquals(ItemCode.READY, result.items().get(1).code(), "Companion was individually eligible");
        assertTrue(result.items().stream().allMatch(item -> item.snapshot() == null), "No usable READY prefix");
        var response = candidateMapping(result);
        assertFalse(response.allReady());
        assertThrows(IllegalStateException.class, () -> consume(wanted, response));
    }

    private List<CatalogCheckoutBatchPreflight.VerifiedProduct> evaluate(List<ExpectedItem> wanted) {
        return consume(wanted, candidateMapping(catalog.validate(wanted)));
    }

    private List<CatalogCheckoutBatchPreflight.VerifiedProduct> consume(List<ExpectedItem> wanted,
            CatalogCheckoutBatchPreflight.BatchResponse response) {
        consumerCalls++;
        return CatalogCheckoutBatchPreflight.requireFullyReady(wanted.stream().map(item ->
                new CatalogCheckoutBatchPreflight.Expected(item.productId().toString(), item.expectedStoreId().toString(),
                        item.expectedSellerId().toString(), item.expectedUnitPrice(), item.expectedCurrency())).toList(), response);
    }

    /** QA-only record mapping: copies existing fields/codes without selecting a transport or error policy. */
    private CatalogCheckoutBatchPreflight.BatchResponse candidateMapping(Result result) {
        return new CatalogCheckoutBatchPreflight.BatchResponse(CatalogCheckoutBatchPreflight.CONTRACT_VERSION, result.valid(),
                result.items().stream().map(item -> {
                    Snapshot s = item.snapshot();
                    var copied = s == null ? null : new CatalogCheckoutBatchPreflight.VerifiedProduct(s.productId().toString(),
                            s.inventorySku(), s.storeId().toString(), s.sellerId().toString(), s.unitPrice(), s.currency(),
                            s.available(), s.sellerApproved(), s.storeOpen(), s.houseStore());
                    return new CatalogCheckoutBatchPreflight.ItemResult(item.productId().toString(), item.code().name(), copied);
                }).toList());
    }

    private Map<String, String> witness() {
        Map<String, String> result = new TreeMap<>();
        for (String table : List.of("products", "categories")) result.put("product." + table, rows(productDb, table));
        for (String table : ORDER_TABLES) result.put("order." + table, rows(orderDb, table));
        return result;
    }
    private String rows(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "
                + table + " t", String.class);
    }
    private StoredProduct active(JsonNode f) {
        var p = application.create(actor(f), id(f, "storeId"), CATEGORY, f.get("inventorySku").textValue(),
                "TV5 preflight fixture", new BigDecimal(f.get("unitPrice").textValue()), "VND");
        p = application.submit(actor(f), p.listing().id(), p.version());
        return application.approve(admin(), p.listing().id(), p.version());
    }
    private ExpectedItem expected(StoredProduct p, JsonNode f) {
        return new ExpectedItem(p.listing().id(), id(f, "storeId"), id(f, "sellerId"), p.listing().unitPrice(), "VND");
    }
    private static String actor(JsonNode f) { return f.get("actorReference").textValue(); }
    private static String admin() { return FIXTURE.get("moderatorReference").textValue(); }
    private static UUID id(JsonNode f, String name) { return UUID.fromString(f.get(name).textValue()); }
    private static JsonNode fixture() {
        try (var in = ProductCatalogCheckoutPreflightPostgresTest.class.getResourceAsStream("/tv5-two-store.json")) {
            if (in == null) throw new IllegalStateException("Missing synthetic fixture");
            return new ObjectMapper().readTree(in);
        } catch (IOException e) { throw new IllegalStateException("Unreadable synthetic fixture", e); }
    }
}
