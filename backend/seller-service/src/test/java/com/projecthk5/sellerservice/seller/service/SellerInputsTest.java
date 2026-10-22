package com.projecthk5.sellerservice.seller.service;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SellerInputsTest {
    @Test
    void acceptsZeroAndMaximumVersionsWithoutOverflow() {
        assertEquals(0L, new SellerInputs.Version(0L).expectedVersion());
        assertEquals(Long.MAX_VALUE, new SellerInputs.Version(Long.MAX_VALUE).expectedVersion());
        assertEquals(Long.MAX_VALUE, new SellerInputs.Moderation(Long.MAX_VALUE, "reason").expectedVersion());
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    void rejectsMissingAndNegativeVersions(Long version) {
        invalid(() -> new SellerInputs.Version(version));
        invalid(() -> new SellerInputs.Moderation(version, "reason"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u2003"})
    void rejectionAndSuspensionRequireMeaningfulReason(String reason) {
        invalid(() -> new SellerInputs.Moderation(0L, reason));
    }

    @Test
    void reasonLimitCountsStrippedUnicodeCodePointsLikePersistence() {
        String boundary = "😀".repeat(500);
        assertEquals(boundary, new SellerInputs.Moderation(0L, "\u2003" + boundary + "\u2003").reason());
        invalid(() -> new SellerInputs.Moderation(0L, boundary + "x"));
        assertEquals("Reason", new SellerInputs.Moderation(0L, "  Reason  ").reason());
    }

    static Stream<Long> invalidVersions() { return Stream.of(null, -1L, Long.MIN_VALUE); }

    private static void invalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(SellerRuleException.Code.INVALID_INPUT,
                assertThrows(SellerRuleException.class, action).getCode());
    }
}
