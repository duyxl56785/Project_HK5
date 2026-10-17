package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.inventoryservice.model.InventoryOperation;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

class InventorySchemaPostgresIT extends InventoryPostgresSupport {
    @Autowired JdbcTemplate sql;
    @Autowired InventoryOperationRepository operations;

    @Test void forwardMigrationSchemaValidatesAndHashRoundTripsThroughJpa() {
        assertEquals("character varying", sql.queryForObject(
            "select data_type from information_schema.columns "
                + "where table_schema='public' and table_name='inventory_operation' and column_name='request_hash'",
            String.class));
        assertEquals(2, sql.queryForObject(
            "select count(*) from flyway_schema_history where success=true", Integer.class));
        String operationId = id("schema");
        String hash = "a".repeat(64);
        operations.saveAndFlush(new InventoryOperation(operationId, "INCREASE", hash));
        assertEquals(hash, operations.findById(operationId).orElseThrow().getRequestHash());
    }

    @Test void upgradingExistingV1PreservesHashLedgerAndStockAndV1Checksum() {
        String schema = "upgrade_" + id("v1").replace("-", "_");
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
            .schemas(schema).locations("classpath:db/migration").target(MigrationVersion.fromVersion("1"))
            .load().migrate();
        String hash = "b".repeat(64);
        sql.update("insert into " + schema + ".inventory_operation "
            + "(operation_id,operation_type,request_hash) values ('legacy','RESERVE',?)", hash);
        sql.update("insert into " + schema + ".inventory_item "
            + "(sku,available_quantity,reserved_quantity,version) values ('legacy-sku',5,2,3)");
        Integer checksum = sql.queryForObject("select checksum from " + schema
            + ".flyway_schema_history where version='1'", Integer.class);
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
            .schemas(schema).locations("classpath:db/migration").load().migrate();
        assertEquals("character varying", sql.queryForObject(
            "select data_type from information_schema.columns where table_schema=? "
                + "and table_name='inventory_operation' and column_name='request_hash'", String.class, schema));
        assertEquals(hash, sql.queryForObject("select request_hash from " + schema
            + ".inventory_operation where operation_id='legacy'", String.class));
        assertEquals(1, sql.queryForObject("select count(*) from " + schema + ".inventory_operation", Integer.class));
        assertEquals(checksum, sql.queryForObject("select checksum from " + schema
            + ".flyway_schema_history where version='1'", Integer.class));
        assertEquals(5L, sql.queryForObject("select available_quantity from " + schema + ".inventory_item", Long.class));
        assertEquals(2L, sql.queryForObject("select reserved_quantity from " + schema + ".inventory_item", Long.class));
        assertEquals(3L, sql.queryForObject("select version from " + schema + ".inventory_item", Long.class));
    }
}
