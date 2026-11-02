package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.security.CustomerIdentity;
import com.projecthk5.orderservice.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class CheckoutController {
    private final CheckoutService service;
    public CheckoutController(CheckoutService service){this.service=service;}
    @PostMapping public CheckoutResult checkout(JwtAuthenticationToken jwt,
        @RequestHeader(value="Idempotency-Key",required=false) String key,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
        @Valid @RequestBody PlaceOrderRequest request){
        ShippingAddress s=request.shipping();
        return service.checkout(CustomerIdentity.customerId(jwt),bearer,key,
            new CheckoutRequest(request.expectedItems(),request.expectedRevision()),
            new Order.Shipping(s.name().strip(),s.phone().strip(),s.address().strip()));
    }
}
