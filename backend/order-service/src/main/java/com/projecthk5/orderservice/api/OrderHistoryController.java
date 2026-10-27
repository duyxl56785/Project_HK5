package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.api.OrderHistoryDtos.*;
import com.projecthk5.orderservice.security.CustomerIdentity;
import com.projecthk5.orderservice.service.OrderHistoryService;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderHistoryController {
    private final OrderHistoryService history;
    public OrderHistoryController(OrderHistoryService history) { this.history = history; }

    @GetMapping
    public Page list(JwtAuthenticationToken jwt,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return history.list(CustomerIdentity.customerId(jwt), page, size);
    }
    @GetMapping("/{id}")
    public Detail detail(JwtAuthenticationToken jwt, @PathVariable UUID id) {
        return history.detail(CustomerIdentity.customerId(jwt), id);
    }
}
