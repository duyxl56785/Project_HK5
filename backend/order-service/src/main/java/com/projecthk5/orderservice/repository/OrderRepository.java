package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,UUID> {
    // Materialize the only collection needed by CheckoutResult before the repository returns.
    // Scope the read to the verified customer, including uncertain-commit recovery reads.
    @EntityGraph(attributePaths = "sellerOrders")
    Optional<Order> findByIdAndCustomerId(UUID id, String customerId);
}
