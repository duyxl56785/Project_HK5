package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.web.ProductVariantRules.Option;
import com.projecthk5.security.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductVariantRulesTest {
    @Test void optionIsStrippedAndBlankPartsBecomeAbsent() {
        assertEquals(new Option("M", "Đỏ"), ProductVariantRules.option("  M ", " Đỏ\t"));
        assertEquals(new Option("XL", null), ProductVariantRules.option("XL", "   "));
        assertEquals(new Option(null, "Xanh"), ProductVariantRules.option(null, "Xanh"));
    }

    @Test void optionNeedsSizeOrColor() {
        assertInvalid(() -> ProductVariantRules.option(" ", null));
        assertInvalid(() -> ProductVariantRules.option(null, null));
    }

    @Test void optionPartsAreBounded() {
        assertEquals("x".repeat(20), ProductVariantRules.option("x".repeat(20), null).size());
        assertInvalid(() -> ProductVariantRules.option("x".repeat(21), null));
        assertEquals("y".repeat(30), ProductVariantRules.option(null, "y".repeat(30)).color());
        assertInvalid(() -> ProductVariantRules.option(null, "y".repeat(31)));
        assertInvalid(() -> ProductVariantRules.option("M\u0000", null));
        assertInvalid(() -> ProductVariantRules.option("M", "Đỏ\nXanh"));
    }

    @Test void labelAndNameJoinTheParts() {
        assertEquals("M / Đỏ", ProductVariantRules.label(new Option("M", "Đỏ")));
        assertEquals("M", ProductVariantRules.label(new Option("M", null)));
        assertEquals("Đỏ", ProductVariantRules.label(new Option(null, "Đỏ")));
        assertEquals("Áo thun (M / Đỏ)", ProductVariantRules.variantName(" Áo thun ", new Option("M", "Đỏ")));
    }

    @Test void variantNameMustFitTheProductNameLimit() {
        Option option = new Option("M", "Đỏ");
        String fits = "a".repeat(255 - " (M / Đỏ)".length());
        assertEquals(255, ProductVariantRules.variantName(fits, option).length());
        assertInvalid(() -> ProductVariantRules.variantName(fits + "a", option));
        assertInvalid(() -> ProductVariantRules.variantName("  ", option));
    }

    @Test void baseNameRemovesOnlyTheOwnLabel() {
        assertEquals("Áo thun", ProductVariantRules.baseName("Áo thun (M / Đỏ)", new Option("M", "Đỏ")));
        assertEquals("Áo thun (L)", ProductVariantRules.baseName("Áo thun (L)", new Option("M", null)));
    }

    @Test void groupNeedsDistinctOptionsCaseInsensitively() {
        ProductVariantRules.requireGroup(List.of(new Option("M", "Đỏ"), new Option("L", "Đỏ"), new Option("M", "Xanh")));
        assertInvalid(() -> ProductVariantRules.requireGroup(List.of(new Option("M", "Đỏ"), new Option("m", "đỏ"))));
    }

    @Test void groupUsesTheSameDimensionsOnEveryVariant() {
        ProductVariantRules.requireGroup(List.of(new Option("M", null), new Option("L", null)));
        assertInvalid(() -> ProductVariantRules.requireGroup(List.of(new Option("M", "Đỏ"), new Option("L", null))));
        assertInvalid(() -> ProductVariantRules.requireGroup(List.of(new Option(null, "Đỏ"), new Option("L", null))));
    }

    @Test void groupSizeIsBounded() {
        assertInvalid(() -> ProductVariantRules.requireGroup(List.of()));
        List<Option> max = new ArrayList<>();
        for (int i = 1; i <= ProductVariantRules.MAX_VARIANTS; i++) max.add(new Option("S" + i, null));
        ProductVariantRules.requireGroup(max);
        max.add(new Option("S0", null));
        assertInvalid(() -> ProductVariantRules.requireGroup(max));
    }

    private static void assertInvalid(Runnable call) {
        ApiException error = assertThrows(ApiException.class, call::run);
        assertEquals("VARIANT_INVALID", error.code());
        assertEquals(400, error.status().value());
    }
}
