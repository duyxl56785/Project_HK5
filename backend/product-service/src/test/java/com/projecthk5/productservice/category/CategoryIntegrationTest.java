package com.projecthk5.productservice.category;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class CategoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private CategoryService service;
    @SpyBean private CategoryHierarchyLock hierarchyLock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void cleanCategories() {
        jdbc.update("UPDATE categories SET parent_id = NULL");
        jdbc.update("DELETE FROM categories");
    }

    @Test
    void flywayCreatesSchemaAndJpaPersistsHierarchy() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'", Integer.class));
        CategoryResponse root = create("Root", null);
        CategoryResponse child = create("Child", root.id());
        CategoryResponse leaf = create("Leaf", child.id());
        assertEquals(child.id(), service.get(leaf.id()).parentId());
        assertEquals(3, service.list().size());
        assertEquals(root.id(), jdbc.queryForObject(
                "SELECT parent_id FROM categories WHERE id = ?", UUID.class, child.id()));
        assertNotNull(jdbc.queryForObject(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'categories' AND indexname = 'categories_parent_id_idx'",
                String.class));
    }

    @Test
    void reparentsThenDeletesLeafAndRejectsMissingId() {
        CategoryResponse root = create("Root", null);
        CategoryResponse child = create("Child", root.id());
        service.update(child.id(), new CategoryRequest(" Independent ", null));
        assertNull(service.get(child.id()).parentId());
        assertEquals("Independent", service.get(child.id()).name());
        service.delete(root.id());
        service.delete(child.id());
        assertTrue(service.list().isEmpty());
        assertEquals("CATEGORY_NOT_FOUND", assertThrows(CategoryException.class,
                () -> service.get(child.id())).getCode());
    }

    @Test
    void rejectsDeletingParentWithoutRemovingAnything() {
        CategoryResponse root = create("Root", null);
        create("Child", root.id());
        assertEquals("CATEGORY_HAS_CHILDREN", assertThrows(CategoryException.class,
                () -> service.delete(root.id())).getCode());
        assertEquals(2, service.list().size());
    }

    @Test
    void databaseRejectsOrphanAndSelfParentAndDeletingReferencedParent() {
        UUID id = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO categories (id, name, parent_id) VALUES (?, ?, ?)", id, "Orphan", UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO categories (id, name, parent_id) VALUES (?, ?, ?)", id, "Self", id));
        CategoryResponse root = create("Root", null);
        create("Child", root.id());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM categories WHERE id = ?", root.id()));
        assertEquals(2, service.list().size());
    }

    @Test
    void databaseRejectsBlankAndOversizedNames() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO categories (id, name) VALUES (?, ?)", UUID.randomUUID(), " \t\n"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO categories (id, name) VALUES (?, ?)", UUID.randomUUID(), "x".repeat(256)));
        assertTrue(service.list().isEmpty());
    }

    @Test
    void rejectsCyclesAndRollsBackChanges() {
        CategoryResponse root = create("Root", null);
        CategoryResponse child = create("Child", root.id());
        CategoryResponse leaf = create("Leaf", child.id());
        assertEquals("CATEGORY_CYCLE", assertThrows(CategoryException.class,
                () -> service.update(root.id(), new CategoryRequest("Changed", root.id()))).getCode());
        assertEquals("CATEGORY_CYCLE", assertThrows(CategoryException.class,
                () -> service.update(root.id(), new CategoryRequest("Changed", leaf.id()))).getCode());
        assertEquals(root, service.get(root.id()));
        // A rejected transaction must release its advisory lock so later writes can proceed.
        create("After rollback", null);
    }

    @Test
    void rejectsMissingParentAndCorruptAncestorCycle() {
        assertEquals("CATEGORY_PARENT_NOT_FOUND", assertThrows(CategoryException.class,
                () -> create("Orphan", UUID.randomUUID())).getCode());
        CategoryResponse first = create("First", null);
        CategoryResponse second = create("Second", first.id());
        // Simulates legacy/direct-SQL corruption: multi-node cycles are a service invariant.
        jdbc.update("UPDATE categories SET parent_id = ? WHERE id = ?", second.id(), first.id());
        assertEquals("CATEGORY_CYCLE", assertThrows(CategoryException.class,
                () -> create("New", first.id())).getCode());
        assertEquals(2, service.list().size());
    }

    @Test
    void realApplicationPermitsReadsAndBlocksPublicWrites() throws Exception {
        CategoryResponse root = create("Root", null);
        mvc.perform(get("/api/categories/{id}", root.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(root.id().toString()));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_WRITE_AUTH_NOT_CONFIGURED"));
        mvc.perform(put("/api/categories/{id}", root.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/categories/{id}", root.id())).andExpect(status().isForbidden());
        assertEquals(root, service.get(root.id()));
        assertEquals(1, service.list().size());
    }

    @Test
    void lockCannotBeAcquiredOutsideTransaction() {
        assertThrows(IllegalStateException.class, hierarchyLock::acquire);
    }

    @Test
    void simultaneousReparentingWaitsForLockAndCannotCreateTwoNodeCycle() throws Exception {
        CategoryResponse first = create("First", null);
        CategoryResponse second = create("Second", null);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch allowFirstWrite = new CountDownLatch(1);
        AtomicBoolean pauseFirstWriter = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (pauseFirstWriter.compareAndSet(true, false)) {
                firstLocked.countDown();
                await(allowFirstWrite);
            }
            return null;
        }).when(hierarchyLock).acquire();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWrite = workers.submit(() ->
                    service.update(first.id(), new CategoryRequest("First", second.id())));
            assertTrue(firstLocked.await(15, TimeUnit.SECONDS), "First writer did not acquire the lock");
            Future<?> secondWrite = workers.submit(() ->
                    service.update(second.id(), new CategoryRequest("Second", first.id())));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' "
                        + "AND classid = 2142 AND objid = 1 AND NOT granted", Integer.class) == 0) {
                    Thread.sleep(25);
                }
            });
            assertFalse(secondWrite.isDone(), "Second writer must wait before reading the hierarchy");
            allowFirstWrite.countDown();
            firstWrite.get(15, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> secondWrite.get(15, TimeUnit.SECONDS));
            CategoryException conflict = assertInstanceOf(CategoryException.class, rejected.getCause());
            assertEquals("CATEGORY_CYCLE", conflict.getCode());
            assertEquals(second.id(), service.get(first.id()).parentId());
            assertNull(service.get(second.id()).parentId());
        } finally {
            allowFirstWrite.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS), "Writer threads did not terminate");
        }
    }

    @Test
    void writeUsesFreshTransactionEvenWhenCallerHasAnOlderSnapshot() throws Exception {
        CategoryResponse first = create("First", null);
        CategoryResponse second = create("Second", null);
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        caller.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            caller.executeWithoutResult(status -> {
                assertNull(service.get(first.id()).parentId());
                assertNull(service.get(second.id()).parentId());
                Future<?> concurrentWrite = worker.submit(() ->
                        service.update(first.id(), new CategoryRequest("First", second.id())));
                try {
                    concurrentWrite.get(15, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent write failed", exception);
                }
                assertEquals("CATEGORY_CYCLE", assertThrows(CategoryException.class,
                        () -> service.update(second.id(), new CategoryRequest("Second", first.id()))).getCode());
            });
            assertEquals(second.id(), service.get(first.id()).parentId());
            assertNull(service.get(second.id()).parentId());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE"})
    void categoryWriteCommitsIndependentlyOfCallerRollback(String operation) {
        CategoryResponse root = create("Root", null);
        AtomicReference<CategoryResponse> created = new AtomicReference<>();
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        caller.executeWithoutResult(status -> {
            assertEquals(root, service.get(root.id()));
            switch (operation) {
                case "CREATE" -> created.set(create("Child", root.id()));
                case "UPDATE" -> service.update(root.id(), new CategoryRequest("Renamed", null));
                case "DELETE" -> service.delete(root.id());
                default -> fail("Unexpected test operation");
            }
            status.setRollbackOnly();
        });
        switch (operation) {
            case "CREATE" -> {
                assertEquals(created.get(), service.get(created.get().id()));
                assertEquals(2, service.list().size());
            }
            case "UPDATE" -> assertEquals("Renamed", service.get(root.id()).name());
            case "DELETE" -> {
                assertEquals("CATEGORY_NOT_FOUND", assertThrows(CategoryException.class,
                        () -> service.get(root.id())).getCode());
                assertTrue(service.list().isEmpty());
            }
            default -> fail("Unexpected test operation");
        }
    }

    private CategoryResponse create(String name, UUID parentId) {
        return service.create(new CategoryRequest(name, parentId));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to commit");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Writer interrupted", exception);
        }
    }
}
