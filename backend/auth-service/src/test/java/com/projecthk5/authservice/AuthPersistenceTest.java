package com.projecthk5.authservice;

import com.projecthk5.authservice.persistence.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthPersistenceTest {
    private final String appPassword = UUID.randomUUID().toString();
    private final String migrationPassword = UUID.randomUUID().toString();
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withReuse(Boolean.parseBoolean(System.getenv("TV1_RETAIN_TEST_DATABASE")))
            .withDatabaseName("postgres").withUsername("hk5").withPassword(UUID.randomUUID().toString())
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("../../docker/postgres/pg_hba.conf").toAbsolutePath()),
                    "/etc/postgresql/test_hba.conf")
            .withCommand("postgres", "-c", "hba_file=/etc/postgresql/test_hba.conf");
    private ConfigurableApplicationContext context;
    private String jdbcUrl;
    private Flyway flyway;

    @BeforeAll
    void startIsolatedDatabase() throws Exception {
        // No disabledWithoutDocker/assumptions: unavailable Docker is a real test failure.
        if (Boolean.parseBoolean(System.getenv("TV1_RETAIN_TEST_DATABASE"))) {
            assertEquals("true", System.getenv("TESTCONTAINERS_RYUK_DISABLED"), "Retained DB must not be reaped");
            assertTrue(org.testcontainers.utility.TestcontainersConfiguration.getInstance().environmentSupportsReuse(),
                    "Set TESTCONTAINERS_REUSE_ENABLE=true to disable JVM shutdown cleanup too");
        }
        postgres.start();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()); var sql = connection.createStatement()) {
            sql.execute("CREATE ROLE auth_migrator LOGIN PASSWORD '" + migrationPassword + "'");
            sql.execute("CREATE ROLE auth_app LOGIN PASSWORD '" + appPassword + "'");
            sql.execute("CREATE DATABASE auth_db OWNER auth_migrator TEMPLATE template0");
            sql.execute("CREATE DATABASE user_db TEMPLATE template0");
        }
        jdbcUrl = postgres.getJdbcUrl().replace("/postgres", "/auth_db");
        try (var connection = adminConnection(); var sql = connection.createStatement()) {
            sql.execute("REVOKE ALL ON DATABASE auth_db FROM PUBLIC");
            sql.execute("GRANT CONNECT ON DATABASE auth_db TO auth_app");
            sql.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            sql.execute("GRANT USAGE ON SCHEMA public TO auth_app");
            sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE auth_migrator REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC");
            try (var rows = sql.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname='public'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
        flyway = migrationFlyway().target("1").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        reproduceV1NormalizationMismatch();
        Map<UUID, String> existing = new LinkedHashMap<>();
        try (var c = appConnection()) {
            for (String prefix : List.of("ascii", "\u00e9", "i\u0307", "\u03bf\u03c2", "\ud801\udc28")) {
                String email = prefix + uniqueEmail();
                existing.put(insertEmail(c, email), email);
            }
        }
        flyway = migrationFlyway().load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        context = application(true);
        assertTrue(context.getEnvironment().getProperty("spring.flyway.enabled", Boolean.class));
        for (var entry : existing.entrySet()) {
            assertEquals(entry.getValue(), repo(AccountRepository.class).findById(entry.getKey()).orElseThrow().getEmail());
        }
        assertEquals(List.of("1", "2", "3", "4"), Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion()).toList());
        System.out.println("V1_TO_V2_UPGRADE: 5 ASCII/Unicode account IDs/emails preserved; V1 checksum validated");
    }

    private ConfigurableApplicationContext application(boolean migration) {
        var environment = new StandardEnvironment();
        byte[] signingKey = new byte[32];
        new java.security.SecureRandom().nextBytes(signingKey);
        // Auth fails closed without a signing keyring; give the test context a throwaway one.
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-jwt", Map.of(
                "JWT_ISSUER_URI", "http://auth.test.invalid", "JWT_AUDIENCE", "smart-ecommerce-api",
                "JWT_HS256_CURRENT_KID", "test-key-v1",
                "JWT_HS256_CURRENT_KEY_B64", java.util.Base64.getEncoder().encodeToString(signingKey))));
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-database", Map.of(
                "spring.datasource.url", jdbcUrl,
                "spring.datasource.password", appPassword,
                "spring.flyway.user", "auth_migrator",
                "spring.flyway.password", migrationPassword,
                "spring.flyway.locations", "classpath:db/migration",
                "spring.main.banner-mode", "off")));
        var builder = new SpringApplicationBuilder(AuthServiceApplication.class)
                .web(WebApplicationType.NONE).environment(environment);
        if (migration) { builder.profiles("migration"); }
        return builder.run();
    }

    @AfterAll
    void stopIsolatedDatabase() {
        if (context != null) { context.close(); }
        if (Boolean.parseBoolean(System.getenv("TV1_RETAIN_TEST_DATABASE"))) {
            System.out.println("RETAINED_TEST_DATABASE_CONTAINER: " + postgres.getContainerId());
        } else {
            postgres.stop();
        }
    }

    @Test
    void migrationRerunsAndValidatesWithRuntimeDdlDisabled() throws Exception {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.info().pending().length);
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        try (var c = appConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT current_user,current_database(),current_setting('TimeZone')")) {
            assertTrue(r.next());
            assertEquals("auth_app", r.getString(1));
            assertEquals("auth_db", r.getString(2));
            assertEquals("UTC", r.getString(3));
        }
    }

    @Test
    void runtimeHasOnlyExplicitBusinessDmlAndCannotTouchSchemaHistory() throws Exception {
        assertSqlState("42501", "CREATE TABLE forbidden(id int)");
        assertSqlState("42501", "CREATE SCHEMA forbidden");
        assertSqlState("42501", "CREATE TEMP TABLE forbidden(id int)");
        assertSqlState("42501", "ALTER TABLE accounts ADD COLUMN forbidden int");
        assertSqlState("42501", "DROP TABLE accounts");
        assertSqlState("42501", "SELECT * FROM flyway_schema_history");
        assertSqlState("42501", "UPDATE flyway_schema_history SET success=false");
        assertSqlState("42501", "DELETE FROM refresh_tokens");
        assertSqlState("42501", "SET ROLE auth_migrator");
        try (var c = DriverManager.getConnection(jdbcUrl, "auth_migrator", migrationPassword);
             var s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("CREATE TABLE migration_probe(id int)");
            c.rollback();
        }
        for (String database : List.of("user_db", "postgres")) {
            for (String role : List.of("auth_app", "auth_migrator")) {
                var error = assertThrows(SQLException.class, () -> DriverManager.getConnection(
                        jdbcUrl.replace("/auth_db", "/" + database), role,
                        role.equals("auth_app") ? appPassword : migrationPassword));
                assertEquals("28000", error.getSQLState());
            }
        }
        try (var c = adminConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT count(*) FROM pg_roles WHERE rolname IN ('auth_app','auth_migrator') "
                     + "AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)")) {
            assertTrue(r.next());
            assertEquals(0, r.getInt(1));
        }
    }

    @Test
    void migrationRejectsWrongActorBeforeCreatingBusinessTables() throws Exception {
        String migration;
        try (var resource = getClass().getResourceAsStream("/db/migration/V1__auth_persistence.sql")) {
            migration = new String(Objects.requireNonNull(resource).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (var c = adminConnection(); var s = c.createStatement()) {
            String sql = migration;
            var error = assertThrows(SQLException.class, () -> s.execute(sql));
            assertEquals("P0001", error.getSQLState());
        }
    }

    @Test
    void accountPersistsNormalizedEmailRolesAndDurableTaskAtomically() {
        var account = account("\u2003 Customer" + UUID.randomUUID() + "@EXAMPLE.INVALID \u2003");
        repo(AccountPersistence.class).create(account);
        tx(() -> {
            var saved = repo(AccountRepository.class).findByEmailNormalized("  " + account.getEmail().toUpperCase(Locale.ROOT) + " ").orElseThrow();
            assertEquals(account.getId(), saved.getId());
            assertEquals(Set.of(Account.Role.CUSTOMER), saved.getRoles());
            assertEquals(account.getCreatedAt(), saved.getCreatedAt());
            var task = repo(ProfileProvisioningTaskRepository.class).findById(saved.getId()).orElseThrow();
            assertEquals(0, task.getAttempts());
            assertNull(task.getCompletedAt());
            assertEquals(saved.getCreatedAt(), task.getNextAttemptAt());
            return null;
        });
        assertEquals("i\u0307@example.invalid", Account.normalizeEmail("\u2003\u0130@EXAMPLE.INVALID\u2003"));
        var unicode = account("\u2003\u0130" + UUID.randomUUID() + "@EXAMPLE.INVALID\u2003");
        repo(AccountPersistence.class).create(unicode);
        assertEquals(unicode.getId(), repo(AccountRepository.class).findByEmailNormalized(
                unicode.getEmail()).orElseThrow().getId());
    }

    @Test
    void accountRolesAndProvisioningRollbackTogether() {
        var account = account(uniqueEmail());
        assertThrows(IllegalStateException.class, () -> tx(() -> {
            repo(AccountPersistence.class).create(account);
            throw new IllegalStateException("Injected failure before commit");
        }));
        assertFalse(repo(AccountRepository.class).existsById(account.getId()));
        assertFalse(repo(ProfileProvisioningTaskRepository.class).existsById(account.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "\u00c9", "\u0130", "\u039f\u03a3", "\ud801\udc00"})
    void normalizedEmailHasOneWinnerUnderConcurrentInsert(String prefix) throws Exception {
        String email = prefix + uniqueEmail();
        var accounts = List.of(account(email), account("\u2003" + email.toUpperCase(Locale.ROOT) + "\u2003"));
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (var candidate : accounts) {
                attempts.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        repo(AccountPersistence.class).create(candidate);
                        return true;
                    } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                        return false;
                    }
                }));
            }
            int successes = 0;
            for (var attempt : attempts) { if (attempt.get(20, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes);
        }
        for (var candidate : accounts) {
            boolean exists = repo(AccountRepository.class).existsById(candidate.getId());
            assertEquals(exists, repo(ProfileProvisioningTaskRepository.class).existsById(candidate.getId()));
            try (var c = appConnection(); var statement = c.prepareStatement(
                    "SELECT count(*) FROM account_role_grants WHERE account_id=?")) {
                statement.setObject(1, candidate.getId());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(exists ? 1 : 0, rows.getInt(1));
                }
            }
        }
    }

    @Test
    void unicodeDuplicateLookupAndCanonicalSqlInsertUseSameUniqueKey() throws Exception {
        for (String prefix : List.of("\u00c9", "\u0130", "\u039f\u03a3", "\ud801\udc00", "\u1e9e")) {
            String input = prefix + uniqueEmail();
            var saved = account(input);
            repo(AccountPersistence.class).create(saved);
            assertEquals(saved.getId(), repo(AccountRepository.class).findByEmailNormalized(
                    "\u2003" + input + "\u2003").orElseThrow().getId());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> repo(AccountPersistence.class).create(account("\u2003" + input + "\u2003")));
            try (var c = appConnection()) {
                var error = assertThrows(SQLException.class, () -> insertEmail(c, saved.getEmail()));
                assertEquals("23505", error.getSQLState());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migrationStopsOnLegacyNoncanonicalDataWithoutChangingData(boolean duplicate) throws Exception {
        String schema = "legacy_probe_" + duplicate;
        var legacy = migrationFlyway().schemas(schema).defaultSchema(schema).target("1").load();
        assertEquals(1, legacy.migrate().migrationsExecuted);
        Map<UUID, String> before = new HashMap<>();
        try (var c = DriverManager.getConnection(jdbcUrl, "auth_migrator", migrationPassword)) {
            c.setSchema(schema);
            String suffix = uniqueEmail();
            before.put(insertEmail(c, "\u00c9" + suffix), "\u00c9" + suffix);
            if (duplicate) { before.put(insertEmail(c, "\u00e9" + suffix), "\u00e9" + suffix); }
        }
        EmailInventoryTest.Inventory inventory;
        try (var c = DriverManager.getConnection(jdbcUrl, "auth_migrator", migrationPassword)) {
            c.setSchema(schema);
            inventory = EmailInventoryTest.scan(c);
            assertEquals(duplicate ? 2 : 1, inventory.total());
            assertEquals(1, inventory.noncanonical().size());
            assertEquals(inventory.noncanonical(), inventory.unicodeNoncanonical());
            assertEquals(duplicate ? 1 : 0, inventory.duplicateCanonicalGroups().size());
            if (duplicate) { assertEquals(before.keySet(), new HashSet<>(inventory.duplicateCanonicalGroups().getFirst())); }
            System.out.println("LEGACY_FIXTURE_READ_ONLY_EMAIL_INVENTORY: " + inventory);
        }
        var upgrade = migrationFlyway().schemas(schema).defaultSchema(schema).load();
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class, upgrade::migrate);
        Throwable cause = failure;
        while (cause.getCause() != null) { cause = cause.getCause(); }
        assertInstanceOf(SQLException.class, cause);
        assertEquals("23514", ((SQLException) cause).getSQLState());
        assertTrue(legacy.validateWithResult().validationSuccessful);
        assertEquals("1", upgrade.info().current().getVersion().getVersion());
        assertEquals(3, upgrade.info().pending().length);
        Map<UUID, String> after = new HashMap<>();
        try (var c = DriverManager.getConnection(jdbcUrl, "auth_migrator", migrationPassword)) {
            c.setSchema(schema);
            try (var s = c.createStatement(); var rows = s.executeQuery("SELECT id,email FROM accounts")) {
                while (rows.next()) { after.put(rows.getObject(1, UUID.class), rows.getString(2)); }
            }
        }
        assertEquals(before, after);
        try (var c = DriverManager.getConnection(jdbcUrl, "auth_migrator", migrationPassword)) {
            c.setSchema(schema);
            assertEquals(inventory, EmailInventoryTest.scan(c));
        }
        System.out.println("V2_LEGACY_CONFLICT: duplicate=" + duplicate + "; migration rejected; all IDs/emails preserved; Flyway remains V1");
    }

    @Test
    void postgresConstraintRejectsExactlyJavaLowercaseChangingScalars() throws Exception {
        String migration = canonicalMigration();
        String marker = "email COLLATE \"C\" !~ ";
        int start = migration.indexOf(marker) + marker.length();
        String literal = migration.substring(start, migration.indexOf('\n', start)).strip();
        Set<Integer> expected = new HashSet<>();
        for (int cp = 1; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE) { continue; }
            String value = new String(Character.toChars(cp));
            if (!value.equals(value.toLowerCase(Locale.ROOT))) { expected.add(cp); }
        }
        Set<Integer> actual = new HashSet<>();
        try (var c = appConnection(); var s = c.createStatement()) {
            s.setQueryTimeout(30);
            try (var rows = s.executeQuery("SELECT code FROM generate_series(1,1114111) cp(code) "
                    + "WHERE chr(CASE WHEN code BETWEEN 55296 AND 57343 THEN 1 ELSE code END) "
                    + "COLLATE \"C\" ~ " + literal)) {
                while (rows.next()) { actual.add(rows.getInt(1)); }
            }
        }
        assertEquals(expected, actual);
        System.out.println("V2_UNICODE_SCALAR_PARITY: " + actual.size() + " changing scalars; all scalar values checked");
    }

    @Test
    void rawUnicodeAliasCannotBypassDatabaseNormalization() throws Exception {
        for (String prefix : List.of("\u00c9", "\u0130", "\u03a3", "\ud801\udc00", "\u2003x", "\u001cx")) {
            String suffix = uniqueEmail();
            var normalized = account(prefix + suffix);
            repo(AccountPersistence.class).create(normalized);
            try (var c = appConnection(); var statement = c.prepareStatement(
                    "INSERT INTO accounts(id,email,password_hash,status,created_at) VALUES (?,?,?,'ACTIVE',?)")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, prefix + suffix);
                statement.setString(3, "{test-only}not-a-real-password-hash");
                statement.setTimestamp(4, Timestamp.from(now()));
                var error = assertThrows(SQLException.class, statement::executeUpdate, prefix);
                assertEquals("23514", error.getSQLState());
            }
        }
    }

    @Test
    void allJavaStripCharactersAreRejectedAtSqlEmailBoundaries() throws Exception {
        try (var c = appConnection()) {
            for (int cp = 1; cp <= Character.MAX_CODE_POINT; cp++) {
                if (!Character.isWhitespace(cp)) { continue; }
                String whitespace = new String(Character.toChars(cp));
                for (String value : List.of(whitespace + uniqueEmail(), uniqueEmail() + whitespace)) {
                    var error = assertThrows(SQLException.class, () -> insertEmail(c, value));
                    assertEquals("23514", error.getSQLState(), codePoints(whitespace));
                }
            }
        }
    }

    @Test
    void canonicalUnicodeIsNotRestrictedToAsciiOrCaseFolded() {
        String suffix = uniqueEmail();
        Set<UUID> saved = new HashSet<>();
        for (String prefix : List.of("\u00df", "ss", "\u00e9", "e\u0301", "\u03bf\u03c2", "\u03bf\u03c3")) {
            var account = account(prefix + suffix);
            repo(AccountPersistence.class).create(account);
            saved.add(repo(AccountRepository.class).findByEmailNormalized(prefix + suffix).orElseThrow().getId());
        }
        assertEquals(6, saved.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumeAcquiresFamilyLockEvenWhenCallerDoesNot(boolean logout) throws Exception {
        var session = session();
        var started = new CountDownLatch(1);
        try (var holder = appConnection(); var sql = holder.prepareStatement(
                "SELECT id FROM refresh_families WHERE id=? FOR UPDATE");
             var executor = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            sql.setObject(1, session.family().getId());
            sql.executeQuery().close();
            if (logout) {
                try (var update = holder.prepareStatement("UPDATE refresh_families SET revoked_at=? WHERE id=?")) {
                    update.setTimestamp(1, Timestamp.from(now()));
                    update.setObject(2, session.family().getId());
                    assertEquals(1, update.executeUpdate());
                }
            }
            var consuming = executor.submit(() -> tx(() -> {
                started.countDown();
                return repo(RefreshTokenRepository.class).consume(session.token().getId(), now());
            }));
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> consuming.get(500, TimeUnit.MILLISECONDS));
            } finally {
                if (logout) { holder.commit(); } else { holder.rollback(); }
            }
            assertEquals(logout ? 0 : 1, consuming.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void consumeKeepsFamilyLockUntilCallerCommits() throws Exception {
        var session = session();
        var consumed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var attempt = executor.submit(() -> tx(() -> {
                assertEquals(1, repo(RefreshTokenRepository.class).consume(session.token().getId(), now()));
                consumed.countDown();
                await(release);
                return null;
            }));
            try {
                assertTrue(consumed.await(10, TimeUnit.SECONDS));
                try (var c = appConnection(); var sql = c.prepareStatement(
                        "SELECT id FROM refresh_families WHERE id=? FOR UPDATE NOWAIT")) {
                    sql.setObject(1, session.family().getId());
                    var error = assertThrows(SQLException.class, sql::executeQuery);
                    assertEquals("55P03", error.getSQLState());
                }
            } finally { release.countDown(); }
            attempt.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void schemaRejectsInvalidRolesEmailsAndMissingAccount() throws Exception {
        var account = persistedAccount();
        assertSqlState("23505", "INSERT INTO accounts SELECT '" + UUID.randomUUID()
                + "',email,password_hash,status,created_at FROM accounts WHERE id='" + account.getId() + "'");
        assertSqlState("23514", "UPDATE accounts SET email='UPPER@EXAMPLE.INVALID' WHERE id='" + account.getId() + "'");
        assertSqlState("23514", "UPDATE accounts SET email=' spaced@example.invalid' WHERE id='" + account.getId() + "'");
        assertSqlState("23514", "UPDATE accounts SET status='' WHERE id='" + account.getId() + "'");
        assertSqlState("23514", "UPDATE accounts SET password_hash='' WHERE id='" + account.getId() + "'");
        assertSqlState("23514", "INSERT INTO account_role_grants VALUES ('" + account.getId() + "','ROOT')");
        assertSqlState("23505", "INSERT INTO account_role_grants VALUES ('" + account.getId() + "','CUSTOMER')");
        assertSqlState("23503", "INSERT INTO account_role_grants VALUES ('" + UUID.randomUUID() + "','CUSTOMER')");
        assertSqlState("23505", "INSERT INTO profile_provisioning_tasks(account_id,created_at,next_attempt_at) VALUES ('"
                + account.getId() + "',now(),now())");
    }

    @Test
    void refreshConstraintsEnforceHashFamilyParentAndAbsoluteExpiry() {
        var root = session();
        var other = session();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(new RefreshToken(
                        UUID.randomUUID(), other.family().getId(), root.token().getTokenHash(),
                        other.token().getId(), now(), other.family().getExpiresAt())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshFamilyRepository.class).saveAndFlush(new RefreshFamily(UUID.randomUUID(),
                        root.family().getAccountId(), now(), now().plus(7, ChronoUnit.DAYS).plusSeconds(1))));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(new RefreshToken(UUID.randomUUID(),
                        root.family().getId(), hash(), null, now(), root.family().getExpiresAt())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(new RefreshToken(UUID.randomUUID(),
                        root.family().getId(), hash(), other.token().getId(), now(), root.family().getExpiresAt())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(new RefreshToken(UUID.randomUUID(),
                        root.family().getId(), hash(), root.token().getId(), now(), root.family().getExpiresAt().plusSeconds(1))));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(new RefreshToken(UUID.randomUUID(),
                        root.family().getId(), "raw-token-is-not-a-sha256-hash", root.token().getId(), now(), root.family().getExpiresAt())));
        repo(RefreshTokenRepository.class).saveAndFlush(child(root));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repo(RefreshTokenRepository.class).saveAndFlush(child(root)));
    }

    @Test
    void tokenConsumptionAndChildInsertRollbackTogether() {
        var session = session();
        assertThrows(IllegalStateException.class, () -> tx(() -> {
            assertEquals(1, repo(RefreshTokenRepository.class).consume(session.token().getId(), now()));
            repo(RefreshTokenRepository.class).saveAndFlush(child(session));
            throw new IllegalStateException("Injected failure after child insert");
        }));
        assertNull(repo(RefreshTokenRepository.class).findById(session.token().getId()).orElseThrow().getUsedAt());
        assertEquals(1L, countTokens(session.family().getId()));
    }

    @Test
    void twoRotationsSerializeAndReuseCanRevokeCommittedChild() throws Exception {
        var session = session();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return tx(() -> {
                        var family = repo(RefreshFamilyRepository.class).lockByTokenHash(session.token().getTokenHash()).orElseThrow();
                        if (repo(RefreshTokenRepository.class).consume(session.token().getId(), now()) == 0) {
                            family.revoke(now());
                            return false;
                        }
                        repo(RefreshTokenRepository.class).saveAndFlush(child(session));
                        return true;
                    });
                }));
            }
            int successes = 0;
            for (var attempt : attempts) { if (attempt.get(20, TimeUnit.SECONDS)) { successes++; } }
            assertEquals(1, successes);
        }
        assertNotNull(repo(RefreshTokenRepository.class).findById(session.token().getId()).orElseThrow().getUsedAt());
        assertNotNull(repo(RefreshFamilyRepository.class).findById(session.family().getId()).orElseThrow().getRevokedAt());
        assertEquals(2L, countTokens(session.family().getId()));
        tx(() -> {
            repo(RefreshFamilyRepository.class).lockById(session.family().getId()).orElseThrow();
            var child = repo(RefreshTokenRepository.class).findAll().stream()
                    .filter(t -> session.token().getId().equals(t.getParentId())).findFirst().orElseThrow();
            assertEquals(0, repo(RefreshTokenRepository.class).consume(child.getId(), now()));
            return null;
        });
    }

    @Test
    void familyLockBlocksOtherTransactionAndLogoutPreventsConsumption() throws Exception {
        var session = session();
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var logout = executor.submit(() -> tx(() -> {
                var family = repo(RefreshFamilyRepository.class).lockById(session.family().getId()).orElseThrow();
                family.revoke(now());
                acquired.countDown();
                await(release);
                return null;
            }));
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            var rotating = executor.submit(() -> tx(() -> {
                repo(RefreshFamilyRepository.class).lockByTokenHash(session.token().getTokenHash()).orElseThrow();
                return repo(RefreshTokenRepository.class).consume(session.token().getId(), now());
            }));
            try {
                assertThrows(TimeoutException.class, () -> rotating.get(250, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            logout.get(20, TimeUnit.SECONDS);
            assertEquals(0, rotating.get(20, TimeUnit.SECONDS));
        }
        var other = session();
        tx(() -> {
            repo(RefreshFamilyRepository.class).lockById(other.family().getId()).orElseThrow();
            assertEquals(1, repo(RefreshTokenRepository.class).consume(other.token().getId(), now()));
            return null;
        });
    }

    @Test
    void consumptionRequiresActiveUnexpiredTokenAndTransaction() {
        var session = session();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> repo(RefreshFamilyRepository.class).lockById(session.family().getId()));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> repo(RefreshTokenRepository.class).consume(session.token().getId(), now()));
        tx(() -> {
            repo(RefreshFamilyRepository.class).lockById(session.family().getId()).orElseThrow();
            assertEquals(0, repo(RefreshTokenRepository.class).consume(session.token().getId(), session.family().getExpiresAt()));
            assertEquals(1, repo(RefreshTokenRepository.class).consume(session.token().getId(), now()));
            assertEquals(0, repo(RefreshTokenRepository.class).consume(session.token().getId(), now()));
            return null;
        });
    }

    @Test
    void committedDataSurvivesApplicationRestartWithRuntimeFlywayDisabled() {
        var session = session();
        context.close();
        context = application(false);
        assertFalse(context.getEnvironment().getProperty("spring.flyway.enabled", Boolean.class));
        assertEquals(session.family().getExpiresAt(), repo(RefreshTokenRepository.class)
                .findByTokenHash(session.token().getTokenHash()).orElseThrow().getExpiresAt());
        assertTrue(repo(AccountRepository.class).existsById(session.family().getAccountId()));
        assertTrue(repo(ProfileProvisioningTaskRepository.class).existsById(session.family().getAccountId()));
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void committedDataSurvivesPostgresRestart() throws Exception {
        var session = session();
        context.close();
        postgres.getDockerClient().restartContainerCmd(postgres.getContainerId()).withTimeout(10).exec();
        // Docker can assign a new ephemeral host port when restarting this container.
        var binding = postgres.getDockerClient().inspectContainerCmd(postgres.getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0];
        jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + binding.getHostPortSpec() + "/auth_db";
        org.awaitility.Awaitility.await().atMost(30, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
            try (var connection = appConnection()) { return connection.isValid(1); }
        });
        context = application(false);
        flyway = migrationFlyway().load();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(session.family().getAccountId(), repo(RefreshFamilyRepository.class)
                .findById(session.family().getId()).orElseThrow().getAccountId());
        assertTrue(repo(RefreshTokenRepository.class).existsById(session.token().getId()));
        assertTrue(repo(ProfileProvisioningTaskRepository.class).existsById(session.family().getAccountId()));
    }

    private record Session(RefreshFamily family, RefreshToken token) {}

    private Session session() {
        var account = persistedAccount();
        return tx(() -> {
            var createdAt = now();
            var family = new RefreshFamily(UUID.randomUUID(), account.getId(), createdAt, createdAt.plus(7, ChronoUnit.DAYS));
            repo(RefreshFamilyRepository.class).saveAndFlush(family);
            var token = new RefreshToken(UUID.randomUUID(), family.getId(), hash(), null, now(), family.getExpiresAt());
            repo(RefreshTokenRepository.class).saveAndFlush(token);
            return new Session(family, token);
        });
    }

    private RefreshToken child(Session session) {
        return new RefreshToken(UUID.randomUUID(), session.family().getId(), hash(),
                session.token().getId(), now(), session.family().getExpiresAt());
    }

    private long countTokens(UUID familyId) {
        return repo(RefreshTokenRepository.class).findAll().stream().filter(t -> t.getFamilyId().equals(familyId)).count();
    }

    private Account persistedAccount() {
        var account = account(uniqueEmail());
        repo(AccountPersistence.class).create(account);
        return account;
    }

    private Account account(String email) {
        return new Account(UUID.randomUUID(), email, "{test-only}not-a-real-password-hash",
                "ACTIVE", Set.of(Account.Role.CUSTOMER), now());
    }

    private String uniqueEmail() { return UUID.randomUUID() + "@example.invalid"; }
    private String hash() { return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""); }
    private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private <T> T repo(Class<T> type) { return context.getBean(type); }
    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(repo(org.springframework.transaction.PlatformTransactionManager.class)).execute(status -> body.get());
    }

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "auth_app", appPassword);
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration migrationFlyway() {
        return Flyway.configure().dataSource(jdbcUrl, "auth_migrator", migrationPassword)
                .locations("classpath:db/migration")
                .cleanDisabled(true).baselineOnMigrate(false);
    }

    private String canonicalMigration() throws Exception {
        try (var source = getClass().getResourceAsStream("/db/migration/V2__enforce_java21_email_canonical_form.sql")) {
            return new String(Objects.requireNonNull(source).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private UUID insertEmail(Connection connection, String email) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var sql = connection.prepareStatement(
                "INSERT INTO accounts(id,email,password_hash,status,created_at) VALUES (?,?,?,'ACTIVE',?)")) {
            sql.setObject(1, id);
            sql.setString(2, email);
            sql.setString(3, "{test-only}not-a-real-password-hash");
            sql.setTimestamp(4, Timestamp.from(now()));
            sql.executeUpdate();
        }
        return id;
    }

    private void reproduceV1NormalizationMismatch() throws Exception {
        try (var c = appConnection(); var probe = c.prepareStatement(
                "SELECT lower(? COLLATE \"C\"), ? = lower(? COLLATE \"C\"), ? !~ '[[:space:]]'")) {
            try (var sql = c.createStatement(); var row = sql.executeQuery(
                    "SELECT version(), current_setting('server_version_num'), datctype "
                            + "FROM pg_database WHERE datname=current_database()")) {
                assertTrue(row.next());
                assertEquals(16, Integer.parseInt(row.getString(2)) / 10000);
                System.out.println("POSTGRES_REPRO: " + row.getString(1) + "; lc_ctype=" + row.getString(3));
            }
            c.setAutoCommit(false);
            for (String prefix : List.of("ascii", "\u00c9", "\u0130", "\u039f\u03a3", "\ud801\udc00", "\u001cx", "\u2003x")) {
                String input = prefix + "@example.invalid";
                for (int i = 1; i <= 4; i++) { probe.setString(i, input); }
                boolean permitted;
                try (var row = probe.executeQuery()) {
                    assertTrue(row.next());
                    permitted = row.getBoolean(2) && row.getBoolean(3);
                    System.out.println("V1_REPRO input=" + codePoints(input) + " java="
                            + codePoints(Account.normalizeEmail(input)) + " pg_C=" + codePoints(row.getString(1))
                            + " V1_check=" + permitted);
                }
                String normalized = Account.normalizeEmail(input);
                if (permitted && !input.equals(normalized)) {
                    var point = c.setSavepoint();
                    insertEmail(c, input);
                    insertEmail(c, normalized);
                    System.out.println("V1_REPRO duplicate Java key admitted by SQL UNIQUE: " + codePoints(normalized));
                    c.rollback(point);
                }
            }
            c.rollback();
        }
    }

    private String codePoints(String value) {
        return value.codePoints().mapToObj(cp -> String.format(Locale.ROOT, "U+%04X", cp))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void assertSqlState(String state, String sql) throws Exception {
        try (var c = appConnection(); var s = c.createStatement()) {
            var error = assertThrows(SQLException.class, () -> s.execute(sql));
            assertEquals(state, error.getSQLState());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
