package com.projecthk5.orderservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.OrderItem;
import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Action;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real PostgreSQL constraint errors and real Return transactions, not mocked persistence.
 * Test-only BEFORE INSERT triggers exercise otherwise defensive catch paths after Java validation.
 * Faults exist only in this class's disposable container; V6 and shared databases are unchanged.
 */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = "app.outbox.relay-enabled=false")
@AutoConfigureMockMvc
class ReturnConstraintPostgresIT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("app.security.issuer", () -> "https://test-issuer.example.invalid");
        r.add("app.security.audience", () -> "ecommerce");
        r.add("app.security.current-kid", () -> "test-kid");
        r.add("app.security.current-key-b64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    private static final String REASON = "SENSITIVE_P3_REASON";
    private static final String KEY = "p3-request";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired OrderRepository orders;
    private String customer;
    private Order order, otherOrder;
    private SellerOrder first, second;

    @BeforeEach void seed() {
        customer = "constraint-audit-" + UUID.randomUUID();
        order = new Order(UUID.randomUUID(), customer);
        first = deliveredStore("one");
        second = deliveredStore("two");
        order.addSellerOrder(first);
        order.addSellerOrder(second);
        orders.saveAndFlush(order);
        otherOrder = orders.saveAndFlush(new Order(UUID.randomUUID(), customer));
    }

    private SellerOrder deliveredStore(String suffix) {
        SellerOrder so = new SellerOrder(order, "seller-" + suffix, "store-" + suffix,
                new BigDecimal("100.00"), new BigDecimal("5.00"));
        so.getItems().add(new OrderItem(so, "product-" + suffix, 1, new BigDecimal("100.00"),
                new BigDecimal("100.00"), "SKU-" + suffix, "Synthetic item"));
        Instant delivered = Instant.now().minusSeconds(30);
        for (Action action : List.of(Action.ACCEPT, Action.START_PREPARING, Action.READY_FOR_HANDOFF,
                Action.DISPATCH, Action.DELIVER)) so.apply(action, null, delivered);
        so.collectCod("audit-admin", delivered.plusSeconds(1));
        return so;
    }

    @AfterEach void clearFault() {
        db.execute("drop trigger if exists audit_p3_before_return on return_requests");
        db.execute("drop function if exists audit_p3_inject()");
    }

    private void fault(String body) {
        db.execute("create function audit_p3_inject() returns trigger language plpgsql as $$ begin "
                + "if NEW.reason='" + REASON + "' then " + body + " end if; return NEW; end; $$");
        db.execute("create trigger audit_p3_before_return before insert on return_requests "
                + "for each row execute function audit_p3_inject()");
    }

    private String insertCompeting(String id, String store, String key) {
        return "insert into return_requests (id,order_id,seller_order_id,customer_id,idempotency_key,"
                + "request_fingerprint,reason,status,requested_at,updated_at) values ("
                + id + ",NEW.order_id," + store + ",NEW.customer_id," + key + ",'"
                + "a".repeat(64) + "','fixture','REQUESTED',NEW.requested_at,NEW.updated_at);";
    }

    private MvcResult send() throws Exception {
        return mvc.perform(post("/api/orders/{order}/store-orders/{store}/returns", order.getId(), first.getId())
                .with(jwt().jwt(j -> j.subject(customer).claim("roles", List.of("CUSTOMER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("reason", REASON))))
                .andReturn();
    }

    private void verifyFailure(int status, String code, String sqlState) throws Exception {
        MvcResult result;
        try {
            result = send();
            assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            JsonNode body = json.readTree(result.getResponse().getContentAsString());
            assertEquals(code, body.path("code").asText());
            Set<String> fields = new HashSet<>();
            body.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("code", "message"), fields);
            String response = result.getResponse().getContentAsString();
            for (String sensitive : List.of(REASON, KEY, customer, "return_requests", "uq_return_",
                    "fk_return_", "request_fingerprint", "insert into")) {
                assertFalse(response.contains(sensitive), "Error response must not include SQL details or request data");
            }
            if (sqlState != null) {
                Throwable cause = result.getResolvedException();
                SQLException original = null;
                while (cause != null) {
                    if (cause instanceof SQLException sql) original = sql;
                    cause = cause.getCause();
                }
                assertNotNull(original, "Keep the real PostgreSQL failure internally, not in the HTTP envelope");
                assertEquals(sqlState, original.getSQLState());
            }
            assertEquals(0, db.queryForObject("select count(*) from return_requests where order_id=?",
                    Integer.class, order.getId()));
            assertEquals(0, notices());
        } finally {
            clearFault();
        }
        // The failed transaction must not burn a key, retain the competing fixture, or enqueue a notice.
        MvcResult accepted = send();
        assertEquals(201, accepted.getResponse().getStatus());
        String requestId = json.readTree(accepted.getResponse().getContentAsString()).path("requestId").asText();
        MvcResult replay = send();
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals(requestId, json.readTree(replay.getResponse().getContentAsString()).path("requestId").asText());
        assertEquals(1, db.queryForObject("select count(*) from return_requests where order_id=?",
                Integer.class, order.getId()));
        assertEquals(1, notices());
    }

    private int notices() {
        return db.queryForObject("select count(*) from outbox_events where aggregate_id in (?,?)",
                Integer.class, first.getId(), second.getId());
    }

    @Test void customerKeyUniqueKeepsIdempotencyConflictAndRollsBack() throws Exception {
        fault(insertCompeting("'" + UUID.randomUUID() + "'::uuid", "'" + second.getId() + "'::uuid",
                "NEW.idempotency_key"));
        verifyFailure(409, "RETURN_IDEMPOTENCY_MISMATCH", null);
    }

    @Test void activeStoreUniqueReportsAlreadyOpenAndRollsBack() throws Exception {
        fault(insertCompeting("'" + UUID.randomUUID() + "'::uuid", "NEW.seller_order_id",
                "NEW.idempotency_key || '-fixture'"));
        verifyFailure(409, "RETURN_ALREADY_OPEN", null);
    }

    @Test void foreignKeyFailureIsServerErrorNotAnIdempotencyMismatch() throws Exception {
        fault("NEW.order_id='" + otherOrder.getId() + "'::uuid;");
        verifyFailure(500, "REQUEST_REJECTED", "23503");
    }

    @Test void checkFailureIsServerErrorWithoutLeakingCheckDetails() throws Exception {
        fault("NEW.request_fingerprint='SENSITIVE_INVALID_CHECK';");
        verifyFailure(500, "REQUEST_REJECTED", "23514");
    }

    @Test void unrelatedPrimaryKeyUniqueIsNotAnIdempotencyConflict() throws Exception {
        fault(insertCompeting("NEW.id", "'" + second.getId() + "'::uuid", "NEW.idempotency_key || '-fixture'"));
        verifyFailure(500, "REQUEST_REJECTED", "23505");
    }
}
