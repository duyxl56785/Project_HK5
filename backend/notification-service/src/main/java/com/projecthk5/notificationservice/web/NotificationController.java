package com.projecthk5.notificationservice.web;

import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {
    private final NotificationRepository notifications;
    private final String orderToken;
    private final String sellerToken;

    public NotificationController(NotificationRepository notifications,
                                  @Value("${ORDER_NOTIFICATION_TOKEN:}") String orderToken,
                                  @Value("${SELLER_NOTIFICATION_TOKEN:}") String sellerToken) {
        this.notifications = notifications;
        this.orderToken = orderToken;
        this.sellerToken = sellerToken;
    }

    public record Create(@NotBlank @Size(max = 128) String recipient, @NotBlank @Size(max = 64) String type,
                         @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 1000) String message,
                         @Size(max = 300) String link, @Size(max = 200) String eventKey) {}
    public record View(UUID id, String type, String title, String message, String link, Instant createdAt, boolean read) {
        static View of(Notification n) {
            return new View(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    /** Order/Seller pairs only. Redelivery with the same eventKey is accepted without a duplicate row. */
    @PostMapping("/internal/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void create(@RequestHeader(value = InternalTokens.HEADER, required = false) String token, @Valid @RequestBody Create req) {
        if (!InternalTokens.matchesAny(token, orderToken, sellerToken)) InternalTokens.require(orderToken, token);
        if (req.eventKey() != null && notifications.existsByEventKey(req.eventKey())) return;
        try {
            notifications.saveAndFlush(new Notification(req.recipient(), req.type(), req.title(), req.message(), req.link(), req.eventKey()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Unique event_key: a concurrent redelivery already stored it.
        }
    }

    @GetMapping("/api/notifications")
    public List<View> mine() {
        return notifications.findByRecipientOrderByCreatedAtDesc(Actor.current().id(), PageRequest.of(0, 50))
                .stream().map(View::of).toList();
    }

    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", notifications.countByRecipientAndReadAtIsNull(Actor.current().id()));
    }

    @PostMapping("/api/notifications/{id}/read")
    @Transactional
    public View read(@PathVariable UUID id) {
        Notification n = notifications.findById(id).filter(x -> x.getRecipient().equals(Actor.current().id()))
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found"));
        n.markRead();
        return View.of(n);
    }

    @PostMapping("/api/notifications/read-all")
    @Transactional
    public Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead(Actor.current().id(), Instant.now()));
    }

    @Configuration
    static class Security {
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
        SecurityFilterChain notificationSecurity(HttpSecurity http) throws Exception {
            return JwtResourceServer.configure(http).authorizeHttpRequests(auth -> auth
                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/notifications").permitAll()
                    .requestMatchers("/api/notifications", "/api/notifications/**").authenticated()
                    .anyRequest().denyAll()).build();
        }
    }
}
