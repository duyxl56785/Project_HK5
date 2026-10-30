package com.projecthk5.authservice.api;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Seller->Auth role changes, and the current-authority lookup each business service makes before a write. */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final AuthService auth;
    private final String sellerToken;
    private final String[] authorityCallers;

    public InternalAccountController(AuthService auth, @Value("${SELLER_AUTH_TOKEN:}") String sellerToken,
                                     @Value("${AUTHORITY_CALLER_TOKENS:}") String authorityCallers) {
        this.auth = auth;
        this.sellerToken = sellerToken;
        this.authorityCallers = authorityCallers.split(",");
    }

    public record RoleChange(@NotNull Boolean granted, @NotNull @PositiveOrZero Long revision) {}
    public record Authority(boolean active, List<String> roles) {}

    @PutMapping("/{accountId}/roles/SELLER")
    public AuthController.AccountResponse sellerRole(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                     @PathVariable UUID accountId, @Valid @RequestBody RoleChange change) {
        InternalTokens.require(sellerToken, token);
        return AuthController.AccountResponse.of(auth.changeSellerRole(accountId, change.granted(), change.revision()));
    }

    @GetMapping("/{accountId}/authority")
    public Authority authority(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                               @PathVariable String accountId) {
        if (!InternalTokens.matchesAny(token, authorityCallers))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_INVALID", "Internal credential invalid");
        return auth.liveRoles(accountId)
                .map(roles -> new Authority(true, roles.stream().map(Enum::name).toList()))
                .orElseGet(() -> new Authority(false, List.of()));
    }
}
