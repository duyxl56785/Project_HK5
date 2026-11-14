package com.projecthk5.sellerservice.voucher;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projecthk5.security.ApiException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** Strict wire parser: rejects duplicate/unknown fields, floating money and client-supplied storeId. */
public final class VoucherJson {
    private static final Set<String> SPEC = Set.of("code", "name", "discountType", "discountValue",
            "minOrderAmount", "usageLimit", "startsAt", "endsAt");
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public record Edit(long expectedVersion, VoucherRules.Spec spec) { }

    public VoucherRules.Spec create(String payload) {
        return spec(parse(payload, SPEC, Set.of("maxDiscount")));
    }

    public Edit edit(String payload) {
        Set<String> required = new java.util.HashSet<>(SPEC);
        required.add("expectedVersion");
        ObjectNode node = parse(payload, required, Set.of("maxDiscount"));
        return new Edit(version(node), spec(node));
    }

    public long action(String payload) {
        return version(parse(payload, Set.of("expectedVersion"), Set.of()));
    }

    private ObjectNode parse(String payload, Set<String> required, Set<String> optional) {
        if (payload == null) throw invalid();
        try (var parser = mapper.createParser(payload)) {
            JsonNode root = mapper.readTree(parser);
            if (!(root instanceof ObjectNode node) || parser.nextToken() != null) throw invalid();
            for (String field : required) if (!node.has(field)) throw invalid();
            var names = node.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                if ((!required.contains(field) && !optional.contains(field)) || node.get(field).isNull())
                    throw invalid();
            }
            return node;
        } catch (java.io.IOException | IllegalArgumentException error) {
            throw invalid();
        }
    }

    private static VoucherRules.Spec spec(ObjectNode node) {
        try {
            String type = text(node, "discountType");
            Long max = node.has("maxDiscount") ? amount(node, "maxDiscount") : null;
            return new VoucherRules.Spec(text(node,"code"), text(node,"name"),
                    VoucherRules.DiscountType.valueOf(type), amount(node,"discountValue"), max,
                    amount(node,"minOrderAmount"), Math.toIntExact(amount(node,"usageLimit")),
                    instant(node,"startsAt"), instant(node,"endsAt"));
        } catch (IllegalArgumentException | ArithmeticException failure) {
            throw invalid();
        }
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static long amount(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) throw invalid();
        try { return new BigInteger(value.asText()).longValueExact(); }
        catch (ArithmeticException | NumberFormatException failure) { throw invalid(); }
    }

    private static long version(ObjectNode node) {
        String raw = text(node, "expectedVersion");
        if (!raw.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(raw); }
        catch (NumberFormatException failure) { throw invalid(); }
    }

    private static Instant instant(ObjectNode node, String field) {
        try { return OffsetDateTime.parse(text(node, field)).toInstant(); }
        catch (DateTimeParseException failure) { throw invalid(); }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("VALIDATION_FAILED", "Invalid Voucher JSON");
    }
}
