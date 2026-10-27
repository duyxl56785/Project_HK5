package com.projecthk5.productservice.product.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** JSON/application/PostgreSQL path; principal and Store policy below are TEST ONLY, not JWT/live authority. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProductManagementHandlerIntegrationTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgresql")
class ProductManagementHandlerIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID STORE = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_STORE = UUID.fromString("72000000-0000-4000-8000-000000000002");
    private static final UUID CATEGORY = UUID.fromString("72000000-0000-4000-8000-000000000003");
    private static final String OWNER = "owner-a-fixture", OTHER = "owner-b-fixture", ADMIN = "admin-fixture";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }
    @Autowired ProductApplicationService application;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired FixturePolicy policy;
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    private final ProductManagementJson json = new ProductManagementJson();

    @BeforeEach void resetDisposableProductsAndFixtures() {
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "Handler category");
        policy.barrier = null;
        policy.ownerIsAdmin = false;
    }

    @Test void jsonLifecyclePersistsAndReturnsOnlySafeExactFields() {
        var owner = handler(OWNER); var admin = handler(ADMIN);
        var product = owner.create(createPayload(STORE, "Exact-é"));
        assertEquals(ProductStatus.DRAFT, product.status());
        assertEquals("0", product.version());
        product = owner.edit(product.id(), editPayload(product.version(), "Edited"));
        product = owner.submit(product.id(), version(product));
        product = admin.reject(product.id(), reason(product));
        assertEquals("Fix details", jdbc.queryForObject("SELECT review_reason FROM products WHERE id=?", String.class, product.id()));
        product = owner.submit(product.id(), version(product));
        product = admin.approve(product.id(), version(product));
        product = owner.deactivate(product.id(), version(product));
        product = owner.submit(product.id(), version(product));
        product = admin.approve(product.id(), version(product));
        product = admin.suspend(product.id(), reason(product));
        assertEquals(ProductStatus.SUSPENDED, product.status());
        assertEquals("9", product.version());
        assertEquals("1", product.unitPrice());
        assertEquals("VND", product.currency());
        assertEquals("Exact-é", product.sku());
        assertEquals(product, owner.findById(product.id()));
        assertEquals("Fix details", jdbc.queryForObject("SELECT review_reason FROM products WHERE id=?", String.class, product.id()));
        assertFalse(json.write(product).contains("reviewReason"));
        assertFalse(json.write(product).contains(OWNER));
    }

    @ParameterizedTest @ValueSource(strings = {"create", "edit", "submit", "approve", "reject", "suspend", "deactivate", "read"})
    void otherSellerCannotAccessStoreOrProduct(String command) {
        var original = handler(OWNER).create(createPayload(STORE, "private"));
        handler(OTHER).create(createPayload(OTHER_STORE, "other-store"));
        if (List.of("edit", "submit", "deactivate", "read").contains(command)) {
            var failure = assertThrows(ProductPersistenceException.class, () -> invoke(handler(OTHER), command, original));
            assertEquals(new ProductManagementErrors.Failure(404, "PRODUCT_NOT_FOUND", "Product was not found"),
                    ProductManagementErrors.from(failure));
        } else {
            var failure = assertThrows(ProductRuleException.class, () -> invoke(handler(OTHER), command, original));
            assertEquals(ProductRuleException.Code.ACCESS_DENIED, failure.getCode());
        }
        assertEquals(original, handler(OWNER).findById(original.id()));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test void unavailableAuthorityCannotReadMissingProductOrWrite() {
        var handler = new ProductManagementHandler(() -> null, application, json);
        assertEquals(503, ProductManagementErrors.from(assertThrows(ProductRuleException.class,
                () -> handler.create(createPayload(STORE, "unavailable")))).status());
        assertEquals(503, ProductManagementErrors.from(assertThrows(ProductRuleException.class,
                () -> handler.findById(UUID.randomUUID()))).status());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "suspend"})
    void sellerCannotSelfModerateEvenWithAdditionalAdminAuthority(String command) {
        var owner = handler(OWNER);
        var product = owner.create(createPayload(STORE, "self"));
        product = owner.submit(product.id(), version(product));
        if (command.equals("suspend")) product = handler(ADMIN).approve(product.id(), version(product));
        policy.ownerIsAdmin = true;
        var original = product;
        var failure = assertThrows(ProductRuleException.class, () -> invoke(owner, command, original));
        assertEquals(ProductRuleException.Code.ACCESS_DENIED, failure.getCode());
        assertEquals(original, owner.findById(original.id()));
    }

    @Test void staleVersionAndInvalidStateMapToConflictAndDoNotWrite() {
        var owner = handler(OWNER);
        var original = owner.create(createPayload(STORE, "version"));
        var edited = owner.edit(original.id(), editPayload(original.version(), "Winner"));
        assertEquals(409, ProductManagementErrors.from(assertThrows(ProductPersistenceException.class,
                () -> owner.edit(original.id(), editPayload(original.version(), "Loser")))).status());
        var pending = owner.submit(edited.id(), version(edited));
        assertEquals(409, ProductManagementErrors.from(assertThrows(ProductRuleException.class,
                () -> owner.edit(pending.id(), editPayload(pending.version(), "Invalid state")))).status());
        assertEquals(pending, owner.findById(pending.id()));
    }

    @Test void requiredOuterRollbackRevertsJsonCommandsAndVersion() {
        var owner = handler(OWNER);
        var original = owner.create(createPayload(STORE, "existing"));
        assertThrows(ProductPersistenceException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            owner.create(createPayload(STORE, "rollback-created"));
            owner.edit(original.id(), editPayload(original.version(), "Rolled back"));
            owner.edit(original.id(), editPayload(original.version(), "Stale"));
            return null;
        }));
        assertEquals(original, owner.findById(original.id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test void concurrentJsonUpdatesOfSameVersionHaveExactlyOneWinner() throws Exception {
        var product = handler(OWNER).create(createPayload(STORE, "concurrent"));
        policy.barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    () -> editOutcome(product, "First"), () -> editOutcome(product, "Second")));
            var outcomes = new java.util.ArrayList<Object>();
            for (var result : results) outcomes.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(x -> x.equals("confirmed")).count());
            assertEquals(1, outcomes.stream().filter(x -> x.equals("conflict")).count());
        } finally { policy.barrier = null; }
        assertEquals("1", handler(OWNER).findById(product.id()).version());
    }

    @Test void preparationRegistersNoAuthorityHandlerOrHttpRoute() throws Exception {
        assertTrue(context.getBeansOfType(ProductManagementAuthority.class).isEmpty());
        assertTrue(context.getBeansOfType(ProductManagementHandler.class).isEmpty());
        mvc.perform(post("/api/products").contentType("application/json").content(createPayload(STORE, "route")))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    private String editOutcome(ProductManagementResponse product, String name) {
        try { handler(OWNER).edit(product.id(), editPayload(product.version(), name)); return "confirmed"; }
        catch (ProductPersistenceException conflict) {
            assertEquals(ProductPersistenceException.Code.STALE_VERSION, conflict.getCode());
            return "conflict";
        }
    }
    private ProductManagementHandler handler(String actor) { return new ProductManagementHandler(() -> actor, application, json); }
    private static String createPayload(UUID store, String sku) {
        return "{\"storeId\":\"%s\",\"categoryId\":\"%s\",\"sku\":\"%s\",\"name\":\"Product\",\"unitPrice\":\"1\",\"currency\":\"VND\"}".formatted(store, CATEGORY, sku);
    }
    private static String editPayload(String version, String name) {
        return "{\"categoryId\":\"%s\",\"name\":\"%s\",\"unitPrice\":\"1\",\"currency\":\"VND\",\"expectedVersion\":\"%s\"}".formatted(CATEGORY, name, version);
    }
    private static String version(ProductManagementResponse product) { return "{\"expectedVersion\":\"%s\"}".formatted(product.version()); }
    private static String reason(ProductManagementResponse product) { return "{\"expectedVersion\":\"%s\",\"reason\":\" Fix details \"}".formatted(product.version()); }
    private void invoke(ProductManagementHandler handler, String command, ProductManagementResponse product) {
        switch (command) {
            case "create" -> handler.create(createPayload(STORE, "forbidden"));
            case "edit" -> handler.edit(product.id(), editPayload(product.version(), "Forbidden"));
            case "submit" -> handler.submit(product.id(), version(product));
            case "approve" -> handler.approve(product.id(), version(product));
            case "reject" -> handler.reject(product.id(), reason(product));
            case "suspend" -> handler.suspend(product.id(), reason(product));
            case "deactivate" -> handler.deactivate(product.id(), version(product));
            case "read" -> handler.findById(product.id());
            default -> throw new AssertionError(command);
        }
    }
    @TestConfiguration static class AuthorityFixture {
        @Bean FixturePolicy productPolicy() { return new FixturePolicy(); }
    }
    static class FixturePolicy implements ProductAccessPolicy {
        volatile CyclicBarrier barrier;
        volatile boolean ownerIsAdmin;
        @Override public UUID currentManagementStore(String actor) {
            // Existing TEST accounts only; not a production current-account/Store provider.
            if (OWNER.equals(actor)) return STORE;
            if (OTHER.equals(actor)) return OTHER_STORE;
            throw denied();
        }
        @Override public StoreAccess requireSellerManagement(String actor, UUID store) {
            var facts = readStore(store);
            if (!facts.ownerReference().equals(actor)) throw denied();
            var waiting = barrier;
            if (waiting != null) try { waiting.await(10, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new AssertionError(failure); }
            return facts;
        }
        @Override public StoreAccess requireModeration(String actor, UUID store) {
            if (!ADMIN.equals(actor) && !(ownerIsAdmin && OWNER.equals(actor))) throw denied();
            return readStore(store);
        }
        @Override public StoreAccess readStore(UUID store) {
            if (!STORE.equals(store) && !OTHER_STORE.equals(store)) throw denied();
            return new StoreAccess(store, store, STORE.equals(store) ? OWNER : OTHER, true, true, false);
        }
        private ProductRuleException denied() { return new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture denial"); }
    }
}
