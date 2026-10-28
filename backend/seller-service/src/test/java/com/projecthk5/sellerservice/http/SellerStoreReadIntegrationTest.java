package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecthk5.sellerservice.seller.domain.*;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.*;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.*;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

/** Real PostgreSQL/Flyway/JPA, test-only authority. Not real JWT or positive HTTP acceptance. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SellerStoreReadIntegrationTest.Wiring.class)
@Testcontainers
@Tag("postgresql")
class SellerStoreReadIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final SellerIdentity A = new SellerIdentity(" Exact-Owner ");
    private static final SellerIdentity B = new SellerIdentity("exact-owner");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired SellerStoreReadHandler handler;
    @Autowired SellerPersistenceService sellers;
    @Autowired StorePersistenceService stores;
    @Autowired FixtureAuthority authority;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc http;

    @BeforeEach
    void resetIsolatedDatabase() {
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM seller_profiles");
        authority.actor = A;
        authority.active = true;
        authority.sellerRole = true;
        authority.available = true;
    }

    @Test
    void registrationIsDurableAndReadUsesExistingPrivateDto() {
        authority.sellerRole = false;
        var created = sellers.register(A);
        var read = handler.sellerOwn();
        assertEquals(created.id(), read.id());
        assertEquals("0", read.version());
        assertEquals(SellerStatus.PENDING_REVIEW, read.status());
        assertNull(read.reviewReason());
        assertEquals(A.reference(), jdbc.queryForObject(
                "SELECT owner_reference FROM seller_profiles WHERE id=?", String.class, created.id()));
        String json = new SellerStoreJson().writeSellerPrivate(read);
        assertFalse(json.contains("ownerReference"));
        assertFalse(json.contains(A.reference()));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2')", Integer.class));
    }

    @Test
    void twoAccountsOnlyReadTheirOwnSellerAndStore() {
        var sellerA = approved(A);
        var storeA = stores.create(A, "A", "store-a");
        authority.actor = B;
        var sellerB = approved(B);
        var storeB = stores.create(B, "B", "store-b");
        assertEquals(sellerB.id(), handler.sellerOwn().id());
        assertEquals(storeB.profile().id(), handler.storeOwn().id());
        authority.actor = A;
        assertEquals(sellerA.id(), handler.sellerOwn().id());
        var own = handler.storeOwn();
        assertEquals(storeA.profile().id(), own.id());
        assertEquals(sellerA.id(), own.sellerId());
        var json = new SellerStoreJson().writeStoreOwner(own);
        assertFalse(json.contains(storeB.profile().id().toString()));
        assertFalse(json.contains(sellerB.id().toString()));
        assertFalse(json.contains("ownerReference"));
    }

    @ParameterizedTest
    @EnumSource(SellerStatus.class)
    void ownSellerReadAllowsAllPersistedStatusesWithoutSellerRole(SellerStatus state) {
        var seller = sellers.register(A);
        setStatus(seller.id(), state);
        authority.sellerRole = false;
        var result = handler.sellerOwn();
        assertEquals(seller.id(), result.id());
        assertEquals(state, result.status());
        assertEquals(state == SellerStatus.REJECTED || state == SellerStatus.SUSPENDED ? "Private reason" : null,
                result.reviewReason());
    }

    @Test
    void missingSellerRemainsDomainAbsenceAfterAuthority() {
        assertEquals(SellerPersistenceException.Code.SELLER_NOT_FOUND,
                assertThrows(SellerPersistenceException.class, handler::sellerOwn).getCode());
    }

    @Test
    void missingStoreIsDistinctFromMissingCurrentSellerFacts() {
        assertThrows(IllegalStateException.class, handler::storeOwn);
        approved(A);
        assertEquals(StorePersistenceException.Code.STORE_NOT_FOUND,
                assertThrows(StorePersistenceException.class, handler::storeOwn).getCode());
    }

    @Test
    void closedOwnStoreRemainsReadableButCurrentSuspensionBlocksManagement() {
        var seller = approved(A);
        var created = stores.create(A, "Own", "own-store");
        stores.setOpen(created.profile().id(), created.version(), A, false);
        assertFalse(handler.storeOwn().open());
        setStatus(seller.id(), SellerStatus.SUSPENDED);
        assertEquals(SellerStatus.SUSPENDED, handler.sellerOwn().status());
        assertEquals(StoreRuleException.Code.SELLER_NOT_APPROVED,
                assertThrows(StoreRuleException.class, handler::storeOwn).getCode());
    }

    @Test
    void roleRevocationBlocksStoreButNotAuthenticatedOwnProfile() {
        approved(A);
        stores.create(A, "Own", "own-store");
        authority.sellerRole = false;
        assertNotNull(handler.sellerOwn().id());
        assertEquals(StoreRuleException.Code.ACCESS_DENIED,
                assertThrows(StoreRuleException.class, handler::storeOwn).getCode());
    }

    @Test
    void unavailableAndInactiveAuthorityNeverReturnPrivateData() {
        approved(A);
        stores.create(A, "Own", "own-store");
        authority.available = false;
        assertThrows(IllegalStateException.class, handler::sellerOwn);
        assertThrows(IllegalStateException.class, handler::storeOwn);
        authority.available = true;
        authority.active = false;
        assertThrows(SellerRuleException.class, handler::sellerOwn);
        assertThrows(SellerRuleException.class, handler::storeOwn);
    }

    @Test
    void joinedReadsRespectOuterRollbackAndNextRequestReloadsCommittedFacts() {
        var seller = approved(A);
        var created = stores.create(A, "Own", "own-store");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            stores.setOpen(created.profile().id(), created.version(), A, false);
            assertFalse(handler.storeOwn().open());
            tx.setRollbackOnly();
        });
        assertTrue(handler.storeOwn().open());
        assertEquals(Long.toString(created.version()), handler.storeOwn().version());
        setStatus(seller.id(), SellerStatus.REJECTED);
        assertEquals(SellerStatus.REJECTED, handler.sellerOwn().status());
        assertEquals(StoreRuleException.Code.SELLER_NOT_APPROVED,
                assertThrows(StoreRuleException.class, handler::storeOwn).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/sellers/me", "/api/stores/me"})
    void ownReadHttpRemainsBlockedEvenWithSpoofedCredentials(String path) throws Exception {
        var seller = approved(A);
        var store = stores.create(A, "Own", "own-store");
        http.perform(get(path)).andExpect(status().isForbidden());
        var result = http.perform(get(path).header("Authorization", "Bearer not-a-verified-token")
                .header("X-User-Id", A.reference()).header("X-Roles", "ADMIN,SELLER")
                .queryParam("sellerId", seller.id().toString())
                .queryParam("storeId", store.profile().id().toString()))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains(seller.id().toString()));
        assertFalse(result.contains(store.profile().id().toString()));
        assertFalse(result.contains("Private reason"));
    }

    private PersistedSeller approved(SellerIdentity owner) {
        var seller = sellers.register(owner);
        setStatus(seller.id(), SellerStatus.APPROVED);
        return sellers.findOwn(owner);
    }

    private void setStatus(UUID id, SellerStatus state) {
        jdbc.update("UPDATE seller_profiles SET status=?,review_reason=?,version=version+1 WHERE id=?",
                state.name(), state == SellerStatus.REJECTED || state == SellerStatus.SUSPENDED ? "Private reason" : null, id);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Wiring {
        @Bean FixtureAuthority fixtureAuthority(SellerProfileRepository repository) {
            return new FixtureAuthority(repository);
        }
        @Bean SellerPersistenceService sellerService(SellerProfileRepository repository, FixtureAuthority authority) {
            return new SellerPersistenceService(repository, authority);
        }
        @Bean StorePersistenceService storeService(StoreRepository repository, EntityManager em, FixtureAuthority authority) {
            return new StorePersistenceService(repository, em, authority);
        }
        @Bean SellerStoreReadHandler readHandler(FixtureAuthority authority, SellerPersistenceService sellers,
                                                StorePersistenceService stores) {
            return new SellerStoreReadHandler(authority, sellers, stores);
        }
    }

    /** Test request context only: never registered in production or accepted from HTTP. */
    static class FixtureAuthority implements SellerStoreReadAuthority, SellerAccessPolicy, StoreAccessPolicy {
        private final SellerProfileRepository repository;
        SellerIdentity actor;
        boolean active, sellerRole, available;
        FixtureAuthority(SellerProfileRepository repository) { this.repository = repository; }

        public SellerIdentity requireCurrentAccount() {
            if (!available) throw new IllegalStateException("Fixture authority unavailable");
            if (!active || actor == null) throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Denied");
            return actor;
        }
        public SellerIdentity requireCurrentSeller() {
            var current = requireCurrentAccount();
            if (!sellerRole) throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Denied");
            return current;
        }
        public void requireAuthenticated(SellerIdentity expected) {
            if (!requireCurrentAccount().equals(expected))
                throw new SellerRuleException(SellerRuleException.Code.ACCESS_DENIED, "Wrong fixture actor");
        }
        public void requireSellerModerationPermission(SellerIdentity actor) {
            throw new AssertionError("Read slice must not moderate");
        }
        public PersistedSeller requireSellerManagement(SellerIdentity expected) {
            if (!requireCurrentSeller().equals(expected))
                throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Wrong fixture actor");
            var profile = repository.findByOwnerReference(expected.reference())
                    .orElseThrow(() -> new IllegalStateException("Current seller unavailable"));
            return new PersistedSeller(profile.getId(), profile.getVersion(),
                    new SellerApplication(expected, profile.getStatus(), profile.getReviewReason()));
        }
    }
}
