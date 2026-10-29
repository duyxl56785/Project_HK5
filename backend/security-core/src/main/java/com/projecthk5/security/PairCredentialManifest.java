package com.projecthk5.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

public final class PairCredentialManifest {
    public static final String PROPERTY = "HK5_INTERNAL_PAIR_CREDENTIAL_MANIFEST";
    public static final List<String> PAIRS = List.of("HK5_SELLER_AUTH_ROLE_SYNC_", "HK5_SELLER_AUTH_CURRENT_ACCOUNT_",
            "HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_", "HK5_PRODUCT_SELLER_STORE_FACTS_");
    private static final List<String> SLOTS = List.of("SECRET", "PREVIOUS_SECRET");
    private PairCredentialManifest() { }

    public static String provision(Function<String, String> secrets) {
        var entries = new LinkedHashMap<String, String>();
        for (String pair : PAIRS) for (String slot : SLOTS) {
            String value = secrets.apply(pair + slot);
            if (value == null && slot.equals("SECRET")) throw invalid();
            if (value != null) new InternalPairVerifier.Credentials(value, null, null);
            entries.put(pair + slot, value == null ? "-" : digest(value));
        }
        validate(entries);
        return "v1;" + String.join(";", entries.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
    }

    static void verify(Function<String, String> environment) {
        String text = environment.apply(PROPERTY);
        if (text == null || text.length() > 2048 || !text.startsWith("v1;")) throw invalid();
        var entries = new HashMap<String, String>();
        for (String field : text.substring(3).split(";", -1)) {
            String[] parts = field.split("=", -1);
            if (parts.length != 2 || entries.putIfAbsent(parts[0], parts[1]) != null) throw invalid();
        }
        validate(entries);
        for (String pair : PAIRS) for (String slot : SLOTS) {
            String local = environment.apply(pair + slot);
            if (local != null && !digest(local).equals(entries.get(pair + slot))) throw invalid();
        }
    }

    static void requireLocalSlots(Function<String, String> environment, String prefix) {
        String text = environment.apply(PROPERTY);
        for (String slot : SLOTS) {
            String local = environment.apply(prefix + slot);
            String expected = local == null ? "-" : digest(local);
            if (!Arrays.asList(text.substring(3).split(";")).contains(prefix + slot + "=" + expected)) throw invalid();
        }
    }

    private static void validate(Map<String, String> entries) {
        if (entries.size() != PAIRS.size() * SLOTS.size()) throw invalid();
        var seen = new HashSet<String>();
        for (String pair : PAIRS) for (String slot : SLOTS) {
            String hash = entries.get(pair + slot);
            if ("-".equals(hash) && slot.equals("PREVIOUS_SECRET")) continue;
            if (hash == null || !hash.matches("[0-9a-f]{64}") || !seen.add(hash)) throw invalid();
        }
    }

    private static String digest(String value) {
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid global pair credential manifest"); }

    public static void main(String[] args) {
        if (args.length != 0) throw invalid();
        System.out.println(provision(System::getenv));
    }
}
