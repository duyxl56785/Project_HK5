package com.projecthk5.authservice.api;

import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.authservice.service.WriteReservations;
import com.projecthk5.security.InternalTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Seller->Auth role changes, and the reservation each business service takes around a write. */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final AuthService auth;
    private final WriteReservations reservations;
    private final String sellerToken;

    public InternalAccountController(AuthService auth, WriteReservations reservations,
                                     @Value("${SELLER_AUTH_TOKEN:}") String sellerToken) {
        this.auth = auth;
        this.reservations = reservations;
        this.sellerToken = sellerToken;
    }

    public record RoleChange(@NotNull Boolean granted, @NotNull @PositiveOrZero Long revision) {}
    public record Claim(@NotNull List<String> roles) {}
    public record Reservation(boolean granted, UUID id) {}

    /** A revocation is acknowledged only once no write under the old role can still commit. */
    @PutMapping("/{accountId}/roles/SELLER")
    public AuthController.AccountResponse sellerRole(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                                                     @PathVariable UUID accountId, @Valid @RequestBody RoleChange change) {
        InternalTokens.require(sellerToken, token);
        var account = AuthController.AccountResponse.of(auth.changeSellerRole(accountId, change.granted(), change.revision()));
        if (!change.granted()) reservations.closeAll(accountId);
        return account;
    }

    @PostMapping("/{accountId}/write-reservations")
    public Reservation reserve(@RequestHeader(value = InternalTokens.HEADER, required = false) String token,
                               @PathVariable String accountId, @Valid @RequestBody Claim claim) {
        return reservations.reserve(accountId, claim.roles(), reservations.callerOf(token))
                .map(id -> new Reservation(true, id)).orElseGet(() -> new Reservation(false, null));
    }

    @DeleteMapping("/write-reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@RequestHeader(value = InternalTokens.HEADER, required = false) String token, @PathVariable UUID id) {
        reservations.callerOf(token);
        reservations.release(id);
    }
}
