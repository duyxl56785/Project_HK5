package com.projecthk5.notificationservice.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

/** Turns Order events into notifications; eventKey makes redelivery harmless. */
@Component
class OrderEventListener {
    private final NotificationController notifications;
    private final ObjectMapper json;
    private final Validator validator;

    OrderEventListener(NotificationController notifications, ObjectMapper json, Validator validator) {
        this.notifications = notifications;
        this.json = json;
        this.validator = validator;
    }

    @KafkaListener(topics = {"order.placed.v1", "seller-order.status-changed.v1"}, groupId = "notification-service")
    void on(String payload) throws JsonProcessingException {
        var notice = json.treeToValue(json.readTree(payload).required("notification"), NotificationController.Create.class);
        var invalid = validator.validate(notice);
        if (!invalid.isEmpty()) throw new ConstraintViolationException(invalid);
        notifications.store(notice);
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
