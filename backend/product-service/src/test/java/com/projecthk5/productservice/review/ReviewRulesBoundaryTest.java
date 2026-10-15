package com.projecthk5.productservice.review;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Local rule boundaries only; no provider/consumer HTTP contract is assumed. */
class ReviewRulesBoundaryTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingBuyerOnBothSidesOfTrustedBoundary(String identity) {
        assertThrows(IllegalArgumentException.class, () -> new ReviewRules.Purchase(identity, true, true));
        assertThrows(IllegalArgumentException.class, () -> ReviewRules.requireEligible(identity,
                new ReviewRules.Purchase("buyer", true, true), 3, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Buyer", " buyer", "buyer ", "other"})
    void neverNormalizesOpaqueIdentityToGrantOwnership(String identity) {
        assertThrows(SecurityException.class, () -> ReviewRules.requireEligible(identity,
                new ReviewRules.Purchase("buyer", true, true), 3, false));
    }

    @Test
    void missingPurchaseIsRejectedInsteadOfAssumingDelivery() {
        assertThrows(NullPointerException.class,
                () -> ReviewRules.requireEligible("buyer", null, 3, false));
    }

    @Test
    void rejectsMalformedRatingCollectionAndDirectInvalidRating() {
        assertThrows(NullPointerException.class, () -> ReviewRules.publicAverage(null));
        assertThrows(NullPointerException.class,
                () -> ReviewRules.publicAverage(Arrays.asList(new ReviewRules.Rating(5, false), null)));
        assertThrows(IllegalArgumentException.class, () -> new ReviewRules.Rating(0, true));
        assertThrows(IllegalArgumentException.class, () -> new ReviewRules.Rating(6, false));
    }

    @Test
    void visibilityChangesPublicCountAndAverageAndUnhideRestoresThem() {
        var low = new ReviewRules.Rating(1, false);
        var high = new ReviewRules.Rating(5, false);
        assertEquals(new BigDecimal("3.00"), ReviewRules.publicAverage(List.of(low, high)).orElseThrow());
        var hidden = ReviewRules.moderate(low, true, () -> { });
        assertEquals(new BigDecimal("5.00"), ReviewRules.publicAverage(List.of(hidden, high)).orElseThrow());
        var restored = ReviewRules.moderate(hidden, false, () -> { });
        assertEquals(new BigDecimal("3.00"), ReviewRules.publicAverage(List.of(restored, high)).orElseThrow());
        assertEquals(low, restored);
    }

    @Test
    void hiddenValuesAndInputOrderDoNotBiasPublicAverage() {
        var one = new ReviewRules.Rating(1, false);
        var five = new ReviewRules.Rating(5, false);
        var hiddenOne = new ReviewRules.Rating(1, true);
        var hiddenFive = new ReviewRules.Rating(5, true);
        assertEquals(ReviewRules.publicAverage(List.of(one, five)),
                ReviewRules.publicAverage(List.of(hiddenFive, five, hiddenOne, one)));
    }

    @Test
    void eachModerationIncludingReplayRequiresFreshAccessCheck() {
        var checks = new AtomicInteger();
        var original = new ReviewRules.Rating(4, false);
        var hidden = ReviewRules.moderate(original, true, () -> checks.incrementAndGet());
        assertEquals(hidden, ReviewRules.moderate(hidden, true, () -> checks.incrementAndGet()));
        assertEquals(2, checks.get());
        assertThrows(SecurityException.class, () -> ReviewRules.moderate(hidden, false,
                () -> { throw new SecurityException("Permission revoked"); }));
        assertTrue(hidden.hidden());
    }

    @Test
    void authorizedModerationStillRejectsMissingStoredReview() {
        assertThrows(NullPointerException.class, () -> ReviewRules.moderate(null, true, () -> { }));
    }
}
