package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Verifies the unmodified production Flyway default boots against the live V1/V2 schema only. */
@Tag("postgres-integration")
@SpringBootTest
class InventoryDefaultFlywayPostgresIT {
    private static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("inventory_default_schema")
            .withPassword(UUID.randomUUID().toString())
            .withLabel("tv4.task", "pr69-default-flyway-check");

    static {
        PG.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
    }

    @Autowired JdbcTemplate sql;

    @Test
    void defaultFlywayLocationAppliesOnlyApprovedV1AndV2AndJpaValidationStarts() {
        assertEquals(2, sql.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class));
        assertFalse(sql.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'inventory_operation'
                      AND column_name = 'reservation_id'
                )
                """, Boolean.class));
        assertFalse(sql.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name IN ('inventory_reservation', 'inventory_reservation_line',
                                         'inventory_reservation_operation_line')
                )
                """, Boolean.class));
    }
}
