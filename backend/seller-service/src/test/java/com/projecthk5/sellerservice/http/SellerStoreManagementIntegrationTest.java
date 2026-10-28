package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.store.*;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SellerStoreManagementIntegrationTest.Wiring.class)
@Testcontainers
@Tag("postgresql")
class SellerStoreManagementIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final SellerIdentity A = new SellerIdentity(" Exact-Owner ");
    private static final SellerIdentity B = new SellerIdentity("exact-owner");
    private static final SellerIdentity ADMIN = new SellerIdentity("test-only-admin");
    @Autowired SellerStoreManagementHandler commands;
    @Autowired SellerStoreReadHandler reads;
    @Autowired SellerPersistenceService sellers;
    @Autowired StorePersistenceService stores;
    @Autowired AuthorityFixture authority;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc http;
    @Autowired ApplicationContext context;

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
    }

    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM seller_profiles");
        as(A, false, false);
        authority.active = true;
        authority.available = true;
    }

    @Test void registrationResubmitAndModerationUseDurableExistingLifecycle() {
        var pending = commands.register("{}");
        assertEquals(SellerStatus.PENDING_REVIEW, pending.status());
        assertEquals(A.reference(), jdbc.queryForObject("SELECT owner_reference FROM seller_profiles WHERE id=?", String.class, pending.id()));
        as(ADMIN, false, true);
        var rejected = commands.reject(pending.id(), "{\"expectedVersion\":\"0\",\"reason\":\"Fix details\"}");
        assertEquals(SellerStatus.REJECTED, rejected.status());
        as(A, false, false);
        var resubmitted = commands.resubmit("{\"expectedVersion\":\"1\"}");
        assertEquals(pending.id(), resubmitted.id());
        assertEquals(SellerStatus.PENDING_REVIEW, resubmitted.status());
        assertNull(resubmitted.reviewReason());
        as(ADMIN, false, true);
        var approved = commands.approve(pending.id(), "{\"expectedVersion\":\"2\"}");
        assertEquals(SellerStatus.APPROVED, approved.status());
        assertEquals(approved, commands.sellerForModeration(pending.id()));
        var suspended = commands.suspend(pending.id(), "{\"expectedVersion\":\"3\",\"reason\":\"Review required\"}");
        assertEquals(SellerStatus.SUSPENDED, suspended.status());
        var reinstated = commands.reinstate(pending.id(), "{\"expectedVersion\":\"4\"}");
        assertEquals(SellerStatus.APPROVED, reinstated.status());
        assertNull(reinstated.reviewReason());
        assertEquals("5", reinstated.version());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class));
    }

    @Test void ownStoreCreateReviseCloseReopenPreservesServerIdentityAndVersions() {
        approved(A);
        var created = commands.createStore("{\"name\":\"Own store\",\"slug\":\"own-store\"}");
        var revised = commands.updateStore("{\"name\":\"Updated\",\"slug\":\"updated-store\",\"expectedVersion\":\"0\"}");
        assertEquals(created.id(), revised.id());
        assertEquals(created.sellerId(), revised.sellerId());
        assertEquals("1", revised.version());
        assertEquals("Updated", reads.storeOwn().name());
        var closed = commands.setStoreOpen("{\"open\":false,\"expectedVersion\":\"1\"}");
        assertFalse(closed.open());
        assertFalse(reads.storeOwn().open());
        var reopened = commands.setStoreOpen("{\"open\":true,\"expectedVersion\":\"2\"}");
        assertTrue(reopened.open());
        assertEquals("3", reopened.version());
    }

    @Test void staleVersionCannotOverwriteCommittedStoreOrSellerStatus() {
        var seller = approved(A);
        var created = commands.createStore("{\"name\":\"Original\",\"slug\":\"original\"}");
        commands.updateStore("{\"name\":\"Committed\",\"slug\":\"committed\",\"expectedVersion\":\"0\"}");
        assertEquals(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                assertThrows(StorePersistenceException.class, () -> commands.updateStore(
                        "{\"name\":\"Stale\",\"slug\":\"stale\",\"expectedVersion\":\"0\"}")).getCode());
        assertEquals("Committed", reads.storeOwn().name());
        assertEquals(created.id(), reads.storeOwn().id());
        as(ADMIN, false, true);
        assertEquals(SellerPersistenceException.Code.SELLER_VERSION_CONFLICT,
                assertThrows(SellerPersistenceException.class, () -> commands.suspend(seller,
                        "{\"expectedVersion\":\"0\",\"reason\":\"Old snapshot\"}")).getCode());
        assertEquals(SellerStatus.APPROVED, commands.sellerForModeration(seller).status());
    }

    @Test void customerCannotModerateExistingOrMissingSellerAndAdminCannotSelfModerate() {
        var target = commands.register("{}");
        for (UUID id : new UUID[]{target.id(), UUID.randomUUID()}) {
            assertEquals(SellerRuleException.Code.ACCESS_DENIED,
                    assertThrows(SellerRuleException.class, () -> commands.approve(id, "{\"expectedVersion\":\"0\"}")).getCode());
            assertEquals(SellerRuleException.Code.ACCESS_DENIED,
                    assertThrows(SellerRuleException.class, () -> commands.sellerForModeration(id)).getCode());
        }
        as(A, false, true);
        assertEquals(SellerRuleException.Code.SELF_MODERATION,
                assertThrows(SellerRuleException.class, () -> commands.approve(target.id(), "{\"expectedVersion\":\"0\"}")).getCode());
        assertEquals(SellerStatus.PENDING_REVIEW, commands.sellerForModeration(target.id()).status());
    }

    @Test void storeIdentityAndOwnerCannotBeChosenThroughJson() {
        approved(A);
        var ownA = commands.createStore("{\"name\":\"A\",\"slug\":\"store-a\"}");
        approved(B);
        var ownB = commands.createStore("{\"name\":\"B\",\"slug\":\"store-b\"}");
        as(A, true, false);
        String forged = "{\"name\":\"Hacked\",\"slug\":\"hacked\",\"expectedVersion\":\"0\",\"storeId\":\"" + ownB.id() + "\"}";
        assertThrows(SellerStoreJson.InvalidInput.class, () -> commands.updateStore(forged));
        assertEquals(ownA.id(), reads.storeOwn().id());
        assertEquals("A", reads.storeOwn().name());
        as(B, true, false);
        assertEquals("B", reads.storeOwn().name());
        assertEquals(ownB.id(), reads.storeOwn().id());
    }

    @Test void duplicateOwnerAndSlugPreserveExistingRows() {
        approved(A);
        var first = commands.createStore("{\"name\":\"A\",\"slug\":\"unique-store\"}");
        assertEquals(SellerPersistenceException.Code.SELLER_ALREADY_EXISTS,
                assertThrows(SellerPersistenceException.class, () -> commands.register("{}")).getCode());
        approved(B);
        assertEquals(StorePersistenceException.Code.SLUG_ALREADY_EXISTS,
                assertThrows(StorePersistenceException.class, () -> commands.createStore("{\"name\":\"B\",\"slug\":\"unique-store\"}")).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM stores", Integer.class));
        as(A, true, false);
        assertEquals(first.id(), reads.storeOwn().id());
    }

    @Test void revokedRoleAndCurrentSellerSuspensionBlockStoreWrites() {
        UUID seller = approved(A);
        commands.createStore("{\"name\":\"A\",\"slug\":\"store-a\"}");
        authority.sellerRole = false;
        assertEquals(StoreRuleException.Code.ACCESS_DENIED,
                assertThrows(StoreRuleException.class, () -> commands.setStoreOpen("{\"open\":false,\"expectedVersion\":\"0\"}")).getCode());
        assertNotNull(reads.sellerOwn().id());
        authority.sellerRole = true;
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED',review_reason='Private reason',version=version+1 WHERE id=?", seller);
        assertEquals(StoreRuleException.Code.SELLER_NOT_APPROVED,
                assertThrows(StoreRuleException.class, () -> commands.updateStore(
                        "{\"name\":\"Denied\",\"slug\":\"denied\",\"expectedVersion\":\"0\"}")).getCode());
        assertEquals("A", jdbc.queryForObject("SELECT name FROM stores", String.class));
    }

    @Test void requiredStoreTransactionRollsBackAndNextCallUsesDatabaseFacts() {
        approved(A);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            commands.createStore("{\"name\":\"Rolled back\",\"slug\":\"rollback\"}");
            tx.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM stores", Integer.class));
        var committed = commands.createStore("{\"name\":\"Committed\",\"slug\":\"committed\"}");
        assertEquals("Committed", committed.name());
        assertEquals("0", committed.version());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/sellers/me", "/api/stores/me", "/api/admin/sellers"})
    void rawHttpIdentityClaimsCannotReachCommandsOrChangeDatabase(String path) throws Exception {
        http.perform(post(path).header("Authorization", "Bearer unverified")
                        .header("X-User-Id", A.reference()).header("X-Roles", "ADMIN,SELLER")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM stores", Integer.class));
    }

    @Test void handlersAreUnregisteredAndNoLocalPasswordAuthorityIsCreated() {
        assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty());
        assertNull(SellerStoreManagementHandler.class.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertNull(SellerStoreManagementHandler.class.getAnnotation(org.springframework.stereotype.Component.class));
    }

    private UUID approved(SellerIdentity owner) {
        as(owner, false, false);
        var created = commands.register("{}");
        jdbc.update("UPDATE seller_profiles SET status='APPROVED',version=version+1 WHERE id=?", created.id());
        as(owner, true, false);
        return created.id();
    }

    private void as(SellerIdentity actor, boolean seller, boolean admin) {
        authority.actor = actor;
        authority.sellerRole = seller;
        authority.admin = admin;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Wiring {
        @Bean AuthorityFixture authority(SellerProfileRepository repository) { return new AuthorityFixture(repository); }
        @Bean SellerPersistenceService sellers(SellerProfileRepository repository, AuthorityFixture authority) {
            return new SellerPersistenceService(repository, authority);
        }
        @Bean StorePersistenceService stores(StoreRepository repository, EntityManager em, AuthorityFixture authority) {
            return new StorePersistenceService(repository, em, authority);
        }
        @Bean SellerStoreReadHandler reads(AuthorityFixture authority, SellerPersistenceService sellers, StorePersistenceService stores) {
            return new SellerStoreReadHandler(authority, sellers, stores);
        }
        @Bean SellerStoreManagementHandler commands(AuthorityFixture authority, SellerPersistenceService sellers, StorePersistenceService stores) {
            return new SellerStoreManagementHandler(authority, sellers, stores, new SellerStoreJson());
        }
    }

    static class AuthorityFixture extends SellerStoreReadIntegrationTest.FixtureAuthority {
        boolean admin;
        AuthorityFixture(SellerProfileRepository repository) { super(repository); }
        @Override public void requireSellerModerationPermission(SellerIdentity actor) {
            requireAuthenticated(actor);
            if (!admin) throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Test policy denied");
        }
    }
}
