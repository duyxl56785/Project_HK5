package com.projecthk5.notificationservice.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;
    @Column(nullable = false, length = 128)
    private String recipient;
    @Column(nullable = false, length = 64)
    private String type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(length = 300)
    private String link;
    @Column(name = "event_key", length = 200)
    private String eventKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(String recipient, String type, String title, String message, String link, String eventKey) {
        this.id = UUID.randomUUID();
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.eventKey = eventKey;
        this.createdAt = Instant.now();
    }

    public void markRead() { if (readAt == null) readAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
