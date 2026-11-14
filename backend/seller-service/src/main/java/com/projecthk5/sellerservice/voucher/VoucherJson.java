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
import java.util.HashSet;
import java.util.Set;

/** Strict wire parser: no duplicate/unknown keys, float money, or client-supplied store ID. */
public final class VoucherJson {
    private static final Set<String> SPEC = Set.of("code","name","discountType","discountValue",
            "minOrderAmount","usageLimit","startsAt","endsAt");
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public record Edit(long expectedVersion, VoucherRules.Spec spec) {}
    public VoucherRules.Spec create(String body) { return spec(parse(body,SPEC,Set.of("maxDiscount"))); }
    public Edit edit(String body) {
        Set<String> fields = new HashSet<>(SPEC);
        fields.add("expectedVersion");
        ObjectNode json = parse(body,fields,Set.of("maxDiscount"));
        return new Edit(version(json),spec(json));
    }
    public long action(String body) { return version(parse(body,Set.of("expectedVersion"),Set.of())); }

    private ObjectNode parse(String body, Set<String> required, Set<String> optional) {
        if (body==null) throw invalid();
        try (var parser=mapper.createParser(body)) {
            JsonNode node=mapper.readTree(parser);
            if (!(node instanceof ObjectNode object) || parser.nextToken()!=null) throw invalid();
            for (String field: required) if (!object.has(field)) throw invalid();
            var names=object.fieldNames();
            while (names.hasNext()) {
                String field=names.next();
                if ((!required.contains(field) && !optional.contains(field)) || object.get(field).isNull())
                    throw invalid();
            }
            return object;
        } catch(java.io.IOException | IllegalArgumentException error) { throw invalid(); }
    }
    private static VoucherRules.Spec spec(ObjectNode n) {
        try {
            String type=text(n,"discountType");
            Long max=n.has("maxDiscount")?amount(n,"maxDiscount"):null;
            return new VoucherRules.Spec(text(n,"code"),text(n,"name"), VoucherRules.DiscountType.valueOf(type),
                    amount(n,"discountValue"),max,amount(n,"minOrderAmount"),
                    Math.toIntExact(amount(n,"usageLimit")),instant(n,"startsAt"),instant(n,"endsAt"));
        } catch(IllegalArgumentException error) { throw invalid(); }
    }
    private static String text(ObjectNode n,String key) {
        JsonNode v=n.get(key);
        if (v==null||!v.isTextual()) throw invalid();
        return v.textValue();
    }
    private static long amount(ObjectNode n,String key) {
        JsonNode v=n.get(key);
        if (v==null||!v.isIntegralNumber()) throw invalid();
        try { return new BigInteger(v.asText()).longValueExact(); }
        catch(ArithmeticException | NumberFormatException e) { throw invalid(); }
    }
    private static Instant instant(ObjectNode n,String key) {
        try { return OffsetDateTime.parse(text(n,key)).toInstant(); }
        catch(DateTimeParseException e) { throw invalid(); }
    }
    private static long version(ObjectNode n) {
        String v=text(n,"expectedVersion");
        if (!v.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(v); } catch(NumberFormatException e) { throw invalid(); }
    }
    private static ApiException invalid() { return ApiException.badRequest("VALIDATION_FAILED","Invalid Voucher request"); }
}
