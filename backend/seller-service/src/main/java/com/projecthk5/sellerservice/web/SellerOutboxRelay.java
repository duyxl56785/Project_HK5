package com.projecthk5.sellerservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/** Delivers committed Seller side effects; both targets are idempotent, so redelivery is safe. */
@Component
public class SellerOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(SellerOutboxRelay.class);
    private final SellerOutboxRepository outbox;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient auth;
    private final RestClient notifications;
    private final String authToken;
    private final String notificationToken;

    public SellerOutboxRelay(SellerOutboxRepository outbox,
                             @Value("${AUTH_INTERNAL_URL:http://localhost:8101}") String authUrl,
                             @Value("${SELLER_AUTH_TOKEN:}") String authToken,
                             @Value("${NOTIFICATION_INTERNAL_URL:http://localhost:8108}") String notificationUrl,
                             @Value("${SELLER_NOTIFICATION_TOKEN:}") String notificationToken) {
        this.outbox = outbox;
        this.auth = JwtResourceServer.internalClient(authUrl);
        this.notifications = JwtResourceServer.internalClient(notificationUrl);
        this.authToken = authToken;
        this.notificationToken = notificationToken;
    }

    @Scheduled(fixedDelay = 2000, initialDelayString = "${seller.outbox.relay-initial-delay-ms:0}")
    @Transactional
    public void relay() {
        for (SellerOutbox item : outbox.pending(PageRequest.of(0, 20))) {
            item.attempted();
            try {
                Map<?, ?> payload = mapper.readValue(item.getPayload(), Map.class);
                if (item.getKind() == SellerOutbox.Kind.ROLE_SYNC) {
                    auth.put().uri("/internal/accounts/{id}/roles/SELLER", payload.get("accountId"))
                            .header(InternalTokens.HEADER, authToken)
                            .body(Map.of("granted", payload.get("granted"))).retrieve().toBodilessEntity();
                } else {
                    notifications.post().uri("/internal/notifications").header(InternalTokens.HEADER, notificationToken)
                            .body(payload).retrieve().toBodilessEntity();
                }
                item.delivered();
            } catch (Exception e) {
                log.warn("Seller outbox {} delivery failed, will retry: {}", item.getKind(), e.getClass().getSimpleName());
                // Keep ordering: a later ROLE_SYNC must not overtake an undelivered earlier one.
                break;
            }
        }
    }
}
