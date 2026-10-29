package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerApplication;
import com.projecthk5.sellerservice.http.SellerStoreResponses;
import com.projecthk5.sellerservice.seller.domain.SellerIdentity;
import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import com.projecthk5.sellerservice.seller.repository.SellerProfileRepository;
import com.projecthk5.sellerservice.seller.service.SellerPersistenceService.PersistedSeller;
import com.projecthk5.sellerservice.store.StorePersistenceService.PersistedStore;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
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

/** Production Flyway/JPA checks with test identities; these do not verify JWT or service-pair HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StorePersistenceIntegrationTest.Wiring.class)
@Testcontainers
@Tag("postgresql")
class StorePersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static final SellerIdentity OWNER = new SellerIdentity(" Exact-Owner ");
    private static final SellerIdentity OTHER = new SellerIdentity("other-owner");
    private static final UUID SELLER_A = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID SELLER_B = UUID.fromString("40000000-0000-4000-8000-000000000002");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "ApplicationName=store-persistence-test");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @Autowired private StorePersistenceService service;
    @Autowired private StoreCurrentSource source;
    @Autowired private StoreRepository stores;
    @Autowired private SellerProfileRepository sellers;
    @Autowired private FixturePolicy policy;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MockMvc http;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping routes;

    @BeforeEach
    void resetIsolatedDatabase() {
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM seller_profiles");
        insertSeller(SELLER_A, OWNER.reference());
        insertSeller(SELLER_B, OTHER.reference());
        policy.known.clear();
        policy.known.addAll(Set.of(OWNER.reference(), OTHER.reference()));
        policy.overrides.clear();
        policy.unavailable = false;
    }

    @Test
    void ownerResponseMappingRequiresSuccessfulCurrentOwnerRead() {
        PersistedStore first = service.create(OWNER, new StoreInputs.Create("First", "first-private"));
        PersistedStore second = service.create(OTHER, new StoreInputs.Create("Second", "second-private"));
        var own = SellerStoreResponses.storeOwner(service.findOwn(OWNER));
        var otherOwn = SellerStoreResponses.storeOwner(service.findOwn(OTHER));
        assertEquals(first.profile().id(), own.id());
        assertEquals(SELLER_A, own.sellerId());
        assertEquals(second.profile().id(), otherOwn.id());
        assertEquals(SELLER_B, otherOwn.sellerId());
        assertNotEquals(own.id(), otherOwn.id());
        policy.unavailable = true;
        assertEquals(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(StoreRuleException.class,
                        () -> SellerStoreResponses.storeOwner(service.findOwn(OWNER))).getCode());
        policy.unavailable = false;
        policy.known.remove(OWNER.reference());
        assertEquals(StoreRuleException.Code.ACCESS_DENIED,
                assertThrows(StoreRuleException.class,
                        () -> SellerStoreResponses.storeOwner(service.findOwn(OWNER))).getCode());
    }

    @Test
    void typedCommandsKeepServerOwnershipVersionAndTransactionGuards() {
        PersistedStore created = service.create(OWNER, new StoreInputs.Create("  Store  ", "typed-store"));
        UUID id = created.profile().id();
        var revised = service.revise(id, OWNER, new StoreInputs.Update("Renamed", "renamed", created.version()));
        assertEquals(id, revised.profile().id());
        assertEquals(SELLER_A, revised.profile().sellerId());
        assertEquals(OWNER.reference(), revised.profile().ownerReference());
        assertTrue(revised.profile().open());
        assertEquals(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                assertThrows(StorePersistenceException.class,
                        () -> service.setOpen(id, OWNER, new StoreInputs.SetOpen(false, created.version()))).getCode());
        assertEquals(StoreRuleException.Code.ACCESS_DENIED,
                assertThrows(StoreRuleException.class,
                        () -> service.revise(id, OTHER, new StoreInputs.Update("Stolen", "stolen", revised.version()))).getCode());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.setOpen(id, OWNER, new StoreInputs.SetOpen(false, revised.version()));
            tx.setRollbackOnly();
        });
        assertEquals(revised, service.findOwn(OWNER));
        var closed = service.setOpen(id, OWNER, new StoreInputs.SetOpen(false, revised.version()));
        assertFalse(closed.profile().open());
        var reopened = service.setOpen(id, OWNER, new StoreInputs.SetOpen(true, closed.version()));
        assertTrue(reopened.profile().open());
        assertEquals(id, reopened.profile().id());
    }

    @Test
    void freshFlywaySchemaAndJpaPersistDurableStoreWithServerOwnership() {
        PersistedStore created = service.create(OWNER, "  First Store  ", "first-store");
        assertEquals(SELLER_A, created.profile().sellerId());
        assertEquals(OWNER.reference(), created.profile().ownerReference());
        assertEquals("First Store", created.profile().name());
        assertTrue(created.profile().open());
        assertEquals(0, created.version());
        assertNotNull(created.createdAt());
        assertEquals(created.createdAt(), created.updatedAt());
        assertEquals(created, service.findOwn(OWNER));
        assertEquals(created.profile().id(), stores.findBySellerId(SELLER_A).orElseThrow().getId());
        assertEquals(created.profile().id(), stores.findBySlug("first-store").orElseThrow().getId());
        assertTrue(stores.findBySlug("FIRST-STORE").isEmpty());
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2')", Integer.class));
        assertEquals(OWNER.reference(), jdbc.queryForObject(
                "SELECT p.owner_reference FROM stores s JOIN seller_profiles p ON p.id=s.seller_id WHERE s.id=?",
                String.class, created.profile().id()));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM stores WHERE id=?", String.class,
                created.profile().id()));
    }

    @Test
    void migrationUpgradesExistingV1SellerWithoutChangingDataOrChecksum() {
        String schema = "store_upgrade";
        jdbc.execute("CREATE SCHEMA " + schema);
        Flyway v1 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").target("1").load();
        v1.migrate();
        Integer checksum = jdbc.queryForObject("SELECT checksum FROM " + schema
                + ".flyway_schema_history WHERE version='1'", Integer.class);
        UUID existing = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema
                + ".seller_profiles(id,owner_reference,status,review_reason,version) VALUES (?,?,'SUSPENDED',?,7)",
                existing, " Kept-Owner ", "Existing moderation reason");
        Flyway current = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).locations("classpath:db/migration").load();
        assertEquals(4, current.migrate().migrationsExecuted);
        current.validate();
        assertEquals(checksum, jdbc.queryForObject("SELECT checksum FROM " + schema
                + ".flyway_schema_history WHERE version='1'", Integer.class));
        assertEquals(Map.of("owner_reference", " Kept-Owner ", "status", "SUSPENDED",
                "review_reason", "Existing moderation reason", "version", 7L), jdbc.queryForMap(
                "SELECT owner_reference,status,review_reason,version FROM " + schema + ".seller_profiles WHERE id=?", existing));
        assertEquals(Map.of("owner_reference", " Kept-Owner ", "seller_status", "SUSPENDED", "source_version", 7L),
                jdbc.queryForMap("SELECT owner_reference,seller_status,source_version FROM " + schema
                        + ".seller_status_outbox WHERE seller_id=?", existing));
        jdbc.update("INSERT INTO " + schema + ".stores(id,seller_id,name,slug,status) VALUES (?,?,?,?,'CLOSED')",
                UUID.randomUUID(), existing, "Existing seller store", "existing-store");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".stores", Integer.class));
    }

    @Test
    void reviseCloseAndReopenKeepIdentityAndAdvanceVersionWithoutChangingSeller() {
        PersistedStore original = service.create(OWNER, "First", "first");
        PersistedStore revised = service.revise(original.profile().id(), original.version(), OWNER, " Second ", "second");
        PersistedStore closed = service.setOpen(original.profile().id(), revised.version(), OWNER, false);
        PersistedStore reopened = service.setOpen(original.profile().id(), closed.version(), OWNER, true);
        assertEquals(original.profile().id(), reopened.profile().id());
        assertEquals(SELLER_A, reopened.profile().sellerId());
        assertEquals(OWNER.reference(), reopened.profile().ownerReference());
        assertEquals("Second", reopened.profile().name());
        assertEquals("second", reopened.profile().slug());
        assertTrue(reopened.profile().open());
        assertEquals(3, reopened.version());
        assertEquals(original.createdAt(), reopened.createdAt());
        assertFalse(reopened.updatedAt().isBefore(original.updatedAt()));
        assertEquals(reopened, service.findOwn(OWNER));
        assertEquals(0L, sellers.findById(SELLER_A).orElseThrow().getVersion());
        assertEquals(SellerStatus.APPROVED, sellers.findById(SELLER_A).orElseThrow().getStatus());
    }

    @Test
    void duplicateSellerAndSlugHaveDifferentErrorsAndLeaveOriginalStoresIntact() {
        PersistedStore first = service.create(OWNER, "First", "first");
        assertPersistence(StorePersistenceException.Code.STORE_ALREADY_EXISTS,
                () -> service.create(OWNER, "Another", "another"));
        assertPersistence(StorePersistenceException.Code.SLUG_ALREADY_EXISTS,
                () -> service.create(OTHER, "Other", "first"));
        PersistedStore second = service.create(OTHER, "Second", "second");
        assertPersistence(StorePersistenceException.Code.SLUG_ALREADY_EXISTS,
                () -> service.revise(second.profile().id(), second.version(), OTHER, "Changed", "first"));
        assertEquals(first, service.findOwn(OWNER));
        assertEquals(second, service.findOwn(OTHER));
        assertEquals(2, stores.count());
    }

    @Test
    void createAndUpdateJoinRequiredCallerTransactionAndRollBackCompletely() {
        AtomicReference<UUID> rolledBack = new AtomicReference<>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            rolledBack.set(service.create(OWNER, "Rollback", "rollback").profile().id());
            tx.setRollbackOnly();
        });
        assertFalse(stores.existsById(rolledBack.get()));
        PersistedStore original = service.create(OWNER, "Committed", "rollback");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.revise(original.profile().id(), original.version(), OWNER, "Lost", "lost");
            tx.setRollbackOnly();
        });
        assertEquals(original, service.findOwn(OWNER));
        assertTrue(stores.findBySlug("lost").isEmpty());
    }

    @Test
    void exceptionAfterAFlushedMutationRollsBackTheWholeCallerTransaction() {
        PersistedStore original = service.create(OWNER, "Committed", "committed");
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.setOpen(original.profile().id(), original.version(), OWNER, false);
            throw new IllegalStateException("Deliberate caller failure");
        }));
        assertEquals(original, service.findOwn(OWNER));
    }

    @Test
    void crossSellerWritesAndUnverifiedOrNormalizedIdentitiesCannotReadOrMutate() {
        PersistedStore first = service.create(OWNER, "First", "first");
        service.create(OTHER, "Other", "other");
        assertRule(StoreRuleException.Code.ACCESS_DENIED,
                () -> service.revise(first.profile().id(), first.version(), OTHER, "Stolen", "stolen"));
        assertRule(StoreRuleException.Code.ACCESS_DENIED,
                () -> service.setOpen(first.profile().id(), first.version(), OTHER, false));
        for (String forged : new String[]{OWNER.reference().strip(), OWNER.reference().toLowerCase(), "unverified"}) {
            assertThrows(StoreRuleException.class, () -> service.findOwn(new SellerIdentity(forged)));
        }
        assertEquals(first, service.findOwn(OWNER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "REJECTED", "SUSPENDED"})
    void currentUnapprovedSellerCannotCreateEvenWhenPolicyClaimsApproved(String status) {
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OWNER));
        changeSellerStatus(SELLER_A, status);
        assertRule(StoreRuleException.Code.SELLER_NOT_APPROVED, () -> service.create(OWNER, "Denied", "denied"));
        assertEquals(0, stores.count());
    }

    @Test
    void suspendedSellerCannotReviseOrReopenAnExistingStoreDespiteStalePolicy() {
        PersistedStore store = service.create(OWNER, "Store", "store");
        PersistedStore closed = service.setOpen(store.profile().id(), store.version(), OWNER, false);
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OWNER));
        changeSellerStatus(SELLER_A, "SUSPENDED");
        assertRule(StoreRuleException.Code.SELLER_NOT_APPROVED,
                () -> service.revise(store.profile().id(), closed.version(), OWNER, "Denied", "denied"));
        assertRule(StoreRuleException.Code.SELLER_NOT_APPROVED,
                () -> service.setOpen(store.profile().id(), closed.version(), OWNER, true));
        assertEquals(StoreStatus.CLOSED, stores.findById(store.profile().id()).orElseThrow().getStatus());
        assertEquals(closed.version(), stores.findById(store.profile().id()).orElseThrow().getVersion());
    }

    @Test
    void policyCannotSubstituteAnotherSellerOrOwnerOrInventAnUnpersistedSeller() {
        policy.overrides.put(OWNER.reference(), approved(SELLER_B, OWNER));
        assertRule(StoreRuleException.Code.ACCESS_DENIED, () -> service.create(OWNER, "Bad", "bad"));
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OTHER));
        assertRule(StoreRuleException.Code.ACCESS_DENIED, () -> service.create(OWNER, "Bad", "bad"));
        policy.overrides.put(OWNER.reference(), approved(UUID.randomUUID(), OWNER));
        assertThrows(StoreRuleException.class, () -> service.create(OWNER, "Bad", "bad"));
        assertEquals(0, stores.count());
    }

    @Test
    void unavailablePolicyAndMissingPersistedSellerFailClosed() {
        policy.unavailable = true;
        assertRule(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE, () -> service.create(OWNER, "Bad", "bad"));
        policy.unavailable = false;
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OWNER));
        jdbc.update("DELETE FROM seller_profiles WHERE id=?", SELLER_A);
        assertThrows(StoreRuleException.class, () -> service.create(OWNER, "Bad", "bad"));
        assertEquals(0, stores.count());
    }

    @Test
    void staleManagedSellerIsRefreshedBeforeCurrentEligibilityCheck() {
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OWNER));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            SellerProfile cached = sellers.findById(SELLER_A).orElseThrow();
            assertEquals(SellerStatus.APPROVED, cached.getStatus());
            requiresNew().executeWithoutResult(other -> changeSellerStatus(SELLER_A, "SUSPENDED"));
            assertEquals(SellerStatus.APPROVED, cached.getStatus());
            assertRule(StoreRuleException.Code.SELLER_NOT_APPROVED,
                    () -> service.create(OWNER, "Denied", "denied"));
            tx.setRollbackOnly();
        });
        assertEquals(0, stores.count());
    }

    @Test
    void ownReadAndMissingStoreErrorsDoNotAllowSelectingAnotherOwnersStore() {
        assertPersistence(StorePersistenceException.Code.STORE_NOT_FOUND, () -> service.findOwn(OWNER));
        PersistedStore other = service.create(OTHER, "Other", "other");
        assertPersistence(StorePersistenceException.Code.STORE_NOT_FOUND, () -> service.findOwn(OWNER));
        assertThrows(StoreRuleException.class,
                () -> service.revise(other.profile().id(), other.version(), OWNER, "Bad", "bad"));
        assertPersistence(StorePersistenceException.Code.STORE_NOT_FOUND,
                () -> service.revise(UUID.randomUUID(), 0, OWNER, "Missing", "missing"));
    }

    @Test
    void staleExpectedVersionCannotOverwriteCommittedMetadataOrState() {
        PersistedStore first = service.create(OWNER, "First", "first");
        PersistedStore changed = service.revise(first.profile().id(), first.version(), OWNER, "Changed", "changed");
        assertPersistence(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                () -> service.revise(first.profile().id(), first.version(), OWNER, "Stale", "stale"));
        assertPersistence(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                () -> service.setOpen(first.profile().id(), first.version(), OWNER, false));
        assertEquals(changed, service.findOwn(OWNER));
    }

    @Test
    void invalidActorIdentifierVersionAndMetadataLeaveDatabaseUnchanged() {
        PersistedStore first = service.create(OWNER, "First", "first");
        assertRule(StoreRuleException.Code.INVALID_INPUT, () -> service.create(null, "Bad", "bad"));
        assertRule(StoreRuleException.Code.INVALID_INPUT, () -> service.revise(null, 0, OWNER, "Bad", "bad"));
        assertRule(StoreRuleException.Code.INVALID_INPUT,
                () -> service.setOpen(first.profile().id(), -1, OWNER, false));
        assertRule(StoreRuleException.Code.INVALID_INPUT,
                () -> service.revise(first.profile().id(), 0, OWNER, " ", "valid"));
        assertRule(StoreRuleException.Code.INVALID_INPUT,
                () -> service.revise(first.profile().id(), 0, OWNER, "N".repeat(256), "valid"));
        assertEquals(first, service.findOwn(OWNER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Upper", "with space", "-first", "last-", "two--parts", "café", "a\nb"})
    void domainAndDatabaseBothRejectNoncanonicalSlugs(String slug) {
        assertRule(StoreRuleException.Code.INVALID_INPUT, () -> service.create(OWNER, "Store", slug));
        assertThrows(DataIntegrityViolationException.class, () -> insertStore(UUID.randomUUID(), SELLER_A, slug));
        assertEquals(0, stores.count());
    }

    @ParameterizedTest(name = "database rejects {0}")
    @ValueSource(strings = {"seller_id=NULL", "name=NULL", "name=''", "name='   '", "name=' padded '",
            "name=repeat('a',256)", "slug=NULL", "slug=repeat('a',129)", "status=NULL", "status='UNKNOWN'",
            "version=NULL", "version=-1", "created_at=NULL", "updated_at=NULL",
            "created_at='infinity'", "updated_at='infinity'", "updated_at=created_at-interval '1 microsecond'"})
    void databaseConstraintsRejectInvalidMetadata(String assignment) {
        PersistedStore original = service.create(OWNER, "Store", "store");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE stores SET " + assignment));
        assertEquals(original, service.findOwn(OWNER));
    }

    @Test
    void databaseProtectsSellerAndSlugUniquenessAndRestrictsMissingOrDeletedSeller() {
        PersistedStore original = service.create(OWNER, "Store", "store");
        assertThrows(DataIntegrityViolationException.class, () -> insertStore(UUID.randomUUID(), SELLER_A, "other"));
        assertThrows(DataIntegrityViolationException.class, () -> insertStore(UUID.randomUUID(), SELLER_B, "store"));
        assertThrows(DataIntegrityViolationException.class, () -> insertStore(UUID.randomUUID(), UUID.randomUUID(), "missing"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM seller_profiles WHERE id=?", SELLER_A));
        assertEquals(original, service.findOwn(OWNER));
        assertEquals(1, stores.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "APPROVED", "REJECTED", "SUSPENDED"})
    void trustedLocalProjectionReadsCurrentSellerAndStoreEligibility(String status) {
        PersistedStore created = service.create(OWNER, "Store", "store");
        changeSellerStatus(SELLER_A, status);
        var facts = source.findById(created.profile().id()).orElseThrow();
        assertEquals(created.profile().id(), facts.storeId());
        assertEquals(SELLER_A, facts.sellerId());
        assertEquals(StoreStatus.OPEN, facts.storeStatus());
        assertEquals(SellerStatus.valueOf(status), facts.sellerStatus());
        assertEquals(status.equals("APPROVED"), facts.eligible());
        assertEquals(facts, source.findBySellerId(SELLER_A).orElseThrow());
        assertEquals(facts, source.findBySlug("store").orElseThrow());
        jdbc.update("UPDATE stores SET status='CLOSED',version=version+1 WHERE id=?", created.profile().id());
        var closed = source.findById(created.profile().id()).orElseThrow();
        assertFalse(closed.eligible());
        assertEquals(StoreStatus.CLOSED, closed.storeStatus());
        assertEquals(facts.storeVersion() + 1, closed.storeVersion());
        assertEquals(1, closed.sellerVersion());
    }

    @Test
    void trustedSourceMissingRowsStayAbsentAndPayloadHasNoOwnerOrHouseAuthority() {
        assertTrue(source.findById(UUID.randomUUID()).isEmpty());
        assertTrue(source.findBySellerId(SELLER_A).isEmpty());
        assertTrue(source.findBySlug("missing").isEmpty());
        assertEquals(Set.of("storeId", "sellerId", "storeStatus", "sellerStatus", "storeVersion", "sellerVersion"),
                Stream.of(StoreCurrentSource.Facts.class.getRecordComponents()).map(component -> component.getName())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void trustedSourceBypassesStaleFirstLevelEntitiesAtReadCommitted() {
        PersistedStore created = service.create(OWNER, "Store", "store");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var cachedStore = stores.findById(created.profile().id()).orElseThrow();
            var cachedSeller = sellers.findById(SELLER_A).orElseThrow();
            assertEquals(StoreStatus.OPEN, cachedStore.getStatus());
            assertEquals(SellerStatus.APPROVED, cachedSeller.getStatus());
            requiresNew().executeWithoutResult(other -> {
                jdbc.update("UPDATE stores SET status='CLOSED',version=version+1 WHERE id=?", created.profile().id());
                changeSellerStatus(SELLER_A, "SUSPENDED");
            });
            assertEquals(StoreStatus.OPEN, cachedStore.getStatus());
            assertEquals(SellerStatus.APPROVED, cachedSeller.getStatus());
            var facts = source.findById(created.profile().id()).orElseThrow();
            assertEquals(StoreStatus.CLOSED, facts.storeStatus());
            assertEquals(SellerStatus.SUSPENDED, facts.sellerStatus());
            assertEquals(1, facts.storeVersion());
            assertEquals(1, facts.sellerVersion());
            assertFalse(facts.eligible());
        });
    }

    @Test
    void trustedCurrentSourceRejectsRepeatableReadSnapshots() {
        PersistedStore created = service.create(OWNER, "Store", "store");
        TransactionTemplate repeatable = new TransactionTemplate(transactions);
        repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatable.executeWithoutResult(tx -> {
            assertRule(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    () -> source.findById(created.profile().id()));
            tx.setRollbackOnly();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentCreatesWaitForDatabaseUniqueConstraintAndOnlyOneCommits(boolean sameSeller) throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PersistedStore> winner = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                PersistedStore created = service.create(OWNER, "Winner", "winner");
                inserted.countDown();
                await(release);
                return created;
            }));
            assertTrue(inserted.await(15, TimeUnit.SECONDS));
            Future<PersistedStore> loser = workers.submit(() -> service.create(sameSeller ? OWNER : OTHER,
                    "Loser", sameSeller ? "loser" : "winner"));
            assertDatabaseLockWait("insert into stores");
            release.countDown();
            PersistedStore committed = winner.get(15, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> loser.get(15, TimeUnit.SECONDS));
            assertEquals(sameSeller ? StorePersistenceException.Code.STORE_ALREADY_EXISTS
                            : StorePersistenceException.Code.SLUG_ALREADY_EXISTS,
                    assertInstanceOf(StorePersistenceException.class, failure.getCause()).getCode());
            assertEquals(1, stores.count());
            assertEquals(committed, service.findOwn(OWNER));
            assertEquals(2, sellers.count());
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    void concurrentServiceUpdatesWithSameExpectedVersionHaveOneWinner() throws Exception {
        PersistedStore created = service.create(OWNER, "Original", "original");
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PersistedStore> first = workers.submit(() -> reviseAfterBarrier(created, ready, "first"));
            Future<PersistedStore> second = workers.submit(() -> reviseAfterBarrier(created, ready, "second"));
            int successes = 0;
            int conflicts = 0;
            for (Future<PersistedStore> result : List.of(first, second)) {
                try {
                    result.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException failure) {
                    assertEquals(StorePersistenceException.Code.STORE_VERSION_CONFLICT,
                            assertInstanceOf(StorePersistenceException.class, failure.getCause()).getCode());
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, service.findOwn(OWNER).version());
        } finally {
            stop(workers);
        }
    }

    @Test
    void actualJpaVersionRejectsTwoEntitiesLoadedBeforeEitherWrite() throws Exception {
        PersistedStore created = service.create(OWNER, "Original", "original");
        CountDownLatch loaded = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> directJpaUpdate(created, loaded, "first"));
            Future<?> second = workers.submit(() -> directJpaUpdate(created, loaded, "second"));
            int successes = 0;
            int conflicts = 0;
            for (Future<?> result : List.of(first, second)) {
                try {
                    result.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException failure) {
                    assertInstanceOf(OptimisticLockingFailureException.class, failure.getCause());
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, service.findOwn(OWNER).version());
        } finally {
            stop(workers);
        }
    }

    @Test
    void currentSellerReadWaitsForConcurrentSuspensionAndDeniesMutationAfterItsCommit() throws Exception {
        CountDownLatch suspended = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        policy.overrides.put(OWNER.reference(), approved(SELLER_A, OWNER));
        try {
            Future<?> suspension = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                changeSellerStatus(SELLER_A, "SUSPENDED");
                suspended.countDown();
                await(release);
            }));
            assertTrue(suspended.await(15, TimeUnit.SECONDS));
            Future<PersistedStore> creation = workers.submit(() -> service.create(OWNER, "Denied", "denied"));
            assertDatabaseLockWait("seller_profiles");
            release.countDown();
            suspension.get(15, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> creation.get(15, TimeUnit.SECONDS));
            assertEquals(StoreRuleException.Code.SELLER_NOT_APPROVED,
                    assertInstanceOf(StoreRuleException.class, failure.getCause()).getCode());
            assertEquals(0, stores.count());
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    void successfulMutationHoldsCurrentSellerEligibilityUntilItsTransactionCommits() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PersistedStore> creation = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                PersistedStore created = service.create(OWNER, "Store", "store");
                inserted.countDown();
                await(release);
                return created;
            }));
            assertTrue(inserted.await(15, TimeUnit.SECONDS));
            Future<?> suspension = workers.submit(() -> changeSellerStatus(SELLER_A, "SUSPENDED"));
            assertDatabaseLockWait("update seller_profiles");
            release.countDown();
            PersistedStore committed = creation.get(15, TimeUnit.SECONDS);
            suspension.get(15, TimeUnit.SECONDS);
            assertTrue(stores.existsById(committed.profile().id()));
            assertFalse(source.findById(committed.profile().id()).orElseThrow().eligible());
            assertRule(StoreRuleException.Code.SELLER_NOT_APPROVED,
                    () -> service.setOpen(committed.profile().id(), committed.version(), OWNER, false));
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest
    @MethodSource("unexposedRoutes")
    void forgedHeadersCannotReachAnyStoreHttpAdapter(HttpMethod method, String path) throws Exception {
        var response = http.perform(MockMvcRequestBuilders.request(method, path)
                .header("X-User-Id", OWNER.reference()).header("X-Roles", "ADMIN,SELLER")
                .header("Authorization", "Bearer forged-not-a-verified-jwt")
                .contentType("application/json").content("{\"sellerId\":\"" + SELLER_A + "\"}"))
                .andReturn().getResponse();
        assertEquals(403, response.getStatus());
        assertTrue(routes.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream())
                .noneMatch(pattern -> new org.springframework.web.util.pattern.PathPatternParser().parse(pattern)
                        .matches(org.springframework.http.server.PathContainer.parsePath(path))),
                "Business and legacy internal handlers must remain unregistered");
        assertTrue(response.getContentAsString().contains("SELLER_ROUTE_AUTH_NOT_CONFIGURED"));
        assertFalse(response.getContentAsString().contains(OWNER.reference()));
        assertEquals(0, stores.count());
    }

    static Stream<Arguments> unexposedRoutes() {
        return Stream.of(Arguments.of(HttpMethod.POST, "/api/stores"),
                Arguments.of(HttpMethod.PUT, "/api/stores/" + SELLER_A),
                Arguments.of(HttpMethod.PATCH, "/api/stores/" + SELLER_A + "/status"),
                Arguments.of(HttpMethod.DELETE, "/api/stores/" + SELLER_A),
                Arguments.of(HttpMethod.GET, "/internal/stores/" + SELLER_A),
                Arguments.of(HttpMethod.POST, "/internal/stores/batch"));
    }

    private PersistedStore reviseAfterBarrier(PersistedStore created, CountDownLatch ready, String name) {
        return new TransactionTemplate(transactions).execute(tx -> {
            assertEquals(created.version(), stores.findById(created.profile().id()).orElseThrow().getVersion());
            ready.countDown();
            await(ready);
            return service.revise(created.profile().id(), created.version(), OWNER, name, name);
        });
    }

    private void directJpaUpdate(PersistedStore created, CountDownLatch loaded, String name) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            StoreEntity entity = stores.findById(created.profile().id()).orElseThrow();
            assertEquals(created.version(), entity.getVersion());
            loaded.countDown();
            await(loaded);
            entity.apply(new StoreProfile(created.profile().id(), SELLER_A, OWNER.reference(), name, name, true));
            stores.saveAndFlush(entity);
        });
    }

    private void assertDatabaseLockWait(String queryFragment) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database() "
                    + "AND application_name='store-persistence-test' AND wait_event_type='Lock' AND lower(query) LIKE ?)",
                    Boolean.class, "%" + queryFragment + "%"))) {
                Thread.sleep(25);
            }
        });
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private void insertSeller(UUID id, String owner) {
        jdbc.update("INSERT INTO seller_profiles(id,owner_reference,status) VALUES (?,?,'APPROVED')", id, owner);
    }

    private void insertStore(UUID id, UUID sellerId, String slug) {
        jdbc.update("INSERT INTO stores(id,seller_id,name,slug,status) VALUES (?,?,?,?,'OPEN')", id, sellerId, "Store", slug);
    }

    private void changeSellerStatus(UUID sellerId, String status) {
        jdbc.update("UPDATE seller_profiles SET status=?,review_reason=?,version=version+1 WHERE id=?", status,
                status.equals("REJECTED") || status.equals("SUSPENDED") ? "Current moderation reason" : null, sellerId);
    }

    private static PersistedSeller approved(UUID id, SellerIdentity owner) {
        return new PersistedSeller(id, 0, new SellerApplication(owner, SellerStatus.APPROVED, null));
    }

    private static void assertPersistence(StorePersistenceException.Code code, Runnable action) {
        assertEquals(code, assertThrows(StorePersistenceException.class, action::run).getCode());
    }

    private static void assertRule(StoreRuleException.Code code, Runnable action) {
        assertEquals(code, assertThrows(StoreRuleException.class, action::run).getCode());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test barrier not reached");
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
        @Bean FixturePolicy storePolicy(JdbcTemplate jdbc) { return new FixturePolicy(jdbc); }

        @Bean StorePersistenceService storePersistenceService(StoreRepository stores, EntityManager entityManager,
                FixturePolicy policy) {
            return new StorePersistenceService(stores, entityManager, policy);
        }
    }

    // Explicit local fixtures; no verified JWT, current ADMIN or service-pair authorization is claimed.
    static class FixturePolicy implements StoreAccessPolicy {
        private final JdbcTemplate jdbc;
        private final Set<String> known = ConcurrentHashMap.newKeySet();
        private final Map<String, PersistedSeller> overrides = new ConcurrentHashMap<>();
        private volatile boolean unavailable;

        FixturePolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override
        public PersistedSeller requireSellerManagement(SellerIdentity actor) {
            if (actor == null || !known.contains(actor.reference())) {
                throw new StoreRuleException(StoreRuleException.Code.ACCESS_DENIED, "Unverified fixture identity");
            }
            if (unavailable) return null;
            PersistedSeller override = overrides.get(actor.reference());
            if (override != null) return override;
            return jdbc.query("SELECT id,status,review_reason,version FROM seller_profiles WHERE owner_reference=?",
                    (row, index) -> new PersistedSeller(row.getObject("id", UUID.class), row.getLong("version"),
                            new SellerApplication(actor, SellerStatus.valueOf(row.getString("status")),
                                    row.getString("review_reason"))), actor.reference()).stream().findFirst().orElse(null);
        }
    }
}
