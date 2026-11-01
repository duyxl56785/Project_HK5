package com.projecthk5.productservice.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/** Opens review rights from delivered events; the primary key drops redelivered events. */
@Component
public class ReviewableItems {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReviewableItems(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = "seller-order.status-changed.v1", groupId = "product-service")
    public void on(String payload) throws JsonProcessingException {
        JsonNode event = json.readTree(payload);
        if (!"DELIVERED".equals(event.path("toStatus").asText()) || !event.path("productIds").isArray()) return;
        UUID orderId = UUID.fromString(event.required("orderId").asText());
        String customer = event.required("customerAccountId").asText();
        for (JsonNode product : event.path("productIds")) {
            jdbc.update("insert into reviewable_items (order_id, product_id, customer_id) values (?, ?, ?) on conflict do nothing",
                    orderId, UUID.fromString(product.asText()), customer);
        }
    }

    /** The buyer of this delivered purchase, empty until its event has arrived. */
    public Optional<String> buyer(UUID orderId, UUID productId) {
        List<String> found = jdbc.queryForList("select customer_id from reviewable_items where order_id = ? and product_id = ?",
                String.class, orderId, productId);
        return found.stream().findFirst();
    }

    @Configuration
    static class DeadLetters {
        /** A bad record gets two retries, then moves to the topic's .dlt. */
        @Bean
        DefaultErrorHandler kafkaErrors(KafkaTemplate<Object, Object> template) {
            return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template,
                    (record, error) -> new TopicPartition(record.topic() + ".dlt", -1)), new FixedBackOff(1000, 2));
        }
    }
}
