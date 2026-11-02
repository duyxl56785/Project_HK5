package com.projecthk5.productservice.product.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.productservice.product.ProductRuleException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Isolated strict codec; does not configure the shared ObjectMapper or register a route/bean. */
public final class ProductManagementJson {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public record Create(UUID storeId, UUID categoryId, String sku, String name, BigDecimal unitPrice, String currency) { }
    public record Edit(UUID categoryId, String name, BigDecimal unitPrice, String currency, long expectedVersion) { }
    public record Moderation(long expectedVersion, String reason) { }
    public static final class InvalidInput extends IllegalArgumentException {
        public InvalidInput() { super("Invalid Product input"); }
    }

    public Create create(String payload) {
        return decode(payload, Set.of("storeId", "categoryId", "sku", "name", "unitPrice", "currency"),
                node -> new Create(uuid(node, "storeId"), uuid(node, "categoryId"), text(node, "sku"), text(node, "name"),
                        price(node), text(node, "currency")));
    }

    public Edit edit(String payload) {
        return decode(payload, Set.of("categoryId", "name", "unitPrice", "currency", "expectedVersion"),
                node -> new Edit(uuid(node, "categoryId"), text(node, "name"), price(node), text(node, "currency"), version(node)));
    }

    public long version(String payload) {
        return decode(payload, Set.of("expectedVersion"), ProductManagementJson::version);
    }

    public Moderation moderation(String payload) {
        return decode(payload, Set.of("expectedVersion", "reason"), node -> new Moderation(version(node), text(node, "reason")));
    }

    public String write(ProductManagementResponse response) {
        if (response == null) throw new IllegalArgumentException("Product response is required");
        try { return mapper.writerFor(ProductManagementResponse.class).writeValueAsString(response); }
        catch (IOException failure) { throw new IllegalStateException("Product result could not be encoded"); }
    }

    private <T> T decode(String payload, Set<String> fields, Function<ObjectNode, T> mapping) {
        if (payload == null) throw new InvalidInput();
        try (var parser = mapper.createParser(payload)) {
            JsonNode tree = mapper.readTree(parser);
            if (tree == null || !tree.isObject() || parser.nextToken() != null || tree.size() != fields.size()) throw new InvalidInput();
            var names = tree.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!fields.contains(name) || tree.get(name).isNull()) throw new InvalidInput();
            }
            return mapping.apply((ObjectNode) tree);
        } catch (IOException | IllegalArgumentException | ProductRuleException invalid) {
            // Never retain Jackson's raw input, SQL details or an exception cause in the prepared error.
            throw new InvalidInput();
        }
    }

    private static String text(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (!value.isTextual()) throw new InvalidInput();
        String text = value.textValue();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw new InvalidInput();
            if (Character.isHighSurrogate(c) && (++i == text.length() || !Character.isLowSurrogate(text.charAt(i)))) throw new InvalidInput();
        }
        return text; // No trim, normalization, defaults or case-folding; domain owns its canonicalization.
    }

    private static UUID uuid(ObjectNode node, String name) {
        String value = text(node, name);
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new InvalidInput();
        return UUID.fromString(value);
    }

    private static BigDecimal price(ObjectNode node) { return ProductPrice.parseCanonical(text(node, "unitPrice"), text(node, "currency")); }

    private static long version(ObjectNode node) {
        String value = text(node, "expectedVersion");
        if (value.length() > 19 || !value.matches("0|[1-9][0-9]*")) throw new InvalidInput();
        return Long.parseLong(value);
    }
}
