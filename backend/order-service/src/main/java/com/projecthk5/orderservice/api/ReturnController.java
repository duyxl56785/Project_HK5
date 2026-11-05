package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.service.ReturnRequestService;
import com.projecthk5.orderservice.service.ReturnRequestService.Created;
import com.projecthk5.orderservice.service.ReturnRequestService.ReturnView;
import com.projecthk5.security.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Buyer-facing Return MVP. No seller action, refund or stock mutation in this controller. */
@RestController
@RequestMapping("/api/orders")
public class ReturnController {
    private final ReturnRequestService returns;

    public ReturnController(ReturnRequestService returns) {
        this.returns = returns;
    }

    public record Request(@NotBlank @Size(max = 500) String reason) {}

    @PostMapping("/{orderId}/store-orders/{storeOrderId}/returns")
    public ResponseEntity<ReturnView> request(@PathVariable UUID orderId, @PathVariable UUID storeOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody Request body) {
        Created result = returns.request(Actor.require("CUSTOMER").id(), orderId, storeOrderId,
                key, body.reason());
        return ResponseEntity.status(result.newRequest() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.request());
    }

    @GetMapping("/{orderId}/returns")
    public List<ReturnView> mine(@PathVariable UUID orderId) {
        return returns.mine(Actor.require("CUSTOMER").id(), orderId);
    }
}
