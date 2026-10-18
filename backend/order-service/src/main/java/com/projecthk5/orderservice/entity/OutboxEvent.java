package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(nullable=false,length=128) private String topic;
    @Column(name="aggregate_id",nullable=false) private UUID aggregateId;
    @Column(nullable=false,columnDefinition="text") private String payload;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected OutboxEvent() {}
    public OutboxEvent(String topic,UUID aggregateId,String payload){
        this.id=UUID.randomUUID();this.topic=topic;this.aggregateId=aggregateId;this.payload=payload;this.createdAt=Instant.now();
    }
}
