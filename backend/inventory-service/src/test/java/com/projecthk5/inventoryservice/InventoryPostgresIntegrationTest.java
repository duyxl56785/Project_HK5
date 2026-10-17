package com.projecthk5.inventoryservice;

import com.projecthk5.inventoryservice.api.InitializeInventoryRequest;
import com.projecthk5.inventoryservice.api.InventoryController;
import com.projecthk5.inventoryservice.api.InventoryOperationRequest;
import com.projecthk5.inventoryservice.api.InventoryOperationServiceException;
import com.projecthk5.inventoryservice.api.InventoryOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(classes = InventoryServiceApplication.class)
class InventoryPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InventoryController controller;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void clearTables() {
        jdbc.execute("TRUNCATE TABLE inventory_operation, inventory_item");
    }

    @Test
    void flywaySchemaMatchesJpaValidationAtApplicationStartup() {
        assertEquals("character varying",
                jdbc.queryForObject("""
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_name = 'inventory_operation'
                          AND column_name = 'request_hash'
                        """, String.class));
    }

    @Test
    void duplicateInitializationReturnsConflictWithoutChangingBalance() {
        controller.initialize(new InitializeInventoryRequest("SKU-1", 5));

        assertThrows(InventoryOperationServiceException.class,
                () -> controller.initialize(new InitializeInventoryRequest("SKU-1", 99)));

        assertEquals(5, controller.getBalance("SKU-1").availableQuantity());
    }

    @Test
    void concurrentDifferentPayloadsCannotBothClaimSameOperationId() throws Exception {
        controller.initialize(new InitializeInventoryRequest("SKU-A", 10));
        controller.initialize(new InitializeInventoryRequest("SKU-B", 10));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> applyAfterBarrier(
                    ready, start, requestFor("SKU-A")));
            Future<Boolean> second = executor.submit(() -> applyAfterBarrier(
                    ready, start, requestFor("SKU-B")));

            ready.await();
            start.countDown();

            assertNotEquals(first.get(), second.get(),
                    "exactly one distinct payload should claim the shared operationId");
            long totalAvailable = controller.getBalance("SKU-A").availableQuantity()
                    + controller.getBalance("SKU-B").availableQuantity();
            assertEquals(19, totalAvailable, "the losing request must roll back its stock mutation");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean applyAfterBarrier(CountDownLatch ready, CountDownLatch start,
                                      InventoryOperationRequest request) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            controller.operate(request);
            return true;
        } catch (InventoryOperationServiceException conflict) {
            return false;
        }
    }

    private static InventoryOperationRequest requestFor(String sku) {
        return new InventoryOperationRequest("shared-operation-id", InventoryOperationType.DECREASE,
                List.of(new InventoryOperationRequest.ItemQuantity(sku, 1)));
    }
}
