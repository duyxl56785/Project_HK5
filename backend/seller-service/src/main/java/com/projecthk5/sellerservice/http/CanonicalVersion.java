package com.projecthk5.sellerservice.http;

/** Exact version text for the Seller/Store HTTP preparation boundary. */
public final class CanonicalVersion {
    private CanonicalVersion() { }
    public static long parse(String text) {
        if (text == null || text.length() > 19 || !text.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException("Version must be canonical nonnegative decimal text");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("Version is outside the supported range");
        }
    }

    public static String format(long version) {
        if (version < 0) throw new IllegalArgumentException("Version must be nonnegative");
        return Long.toString(version);
    }
}
