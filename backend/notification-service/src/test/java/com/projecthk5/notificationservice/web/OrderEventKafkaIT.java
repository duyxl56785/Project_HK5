package com.projecthk5.notificationservice.web;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Real broker and PostgreSQL: redelivery stores one row, and a bad record reaches its .dlt. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "JWT_ISSUER_URI=https://test-issuer.example.invalid", "JWT_AUDIENCE=ecommerce", "JWT_HS256_CURRENT_KID=test-kid",
        "JWT_HS256_CURRENT_KEY_B64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class OrderEventKafkaIT {
    private static final String TOPIC = "seller-order.status-changed.v1";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final KafkaContainer BROKER = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.kafka.bootstrap-servers", BROKER::getBootstrapServers);
    }

    @Autowired KafkaTemplate<Object, Object> kafka;
    @Autowired ConsumerFactory<?, ?> consumers;
    @Autowired JdbcTemplate sql;

    private int rows(String key) {
        return sql.queryForObject("select count(*) from notifications where event_key = ?", Integer.class, key);
    }

    @Test
    void redeliveredEventStoresOneNotification() throws Exception {
        String key = "k-" + UUID.randomUUID();
        String event = """
                {"notification":{"recipient":"account-1","type":"ORDER_ACCEPTED","title":"t","message":"m","link":"/orders/1","eventKey":"%s"}}
                """.formatted(key);
        kafka.send(TOPIC, "same", event).get();
        kafka.send(TOPIC, "same", event).get();
        await().atMost(Duration.ofSeconds(30)).until(() -> rows(key) > 0);
        // The marker follows the duplicate on the same partition, so the duplicate has been handled.
        String marker = event.replace(key, key + "-end");
        kafka.send(TOPIC, "same", marker).get();
        await().atMost(Duration.ofSeconds(30)).until(() -> rows(key + "-end") > 0);
        assertEquals(1, rows(key));
    }

    @Test
    void poisonRecordMovesToTheDeadLetterTopic() throws Exception {
        String poison = "not-json-" + UUID.randomUUID();
        kafka.send(TOPIC, "poison", poison).get();
        try (var consumer = consumers.createConsumer("dlt-check", "it")) {
            consumer.subscribe(List.of(TOPIC + ".dlt"));
            List<Object> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                for (ConsumerRecord<?, ?> record : consumer.poll(Duration.ofMillis(500))) seen.add(record.value());
                return seen.contains(poison);
            });
        }
    }
}
