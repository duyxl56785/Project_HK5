package com.projecthk5.cartservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public final class CustomerIdentity {
    private CustomerIdentity() {}
    public static String customerId(JwtAuthenticationToken jwt) {
        if (jwt == null || jwt.getToken() == null || jwt.getName() == null || jwt.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified customer JWT required");
        }
        if (jwt.getToken().getClaimAsStringList("roles") == null || !jwt.getToken().getClaimAsStringList("roles").contains("CUSTOMER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER role required");
        }
        return jwt.getName();
    }
}
