package com.projecthk5.cartservice.api;

import com.projecthk5.cartservice.api.CartDtos.*;
import com.projecthk5.cartservice.security.CustomerIdentity;
import com.projecthk5.cartservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service=service; }
    @GetMapping public CartView get(JwtAuthenticationToken jwt) { return service.view(CustomerIdentity.customerId(jwt)); }
    @PostMapping("/items") public CartView add(JwtAuthenticationToken jwt,@Valid @RequestBody AddItem body) {return service.add(CustomerIdentity.customerId(jwt), body);}
    @PatchMapping("/items/{productId}") public CartView change(JwtAuthenticationToken jwt,@PathVariable String productId,@Valid @RequestBody UpdateItem body) {return service.update(CustomerIdentity.customerId(jwt),productId,body.quantity());}
    @DeleteMapping("/items/{productId}") public CartView remove(JwtAuthenticationToken jwt,@PathVariable String productId) {return service.remove(CustomerIdentity.customerId(jwt),productId);}
}
