package com.projecthk5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A JWT outlives its account state by up to its TTL, so every write reserves itself with Auth, which
 * re-checks the account. Auth can then stop the write through {@link WriteFence} if authority is revoked.
 * Fails closed: if Auth cannot answer, the write gets 503.
 * shortcut: reads still trust the token until it expires, add reads here if that window matters.
 */
public class CurrentAuthorityFilter extends OncePerRequestFilter {
    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    public record Reservation(boolean granted, UUID id) {}

    private final RestClient auth;
    private final String token;

    public CurrentAuthorityFilter(String authUrl, String token) {
        this.auth = JwtResourceServer.internalClient(authUrl);
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (READS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/")
                || !(authentication instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }
        List<String> claimed = jwt.getToken().getClaimAsStringList("roles");
        Reservation reservation = null;
        if (claimed != null) {
            try {
                reservation = auth.post().uri("/internal/accounts/{id}/write-reservations", jwt.getName())
                        .header(InternalTokens.HEADER, token).body(Map.of("roles", claimed))
                        .retrieve().body(Reservation.class);
            } catch (RestClientException | IllegalArgumentException unavailable) {
                JwtResourceServer.write(response, 503, "AUTH_AUTHORITY_UNAVAILABLE", "Cannot verify current authority");
                return;
            }
            if (reservation == null) {
                JwtResourceServer.write(response, 503, "AUTH_AUTHORITY_UNAVAILABLE", "Cannot verify current authority");
                return;
            }
        }
        if (reservation == null || !reservation.granted()) {
            // 401 sends the client through refresh, which issues a token with the current roles or ends the session.
            response.setHeader("WWW-Authenticate", "Bearer realm=\"smart-ecommerce\", error=\"invalid_token\"");
            JwtResourceServer.write(response, 401, "AUTH_ACCESS_INVALID", "Access token no longer matches the account");
            return;
        }
        WriteFence.enter(reservation.id());
        try {
            chain.doFilter(request, response);
        } finally {
            WriteFence.exit();
            release(reservation.id());
        }
    }

    private void release(UUID id) {
        try {
            auth.delete().uri("/internal/accounts/write-reservations/{id}", id)
                    .header(InternalTokens.HEADER, token).retrieve().toBodilessEntity();
        } catch (RestClientException | IllegalArgumentException unavailable) {
            logger.warn("Write reservation not released, Auth will close it: " + unavailable.getClass().getSimpleName());
        }
    }
}
