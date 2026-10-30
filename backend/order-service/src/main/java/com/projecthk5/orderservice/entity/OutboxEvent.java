package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="outbox_events")
public class OutboxEvent {
    public static final int MAX_ATTEMPTS = 20;
    @Id private UUID id;
    @Column(nullable=false,length=128) private String topic;
    @Column(name="aggregate_id",nullable=false) private UUID aggregateId;
    @Column(nullable=false,columnDefinition="text") private String payload;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="published_at") private Instant publishedAt;
    @Column(nullable=false) private int attempts;
    @Column(name="last_error",length=300) private String lastError;
    @Column(nullable=false) private boolean dead;
    protected OutboxEvent() {}
    public OutboxEvent(String topic,UUID aggregateId,String payload){
        this.id=UUID.randomUUID();this.topic=topic;this.aggregateId=aggregateId;this.payload=payload;this.createdAt=Instant.now();
    }
    public UUID getId(){return id;}
    public String getTopic(){return topic;}
    public UUID getAggregateId(){return aggregateId;}
    public String getPayload(){return payload;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getPublishedAt(){return publishedAt;}
    public int getAttempts(){return attempts;}
    public String getLastError(){return lastError;}
    public boolean isDead(){return dead;}
    public void published(Instant now){this.publishedAt=now;this.lastError=null;}
    /** Transient failures retry later; a definitive or exhausted failure parks the event for operators. */
    public void failed(String error,boolean definitive){
        this.attempts++;
        this.lastError=error==null?null:error.substring(0,Math.min(300,error.length()));
        if (definitive || attempts>=MAX_ATTEMPTS) this.dead=true;
    }
}
