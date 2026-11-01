package com.projecthk5.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Verified caller from the JWT in the SecurityContext; never from body or headers. */
public record Actor(String id, List<String> roles, String bearer) {
    public boolean has(String role) { return roles.contains(role); }

    public static Actor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || jwt.getName() == null || jwt.getName().isBlank())
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_REQUIRED", "Access token required");
        List<String> roles = jwt.getToken().getClaimAsStringList("roles");
        return new Actor(jwt.getName(), roles == null ? List.of() : List.copyOf(roles), "Bearer " + jwt.getToken().getTokenValue());
    }

    public static Actor require(String role) {
        Actor actor = current();
        if (!actor.has(role)) throw ApiException.forbidden(role + " role required");
        return actor;
    }
}
