package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalVersionTest {
    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "123,123", "9223372036854775807,9223372036854775807"})
    void roundTripsExactNonnegativeLongRange(String text, long value) {
        assertEquals(value, CanonicalVersion.parse(text));
        assertEquals(text, CanonicalVersion.format(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-1", "+1", "01", "00", " 1 ", "1.0", "1e3", "9223372036854775808",
            "99999999999999999999999999999999", "\u20031", "１", "١", "1\n"})
    void rejectsNoncanonicalOrOverflowTextWithoutNormalization(String text) {
        assertThrows(IllegalArgumentException.class, () -> CanonicalVersion.parse(text));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void cannotSerializeNegativeVersion(long value) {
        assertThrows(IllegalArgumentException.class, () -> CanonicalVersion.format(value));
    }
}
