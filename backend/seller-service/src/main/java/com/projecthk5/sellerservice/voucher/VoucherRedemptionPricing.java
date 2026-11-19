package com.projecthk5.sellerservice.voucher;

import com.projecthk5.security.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Voucher C2 amounts in whole Vietnamese dong, using the C1 voucher configuration. */
final class VoucherRedemptionPricing {
    private VoucherRedemptionPricing() { }

    static long discount(VoucherRules.Spec spec, long merchandiseAmount) {
        if (merchandiseAmount < 1 || merchandiseAmount > VoucherRules.MAX_VND) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Invalid merchandise amount");
        }
        if (merchandiseAmount < spec.minOrderAmount()) {
            throw ApiException.conflict("VOUCHER_MIN_ORDER_AMOUNT", "Voucher minimum was not met");
        }

        long amount = switch (spec.discountType()) {
            case FIXED -> spec.discountValue();
            case PERCENT -> {
                long rounded = BigDecimal.valueOf(merchandiseAmount)
                        .multiply(BigDecimal.valueOf(spec.discountValue()))
                        .movePointLeft(2)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                yield Math.min(rounded, spec.maxDiscount());
            }
        };
        if (amount >= merchandiseAmount) {
            throw ApiException.conflict("VOUCHER_GROSS_TOO_LOW", "Payable amount must be at least one dong");
        }
        return amount;
    }
}
