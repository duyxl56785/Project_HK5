package com.projecthk5.sellerservice.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projecthk5.security.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoucherRedemptionPricingTest {
    @Test
    void fixedDiscountAcceptsMinimumAndLeavesAtLeastOneDong() {
        var spec = voucher(VoucherRules.DiscountType.FIXED, 30_000, null, 30_000);
        assertEquals(30_000, VoucherRedemptionPricing.discount(spec, 200_000));
        assertEquals(30_000, VoucherRedemptionPricing.discount(spec, 30_001));
        assertEquals("VOUCHER_GROSS_TOO_LOW", assertThrows(ApiException.class,
                () -> VoucherRedemptionPricing.discount(spec, 30_000)).code());
        assertEquals("VOUCHER_MIN_ORDER_AMOUNT", assertThrows(ApiException.class,
                () -> VoucherRedemptionPricing.discount(spec, 29_999)).code());
    }

    @Test
    void percentRoundsHalfUpBeforeApplyingCap() {
        var rounded = voucher(VoucherRules.DiscountType.PERCENT, 15, 50_000L, 0);
        var capped = voucher(VoucherRules.DiscountType.PERCENT, 10, 30_000L, 0);
        assertEquals(30_000, VoucherRedemptionPricing.discount(rounded, 199_999));
        assertEquals(2, VoucherRedemptionPricing.discount(rounded, 10));
        assertEquals(30_000, VoucherRedemptionPricing.discount(capped, 500_000));
    }

    @Test
    void roundedPercentCannotLeaveZeroGrossAndCannotOverflowWholeVnd() {
        var spec = voucher(VoucherRules.DiscountType.PERCENT, 90, VoucherRules.MAX_VND, 0);
        assertEquals("VOUCHER_GROSS_TOO_LOW", assertThrows(ApiException.class,
                () -> VoucherRedemptionPricing.discount(spec, 1)).code());
        assertEquals(89_999_999_999_999_999L, VoucherRedemptionPricing.discount(spec, VoucherRules.MAX_VND));
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> VoucherRedemptionPricing.discount(spec, VoucherRules.MAX_VND + 1)).code());
    }

    private static VoucherRules.Spec voucher(VoucherRules.DiscountType type, long value, Long cap, long min) {
        return new VoucherRules.Spec("DEMO30", "Synthetic voucher", type, value, cap, min, 5,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(3_600));
    }
}
