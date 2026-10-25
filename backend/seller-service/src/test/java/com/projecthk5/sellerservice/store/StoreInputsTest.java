package com.projecthk5.sellerservice.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StoreInputsTest {
    @Test
    void stripsOnlyNameAndPreservesCanonicalSlugAndExplicitFalse() {
        var create = new StoreInputs.Create("\u2003 Store \u2003", "store-1");
        var update = new StoreInputs.Update("\u2003 Store \u2003", "store-1", 0L);
        assertEquals("Store", create.name());
        assertEquals(create.name(), update.name());
        assertEquals("store-1", create.slug());
        assertEquals(0L, update.expectedVersion());
        assertFalse(new StoreInputs.SetOpen(false, 0L).open());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u2003"})
    void createAndUpdateRejectMissingName(String name) {
        invalid(() -> new StoreInputs.Create(name, "store"));
        invalid(() -> new StoreInputs.Update(name, "store", 0L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Store", " store", "store ", "\u2003store", "store\u2003",
            "-store", "store-", "store--one", "store_one", "cửa-hàng"})
    void createAndUpdateRejectNoncanonicalSlugWithoutNormalizingIt(String slug) {
        invalid(() -> new StoreInputs.Create("Store", slug));
        invalid(() -> new StoreInputs.Update("Store", slug, 0L));
    }

    @Test
    void exactLengthLimitsMatchExistingDomainIncludingUtf16Names() {
        assertDoesNotThrow(() -> new StoreInputs.Create(" " + "x".repeat(255) + " ", "x".repeat(128)));
        invalid(() -> new StoreInputs.Create("x".repeat(256), "store"));
        invalid(() -> new StoreInputs.Update("Store", "x".repeat(129), 0L));
        assertDoesNotThrow(() -> new StoreInputs.Update("😀".repeat(127) + "x", "store", Long.MAX_VALUE));
        invalid(() -> new StoreInputs.Create("😀".repeat(128), "store"));
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    void requiresAnExplicitNonnegativeVersion(Long version) {
        invalid(() -> new StoreInputs.Update("Store", "store", version));
        invalid(() -> new StoreInputs.SetOpen(true, version));
    }

    @Test
    void missingOpenIsNotSilentlyInterpretedAsClose() {
        invalid(() -> new StoreInputs.SetOpen(null, 0L));
        assertTrue(new StoreInputs.SetOpen(true, Long.MAX_VALUE).open());
    }

    static Stream<Long> invalidVersions() { return Stream.of(null, -1L, Long.MIN_VALUE); }

    private static void invalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(StoreRuleException.Code.INVALID_INPUT,
                assertThrows(StoreRuleException.class, action).getCode());
    }
}
