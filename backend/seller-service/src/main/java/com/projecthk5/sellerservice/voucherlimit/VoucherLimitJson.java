package com.projecthk5.sellerservice.voucherlimit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projecthk5.security.ApiException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;

/** All write requests reject duplicate and unknown JSON keys, including untrusted store IDs. */
public final class VoucherLimitJson {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public record Submit(int requestedLimit, String reason, String budgetNote, Instant requestedFrom,
                         Instant requestedUntil) { }
    public record Decision(long expectedVersion, int approvedLimit, Instant approvedFrom,
                           Instant approvedUntil, String reason) { }
    public record Reject(long expectedVersion, String reason) { }

    public Submit submit(String raw) {
        ObjectNode n = parse(raw, Set.of("requestedLimit", "reason", "requestedFrom", "requestedUntil"),
                Set.of("budgetNote"));
        int limit = integer(n, "requestedLimit", 21, 50);
        String reason = text(n, "reason", 1, 500);
        String note = n.has("budgetNote") ? text(n, "budgetNote", 1, 500) : null;
        Instant from = date(n, "requestedFrom"), until = date(n, "requestedUntil");
        if (!until.isAfter(from) || until.isAfter(from.plus(java.time.Duration.ofDays(30)))) throw invalid();
        return new Submit(limit, reason, note, from, until);
    }

    public Decision approve(String raw) {
        ObjectNode n = parse(raw, Set.of("expectedVersion", "approvedLimit", "approvedFrom", "approvedUntil"),
                Set.of("reason"));
        return new Decision(version(n), integer(n, "approvedLimit", 21, 50),
                date(n, "approvedFrom"), date(n, "approvedUntil"),
                n.has("reason") ? text(n, "reason", 1, 500) : null);
    }

    public Reject reject(String raw) {
        ObjectNode n = parse(raw, Set.of("expectedVersion", "reason"), Set.of());
        return new Reject(version(n), text(n, "reason", 1, 500));
    }

    public long cancel(String raw) { return version(parse(raw, Set.of("expectedVersion"), Set.of())); }

    private ObjectNode parse(String raw, Set<String> required, Set<String> optional) {
        if (raw == null) throw invalid();
        try (var parser = mapper.createParser(raw)) {
            JsonNode root = mapper.readTree(parser);
            if (!(root instanceof ObjectNode obj) || parser.nextToken() != null) throw invalid();
            for (String key : required) if (!obj.has(key)) throw invalid();
            var keys = obj.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                if ((!required.contains(key) && !optional.contains(key)) || obj.get(key).isNull()) throw invalid();
            }
            return obj;
        } catch (java.io.IOException | IllegalArgumentException failure) { throw invalid(); }
    }

    private static String text(ObjectNode n, String key, int min, int max) {
        JsonNode v = n.get(key);
        if (v == null || !v.isTextual()) throw invalid();
        String value = v.textValue();
        if (value.codePointCount(0, value.length()) < min || value.codePointCount(0, value.length()) > max
                || value.isBlank() || !value.equals(value.strip())) throw invalid();
        return value;
    }
    private static int integer(ObjectNode n, String key, int min, int max) {
        JsonNode v = n.get(key);
        if (v == null || !v.isIntegralNumber()) throw invalid();
        try {
            int value = new BigInteger(v.asText()).intValueExact();
            if (value < min || value > max) throw invalid();
            return value;
        } catch (ArithmeticException | NumberFormatException failure) { throw invalid(); }
    }
    private static long version(ObjectNode n) {
        String raw = text(n, "expectedVersion", 1, 19);
        if (!raw.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(raw); }
        catch (NumberFormatException failure) { throw invalid(); }
    }
    private static Instant date(ObjectNode n, String key) {
        try { return OffsetDateTime.parse(text(n,key,1,48)).toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
        catch (java.time.format.DateTimeParseException failure) { throw invalid(); }
    }
    private static ApiException invalid() {
        return ApiException.badRequest("VALIDATION_FAILED", "Invalid voucher limit request");
    }
}
