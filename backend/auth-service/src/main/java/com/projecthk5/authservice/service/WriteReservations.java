package com.projecthk5.authservice.service;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Tracks writes in flight so a revocation can stop them. The account row lock orders each reservation
 * against a revocation: it either sees the revoked role or is visible to the revoker once it commits.
 * Closing goes through the service's WriteFence, never by age, so a slow write cannot outlive its
 * reservation; a reservation left by a crash is closed by the sweep.
 */
@Service
public class WriteReservations {
    private static final Logger log = LoggerFactory.getLogger(WriteReservations.class);
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final Map<String, String> urlByToken = new LinkedHashMap<>();

    public WriteReservations(AccountRepository accounts, JdbcTemplate jdbc,
                             @Value("${WRITE_FENCE_TARGETS:}") String targets) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        for (String target : targets.split(",")) {
            int separator = target.indexOf('@');
            if (separator > 0) urlByToken.put(target.substring(0, separator), target.substring(separator + 1));
        }
    }

    private record Held(UUID id, String serviceUrl) {}

    /** The caller's own URL, taken from its pair token so it cannot name another service. */
    public String callerOf(String token) {
        for (var target : urlByToken.entrySet())
            if (InternalTokens.matchesAny(token, target.getKey())) return target.getValue();
        throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_INVALID", "Internal credential invalid");
    }

    /** Empty when the account is gone, not ACTIVE, or no longer holds every claimed role. */
    @Transactional
    public Optional<UUID> reserve(String subject, Collection<String> claimed, String serviceUrl) {
        UUID accountId;
        try {
            accountId = UUID.fromString(subject);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return accounts.findSharedById(accountId)
                .filter(account -> AuthService.ACTIVE.equals(account.getStatus()))
                .filter(account -> account.getRoles().stream().map(Account.Role::name).toList().containsAll(claimed))
                .map(account -> {
                    UUID id = UUID.randomUUID();
                    jdbc.update("insert into write_reservations (id, account_id, service_url) values (?, ?, ?)",
                            id, accountId, serviceUrl);
                    return id;
                });
    }

    public void release(UUID id) {
        jdbc.update("delete from write_reservations where id = ?", id);
    }

    /** Runs after the revocation commits. On failure the rows stay, so the caller's retry finishes the job. */
    public void closeAll(UUID accountId) {
        try {
            held("select id, service_url from write_reservations where account_id = ?", accountId).forEach(this::close);
        } catch (RestClientException | IllegalStateException unreachable) {
            throw ApiException.unavailable("AUTH_WRITE_FENCE_UNAVAILABLE", "Cannot stop writes in flight");
        }
    }

    /** No business write lasts this long, so these were left by a crash or a lost release. */
    @Scheduled(fixedDelayString = "${write-reservations.sweep-ms:30000}")
    public void closeAbandoned() {
        for (Held reservation : held("select id, service_url from write_reservations where created_at < now() - interval '60 seconds'")) {
            try {
                close(reservation);
            } catch (RestClientException | IllegalStateException unreachable) {
                log.warn("Write reservation {} not closed, will retry: {}", reservation.id(), unreachable.getClass().getSimpleName());
            }
        }
    }

    private List<Held> held(String query, Object... arguments) {
        return jdbc.query(query, (row, index) -> new Held(row.getObject("id", UUID.class), row.getString("service_url")), arguments);
    }

    private void close(Held reservation) {
        String token = urlByToken.entrySet().stream().filter(target -> target.getValue().equals(reservation.serviceUrl()))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalStateException("No fence target for " + reservation.serviceUrl()));
        JwtResourceServer.internalClient(reservation.serviceUrl()).post()
                .uri("/internal/write-fences/{id}/close", reservation.id())
                .header(InternalTokens.HEADER, token).retrieve().toBodilessEntity();
        release(reservation.id());
    }
}
