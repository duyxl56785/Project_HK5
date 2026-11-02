package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID> {
    @Query(value = "select id from outbox_events where published_at is null and dead = false "
        + "order by created_at limit :limit", nativeQuery = true)
    List<UUID> pendingIds(@Param("limit") int limit);

    /** SKIP LOCKED lets several relay instances share the queue without double delivery. */
    @Query(value = "select * from outbox_events where id = :id and published_at is null and dead = false "
        + "for update skip locked", nativeQuery = true)
    Optional<OutboxEvent> claim(@Param("id") UUID id);

    @Query("select e from OutboxEvent e where e.dead = true order by e.createdAt desc")
    List<OutboxEvent> findDead();
}
