package com.projecthk5.cartservice.integration;

import com.projecthk5.cartservice.entity.Cart;
import com.projecthk5.cartservice.repository.CartRepository;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.WriteFence;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "AUTH_AUTHORITY_TOKEN=pair")
class WriteFencePostgres16IT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("app.security.issuer",()->"https://test-issuer.example.invalid");
        r.add("app.security.audience",()->"ecommerce");
        r.add("app.security.current-kid",()->"test-kid");
        r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CartRepository carts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate sql;
    @LocalServerPort int port;
    private final UUID operation = UUID.randomUUID();

    @AfterEach void leave() { WriteFence.exit(); }

    @Test void writeClosedBeforeItCommitsRollsBack() {
        WriteFence.enter(operation);
        String owner = "fence-" + UUID.randomUUID();
        var refused = assertThrows(ApiException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            carts.saveAndFlush(new Cart(owner));
            assertEquals(HttpStatus.NO_CONTENT, close("pair"));
        }));
        assertEquals("AUTH_ACCESS_INVALID", refused.code());
        assertEquals(0, rows(owner));
    }

    @Test void everyLaterTransactionOfAClosedOperationIsRefused() {
        WriteFence.enter(operation);
        String first = save();
        assertEquals(1, rows(first));
        assertEquals(HttpStatus.NO_CONTENT, close("pair"));
        assertEquals(HttpStatus.NO_CONTENT, close("pair"));
        assertThrows(ApiException.class, this::save);
    }

    @Test void writeThatWasNotClosedCommitsInEveryTransaction() {
        WriteFence.enter(operation);
        assertEquals(1, rows(save()));
        assertEquals(1, rows(save()));
    }

    @Test void writeOutsideAnyReservationIsNotChecked() {
        assertEquals(1, rows(save()));
    }

    @Test void closeWaitsForACommitAlreadyPastItsCheck() throws Exception {
        CountDownLatch held = new CountDownLatch(1), finish = new CountDownLatch(1);
        var commit = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            sql.query("select pg_advisory_xact_lock_shared(hashtextextended(?::text, 0))", rs -> {}, operation);
            held.countDown();
            try { assertTrue(finish.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        }));
        assertTrue(held.await(10, TimeUnit.SECONDS));
        var close = CompletableFuture.supplyAsync(() -> close("pair"));
        assertThrows(TimeoutException.class, () -> close.get(700, TimeUnit.MILLISECONDS));
        finish.countDown();
        commit.get(10, TimeUnit.SECONDS);
        assertEquals(HttpStatus.NO_CONTENT, close.get(10, TimeUnit.SECONDS));
    }

    @Test void closeRequiresThePairToken() {
        assertEquals(HttpStatus.UNAUTHORIZED, close("wrong"));
        assertEquals(0, sql.queryForObject("select count(*) from write_fence_abort where operation_id = ?", Integer.class, operation));
    }

    @Test void weakerSnapshotIsRefusedBecauseItCouldMissATombstone() {
        WriteFence.enter(operation);
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String owner = "fence-" + UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(tx -> carts.saveAndFlush(new Cart(owner))));
        assertEquals(0, rows(owner));
    }

    private String save() {
        String owner = "fence-" + UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> carts.saveAndFlush(new Cart(owner)));
        return owner;
    }

    private int rows(String owner) {
        return sql.queryForObject("select count(*) from carts where customer_id = ?", Integer.class, owner);
    }

    private HttpStatus close(String token) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/write-fences/" + operation + "/close"))
                .header(InternalTokens.HEADER, token).POST(HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return HttpStatus.valueOf(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
