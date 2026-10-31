package com.projecthk5.tv5integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.ProductServiceApplication;
import com.projecthk5.productservice.catalog.CatalogBatchException;
import com.projecthk5.productservice.catalog.CatalogBatchValidator;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.catalog.CurrentStoreFactsSource;
import com.projecthk5.productservice.catalog.ProductCatalogSnapshotSource;
import com.projecthk5.productservice.product.*;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.productservice.product.persistence.ProductRepository;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
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

/** Real lifecycle/JPA/Flyway/PG -> real batch reader; identity/Store/House below are TEST ONLY. */
@SpringBootTest(classes = ProductServiceApplication.class)
@Import(ProductCatalogCompositionPostgresTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgres-integration")
class ProductCatalogCompositionPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final JsonNode FIXTURE = fixture();
    private static final JsonNode A = FIXTURE.get("stores").get(0), B = FIXTURE.get("stores").get(1);
    private static final UUID CATEGORY = id(FIXTURE, "categoryId");
    private static final String ADMIN = FIXTURE.get("moderatorReference").textValue();
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired ProductApplicationService application;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired FixturePolicy policy;
    private CatalogBatchValidator catalog;

    @BeforeEach void resetOnlyDisposableFixtureDatabase() {
        assertTrue(PG.isRunning());
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "TV5 composition fixture");
        policy.unknownHouse = false;
        CurrentStoreFactsSource facts = ids -> ids.stream().map(store -> {
            JsonNode f = store.equals(id(A, "storeId")) ? A : B;
            Boolean house = f.get("houseStore").booleanValue();
            if (policy.unknownHouse && store.equals(id(B, "storeId"))) house = null;
            return new CurrentStoreFactsSource.StoreFacts(store, id(f, "sellerId"), actor(f), true, true,
                    house, true);
        }).toList();
        var beans = new StaticListableBeanFactory();
        beans.addBean("fixtureStoreFacts", facts);
        catalog = new CatalogBatchValidator(new ProductCatalogSnapshotSource(products,
                beans.getBeanProvider(CurrentStoreFactsSource.class)));
    }

    @Test void committedLifecycleDrivesCatalogVisibilityWithoutDirectStateSql() {
        StoredProduct first = create(A), second = active(B);
        withheld(first, second, ItemCode.PRODUCT_NOT_SELLABLE);
        first = application.submit(actor(A), first.listing().id(), first.version());
        withheld(first, second, ItemCode.PRODUCT_NOT_SELLABLE);
        first = application.reject(ADMIN, first.listing().id(), first.version(), "Fixture revision required");
        withheld(first, second, ItemCode.PRODUCT_NOT_SELLABLE);
        first = application.submit(actor(A), first.listing().id(), first.version());
        first = application.approve(ADMIN, first.listing().id(), first.version());
        Result ready = catalog.validate(expected(first, second));
        assertTrue(ready.valid());
        assertEquals(A.get("inventorySku").textValue(), ready.items().getFirst().snapshot().inventorySku());
        assertNotEquals(first.listing().id().toString(), ready.items().getFirst().snapshot().inventorySku());
        assertEquals(id(A, "sellerId"), ready.items().getFirst().snapshot().sellerId());
        first = application.suspend(ADMIN, first.listing().id(), first.version(), "Fixture suspension");
        withheld(first, second, ItemCode.PRODUCT_NOT_SELLABLE);
        assertEquals("SUSPENDED", jdbc.queryForObject("SELECT status FROM products WHERE id=?", String.class, first.listing().id()));
    }

    @Test void changedPriceRejectsTheWholeCartExpectationAndDoesNotRewriteEarlierValueObjects() {
        StoredProduct first = active(A), second = active(B);
        List<ExpectedItem> cartExpectation = expected(first, second);
        Result before = catalog.validate(cartExpectation);
        first = application.deactivate(actor(A), first.listing().id(), first.version());
        first = application.edit(actor(A), first.listing().id(), first.version(), CATEGORY, "Updated fixture",
                new BigDecimal("1000000000"), "VND");
        first = application.submit(actor(A), first.listing().id(), first.version());
        first = application.approve(ADMIN, first.listing().id(), first.version());
        Result stale = catalog.validate(cartExpectation);
        assertFalse(stale.valid());
        assertEquals(ItemCode.PRICE_CHANGED, stale.items().getFirst().code());
        assertTrue(stale.items().stream().allMatch(i -> i.snapshot() == null));
        Result fresh = catalog.validate(expected(first, second));
        assertTrue(fresh.valid());
        assertEquals(0, new BigDecimal("1000000000").compareTo(fresh.items().getFirst().snapshot().unitPrice()));
        assertEquals(0, new BigDecimal("10000").compareTo(before.items().getFirst().snapshot().unitPrice()));
        assertEquals(A.get("inventorySku").textValue(), fresh.items().getFirst().snapshot().inventorySku());
        assertEquals("VND", fresh.items().getFirst().snapshot().currency());
    }

    @Test void foreignStoreDenialRollsBackAnEarlierLifecycleWriteInTheSameTransaction() {
        StoredProduct first = active(A), second = active(B);
        var failure = assertThrows(ProductPersistenceException.class, () -> transaction().executeWithoutResult(tx -> {
            application.deactivate(actor(A), first.listing().id(), first.version());
            application.deactivate(actor(A), second.listing().id(), second.version());
        }));
        // Approved V01: verified owner scope conceals a foreign Product as NOT_FOUND.
        assertEquals(ProductPersistenceException.Code.NOT_FOUND, failure.getCode());
        var unknown = assertThrows(ProductRuleException.class,
                () -> application.findById("unknown-tv5-fixture-actor", first.listing().id()));
        assertEquals(ProductRuleException.Code.ACCESS_DENIED, unknown.getCode());
        assertEquals(first, application.findById(actor(A), first.listing().id()));
        assertEquals(second, application.findById(actor(B), second.listing().id()));
        assertTrue(catalog.validate(expected(first, second)).valid());
    }

    @Test void successfulLocalWriteCannotSupplyUnknownHouseAuthorityToTheBatchReader() {
        StoredProduct first = active(A), second = active(B);
        assertTrue(catalog.validate(expected(first, second)).valid());
        policy.unknownHouse = true;
        var unavailable = assertThrows(CatalogBatchException.class, () -> catalog.validate(expected(first, second)));
        assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE, unavailable.getCode());
        policy.unknownHouse = false;
        assertTrue(catalog.validate(expected(first, second)).valid());
    }

    @Test void rolledBackApprovalCannotBecomeCommittedCatalogEligibility() {
        StoredProduct draft = create(A), second = active(B);
        StoredProduct pending = application.submit(actor(A), draft.listing().id(), draft.version());
        assertThrows(FixtureRollback.class, () -> transaction().executeWithoutResult(tx -> {
            StoredProduct inside = application.approve(ADMIN, pending.listing().id(), pending.version());
            assertTrue(catalog.validate(expected(inside, second)).valid());
            throw new FixtureRollback();
        }));
        assertEquals(pending, application.findById(actor(A), pending.listing().id()));
        withheld(pending, second, ItemCode.PRODUCT_NOT_SELLABLE);
    }

    private StoredProduct create(JsonNode f) {
        return application.create(actor(f), id(f, "storeId"), CATEGORY, f.get("inventorySku").textValue(),
                "TV5 fixture Product", new BigDecimal(f.get("unitPrice").textValue()), f.get("currency").textValue());
    }
    private StoredProduct active(JsonNode f) {
        var p = create(f); p = application.submit(actor(f), p.listing().id(), p.version());
        return application.approve(ADMIN, p.listing().id(), p.version());
    }
    private List<ExpectedItem> expected(StoredProduct first, StoredProduct second) {
        return List.of(item(first, A), item(second, B));
    }
    private ExpectedItem item(StoredProduct p, JsonNode f) {
        return new ExpectedItem(p.listing().id(), id(f, "storeId"), id(f, "sellerId"), p.listing().unitPrice(), "VND");
    }
    private void withheld(StoredProduct first, StoredProduct second, ItemCode code) {
        Result result = catalog.validate(expected(first, second));
        assertFalse(result.valid()); assertEquals(code, result.items().getFirst().code());
        assertTrue(result.items().stream().allMatch(i -> i.snapshot() == null));
    }
    private TransactionTemplate transaction() {
        var t = new TransactionTemplate(transactions); t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); return t;
    }
    private static String actor(JsonNode f) { return f.get("actorReference").textValue(); }
    private static UUID id(JsonNode f, String name) { return UUID.fromString(f.get(name).textValue()); }
    private static JsonNode fixture() {
        try (var input = ProductCatalogCompositionPostgresTest.class.getResourceAsStream("/tv5-two-store.json")) {
            if (input == null) throw new IllegalStateException("TV5 synthetic fixture is missing");
            return new ObjectMapper().readTree(input);
        } catch (IOException failure) { throw new IllegalStateException("TV5 synthetic fixture cannot be read", failure); }
    }
    private static final class FixtureRollback extends RuntimeException { }
    @TestConfiguration static class AuthorityFixture { @Bean FixturePolicy fixturePolicy() { return new FixturePolicy(); } }
    static final class FixturePolicy implements ProductAccessPolicy {
        volatile boolean unknownHouse;
        @Override public UUID currentManagementStore(String actor) {
            if (actor(A).equals(actor)) return id(A, "storeId");
            if (actor(B).equals(actor)) return id(B, "storeId");
            throw denied(); // Explicit synthetic fixtures only; no default Store or production authority.
        }
        public StoreAccess requireSellerManagement(String actor, UUID store) {
            StoreAccess facts = readStore(store);
            if (!Set.of(actor(A), actor(B)).contains(actor) || !facts.ownerReference().equals(actor)) throw denied();
            return facts;
        }
        public StoreAccess requireModeration(String actor, UUID store) {
            if (!ADMIN.equals(actor)) throw denied();
            return readStore(store);
        }
        public StoreAccess readStore(UUID store) {
            JsonNode f = store.equals(id(A, "storeId")) ? A : store.equals(id(B, "storeId")) ? B : null;
            if (f == null) throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Unknown fixture Store");
            return new StoreAccess(store, id(f, "sellerId"), actor(f), true, true, f.get("houseStore").booleanValue());
        }
        private ProductRuleException denied() { return new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture current Store denied"); }
    }
}
