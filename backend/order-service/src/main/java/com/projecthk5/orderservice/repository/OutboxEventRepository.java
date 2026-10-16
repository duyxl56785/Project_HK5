package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID> {
}
