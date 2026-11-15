package com.projecthk5.productservice.promotion;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgresql")
class PromotionMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).locations("classpath:db/migration").target(target).load();
    }

    private static Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int number(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String scheduled(UUID id, String kind, String date) {
        boolean flash = kind.equals("FLASH_SALE");
        String column = flash ? "flash_date" : "super_sale_date";
        return """
                INSERT INTO promotion_campaigns (id,name,kind,%s,starts_at,ends_at)
                SELECT '%s','Sale','%s',d,(d + TIME '%s') AT TIME ZONE 'Asia/Ho_Chi_Minh',
                       ((d + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                FROM (VALUES (DATE '%s')) AS dates(d)
                """.formatted(column, id, kind, flash ? "22:00" : "00:00", date);
    }

    @Test
    void migratesBlankDatabaseAndEnforcesVietnamScheduleUniqueness() throws Exception {
        Flyway migration = flyway("promotion_fresh", "10");
        assertEquals(10, migration.migrate().migrationsExecuted);
        migration.validate();
        try (Connection connection = connect("promotion_fresh")) {
            var flash = UUID.randomUUID();
            execute(connection, scheduled(flash, "FLASH_SALE", "2026-11-14"));
            assertEquals(1, number(connection, """
                    SELECT count(*) FROM promotion_campaigns WHERE kind='FLASH_SALE'
                      AND starts_at='2026-11-14T15:00:00Z' AND ends_at='2026-11-14T17:00:00Z'
                    """));
            assertEquals("23514", assertThrows(SQLException.class, () ->
                    execute(connection, scheduled(UUID.randomUUID(), "FLASH_SALE", "2026-11-15"))).getSQLState());
            assertEquals("23505", assertThrows(SQLException.class, () ->
                    execute(connection, scheduled(UUID.randomUUID(), "FLASH_SALE", "2026-11-14"))).getSQLState());
            execute(connection, "UPDATE promotion_campaigns SET status='CANCELLED' WHERE id='" + flash + "'");
            execute(connection, scheduled(UUID.randomUUID(), "FLASH_SALE", "2026-11-14"));
        }
    }

    @Test
    void upgradesV9WithoutChangingProductPriceAndEnforcesAuditAndOfferConstraints() throws Exception {
        String schema = "promotion_upgrade";
        assertEquals(9, flyway(schema, "9").migrate().migrationsExecuted);
        UUID product = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        try (var connection = connect(schema)) {
            execute(connection, "INSERT INTO categories(id,name) VALUES ('" + category + "','Promotion category')");
            execute(connection, """
                    INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status)
                    VALUES ('%s','%s','%s','promotion-sku','Product',100000,'VND','ACTIVE')
                    """.formatted(product, UUID.randomUUID(), category));
        }
        Flyway upgrade = flyway(schema, "10");
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        try (var connection = connect(schema)) {
            assertEquals(100000, number(connection, "SELECT unit_price FROM products WHERE id='" + product + "'"));
            UUID campaign = UUID.randomUUID();
            execute(connection, scheduled(campaign, "SUPER_SALE", "2026-11-18"));
            String offer = """
                    INSERT INTO promotion_campaign_products(campaign_id,product_id,promotional_unit_price)
                    VALUES ('%s','%s',%s)
                    """;
            assertEquals("23514", assertThrows(SQLException.class, () ->
                    execute(connection, offer.formatted(campaign, product, "1.5"))).getSQLState());
            assertEquals("23503", assertThrows(SQLException.class, () ->
                    execute(connection, offer.formatted(campaign, UUID.randomUUID(), "80000"))).getSQLState());
            execute(connection, offer.formatted(campaign, product, "80000"));
            String audit = """
                    INSERT INTO promotion_campaign_audit(campaign_id,product_id,actor,action,to_status,version)
                    VALUES ('%s','%s','seller','REGISTER','PENDING',0)
                    """.formatted(campaign, product);
            connection.setAutoCommit(false);
            execute(connection, audit);
            connection.rollback();
            assertEquals(0, number(connection, "SELECT count(*) FROM promotion_campaign_audit"));
            connection.setAutoCommit(true);
            execute(connection, audit);
            for (String command : List.of("UPDATE promotion_campaign_audit SET actor='other'",
                    "DELETE FROM promotion_campaign_audit", "TRUNCATE promotion_campaign_audit")) {
                assertEquals("23514", assertThrows(SQLException.class, () ->
                        execute(connection, command)).getSQLState());
            }
            assertEquals(1, number(connection, "SELECT count(*) FROM promotion_campaign_audit"));
        }
    }

    @Test
    void concurrentSuperSaleDatesInSameMonthHaveOneWinner() throws Exception {
        String schema = "promotion_concurrency";
        flyway(schema, "10").migrate();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> insertSuperSale(schema, "2026-11-01", ready, start));
            var second = pool.submit(() -> insertSuperSale(schema, "2026-11-30", ready, start));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            assertEquals(List.of("23505", "OK"), List.of(first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        try (var connection = connect(schema)) {
            assertEquals(1, number(connection, "SELECT count(*) FROM promotion_campaigns"));
            execute(connection, scheduled(UUID.randomUUID(), "SUPER_SALE", "2026-12-01"));
        }
    }

    private static String insertSuperSale(String schema, String date, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        try (var connection = connect(schema)) {
            try (var statement = connection.createStatement()) {
                statement.execute("SET statement_timeout = '10s'");
            }
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            execute(connection, scheduled(UUID.randomUUID(), "SUPER_SALE", date));
            return "OK";
        } catch (SQLException failure) {
            return failure.getSQLState();
        }
    }
}
