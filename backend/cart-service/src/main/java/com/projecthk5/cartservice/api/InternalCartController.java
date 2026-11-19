package com.projecthk5.cartservice.api;

import com.projecthk5.cartservice.api.CartDtos.*;
import com.projecthk5.cartservice.security.CustomerIdentity;
import com.projecthk5.cartservice.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/cart")
public class InternalCartController {
    private final CartService service;
    private final String credential;
    public InternalCartController(CartService service,@Value("${app.internal.checkout-token:}") String credential) {this.service=service;this.credential=credential;}
    private void authorize(String token) {
        if (credential.isBlank() || token == null || !MessageDigest.isEqual(credential.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Internal cart permission denied");
    }
    @PostMapping("/claim") public CartView claim(JwtAuthenticationToken jwt,@RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody Claim req) {authorize(token); return service.claim(CustomerIdentity.customerId(jwt),req.key(),req.expectedRevision());}
    @PostMapping("/release") public void release(JwtAuthenticationToken jwt,@RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody Claim req) {authorize(token); service.release(CustomerIdentity.customerId(jwt),req.key());}
    @PostMapping("/finalize") public void finalizeOrder(JwtAuthenticationToken jwt,@RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody Claim req) {authorize(token); service.finalizeCheckout(CustomerIdentity.customerId(jwt),req.key());}

    record Recovery(@NotBlank @Size(max=200) String customerId, @NotBlank @Size(max=128) String key,
                    @PositiveOrZero long claimedRevision, boolean placed) { }

    @PostMapping("/recover")
    public void recover(@RequestHeader(value="X-Internal-Token",required=false) String token,
                        @Valid @RequestBody Recovery req) {
        authorize(token);
        service.recoverCheckout(req.customerId(), req.key(), req.claimedRevision(), req.placed());
    }
}
