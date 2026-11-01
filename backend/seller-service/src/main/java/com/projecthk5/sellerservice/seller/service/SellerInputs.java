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

    public record Contact(String representativeName, String phone, String pickupAddress) {
        public Contact {
            representativeName = required(representativeName, 100);
            phone = required(phone, 20);
            pickupAddress = required(pickupAddress, 255);
            if (!phone.matches("\\+?[0-9]{8,15}")) throw invalid("A valid phone number is required");
        }
    }

    public record Resubmission(Long expectedVersion, Contact contact) {
        public Resubmission { requireVersion(expectedVersion); }
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank()) throw invalid("Seller contact details are required");
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > max) throw invalid("Seller contact details are too long");
        return stripped;
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) throw invalid("A nonnegative expected version is required");
    }

    private static SellerRuleException invalid(String message) {
        return new SellerRuleException(SellerRuleException.Code.INVALID_INPUT, message);
    }
}
