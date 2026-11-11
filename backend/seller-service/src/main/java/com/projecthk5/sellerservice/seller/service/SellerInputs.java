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

    /** The marketplace rules version an applicant must accept; bump it with frontend/src/features/policy/terms.ts. */
    public static final String TERMS_VERSION = "2026-11-11";

    public enum BusinessType { INDIVIDUAL, HOUSEHOLD, COMPANY }

    /** Format checks only: the platform does not verify these against any registry. */
    public record Contact(String representativeName, String phone, String pickupAddress, BusinessType businessType,
                          String idNumber, String taxCode, String email, String bankName, String bankAccountNumber,
                          String bankAccountHolder) {
        public Contact {
            representativeName = required(representativeName, 100);
            phone = required(phone, 20);
            pickupAddress = required(pickupAddress, 255);
            if (!phone.matches("\\+?[0-9]{8,15}")) throw invalid("A valid phone number is required");
            if (businessType == null) throw invalid("A business type is required");
            idNumber = required(idNumber, 12);
            if (!idNumber.matches("[0-9]{12}")) throw invalid("A 12-digit citizen ID is required");
            taxCode = taxCode == null || taxCode.isBlank() ? null : taxCode.strip();
            if (taxCode == null && businessType != BusinessType.INDIVIDUAL) throw invalid("A tax code is required");
            if (taxCode != null && !taxCode.matches("[0-9]{10}(-[0-9]{3})?")) throw invalid("A valid tax code is required");
            email = required(email, 254);
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("A valid email is required");
            bankName = required(bankName, 100);
            bankAccountNumber = required(bankAccountNumber, 19);
            if (!bankAccountNumber.matches("[0-9]{6,19}")) throw invalid("A valid bank account number is required");
            bankAccountHolder = required(bankAccountHolder, 100);
        }
    }

    /** An application: contact details plus acceptance of the current marketplace rules. */
    public record Application(Contact contact, String termsVersion) {
        public Application {
            if (contact == null) throw invalid("Seller contact details are required");
            if (!TERMS_VERSION.equals(termsVersion)) throw invalid("The current marketplace rules must be accepted");
        }
    }

    public record Resubmission(Long expectedVersion, Application application) {
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
