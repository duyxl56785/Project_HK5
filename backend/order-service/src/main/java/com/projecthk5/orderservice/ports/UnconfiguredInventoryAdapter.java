package com.projecthk5.orderservice.ports;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UnconfiguredInventoryAdapter implements InventoryPort {
    @Override public void reserve(UUID orderId,List<Reservation> items) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"TV4 inventory reservation contract not connected");
    }
    @Override public void release(UUID orderId) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"TV4 inventory release contract not connected");
    }
}
