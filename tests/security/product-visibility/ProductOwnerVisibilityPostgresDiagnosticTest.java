package com.projecthk5.productservice.product.http;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.ProductServiceApplication;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductRuleException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** TV1 decision6430723502; prepared handler records only. Real PG, existing TEST authority, no HTTP/JWT proof. */
@SpringBootTest(classes = ProductServiceApplication.class)
@Import(ProductManagementHandlerIntegrationTest.AuthorityFixture.class)
@Testcontainers
@Tag("postgres-integration")
class ProductOwnerVisibilityPostgresDiagnosticTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID STORE = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_STORE = UUID.fromString("72000000-0000-4000-8000-000000000002");
    private static final UUID CATEGORY = UUID.fromString("72000000-0000-4000-8000-000000000003");
    private static final String OWNER = "owner-a-fixture", OTHER = "owner-b-fixture", ADMIN = "admin-fixture";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired ProductApplicationService application;
    @Autowired ProductManagementHandlerIntegrationTest.FixturePolicy policy;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private final ProductManagementJson json = new ProductManagementJson();
    private ProductManagementResponse mine, foreign;

    @BeforeEach void resetOnlyDisposableDatabaseAndExistingOwnerFixture() {
        assertTrue(PG.isRunning());
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", CATEGORY, "Visibility fixture");
        policy.barrier = null; policy.ownerIsAdmin = false;
        mine = handler(OWNER).create(createPayload(STORE, "mine"));
        foreign = handler(OTHER).create(createPayload(OTHER_STORE, "foreign"));
        foreign = handler(OTHER).submit(foreign.id(), version(foreign));
        foreign = handler(ADMIN).approve(foreign.id(), version(foreign));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "edit", "submit", "deactivate"})
    void scopedOwnerOperationsMustConcealForeignAndMissingIdsWithoutWrites(String operation) {
        // OWNER fixture has its own eligible Store. Public JWT/current Store verification is NOT modeled here.
        var foreignFailure = deniedWithRollback(operation, foreign.id(), "companion-foreign");
        var missingFailure = deniedWithRollback(operation, UUID.randomUUID(), "companion-missing");
        var concealed = new ProductManagementErrors.Failure(404, "PRODUCT_NOT_FOUND", "Product was not found");
        assertEquals(concealed, missingFailure, "Missing target control must use the existing generic404");
        assertEquals(concealed, foreignFailure, "TV1 scoped decision: same prepared status/code/message for foreign target");
        assertEquals(missingFailure, foreignFailure);
    }

    @Test void sameStoreReadAndVersionConflictRemainDistinctControls() {
        assertEquals(mine, handler(OWNER).findById(mine.id()));
        var changed = handler(OWNER).edit(mine.id(), editPayload(mine.version()));
        var stale = assertThrows(RuntimeException.class, () -> handler(OWNER).edit(mine.id(), editPayload(mine.version())));
        assertEquals(409, ProductManagementErrors.from(stale).status());
        var invalidState = assertThrows(RuntimeException.class, () -> handler(ADMIN).approve(changed.id(), version(changed)));
        assertEquals(409, ProductManagementErrors.from(invalidState).status());
        assertEquals(changed, handler(OWNER).findById(mine.id()));
    }

    @Test void selfModerationStillReturns403InsteadOfGlobal404Remapping() {
        var pending = handler(OWNER).submit(mine.id(), version(mine));
        policy.ownerIsAdmin = true;
        var denied = assertThrows(ProductRuleException.class, () -> handler(OWNER).approve(pending.id(), version(pending)));
        assertEquals(403, ProductManagementErrors.from(denied).status());
        assertEquals(pending, handler(OWNER).findById(mine.id()));
    }

    @ParameterizedTest @ValueSource(strings = {"denied", "unavailable"})
    void independentAuthorityDenialKeeps403Or503ForBothTargetShapes(String mode) {
        ProductManagementAuthority authority = () -> {
            throw new ProductRuleException(mode.equals("denied") ? ProductRuleException.Code.ACCESS_DENIED
                    : ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Fixture scope refused");
        };
        var closed = new ProductManagementHandler(authority, application, json);
        for (UUID id : new UUID[]{foreign.id(), UUID.randomUUID()}) {
            var denied = assertThrows(ProductRuleException.class, () -> closed.findById(id));
            assertEquals(mode.equals("denied") ? 403 : 503, ProductManagementErrors.from(denied).status());
        }
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    private ProductManagementErrors.Failure deniedWithRollback(String operation, UUID target, String companionSku) {
        var denied = assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            handler(OWNER).create(createPayload(STORE, companionSku));
            var h = handler(OWNER);
            switch (operation) {
                case "read" -> h.findById(target);
                case "edit" -> h.edit(target, editPayload(foreign.version()));
                case "submit" -> h.submit(target, version(foreign));
                case "deactivate" -> h.deactivate(target, version(foreign));
                default -> throw new AssertionError("Unknown fixture operation");
            }
        }));
        // Check rollback BEFORE the intended failing parity assertion.
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        assertEquals(mine, handler(OWNER).findById(mine.id()));
        assertEquals(foreign, handler(OTHER).findById(foreign.id()));
        return ProductManagementErrors.from(denied);
    }
    private ProductManagementHandler handler(String actor) { return new ProductManagementHandler(() -> actor, application, json); }
    private String createPayload(UUID store, String sku) {
        return "{\"storeId\":\"%s\",\"categoryId\":\"%s\",\"sku\":\"%s\",\"name\":\"Fixture\",\"unitPrice\":\"1\",\"currency\":\"VND\"}".formatted(store, CATEGORY, sku);
    }
    private String editPayload(String expectedVersion) {
        return "{\"categoryId\":\"%s\",\"name\":\"Edited fixture\",\"unitPrice\":\"1\",\"currency\":\"VND\",\"expectedVersion\":\"%s\"}".formatted(CATEGORY, expectedVersion);
    }
    private String version(ProductManagementResponse p) { return "{\"expectedVersion\":\"%s\"}".formatted(p.version()); }
}
