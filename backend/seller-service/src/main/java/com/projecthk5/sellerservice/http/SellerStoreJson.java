package com.projecthk5.sellerservice.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projecthk5.sellerservice.seller.domain.SellerRuleException;
import com.projecthk5.sellerservice.seller.service.SellerInputs;
import com.projecthk5.sellerservice.store.StoreInputs;
import com.projecthk5.sellerservice.store.StoreRuleException;
import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

/** Isolated codec preparation; not registered as a Spring bean or HTTP handler. */
public final class SellerStoreJson {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ObjectWriter sellerWriter = mapper.writerFor(SellerStoreResponses.SellerPrivate.class);
    private final ObjectWriter storeWriter = mapper.writerFor(SellerStoreResponses.StoreOwner.class);

    public static final class InvalidInput extends IllegalArgumentException {
        private InvalidInput() { super("Invalid Seller/Store JSON"); }
    }
    public SellerInputs.Contact registration(String payload) {
        return decode(payload, Set.of("representativeName", "phone", "pickupAddress"), SellerStoreJson::contact);
    }

    public SellerInputs.Resubmission resubmission(String payload) {
        return decode(payload, Set.of("expectedVersion", "representativeName", "phone", "pickupAddress"),
                node -> new SellerInputs.Resubmission(version(node), contact(node)));
    }

    public SellerInputs.Version version(String payload) {
        return decode(payload, Set.of("expectedVersion"), node -> new SellerInputs.Version(version(node)));
    }

    public SellerInputs.Moderation moderation(String payload) {
        return decode(payload, Set.of("expectedVersion", "reason"),
                node -> new SellerInputs.Moderation(version(node), text(node, "reason")));
    }

    public StoreInputs.Create createStore(String payload) {
        return decode(payload, Set.of("name", "slug"),
                node -> new StoreInputs.Create(text(node, "name"), text(node, "slug")));
    }

    public StoreInputs.Update updateStore(String payload) {
        return decode(payload, Set.of("name", "slug", "expectedVersion"),
                node -> new StoreInputs.Update(text(node, "name"), text(node, "slug"), version(node)));
    }

    public StoreInputs.SetOpen setOpen(String payload) {
        return decode(payload, Set.of("open", "expectedVersion"), node -> {
            if (!node.get("open").isBoolean()) throw new InvalidInput();
            return new StoreInputs.SetOpen(node.get("open").booleanValue(), version(node));
        });
    }

    public String writeSellerPrivate(SellerStoreResponses.SellerPrivate response) {
        return write(sellerWriter, response);
    }

    public String writeStoreOwner(SellerStoreResponses.StoreOwner response) {
        return write(storeWriter, response);
    }

    private <T> T decode(String payload, Set<String> fields, Function<ObjectNode, T> mapping) {
        if (payload == null) throw new InvalidInput();
        try (var parser = mapper.createParser(payload)) {
            JsonNode tree = mapper.readTree(parser);
            if (tree == null || !tree.isObject() || parser.nextToken() != null || tree.size() != fields.size()) {
                throw new InvalidInput();
            }
            var names = tree.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!fields.contains(name) || tree.get(name).isNull()) throw new InvalidInput();
            }
            return mapping.apply((ObjectNode) tree);
        } catch (IOException | IllegalArgumentException | SellerRuleException | StoreRuleException invalid) {
            // Jackson errors can contain raw request values; never expose or retain them as a public cause.
            throw new InvalidInput();
        }
    }

    private static String text(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (!value.isTextual()) throw new InvalidInput();
        return value.textValue();
    }

    private static SellerInputs.Contact contact(ObjectNode node) {
        return new SellerInputs.Contact(text(node, "representativeName"), text(node, "phone"), text(node, "pickupAddress"));
    }

    private static long version(ObjectNode node) {
        return CanonicalVersion.parse(text(node, "expectedVersion"));
    }

    private static String write(ObjectWriter writer, Object response) {
        if (response == null) throw new IllegalArgumentException("Seller/Store response is required");
        try {
            return writer.writeValueAsString(response);
        } catch (IOException failure) {
            throw new IllegalStateException("Seller/Store response could not be encoded");
        }
    }
}
