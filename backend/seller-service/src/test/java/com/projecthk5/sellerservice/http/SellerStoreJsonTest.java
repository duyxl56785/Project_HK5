package com.projecthk5.sellerservice.http;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SellerStoreJsonTest {
    private final SellerStoreJson json = new SellerStoreJson();

    @Test
    void mapsAllSixSchemasToExistingValidatedInputsWithoutCoercion() {
        assertEquals("An", json.registration(" {\"representativeName\":\" An \",\"phone\":\"+84901234567\",\"pickupAddress\":\"1 Le Loi\"} \n").representativeName());
        assertEquals("1 Le Loi", json.resubmission("{\"expectedVersion\":\"3\",\"representativeName\":\"An\",\"phone\":\"0901234567\",\"pickupAddress\":\"1 Le Loi\"}").contact().pickupAddress());
        assertEquals(Long.MAX_VALUE, json.version("{\"expectedVersion\":\"9223372036854775807\"}").expectedVersion());
        assertEquals("Reason", json.moderation("{\"reason\":\" Reason \",\"expectedVersion\":\"0\"}").reason());
        assertEquals("Store", json.createStore("{\"name\":\" Store \",\"slug\":\"store\"}").name());
        assertEquals("store", json.updateStore("{\"slug\":\"store\",\"name\":\"Store\",\"expectedVersion\":\"123\"}").slug());
        assertFalse(json.setOpen("{\"open\":false,\"expectedVersion\":\"0\"}").open());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "[]", "\"text\"", "0", "true", "{", "{}", "{}{}", "{} null", "{} garbage",
            "{\"representativeName\":\"An\",\"phone\":\"abc\",\"pickupAddress\":\"1 Le Loi\"}",
            "{\"representativeName\":\" \",\"phone\":\"0901234567\",\"pickupAddress\":\"1 Le Loi\"}",
            "{\"role\":\"SELLER\"}", "{\"ownerReference\":\"other-owner\"}", "{\"status\":\"APPROVED\"}",
            "{\"id\":\"other\"}", "{\"sellerId\":\"other\"}", "{\"storeId\":\"other\"}",
            "{\"role\":null}", "{\"extra\":{}}", "{/*comment*/}", "{,}"})
    void registrationRequiresExactlyValidContactDetails(String payload) {
        invalid(() -> json.registration(payload));
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    void everyVersionedSchemaRejectsNoncanonicalTypeTextAndOverflow(String value) {
        invalid(() -> json.version("{\"expectedVersion\":" + value + "}"));
        invalid(() -> json.moderation("{\"reason\":\"Reason\",\"expectedVersion\":" + value + "}"));
        invalid(() -> json.updateStore("{\"name\":\"Store\",\"slug\":\"store\",\"expectedVersion\":" + value + "}"));
        invalid(() -> json.setOpen("{\"open\":false,\"expectedVersion\":" + value + "}"));
    }

    @ParameterizedTest
    @MethodSource("badBodies")
    void strictSchemasRejectMissingUnknownDuplicateNullAndWrongKinds(Body body) {
        invalid(() -> body.parser(json).accept(body.payload()));
    }

    @Test
    void strictnessIsIsolatedFromApplicationObjectMapper() throws Exception {
        ObjectMapper applicationMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertEquals(1, applicationMapper.readTree("{\"x\":0,\"x\":1}").get("x").intValue());
        invalid(() -> json.createStore("{\"name\":\"Store\",\"slug\":\"store\",\"ownerReference\":\"secret-owner\"}"));
        assertEquals(1, applicationMapper.readTree("{\"x\":0,\"x\":1}").get("x").intValue());
        assertFalse(applicationMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void parseErrorsDoNotEchoUntrustedValuesOrJacksonSource() {
        String sensitive = "private-untrusted-value";
        var error = assertThrows(SellerStoreJson.InvalidInput.class,
                () -> json.registration("{\"role\":\"" + sensitive + "\"}"));
        assertFalse(error.toString().contains(sensitive));
        assertNull(error.getCause());
    }

    static Stream<String> invalidVersions() {
        return Stream.of("0", "-1", "true", "[]", "{}", "null", "\"\"", "\"+1\"", "\"01\"", "\" 1 \"",
                "\"1.0\"", "\"1e3\"", "\"9223372036854775808\"");
    }

    enum Schema { VERSION, MODERATION, CREATE, UPDATE, OPEN }
    record Body(Schema schema, String payload) {
        Consumer<String> parser(SellerStoreJson json) {
            return switch (schema) {
                case VERSION -> json::version;
                case MODERATION -> json::moderation;
                case CREATE -> json::createStore;
                case UPDATE -> json::updateStore;
                case OPEN -> json::setOpen;
            };
        }
    }

    static Stream<Body> badBodies() {
        return Stream.of(
                new Body(Schema.VERSION, "{}"), new Body(Schema.VERSION, "{\"expectedVersion\":\"0\",\"role\":\"ADMIN\"}"),
                new Body(Schema.VERSION, "{\"expectedVersion\":\"0\",\"expectedVersion\":\"1\"}"),
                new Body(Schema.VERSION, "{\"expectedVersion\":\"0\",\"expected\\u0056ersion\":\"1\"}"),
                new Body(Schema.MODERATION, "{\"expectedVersion\":\"0\"}"),
                new Body(Schema.MODERATION, "{\"expectedVersion\":\"0\",\"reason\":null}"),
                new Body(Schema.MODERATION, "{\"expectedVersion\":\"0\",\"reason\":42}"),
                new Body(Schema.MODERATION, "{\"expectedVersion\":\"0\",\"reason\":\" \"}"),
                new Body(Schema.MODERATION, "{\"expectedVersion\":\"0\",\"reason\":\"" + "x".repeat(501) + "\"}"),
                new Body(Schema.CREATE, "{}"), new Body(Schema.CREATE, "{\"name\":\"Store\"}"),
                new Body(Schema.CREATE, "{\"name\":null,\"slug\":\"store\"}"),
                new Body(Schema.CREATE, "{\"name\":1,\"slug\":\"store\"}"),
                new Body(Schema.CREATE, "{\"name\":[],\"slug\":\"store\"}"),
                new Body(Schema.CREATE, "{\"name\":\"Store\",\"slug\":true}"),
                new Body(Schema.CREATE, "{\"name\":\"Store\",\"slug\":\"store\",\"slug\":\"other\"}"),
                new Body(Schema.CREATE, "{\"name\":\"Store\",\"slug\":\"STORE\"}"),
                new Body(Schema.CREATE, "{\"name\":\"Store\",\"slug\":\"store\",\"ownerReference\":\"B\"}"),
                new Body(Schema.UPDATE, "{\"name\":\"Store\",\"slug\":\"store\"}"),
                new Body(Schema.UPDATE, "{\"name\":\"Store\",\"slug\":\"store\",\"expectedVersion\":\"0\",\"status\":\"OPEN\"}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\"}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":null}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":\"false\"}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":0}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":[]}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":false,\"role\":\"SELLER\"}"),
                new Body(Schema.OPEN, "{\"expectedVersion\":\"0\",\"open\":false}{}"));
    }

    private static void invalid(org.junit.jupiter.api.function.Executable action) {
        assertThrows(SellerStoreJson.InvalidInput.class, action);
    }
}
