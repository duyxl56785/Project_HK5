package com.projecthk5.sellerservice.voucher;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** C1 contract rules only. Does not calculate or redeem discounts at Checkout. */
public final class VoucherRules {
    private VoucherRules() { }

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_APPROVED_LIMIT = 50;
    public static final long MAX_VND = 99_999_999_999_999_999L;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{4,20}");

    public enum StoredStatus { DRAFT, PUBLISHED, PAUSED, CANCELLED }
    public enum DisplayStatus { DRAFT, SCHEDULED, ACTIVE, PAUSED, EXPIRED, CANCELLED }
    public enum DiscountType { FIXED, PERCENT }

    public record Spec(String code, String name, DiscountType discountType,
                       long discountValue, Long maxDiscount, long minOrderAmount,
                       int usageLimit, Instant startsAt, Instant endsAt) {
        public Spec {
            Objects.requireNonNull(discountType, "discountType");
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            if (code == null) throw new IllegalArgumentException("code required");
            code = code.toUpperCase(Locale.ROOT);
            if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("invalid code");
            if (name == null || name.codePointCount(0, name.length()) < 1 || name.codePointCount(0, name.length()) > 120
                    || name.isBlank() || !name.equals(name.trim()))
                throw new IllegalArgumentException("invalid name");
            if (discountValue <= 0 || minOrderAmount < 0 || minOrderAmount > MAX_VND
                    || usageLimit < 1 || usageLimit > 100_000)
                throw new IllegalArgumentException("invalid amount or usageLimit");
            if (discountType == DiscountType.FIXED) {
                if (discountValue > MAX_VND || maxDiscount != null || minOrderAmount < discountValue)
                    throw new IllegalArgumentException("invalid FIXED discount");
            } else {
                if (discountValue > 90 || maxDiscount == null || maxDiscount <= 0 || maxDiscount > MAX_VND)
                    throw new IllegalArgumentException("invalid PERCENT discount");
            }
            if (!endsAt.isAfter(startsAt) || Duration.between(startsAt, endsAt).compareTo(Duration.ofDays(90)) > 0)
                throw new IllegalArgumentException("invalid validity window");
        }
    }

    public record Grant(int limit, Instant validFrom, Instant validUntil) {
        public Grant {
            if (limit <= DEFAULT_LIMIT || limit > MAX_APPROVED_LIMIT || validFrom == null
                    || validUntil == null || !validUntil.isAfter(validFrom))
                throw new IllegalArgumentException("invalid grant");
        }
        public boolean activeAt(Instant now) {
            return !now.isBefore(validFrom) && now.isBefore(validUntil);
        }
    }

    public static DisplayStatus display(StoredStatus stored, Instant startsAt, Instant endsAt, Instant now) {
        Objects.requireNonNull(stored);
        Objects.requireNonNull(now);
        if (stored == StoredStatus.CANCELLED) return DisplayStatus.CANCELLED;
        if (stored == StoredStatus.DRAFT) return DisplayStatus.DRAFT;
        if (!now.isBefore(endsAt)) return DisplayStatus.EXPIRED;
        if (stored == StoredStatus.PAUSED) return DisplayStatus.PAUSED;
        return now.isBefore(startsAt) ? DisplayStatus.SCHEDULED : DisplayStatus.ACTIVE;
    }

    /** One slot per PUBLISHED, non-expired voucher; caller must serialize with Store row lock. */
    public static int permittedLimit(int used, Instant startsAt, Instant now, Grant grant) {
        if (used < 0 || startsAt == null || now == null) throw new IllegalArgumentException("invalid quota input");
        if (used < DEFAULT_LIMIT) return DEFAULT_LIMIT;
        return grant != null && grant.activeAt(now) && startsAt.isBefore(grant.validUntil())
                ? grant.limit() : DEFAULT_LIMIT;
    }

    public static boolean canTakeSlot(int used, Instant startsAt, Instant now, Grant grant) {
        return used < permittedLimit(used, startsAt, now, grant);
    }
}
