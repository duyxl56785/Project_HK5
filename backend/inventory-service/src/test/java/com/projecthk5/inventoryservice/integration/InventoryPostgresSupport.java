package com.projecthk5.inventoryservice.integration;

import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** One isolated, disposable database per Gradle test JVM; no existing DB/volume is used. */
@Tag("postgres-integration")
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
abstract class InventoryPostgresSupport {
    static final String TOKEN = UUID.randomUUID().toString();
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
        .withUsername("tv5_fixture").withPassword(UUID.randomUUID().toString())
        .withLabel("tv5.task", "pr31-db-regression");
    static { PG.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> PG.getJdbcUrl()
            + (PG.getJdbcUrl().contains("?") ? "&" : "?") + "ApplicationName=tv5-inventory-regression");
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
        properties.add("inventory.internal-token", () -> TOKEN);
        // ddl-auto=validate and Flyway are loaded unchanged from production application.yml.
    }

    static String id(String prefix) { return prefix + "-" + UUID.randomUUID(); }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test transaction gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    static void waitForDatabaseLock(JdbcTemplate sql, String table) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (!Boolean.TRUE.equals(sql.queryForObject(
                "select exists(select 1 from pg_stat_activity where datname=current_database() "
                    + "and application_name='tv5-inventory-regression' and wait_event_type='Lock' "
                    + "and lower(query) like ? and pid<>pg_backend_pid())", Boolean.class, "%" + table + "%"))) {
                Thread.sleep(25);
            }
        });
    }
}
