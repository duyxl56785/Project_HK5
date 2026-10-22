package com.projecthk5.sellerservice.seller.service;

import com.projecthk5.sellerservice.seller.domain.SellerRuleException;

/** Internal service inputs; identity and state always come from the verified service boundary. */
public final class SellerInputs {
    private SellerInputs() { }

    public record Version(Long expectedVersion) {
        public Version { requireVersion(expectedVersion); }
    }

    public record Moderation(Long expectedVersion, String reason) {
        public Moderation {
            requireVersion(expectedVersion);
            if (reason == null || reason.isBlank()) {
                throw invalid("A moderation reason is required");
            }
            reason = reason.strip();
            if (reason.codePointCount(0, reason.length()) > 500) {
                throw invalid("Seller review reason must contain at most 500 characters");
            }
        }
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) throw invalid("A nonnegative expected version is required");
    }

    private static SellerRuleException invalid(String message) {
        return new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, message);
    }
}
