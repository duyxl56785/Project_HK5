package com.projecthk5.productservice.review;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewRulesTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void acceptsDeliveredPurchaseWithValidStars(int stars) {
        assertDoesNotThrow(() -> ReviewRules.requireEligible("buyer",
                new ReviewRules.Purchase("buyer", true, true), stars, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6, Integer.MAX_VALUE})
    void rejectsInvalidStars(int stars) {
        assertThrows(IllegalArgumentException.class, () -> ReviewRules.requireEligible("buyer",
                new ReviewRules.Purchase("buyer", true, true), stars, false));
    }

    @Test
    void rejectsOtherBuyerAndMissingVerifiedIdentity() {
        var purchase = new ReviewRules.Purchase("buyer", true, true);
        assertThrows(SecurityException.class,
                () -> ReviewRules.requireEligible("other", purchase, 5, false));
        assertThrows(IllegalArgumentException.class,
                () -> ReviewRules.requireEligible(null, purchase, 5, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewRules.Purchase(" ", true, true));
    }

    @Test
    void rejectsUndeliveredUnpurchasedAndDuplicateReview() {
        assertThrows(IllegalStateException.class, () -> ReviewRules.requireEligible("buyer",
                new ReviewRules.Purchase("buyer", false, true), 5, false));
        assertThrows(IllegalStateException.class, () -> ReviewRules.requireEligible("buyer",
                new ReviewRules.Purchase("buyer", true, false), 5, false));
        assertThrows(IllegalStateException.class, () -> ReviewRules.requireEligible("buyer",
                new ReviewRules.Purchase("buyer", true, true), 5, true));
    }

    @Test
    void hiddenReviewDoesNotAffectPublicSumOrCount() {
        assertEquals(new BigDecimal("4.00"), ReviewRules.publicAverage(List.of(
                new ReviewRules.Rating(3, false), new ReviewRules.Rating(5, false),
                new ReviewRules.Rating(1, true))).orElseThrow());
        assertTrue(ReviewRules.publicAverage(List.of(new ReviewRules.Rating(5, true))).isEmpty());
        assertTrue(ReviewRules.publicAverage(List.of()).isEmpty());
    }

    @Test
    void roundsPublicAverageHalfUp() {
        assertEquals(new BigDecimal("3.67"), ReviewRules.publicAverage(List.of(
                new ReviewRules.Rating(5, false), new ReviewRules.Rating(5, false),
                new ReviewRules.Rating(1, false))).orElseThrow());
    }

    @Test
    void adminCanHideUnhideAndRepeatWithoutChangingStars() {
        var original = new ReviewRules.Rating(5, false);
        var hidden = ReviewRules.moderate(original, true, () -> { });
        assertTrue(ReviewRules.publicAverage(List.of(hidden)).isEmpty());
        assertEquals(hidden, ReviewRules.moderate(hidden, true, () -> { }));
        assertEquals(original, ReviewRules.moderate(hidden, false, () -> { }));
        assertFalse(original.hidden());
    }

    @Test
    void deniedAdminPolicyCannotModerate() {
        var original = new ReviewRules.Rating(5, false);
        assertThrows(SecurityException.class, () -> ReviewRules.moderate(original, true,
                () -> { throw new SecurityException("Admin required"); }));
        assertThrows(NullPointerException.class, () -> ReviewRules.moderate(original, true, null));
        assertFalse(original.hidden());
    }
}
