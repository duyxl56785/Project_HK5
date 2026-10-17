package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.model.InventoryOperation;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InventorySchemaPostgresIT extends InventoryPostgresSupport {
    @Autowired JdbcTemplate sql;
    @Autowired InventoryOperationRepository operations;

    @Test void flywaySchemaValidatesAndCharHashRoundTripsThroughJpa() {
        assertEquals("character", sql.queryForObject(
            "select data_type from information_schema.columns "
                + "where table_schema='public' and table_name='inventory_operation' and column_name='request_hash'",
            String.class));
        assertEquals(1, sql.queryForObject(
            "select count(*) from flyway_schema_history where success=true", Integer.class));
        String operationId = id("schema");
        String hash = "a".repeat(64);
        operations.saveAndFlush(new InventoryOperation(operationId, "INCREASE", hash));
        assertEquals(hash, operations.findById(operationId).orElseThrow().getRequestHash());
    }
}
