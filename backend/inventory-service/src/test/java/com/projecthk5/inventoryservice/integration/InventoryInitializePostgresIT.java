package com.projecthk5.inventoryservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.projecthk5.inventoryservice.api.*;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryInitializePostgresIT extends InventoryPostgresSupport {
    @Autowired MockMvc http;
    @Autowired InventoryController controller;
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;

    @Test void duplicateFreshSkuReturns409WithoutChangingAnyStoredBalanceField() throws Exception {
        String sku = id("fresh");
        initializeOverHttp(sku, 5, 201);
        var before = controller.getBalance(sku);
        long version = sql.queryForObject("select version from inventory_item where sku=?", Long.class, sku);
        initializeOverHttp(sku, 99, 409);
        assertEquals(before, controller.getBalance(sku));
        assertEquals(version, sql.queryForObject("select version from inventory_item where sku=?", Long.class, sku));
    }

    @Test void duplicateAfterReservationPreservesAvailableReservedVersionAndTimestamp() throws Exception {
        String sku = id("reserved");
        controller.initialize(new InitializeInventoryRequest(sku, 5));
        controller.operate(new InventoryOperationRequest(id("reserve"), InventoryOperationType.RESERVE,
            List.of(new InventoryOperationRequest.ItemQuantity(sku, 2))));
        var before = controller.getBalance(sku);
        long version = sql.queryForObject("select version from inventory_item where sku=?", Long.class, sku);
        initializeOverHttp(sku, 99, 409);
        assertEquals(3, before.availableQuantity());
        assertEquals(2, before.reservedQuantity());
        assertEquals(before, controller.getBalance(sku));
        assertEquals(version, sql.queryForObject("select version from inventory_item where sku=?", Long.class, sku));
    }

    @Test void competingInitializationWaitsForUniqueRowAndReturns409AfterWinnerCommits() throws Exception {
        String sku = id("init-race");
        CountDownLatch inserted = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                controller.initialize(new InitializeInventoryRequest(sku, 5));
                inserted.countDown();
                await(release);
            }));
            assertTrue(inserted.await(15, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> {
                try { initializeOverHttp(sku, 99, 409); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            waitForDatabaseLock(sql, "inventory_item");
            release.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            assertEquals(5, controller.getBalance(sku).availableQuantity());
            assertEquals(0, controller.getBalance(sku).reservedQuantity());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test void encodedAndMatrixPathsRequireTokenAndNeverWriteWithoutIt() throws Exception {
        for (String path : new String[]{"/api/inventory;review=1/items", "/api/%69nventory/items"}) {
            String sku = id("auth-path");
            String body = "{\"sku\":\"" + sku + "\",\"availableQuantity\":1}";

            http.perform(post(URI.create(path)).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            assertNoInventoryRow(sku);

            http.perform(post(URI.create(path)).header("X-Inventory-Internal-Token", "wrong-token")
                            .contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            assertNoInventoryRow(sku);
        }
    }

    private void assertNoInventoryRow(String sku) {
        assertEquals(0, sql.queryForObject(
                "select count(*) from inventory_item where sku=?", Integer.class, sku));
    }

    private void initializeOverHttp(String sku, long quantity, int status) throws Exception {
        http.perform(post("/api/inventory/items").header("X-Inventory-Internal-Token", TOKEN)
                .contentType("application/json").content("{\"sku\":\"" + sku
                    + "\",\"availableQuantity\":" + quantity + "}"))
            .andExpect(status().is(status));
    }
}
