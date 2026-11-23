package com.projecthk5.productservice.web;

import com.projecthk5.security.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Size/color variants: each option is its own Product row with its own SKU, price and stock, grouped behind
 * the first one (the lead). The name of each row is the shared base name plus its option label.
 */
public final class ProductVariantRules {
    public static final int MAX_VARIANTS = 30, MAX_SIZE = 20, MAX_COLOR = 30, MAX_NAME = 255;

    private ProductVariantRules() {}

    /** Normalized option: stripped, a blank part is absent, at least one part present. */
    public record Option(String size, String color) {}

    public static Option option(String size, String color) {
        Option option = new Option(part(size, MAX_SIZE, "Size"), part(color, MAX_COLOR, "Color"));
        if (option.size() == null && option.color() == null) throw invalid("A variant needs a size or a color");
        return option;
    }

    public static String label(Option option) {
        if (option.size() != null && option.color() != null) return option.size() + " / " + option.color();
        return option.size() != null ? option.size() : option.color();
    }

    public static String variantName(String base, Option option) {
        String stripped = base == null ? "" : base.strip();
        if (stripped.isEmpty()) throw invalid("Product name is required");
        String name = stripped + " (" + label(option) + ")";
        if (name.length() > MAX_NAME) throw invalid("Product name is too long for its variant label");
        return name;
    }

    /** The shared name; a row renamed outside the group keeps its whole name. */
    public static String baseName(String name, Option option) {
        String suffix = " (" + label(option) + ")";
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /** One to MAX_VARIANTS distinct options, all with the same parts (size only, color only, or both). */
    public static void requireGroup(List<Option> options) {
        if (options == null || options.isEmpty() || options.size() > MAX_VARIANTS)
            throw invalid("A product has 1 to " + MAX_VARIANTS + " variants");
        boolean sized = options.get(0).size() != null, colored = options.get(0).color() != null;
        Set<String> seen = new HashSet<>();
        for (Option option : options) {
            if ((option.size() != null) != sized || (option.color() != null) != colored)
                throw invalid("Every variant must use the same option types");
            if (!seen.add(key(option.size()) + "\u0000" + key(option.color())))
                throw invalid("Variant options must be distinct");
        }
    }

    private static String key(String part) {
        return part == null ? "" : part.toLowerCase(Locale.ROOT);
    }

    private static String part(String value, int max, String what) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        if (stripped.length() > max) throw invalid(what + " is at most " + max + " characters");
        if (stripped.chars().anyMatch(Character::isISOControl)) throw invalid(what + " has a control character");
        return stripped;
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("VARIANT_INVALID", message);
    }
}
