package com.projecthk5.authservice.api;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.security.InternalTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Seller->Auth pair only: SELLER grant/revoke after a committed Seller status transition. */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final AuthService auth;
    private final String sellerToken;

    public InternalAccountController(AuthService auth, @Value("${SELLER_AUTH_TOKEN:}") String sellerToken) {
        this.auth = auth;
        this.sellerToken = sellerToken;
    }

    public record RoleChange(@NotNull Boolean granted) {}

    @PutMapping("/{accountId}/roles/SELLER")
    public AuthController.AccountResponse sellerRole(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                     @PathVariable UUID accountId, @Valid @RequestBody RoleChange change) {
        InternalTokens.require(sellerToken, token);
        return AuthController.AccountResponse.of(auth.changeRole(accountId, Account.Role.SELLER, change.granted()));
    }
}
