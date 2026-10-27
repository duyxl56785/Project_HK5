package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.api.OrderHistoryDtos.*;
import com.projecthk5.orderservice.repository.OrderHistoryRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OrderHistoryService {
    private final OrderHistoryRepository orders;
    public OrderHistoryService(OrderHistoryRepository orders) { this.orders = orders; }

    public Page list(String customerId, int page, int size) {
        requireCustomer(customerId);
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >=0 and size must be 1..100");
        return orders.list(customerId, page, size);
    }
    public Detail detail(String customerId, UUID id) {
        requireCustomer(customerId);
        return orders.detail(customerId, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
    private static void requireCustomer(String customerId) {
        if (customerId == null || customerId.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified customer required");
    }
}
