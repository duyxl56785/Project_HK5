package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,UUID> {
}
