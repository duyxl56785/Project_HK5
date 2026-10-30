package com.projecthk5.notificationservice.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable page);

    long countByRecipientAndReadAtIsNull(String recipient);

    boolean existsByEventKey(String eventKey);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient = :recipient and n.readAt is null")
    int markAllRead(String recipient, Instant now);
}
