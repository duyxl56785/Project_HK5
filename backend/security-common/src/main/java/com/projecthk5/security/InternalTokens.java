package com.projecthk5.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;

/** OD-09 pair credential check: one opaque token per caller->callee pair, compared in constant time. */
public final class InternalTokens {
    public static final String HEADER = "X-Internal-Token";

    private InternalTokens() {}

    public static void require(String configured, String supplied) {
        if (configured == null || configured.isBlank())
            throw ApiException.unavailable("INTERNAL_AUTH_NOT_CONFIGURED", "Internal pair credential not configured");
        if (supplied == null || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_INVALID", "Internal credential invalid");
    }

    /** True when supplied matches any of the configured pair tokens. */
    public static boolean matchesAny(String supplied, String... configured) {
        if (supplied == null) return false;
        boolean match = false;
        for (String token : configured)
            if (token != null && !token.isBlank() && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) match = true;
        return match;
    }
}
