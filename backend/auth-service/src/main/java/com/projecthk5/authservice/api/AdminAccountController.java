package com.projecthk5.authservice.api;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.authservice.service.WriteReservations;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** Admin locks and unlocks accounts. Auth runs no CurrentAuthorityFilter, so each call re-checks the caller. */
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountController.class);
    private final AuthService auth;
    private final WriteReservations reservations;

    public AdminAccountController(AuthService auth, WriteReservations reservations) {
        this.auth = auth;
        this.reservations = reservations;
    }

    public record AccountRow(String id, String email, List<String> roles, String status, Instant createdAt) {
        static AccountRow of(Account a) {
            return new AccountRow(a.getId().toString(), a.getEmail(), a.getRoles().stream().map(Enum::name).sorted().toList(),
                    a.getStatus(), a.getCreatedAt());
        }
    }

    @GetMapping
    public List<AccountRow> search(@RequestParam(defaultValue = "") String term) {
        admin();
        return auth.search(term).stream().map(AccountRow::of).toList();
    }

    /** Acknowledged only once no write of this account can still commit; a retry finishes a failed close. */
    @PostMapping("/{id}/disable")
    public AccountRow disable(@PathVariable UUID id) {
        String admin = admin();
        Account account = auth.disable(id);
        reservations.closeAll(id);
        log.info("Account {} disabled by {}", id, admin);
        return AccountRow.of(account);
    }

    @PostMapping("/{id}/enable")
    public AccountRow enable(@PathVariable UUID id) {
        String admin = admin();
        Account account = auth.enable(id);
        log.info("Account {} enabled by {}", id, admin);
        return AccountRow.of(account);
    }

    private String admin() {
        String id = Actor.current().id();
        if (!auth.current(id).getRoles().contains(Account.Role.ADMIN)) throw ApiException.forbidden("ADMIN role required");
        return id;
    }
}
