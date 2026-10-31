package com.projecthk5.productservice.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.catalog.CatalogSnapshotSource;
import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.product.ProductAccessPolicy;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductLifecycleService;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.PGConnection;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
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

/** T02-T15 use production Flyway/JPA and private persistence; no JWT or positive provider acceptance is claimed. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class ProductPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID STORE_A = UUID.fromString("60000000-0000-4000-8000-000000000002");
    private static final UUID STORE_B = UUID.fromString("60000000-0000-4000-8000-000000000003");
    private static final String INSERT = "INSERT INTO products"
            + "(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
            + " VALUES (?,?,?,?,?,?,?,'DRAFT',0)";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "ApplicationName=product-persistence-acceptance");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 8);
    }

    @Autowired private ProductPersistenceAdapter adapter;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagers;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MockMvc http;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;
    @Autowired private Flyway flyway;

    @BeforeEach
    void resetDisposableDatabase() {
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        insertCategory(CATEGORY);
    }

    @Test
    void productionAuthorityWithoutVerifiedActorKeepsEveryLifecycleOperationClosed() {
        ProductApplicationService service = context.getBean(ProductApplicationService.class);
        UUID id = UUID.randomUUID();
        for (Runnable operation : List.<Runnable>of(
                () -> service.create("owner", STORE_A, CATEGORY, "closed", "Name", BigDecimal.ONE, "VND"),
                () -> service.edit("owner", id, 0, CATEGORY, "Name", BigDecimal.ONE, "VND"),
                () -> service.submit("owner", id, 0), () -> service.approve("admin", id, 0),
                () -> service.reject("admin", id, 0, "Reason"), () -> service.suspend("admin", id, 0, "Reason"),
                () -> service.deactivate("owner", id, 0), () -> service.findById("owner", id))) {
            // Operations on a missing id stop at the lookup; creation reaches the actor check.
            assertThrows(RuntimeException.class, operation::run);
        }
        assertEquals(0, products.count());
    }

    @Test
    void T02_minimumAndMaximumVndPersistExactlyThroughJpaAndRawSql() {
        for (String amount : List.of("1", "1000000000")) {
            BigDecimal price = new BigDecimal(amount);
            var stored = adapter.create(listing("jpa-" + amount, price, "VND"));
            assertEquals(0, stored.version());
            assertEquals(0, price.compareTo(fresh(stored.listing().id()).getUnitPrice()));
            assertEquals("VND", fresh(stored.listing().id()).getCurrency());
            UUID rawId = rawInsert("sql-" + amount, price, "VND");
            assertEquals(0, price.compareTo(fresh(rawId).getUnitPrice()));
            assertEquals("VND", adapter.findById(rawId).orElseThrow().listing().currency());
        }
        assertEquals(4, products.count());
    }

    @Test
    void T03_fractionalInsertUpdateJpaAndCopyRejectOriginalValuesWithoutRounding() throws Exception {
        var original = adapter.create(listing("unchanged", BigDecimal.TEN, "VND"));
        for (String amount : List.of("1.5", "1.000000001")) {
            BigDecimal fraction = new BigDecimal(amount);
            assertThrows(ProductRuleException.class, () -> listing("domain-" + amount, fraction, "VND"));
            assertThrows(ProductRuleException.class,
                    () -> adapter.update(revise(original.listing(), "Changed", fraction, "VND"), original.version()));
            assertThrows(DataIntegrityViolationException.class,
                    () -> rawInsert("sql-" + amount, fraction, "VND"));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE products SET unit_price=? WHERE id=?", fraction, original.listing().id()));
            assertThrows(PersistenceException.class, () -> transaction().executeWithoutResult(tx -> {
                entityManager.createNativeQuery("INSERT INTO products"
                        + "(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                        + " VALUES (:id,:store,:category,:sku,'JPA exact input',:price,'VND','DRAFT',0)")
                        .setParameter("id", UUID.randomUUID()).setParameter("store", STORE_A)
                        .setParameter("category", CATEGORY).setParameter("sku", "jpa-fraction-" + amount)
                        .setParameter("price", fraction).executeUpdate();
                entityManager.flush();
            }));
            assertThrows(PersistenceException.class, () -> transaction().executeWithoutResult(tx -> {
                entityManager.createNativeQuery("UPDATE products SET unit_price=:price WHERE id=:id")
                        .setParameter("price", fraction).setParameter("id", original.listing().id()).executeUpdate();
                entityManager.flush();
            }));
            try (Connection connection = connect()) {
                String row = UUID.randomUUID() + "," + STORE_A + "," + CATEGORY
                        + ",copy-" + amount + ",COPY exact input," + amount + ",VND,DRAFT,0\n";
                SQLException failure = assertThrows(SQLException.class, () -> connection.unwrap(PGConnection.class)
                        .getCopyAPI().copyIn("COPY products"
                                + "(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                                + " FROM STDIN WITH (FORMAT csv)", new StringReader(row)));
                assertEquals("23514", failure.getSQLState());
            }
            assertEquals(original, adapter.findById(original.listing().id()).orElseThrow());
            assertEquals(1, products.count());
        }
    }

    @Test
    void T04_zeroNegativeAndAboveCapFailInJavaAndDirectDatabaseWrites() {
        var original = adapter.create(listing("range-original", BigDecimal.TEN, "VND"));
        for (BigDecimal invalid : List.of(BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1000000001"))) {
            assertThrows(ProductRuleException.class, () -> ProductPrice.validate(invalid, "VND"));
            assertThrows(ProductRuleException.class, () -> listing("invalid", invalid, "VND"));
            assertThrows(DataIntegrityViolationException.class, () -> rawInsert("invalid-" + invalid, invalid, "VND"));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE products SET unit_price=? WHERE id=?", invalid, original.listing().id()));
            assertEquals(original, adapter.findById(original.listing().id()).orElseThrow());
        }
    }

    @Test
    void T05_priceAndCurrencyAreRequiredExactVndTextWithoutDefaults() {
        for (String currency : new String[]{null, "", "USD", "vnd", " VND", "VND ", "VND\t", "VND\n", "VND\u00a0"}) {
            assertThrows(ProductRuleException.class, () -> listing("currency", BigDecimal.ONE, currency));
            assertThrows(DataIntegrityViolationException.class,
                    () -> rawInsert("currency-" + UUID.randomUUID(), BigDecimal.ONE, currency));
        }
        assertThrows(ProductRuleException.class, () -> listing("null-price", null, "VND"));
        assertThrows(DataIntegrityViolationException.class, () -> rawInsert("null-price", null, "VND"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,status,version)"
                        + " VALUES (?,?,?,'missing-currency','Missing currency',1,'DRAFT',0)",
                UUID.randomUUID(), STORE_A, CATEGORY));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO products(id,store_id,category_id,inventory_sku,name,currency,status,version)"
                        + " VALUES (?,?,?,'missing-price','Missing price','VND','DRAFT',0)",
                UUID.randomUUID(), STORE_A, CATEGORY));
        for (String column : List.of("unit_price", "currency")) {
            var metadata = jdbc.queryForMap("SELECT is_nullable,column_default,data_type"
                    + " FROM information_schema.columns"
                    + " WHERE table_schema='public' AND table_name='products' AND column_name=?", column);
            assertEquals("NO", metadata.get("is_nullable"));
            assertNull(metadata.get("column_default"));
            assertEquals(column.equals("currency") ? "text" : "numeric", metadata.get("data_type"));
        }
        assertEquals("C", jdbc.queryForObject("SELECT c.collname FROM pg_attribute a"
                + " JOIN pg_collation c ON c.oid=a.attcollation"
                + " WHERE a.attrelid='products'::regclass AND a.attname='currency'", String.class));
        assertEquals(0, products.count());
    }

    @Test
    void T06_nonfiniteAndOversizeNumericFailInDatabaseAndJavaParser() {
        for (String invalid : List.of("NaN", "Infinity", "+Infinity", "-Infinity", "1e131072")) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,version)"
                            + " VALUES (?,?,?,?,?,CAST(? AS NUMERIC),'VND','DRAFT',0)",
                    UUID.randomUUID(), STORE_A, CATEGORY, "special-" + invalid, "Invalid price", invalid));
            assertThrows(ProductRuleException.class, () -> ProductPrice.parseCanonical(invalid, "VND"));
        }
        assertThrows(ProductRuleException.class, () -> ProductPrice.parseCanonical("+Infinity", "VND"));
        assertEquals(0, products.count());
    }

    @Test
    void T07_canonicalPriceParserRejectsAliasesAndAppliesInclusiveCap() {
        for (String valid : List.of("1", "10", "999999999", "1000000000")) {
            assertEquals(new BigDecimal(valid), ProductPrice.parseCanonical(valid, "VND"));
            assertEquals(valid, ProductPrice.canonicalString(new BigDecimal(valid + ".00"), "VND"));
        }
        for (String invalid : new String[]{null, "", "0", "-1", "+1", "01", "0001", " 1", "1 ", "1\t", "1\n",
                "1.0", "1.00", ".1", "1e0", "1E0", "1e+0", "1000000001", "9999999999",
                "10000000000", "1_000", "\u0661", "\uff11"}) {
            assertThrows(ProductRuleException.class, () -> ProductPrice.parseCanonical(invalid, "VND"), String.valueOf(invalid));
        }
        assertThrows(ProductRuleException.class, () -> ProductPrice.parseCanonical("1", null));
        assertThrows(ProductRuleException.class, () -> ProductPrice.canonicalString(BigDecimal.ONE, "vnd"));
    }

    @Test
    void T08_actualNumericHasNoTypmodOrDefaultAndJavaKeepsOriginalBigDecimal() {
        assertEquals(-1, jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid='products'::regclass AND attname='unit_price'",
                Integer.class));
        assertEquals("numeric", jdbc.queryForObject(
                "SELECT format_type(atttypid,atttypmod) FROM pg_attribute"
                        + " WHERE attrelid='products'::regclass AND attname='unit_price'", String.class));
        assertNull(jdbc.queryForObject("SELECT column_default FROM information_schema.columns"
                + " WHERE table_schema='public' AND table_name='products' AND column_name='unit_price'", String.class));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        BigDecimal original = new BigDecimal("1.000000000000000000000000000000");
        assertSame(original, ProductPrice.validate(original, "VND"));
        var stored = adapter.create(listing("no-typmod", original, "VND"));
        assertEquals(original, fresh(stored.listing().id()).getUnitPrice());
        assertThrows(ProductRuleException.class,
                () -> ProductPrice.validate(new BigDecimal("1.000000001"), "VND"));
    }

    @Test
    void T09_bigDecimalCurrencyAndAllProductFieldsRoundTripThroughFreshEntityManagers() {
        BigDecimal exact = new BigDecimal("123456789.0000000000000000000000000000");
        ProductListing original = listing("Exact-\u00e9-  SKU", exact, "VND");
        var created = adapter.create(original);
        ProductEntity reloaded = fresh(original.id());
        assertEquals(original, reloaded.toDomain());
        assertEquals(exact, reloaded.getUnitPrice());
        assertEquals(original.id(), reloaded.getId());
        assertEquals(original.storeId(), reloaded.getStoreId());
        assertEquals(original.categoryId(), reloaded.getCategoryId());
        assertEquals(original.sku(), reloaded.getInventorySku());
        assertEquals(original.name(), reloaded.getName());
        assertEquals(original.status(), reloaded.getStatus());
        assertNull(reloaded.getReviewReason());
        assertEquals(0L, reloaded.getVersion());
        assertEquals(created, adapter.findBySku(original.sku()).orElseThrow());
        assertTrue(adapter.findBySku(original.sku().toLowerCase(java.util.Locale.ROOT)).isEmpty());
        var updated = adapter.update(revise(original, "Updated exactly", new BigDecimal("999999999.000000000000"), "VND"),
                created.version());
        assertEquals(1, updated.version());
        assertEquals(updated.listing(), fresh(original.id()).toDomain());
        assertEquals(updated, adapter.findById(original.id()).orElseThrow());
    }

    @Test
    void T10_globalSkuUniqueConstraintBlocksConcurrentStoresAndRollsBackLoserTransaction() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1), attempting = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger winnerPid = new AtomicInteger(), loserPid = new AtomicInteger();
        ProductListing winnerListing = listing("global-unique", BigDecimal.ONE, "VND");
        ProductListing losingListing = withStore(listing("global-unique", BigDecimal.TEN, "VND"), STORE_B);
        ProductListing companion = withStore(listing("loser-companion", BigDecimal.ONE, "VND"), STORE_B);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ProductPersistenceAdapter.StoredProduct> winner = workers.submit(() -> transaction().execute(tx -> {
                winnerPid.set(backendPid());
                var created = adapter.create(winnerListing);
                inserted.countDown();
                await(release);
                return created;
            }));
            assertTrue(inserted.await(15, TimeUnit.SECONDS));
            Future<?> loser = workers.submit(() -> transaction().executeWithoutResult(tx -> {
                loserPid.set(backendPid());
                adapter.create(companion);
                attempting.countDown();
                adapter.create(losingListing);
            }));
            assertTrue(attempting.await(15, TimeUnit.SECONDS));
            assertBlockedBy(loserPid.get(), winnerPid.get());
            assertFalse(loser.isDone());
            release.countDown();
            assertEquals(winnerListing.id(), winner.get(15, TimeUnit.SECONDS).listing().id());
            assertFailureCode(loser, ProductPersistenceException.Code.DUPLICATE_SKU);
            assertEquals(1, products.count());
            assertTrue(adapter.findById(companion.id()).isEmpty());
            assertTrue(adapter.findById(losingListing.id()).isEmpty());
            assertEquals(winnerListing, fresh(winnerListing.id()).toDomain());
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    @Test
    void T11_exactUnicodeSkuIdentityUsesJavaUtf16LimitsAndRejectsEveryBoundarySpace() {
        for (String sku : List.of("Sku", "sku", "SKU", "\u00e9-SKU", "e\u0301-SKU", "Internal space", "Internal  space",
                "a".repeat(128), "b".repeat(126) + "\ud83d\ude80", "\ud83d\ude80".repeat(64))) {
            var stored = adapter.create(listing(sku, BigDecimal.ONE, "VND"));
            assertEquals(sku, fresh(stored.listing().id()).getInventorySku());
            assertEquals(sku.length(), jdbc.queryForObject("SELECT product_utf16_length(?)", Integer.class, sku));
            assertEquals(stored, adapter.findBySku(sku).orElseThrow());
        }
        for (String oversized : List.of("c".repeat(129), "d".repeat(127) + "\ud83d\ude80", "\ud83d\ude80".repeat(64) + "e")) {
            assertThrows(ProductRuleException.class, () -> listing(oversized, BigDecimal.ONE, "VND"));
            assertThrows(DataIntegrityViolationException.class, () -> rawInsert(oversized, BigDecimal.ONE, "VND"));
        }
        int[] whitespace = IntStream.concat(IntStream.concat(IntStream.rangeClosed(0x09, 0x0d),
                        IntStream.rangeClosed(0x1c, 0x20)),
                IntStream.concat(IntStream.of(0x00a0, 0x1680), IntStream.concat(IntStream.rangeClosed(0x2000, 0x200a),
                        IntStream.of(0x2028, 0x2029, 0x202f, 0x205f, 0x3000)))).toArray();
        assertEquals(28, whitespace.length);
        for (int codepoint : whitespace) {
            String space = new String(Character.toChars(codepoint));
            assertTrue(Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint));
            for (String padded : List.of(space + "Padded", "Padded" + space)) {
                assertThrows(ProductRuleException.class, () -> listing(padded, BigDecimal.ONE, "VND"),
                        "Java boundary U+" + Integer.toHexString(codepoint));
                assertThrows(DataIntegrityViolationException.class, () -> rawInsert(padded, BigDecimal.ONE, "VND"),
                        "PostgreSQL boundary U+" + Integer.toHexString(codepoint));
            }
        }
        for (String invalid : new String[]{"", "A\u0000B", "\ud800", "\udc00", "A\ud800B", "A\udc00B"}) {
            assertThrows(ProductRuleException.class, () -> listing(invalid, BigDecimal.ONE, "VND"));
        }
        assertThrows(DataIntegrityViolationException.class, () -> rawInsert("", BigDecimal.ONE, "VND"));
        assertThrows(DataIntegrityViolationException.class, () -> rawInsert("A\u0000B", BigDecimal.ONE, "VND"));
        assertEquals(10, products.count());
    }

    @Test
    void T12_domainEditJpaApplyAdapterAndDatabaseAllPreserveImmutableSku() {
        ProductListing original = listing("Immutable-SKU", BigDecimal.ONE, "VND");
        var stored = adapter.create(original);
        ProductAccessPolicy fixturePolicy = new ProductAccessPolicy() {
            @Override public StoreAccess requireSellerManagement(String actor, UUID storeId) { return readStore(storeId); }
            @Override public StoreAccess requireModeration(String actor, UUID storeId) { return readStore(storeId); }
            @Override public StoreAccess readStore(UUID storeId) {
                return new StoreAccess(storeId, UUID.randomUUID(), "fixture-owner", true, true, false);
            }
        };
        ProductLifecycleService domain = new ProductLifecycleService(fixturePolicy, categories);
        ProductListing domainEdited = domain.edit("fixture-owner", original, CATEGORY, "Domain edit", BigDecimal.TEN, "VND");
        assertEquals(original.sku(), domainEdited.sku());
        ProductListing changedSku = new ProductListing(original.id(), original.storeId(), original.categoryId(),
                "Changed-SKU", "Sneaky change", original.unitPrice(), original.currency(), original.status(), original.reviewReason());
        ProductEntity detached = fresh(original.id());
        assertCode(ProductPersistenceException.Code.IMMUTABLE_IDENTITY, () -> detached.apply(changedSku));
        assertEquals(original, detached.toDomain());
        assertCode(ProductPersistenceException.Code.IMMUTABLE_IDENTITY,
                () -> adapter.update(changedSku, stored.version()));
        ProductListing changedStore = withStore(original, STORE_B);
        assertCode(ProductPersistenceException.Code.IMMUTABLE_IDENTITY, () -> detached.apply(changedStore));
        assertCode(ProductPersistenceException.Code.IMMUTABLE_IDENTITY,
                () -> adapter.update(changedStore, stored.version()));
        assertEquals(original, detached.toDomain());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE products SET inventory_sku=?,name=? WHERE id=?",
                        "Changed-SKU", "Sneaky change", original.id()));
        assertEquals(1, jdbc.update("UPDATE products SET inventory_sku=inventory_sku WHERE id=?", original.id()));
        assertEquals(original, fresh(original.id()).toDomain());
        assertTrue(adapter.findBySku("Changed-SKU").isEmpty());
    }

    @Test
    void T13_categoryRestrictAndBothDeleteInsertRaceOrdersNeverLeaveAnOrphan() throws Exception {
        var existing = adapter.create(listing("category-restrict", BigDecimal.ONE, "VND"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM categories WHERE id=?", CATEGORY));
        ProductListing missingCategory = withCategory(listing("missing-category", BigDecimal.ONE, "VND"), UUID.randomUUID());
        assertCode(ProductPersistenceException.Code.CATEGORY_NOT_FOUND, () -> adapter.create(missingCategory));
        assertEquals(existing.listing(), fresh(existing.listing().id()).toDomain());
        assertEquals("r", jdbc.queryForObject("SELECT confdeltype::text FROM pg_constraint"
                + " WHERE conrelid='products'::regclass AND conname='products_category_fk'", String.class));
        categoryInsertWinsDeleteRace();
        categoryDeleteWinsInsertRace();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM products p LEFT JOIN categories c ON c.id=p.category_id"
                + " WHERE c.id IS NULL", Integer.class));
    }

    @Test
    void T14_storeUuidIsOnlyLogicalAndTwoEntityManagersRejectAStaleUpdate() {
        ProductListing original = withStore(listing("versioned", BigDecimal.ONE, "VND"), UUID.randomUUID());
        ProductListing companion = listing("stale-em-companion", BigDecimal.ONE, "VND");
        var stored = adapter.create(original);
        assertEquals(original.storeId(), fresh(original.id()).getStoreId());
        assertEquals(Set.of("category_id"), Set.copyOf(jdbc.queryForList(
                "SELECT a.attname FROM pg_constraint c JOIN pg_attribute a"
                        + " ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)"
                        + " WHERE c.conrelid='products'::regclass AND c.contype='f'", String.class)));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE products SET version=-1 WHERE id=?", original.id()));
        EntityManager first = entityManagers.createEntityManager(), second = entityManagers.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            ProductEntity winner = first.find(ProductEntity.class, original.id());
            ProductEntity stale = second.find(ProductEntity.class, original.id());
            assertEquals(stored.version(), winner.getVersion().longValue());
            assertEquals(stored.version(), stale.getVersion().longValue());
            winner.apply(revise(original, "Winner", BigDecimal.TEN, "VND"));
            first.flush();
            first.getTransaction().commit();
            second.persist(new ProductEntity(companion));
            second.flush();
            stale.apply(revise(original, "Stale overwrite", new BigDecimal("20"), "VND"));
            assertThrows(OptimisticLockException.class, second::flush);
            second.getTransaction().rollback();
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
        }
        assertEquals("Winner", fresh(original.id()).getName());
        assertEquals(BigDecimal.TEN, fresh(original.id()).getUnitPrice());
        assertEquals(1L, fresh(original.id()).getVersion());
        assertTrue(adapter.findById(companion.id()).isEmpty(), "The real optimistic failure must roll back its companion insert");
        assertCode(ProductPersistenceException.Code.STALE_VERSION,
                () -> adapter.update(revise(original, "Old expected version", BigDecimal.ONE, "VND"), stored.version()));
        assertEquals("Winner", adapter.findById(original.id()).orElseThrow().listing().name());
    }

    @Test
    void T15_requiredOuterRollbackAndIntegrityFailuresAreAtomicAndPublicGuardsStayClosed() throws Exception {
        var original = adapter.create(listing("atomic-original", BigDecimal.ONE, "VND"));
        ProductListing rolledBack = listing("outer-rollback", BigDecimal.ONE, "VND");
        transaction().executeWithoutResult(tx -> {
            adapter.create(rolledBack);
            adapter.update(revise(original.listing(), "Rolled back update", BigDecimal.TEN, "VND"), original.version());
            tx.setRollbackOnly();
        });
        assertTrue(adapter.findById(rolledBack.id()).isEmpty());
        assertEquals(original, adapter.findById(original.listing().id()).orElseThrow());
        ProductListing duplicateCompanion = listing("duplicate-companion", BigDecimal.ONE, "VND");
        assertCode(ProductPersistenceException.Code.DUPLICATE_SKU, () -> transaction().executeWithoutResult(tx -> {
            adapter.create(duplicateCompanion);
            adapter.create(withStore(listing(original.listing().sku(), BigDecimal.TEN, "VND"), STORE_B));
        }));
        assertTrue(adapter.findById(duplicateCompanion.id()).isEmpty());
        ProductListing fkCompanion = listing("fk-companion", BigDecimal.ONE, "VND");
        assertCode(ProductPersistenceException.Code.CATEGORY_NOT_FOUND, () -> transaction().executeWithoutResult(tx -> {
            adapter.create(fkCompanion);
            adapter.create(withCategory(listing("fk-failure", BigDecimal.ONE, "VND"), UUID.randomUUID()));
        }));
        assertTrue(adapter.findById(fkCompanion.id()).isEmpty());
        ProductListing staleCompanion = listing("stale-companion", BigDecimal.ONE, "VND");
        assertCode(ProductPersistenceException.Code.STALE_VERSION, () -> transaction().executeWithoutResult(tx -> {
            products.findById(original.listing().id()).orElseThrow();
            requiresNew().executeWithoutResult(other -> adapter.update(
                    revise(original.listing(), "Committed winner", BigDecimal.TEN, "VND"), original.version()));
            adapter.create(staleCompanion);
            adapter.update(revise(original.listing(), "Stale overwrite", BigDecimal.ONE, "VND"), original.version());
        }));
        assertTrue(adapter.findById(staleCompanion.id()).isEmpty());
        assertEquals("Committed winner", fresh(original.listing().id()).getName());
        assertEquals(1L, fresh(original.listing().id()).getVersion());
        assertEquals(1, products.count());

        assertTrue(context.getBeansOfType(ProductLifecycleService.class).isEmpty());
        assertFalse(environment.getProperty("HK5_FINANCE_POLICY_APPROVED", Boolean.class, false));
        assertFalse(environment.getProperty("INVENTORY_ORDER_PAIR_ENABLED", Boolean.class, false));
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            for (String path : List.of("/api/products", "/api/products/" + original.listing().id(),
                    "/internal/products/batch", "/internal/catalog/checkout")) {
                var response = http.perform(MockMvcRequestBuilders.request(method, path)
                        .header("X-User-Id", "fixture-owner").header("X-Roles", "ADMIN,SELLER")
                        .header("Authorization", "Bearer forged-not-a-verified-jwt")
                        .contentType("application/json").content("{\"storeId\":\"" + STORE_A + "\"}"))
                        .andReturn().getResponse();
                if (path.startsWith("/api/products")) {
                    assertEquals(403, response.getStatus(), method + " " + path);
                    assertTrue(response.getContentAsString().contains("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
                } else assertEquals(401, response.getStatus(), method + " " + path);
            }
        }
        assertEquals(1, products.count());
    }

    @Test
    void cachedNoOpUpdateRejectsExpectedVersionOlderThanCommittedDatabaseVersion() {
        var original = adapter.create(listing("cached-noop", BigDecimal.ONE, "VND"));
        ProductListing winner = revise(original.listing(), "Committed winner", BigDecimal.TEN, "VND");
        assertCode(ProductPersistenceException.Code.STALE_VERSION, () -> transaction().executeWithoutResult(tx -> {
            ProductEntity cached = products.findById(original.listing().id()).orElseThrow();
            assertEquals(0L, cached.getVersion());
            requiresNew().executeWithoutResult(other -> adapter.update(winner, original.version()));
            assertEquals(0L, cached.getVersion(), "The outer EntityManager must still hold its old version");
            // Applying this unchanged old snapshot causes no dirty SQL; a stale check must still reject it.
            adapter.update(original.listing(), original.version());
        }));
        assertEquals(winner, fresh(original.listing().id()).toDomain());
        assertEquals(1L, fresh(original.listing().id()).getVersion());
        assertEquals(1, products.count());
    }

    @Test
    void cachedOldEntityDoesNotRejectValidExpectedVersionFromCurrentDatabaseState() {
        var original = adapter.create(listing("cached-current", BigDecimal.ONE, "VND"));
        var updated = transaction().execute(tx -> {
            ProductEntity cached = products.findById(original.listing().id()).orElseThrow();
            assertEquals(0L, cached.getVersion());
            var committed = requiresNew().execute(other -> adapter.update(
                    revise(original.listing(), "First committed edit", BigDecimal.TEN, "VND"), original.version()));
            assertNotNull(committed);
            assertEquals(1, committed.version());
            assertEquals(0L, cached.getVersion(), "The outer EntityManager must still hold its old version");
            return adapter.update(revise(committed.listing(), "Second committed edit", new BigDecimal("20"), "VND"),
                    committed.version());
        });
        assertNotNull(updated);
        assertEquals(2, updated.version());
        assertEquals(updated.listing(), fresh(original.listing().id()).toDomain());
        assertEquals(2L, fresh(original.listing().id()).getVersion());
        assertEquals(1, products.count());
    }

    @Test
    void duplicatePrimaryIdCannotMergeOverTheExistingProduct() {
        var original = adapter.create(listing("primary-original", BigDecimal.ONE, "VND"));
        ProductListing reusedId = new ProductListing(original.listing().id(), STORE_B, CATEGORY, "primary-reused",
                "Attempted overwrite", BigDecimal.TEN, "VND", ProductStatus.DRAFT, null);
        assertCode(ProductPersistenceException.Code.CONSTRAINT_VIOLATION, () -> adapter.create(reusedId));
        assertEquals(original, adapter.findById(original.listing().id()).orElseThrow());
        assertEquals(original.listing(), fresh(original.listing().id()).toDomain());
        assertTrue(adapter.findBySku(reusedId.sku()).isEmpty());
        assertEquals(1, products.count());
    }

    @Test
    void productionModerationConstraintsRequireReasonsAndCountSupplementaryCodepoints() {
        String validReason = "\ud83d\ude80".repeat(500), oversizedReason = validReason + "\ud83d\ude80";
        assertEquals(500, validReason.codePointCount(0, validReason.length()));
        assertEquals(1000, validReason.length());
        for (ProductStatus status : List.of(ProductStatus.REJECTED, ProductStatus.SUSPENDED)) {
            for (String invalid : new String[]{null, "", " ", "\t", "\u2003", oversizedReason}) {
                assertThrows(ProductRuleException.class, () -> new ProductListing(UUID.randomUUID(), STORE_A, CATEGORY,
                        "invalid-reason", "Product", BigDecimal.ONE, "VND", status, invalid));
                assertThrows(DataIntegrityViolationException.class, () -> rawModerationInsert(status, invalid));
            }
            ProductListing listing = new ProductListing(UUID.randomUUID(), STORE_A, CATEGORY,
                    "moderation-" + status, "Product", BigDecimal.ONE, "VND", status, validReason);
            var stored = adapter.create(listing);
            assertEquals(listing, fresh(listing.id()).toDomain());
            assertEquals(validReason, adapter.findById(listing.id()).orElseThrow().listing().reviewReason());
            assertThrows(ProductRuleException.class, () -> new ProductListing(listing.id(), STORE_A, CATEGORY,
                    listing.sku(), listing.name(), listing.unitPrice(), "VND", ProductStatus.ACTIVE, validReason));
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE products SET status='ACTIVE' WHERE id=?", listing.id()));
            assertEquals(listing, fresh(listing.id()).toDomain());
            ProductListing active = new ProductListing(listing.id(), STORE_A, CATEGORY, listing.sku(), listing.name(),
                    listing.unitPrice(), "VND", ProductStatus.ACTIVE, null);
            assertEquals(active, adapter.update(active, stored.version()).listing());
            assertEquals(active, fresh(listing.id()).toDomain());
            UUID rawId = rawModerationInsert(status, validReason);
            assertEquals(validReason, fresh(rawId).getReviewReason());
            assertEquals(1, jdbc.update("UPDATE products SET status='ACTIVE',review_reason=NULL,version=version+1 WHERE id=?",
                    rawId));
            assertEquals(ProductStatus.ACTIVE, fresh(rawId).getStatus());
            assertNull(fresh(rawId).getReviewReason());
        }
        assertEquals(4, products.count());
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"REJECTED", "SUSPENDED"})
    void lifecycleModerationCanonicalReasonRoundTripsThroughJpaAndRawSql(ProductStatus status) {
        flyway.validate();
        System.out.println("Moderation parity Flyway history: " + jdbc.queryForList(
                "SELECT version,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"));
        assertModerationReasonPolicy(status, " Fix details ", "Fix details");
        assertModerationReasonPolicy(status, "Fix details", "Fix details");
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"REJECTED", "SUSPENDED"})
    void lifecycleModerationUnicodeReasonMatchesJavaStripAndDatabasePolicy(ProductStatus status) {
        int[] javaWhitespace = {9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 5760,
                8192, 8193, 8194, 8195, 8196, 8197, 8198, 8200, 8201, 8202, 8232, 8233, 8287, 12288};
        assertEquals(25, javaWhitespace.length);
        for (int codepoint : javaWhitespace) {
            assertTrue(Character.isWhitespace(codepoint));
            String space = new String(Character.toChars(codepoint));
            assertModerationReasonPolicy(status, space + "Fix details", "Fix details");
            assertModerationReasonPolicy(status, "Fix details" + space, "Fix details");
        }
        for (int codepoint : new int[]{0x00a0, 0x2007, 0x202f}) {
            assertFalse(Character.isWhitespace(codepoint));
            assertTrue(Character.isSpaceChar(codepoint));
            String space = new String(Character.toChars(codepoint));
            String unchanged = space + "Fix details" + space;
            assertModerationReasonPolicy(status, unchanged, unchanged);
            assertModerationReasonPolicy(status, space, space); // Java isBlank/strip preserve non-breaking spaces.
            assertModerationReasonPolicy(status, "\t" + space + " Fix details " + space + "\n",
                    space + " Fix details " + space);
        }
        // Preserve internal whitespace, case and Unicode composition; only boundary Java whitespace is stripped.
        for (String unchanged : List.of("Fix \t\u2003details", "R\u00e9viser d\u00e9tails", "Re\u0301viser de\u0301tails",
                "\u200bFix details\u200b")) {
            assertModerationReasonPolicy(status, " " + unchanged + " ", unchanged);
        }
    }

    private void assertModerationReasonPolicy(ProductStatus status, String raw, String canonical) {
        // A fixture supplies moderation authority only; persistence and SQL enforcement use the real database.
        ProductAccessPolicy fixturePolicy = new ProductAccessPolicy() {
            @Override public StoreAccess requireSellerManagement(String actor, UUID storeId) { return readStore(storeId); }
            @Override public StoreAccess requireModeration(String actor, UUID storeId) { return readStore(storeId); }
            @Override public StoreAccess readStore(UUID storeId) {
                return new StoreAccess(storeId, STORE_B, "fixture-owner", true, true, false);
            }
        };
        ProductLifecycleService lifecycle = new ProductLifecycleService(fixturePolicy, categories);
        for (boolean update : new boolean[]{false, true}) {
            var stored = transaction().execute(tx -> {
                ProductListing current = new ProductListing(UUID.randomUUID(), STORE_A, CATEGORY,
                        "reason-" + UUID.randomUUID(), "Moderated product", BigDecimal.ONE, "VND",
                        status == ProductStatus.REJECTED ? ProductStatus.PENDING_REVIEW : ProductStatus.ACTIVE, null);
                var existing = update ? adapter.create(current) : null;
                ProductListing moderated = status == ProductStatus.REJECTED
                        ? lifecycle.reject("fixture-admin", current, raw) : lifecycle.suspend("fixture-admin", current, raw);
                assertEquals(status, moderated.status());
                assertEquals(canonical, moderated.reviewReason(), "Canonical constructor must run before JPA mapping");
                assertEquals(canonical, new ProductEntity(moderated).getReviewReason());
                var persisted = update ? adapter.update(moderated, existing.version()) : adapter.create(moderated);
                entityManager.flush();
                return persisted;
            });
            assertNotNull(stored);
            assertEquals(canonical, stored.listing().reviewReason());
            assertStoredModerationReason(stored.listing().id(), status, canonical);
        }
        if (!raw.equals(canonical)) assertReasonCheckRejected(() -> rawModerationInsert(status, raw));
        UUID rawId = rawModerationInsert(status, canonical);
        assertStoredModerationReason(rawId, status, canonical);
        if (!raw.equals(canonical)) {
            assertReasonCheckRejected(() -> jdbc.update("UPDATE products SET review_reason=? WHERE id=?", raw, rawId));
        } else {
            assertEquals(1, jdbc.update("UPDATE products SET review_reason=? WHERE id=?", raw, rawId));
        }
        assertStoredModerationReason(rawId, status, canonical);
    }

    private void assertStoredModerationReason(UUID id, ProductStatus status, String expected) {
        ProductEntity reloaded = fresh(id);
        assertEquals(status, reloaded.getStatus());
        // Inspect both the entity field and raw SQL so toDomain's canonical constructor cannot hide stored padding.
        assertEquals(expected, reloaded.getReviewReason());
        assertEquals(expected, jdbc.queryForObject("SELECT review_reason FROM products WHERE id=?", String.class, id));
    }

    private void assertReasonCheckRejected(Runnable write) {
        var failure = assertThrows(DataIntegrityViolationException.class, write::run);
        var postgres = assertInstanceOf(PSQLException.class, failure.getMostSpecificCause());
        assertEquals("23514", postgres.getSQLState());
        assertNotNull(postgres.getServerErrorMessage());
        assertEquals("products_reason_valid", postgres.getServerErrorMessage().getConstraint());
    }

    private void categoryInsertWinsDeleteRace() throws Exception {
        UUID category = UUID.randomUUID();
        insertCategory(category);
        ProductListing listing = withCategory(listing("insert-before-delete", BigDecimal.ONE, "VND"), category);
        CountDownLatch inserted = new CountDownLatch(1), deleting = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger insertPid = new AtomicInteger(), deletePid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> insertion = workers.submit(() -> transaction().executeWithoutResult(tx -> {
                insertPid.set(backendPid());
                adapter.create(listing);
                inserted.countDown();
                await(release);
            }));
            assertTrue(inserted.await(15, TimeUnit.SECONDS));
            Future<?> deletion = workers.submit(() -> transaction().executeWithoutResult(tx -> {
                deletePid.set(backendPid());
                deleting.countDown();
                jdbc.update("DELETE FROM categories WHERE id=?", category);
            }));
            assertTrue(deleting.await(15, TimeUnit.SECONDS));
            assertBlockedBy(deletePid.get(), insertPid.get());
            release.countDown();
            insertion.get(15, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> deletion.get(15, TimeUnit.SECONDS));
            assertInstanceOf(DataIntegrityViolationException.class, failure.getCause());
            assertEquals(listing, fresh(listing.id()).toDomain());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM categories WHERE id=?", Integer.class, category));
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    private void categoryDeleteWinsInsertRace() throws Exception {
        UUID category = UUID.randomUUID();
        insertCategory(category);
        ProductListing listing = withCategory(listing("delete-before-insert", BigDecimal.ONE, "VND"), category);
        CountDownLatch deleted = new CountDownLatch(1), inserting = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger deletePid = new AtomicInteger(), insertPid = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = workers.submit(() -> transaction().executeWithoutResult(tx -> {
                deletePid.set(backendPid());
                jdbc.update("DELETE FROM categories WHERE id=?", category);
                deleted.countDown();
                await(release);
            }));
            assertTrue(deleted.await(15, TimeUnit.SECONDS));
            Future<?> insertion = workers.submit(() -> transaction().executeWithoutResult(tx -> {
                insertPid.set(backendPid());
                inserting.countDown();
                adapter.create(listing);
            }));
            assertTrue(inserting.await(15, TimeUnit.SECONDS));
            assertBlockedBy(insertPid.get(), deletePid.get());
            release.countDown();
            deletion.get(15, TimeUnit.SECONDS);
            assertFailureCode(insertion, ProductPersistenceException.Code.CATEGORY_NOT_FOUND);
            assertTrue(adapter.findById(listing.id()).isEmpty());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM categories WHERE id=?", Integer.class, category));
        } finally {
            release.countDown();
            stop(workers);
        }
    }

    private ProductListing listing(String sku, BigDecimal price, String currency) {
        return new ProductListing(UUID.randomUUID(), STORE_A, CATEGORY, sku, "Product", price, currency, ProductStatus.DRAFT, null);
    }

    private ProductListing revise(ProductListing original, String name, BigDecimal price, String currency) {
        return new ProductListing(original.id(), original.storeId(), original.categoryId(), original.sku(),
                name, price, currency, original.status(), original.reviewReason());
    }

    private ProductListing withStore(ProductListing original, UUID store) {
        return new ProductListing(original.id(), store, original.categoryId(), original.sku(), original.name(),
                original.unitPrice(), original.currency(), original.status(), original.reviewReason());
    }

    private ProductListing withCategory(ProductListing original, UUID category) {
        return new ProductListing(original.id(), original.storeId(), category, original.sku(), original.name(),
                original.unitPrice(), original.currency(), original.status(), original.reviewReason());
    }

    private UUID rawInsert(String sku, BigDecimal price, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT, id, STORE_A, CATEGORY, sku, "Raw product", price, currency);
        return id;
    }

    private UUID rawModerationInsert(ProductStatus status, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO products"
                + "(id,store_id,category_id,inventory_sku,name,unit_price,currency,status,review_reason,version)"
                + " VALUES (?,?,?,?,'Moderated product',1,'VND',?,?,0)",
                id, STORE_A, CATEGORY, "raw-moderation-" + id, status.name(), reason);
        return id;
    }

    private void insertCategory(UUID id) {
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", id, "Acceptance category");
    }

    private ProductEntity fresh(UUID id) {
        EntityManager fresh = entityManagers.createEntityManager();
        try {
            ProductEntity found = fresh.find(ProductEntity.class, id);
            assertNotNull(found);
            return found;
        } finally {
            fresh.close();
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate transaction = transaction();
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private int backendPid() {
        jdbc.execute("SET LOCAL lock_timeout='20s'");
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void assertBlockedBy(int waiter, int holder) {
        assertTrue(waiter > 0 && holder > 0 && waiter != holder);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, holder, waiter))) {
                Thread.sleep(25);
            }
        }, "PostgreSQL never reported the expected blocking backend relationship");
    }

    private static void assertCode(ProductPersistenceException.Code code, Runnable action) {
        assertEquals(code, assertThrows(ProductPersistenceException.class, action::run).getCode());
    }

    private static void assertFailureCode(Future<?> future, ProductPersistenceException.Code code) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS));
        assertEquals(code, assertInstanceOf(ProductPersistenceException.class, failure.getCause()).getCode());
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

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
