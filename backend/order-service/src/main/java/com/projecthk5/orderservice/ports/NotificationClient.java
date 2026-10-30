package com.projecthk5.orderservice.ports;

import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Order->Notification pair; eventKey makes redelivery from the outbox relay idempotent. */
@Component
public class NotificationClient {
    private final RestClient notification;
    private final String token;

    public NotificationClient(@Value("${NOTIFICATION_INTERNAL_URL:http://localhost:8108}") String url,
                              @Value("${ORDER_NOTIFICATION_TOKEN:}") String token) {
        this.notification = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    public record Message(String recipient, String type, String title, String message, String link, String eventKey) {}

    public void send(Message message) {
        if (token.isBlank()) throw new IllegalStateException("Notification internal contract not configured");
        notification.post().uri("/internal/notifications").header(InternalTokens.HEADER, token)
                .body(message).retrieve().toBodilessEntity();
    }
}
