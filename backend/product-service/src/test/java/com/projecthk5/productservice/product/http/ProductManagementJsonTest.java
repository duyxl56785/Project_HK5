package com.projecthk5.productservice.product.http;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductManagementJsonTest {
    private final ProductManagementJson json = new ProductManagementJson();
    private static final String STORE = "71000000-0000-4000-8000-000000000001";
    private static final String CATEGORY = "71000000-0000-4000-8000-000000000002";
    private static final String CREATE = "{\"storeId\":\"%s\",\"categoryId\":\"%s\",\"sku\":\"SKU-é\",\"name\":\"Cà phê\",\"unitPrice\":\"1000000000\",\"currency\":\"VND\"}".formatted(STORE, CATEGORY);

    @Test void exactExplicitMoneyAndIdentityHaveNoDefaults() {
        var input = json.create(CREATE);
        assertEquals(UUID.fromString(STORE), input.storeId());
        assertEquals(UUID.fromString(CATEGORY), input.categoryId());
        assertEquals("SKU-é", input.sku());
        assertEquals("Cà phê", input.name());
        assertEquals(new BigDecimal("1000000000"), input.unitPrice());
        assertEquals("VND", input.currency());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "null", "[]", "{}", "{", "{}{}"})
    void malformedOrIncompleteObjectIsRejected(String payload) {
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.create(payload));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "01", "1.0", "1e2", "1 ", " 1", "NaN", "Infinity", "1000000001", "１"})
    void priceIsCanonicalPositiveIntegralVnd(String price) {
        assertThrows(ProductManagementJson.InvalidInput.class,
                () -> json.create(CREATE.replace("1000000000", price)));
    }

    @ParameterizedTest @ValueSource(strings = {"USD", "vnd", "VND ", ""})
    void currencyIsExplicitAndExact(String currency) {
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.create(CREATE.replace("VND", currency)));
    }

    @ParameterizedTest @ValueSource(strings = {
        ",\"ownerReference\":\"admin\"", ",\"sellerId\":\"spoof\"", ",\"role\":\"ADMIN\"",
        ",\"status\":\"ACTIVE\"", ",\"version\":\"0\"", ",\"sku\":\"duplicate\""
    })
    void unknownAndDuplicateFieldsCannotSmuggleIdentityOrState(String extra) {
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.create(CREATE.substring(0, CREATE.length() - 1) + extra + "}"));
    }

    @Test void numericCoercionMissingNullTrailingAndInvalidUnicodeAreRejectedWithoutRawCause() {
        for (String bad : new String[] {
                CREATE.replace("\"1000000000\"", "1000000000"),
                CREATE.replace(",\"currency\":\"VND\"", ""),
                CREATE.replace("\"VND\"", "null"), CREATE + " true",
                CREATE.replace(STORE, "1-1-1-1-1"),
                CREATE.replace("SKU-é", "bad\\u0000"), CREATE.replace("SKU-é", "bad\\ud800")}) {
            var failure = assertThrows(ProductManagementJson.InvalidInput.class, () -> json.create(bad));
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("SKU"));
        }
    }

    @Test void editHasNoSkuOrStoreMutationAndVersionNeverPassesThroughDouble() {
        String edit = "{\"categoryId\":\"%s\",\"name\":\"Edited\",\"unitPrice\":\"1\",\"currency\":\"VND\",\"expectedVersion\":\"9223372036854775807\"}".formatted(CATEGORY);
        assertEquals(Long.MAX_VALUE, json.edit(edit).expectedVersion());
        assertEquals(BigDecimal.ONE, json.edit(edit).unitPrice());
        for (String field : new String[] {"sku", "storeId"}) {
            assertThrows(ProductManagementJson.InvalidInput.class, () -> json.edit(edit.substring(0, edit.length() - 1) + ",\"" + field + "\":\"changed\"}"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "01", "+1", "1.0", "1e1", "1 ", "9223372036854775808", "１"})
    void noncanonicalVersionIsRejected(String version) {
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.version("{\"expectedVersion\":\"" + version + "\"}"));
    }

    @Test void versionAndModerationHaveExactShapes() {
        assertEquals(0, json.version("{\"expectedVersion\":\"0\"}"));
        var moderation = json.moderation("{\"expectedVersion\":\"7\",\"reason\":\" Fix details \"}");
        assertEquals(7, moderation.expectedVersion());
        assertEquals(" Fix details ", moderation.reason()); // Domain owns strip/code-point policy.
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.version("{\"expectedVersion\":1}"));
        assertThrows(ProductManagementJson.InvalidInput.class, () -> json.moderation("{\"expectedVersion\":\"0\"}"));
    }

    @Test void responseIsAnAllowlistedProjectionWithExactMoneyVersionAndNoPrivateReason() throws Exception {
        var listing = new ProductListing(UUID.randomUUID(), UUID.fromString(STORE), UUID.fromString(CATEGORY),
                "Exact-é", "Name", new BigDecimal("25.000"), "VND", ProductStatus.REJECTED, "Private moderation detail");
        var response = ProductManagementResponse.from(new StoredProduct(listing, Long.MAX_VALUE));
        var tree = new ObjectMapper().readTree(json.write(response));
        var fields = new java.util.HashSet<String>();
        tree.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "storeId", "categoryId", "sku", "name", "unitPrice", "currency", "status", "version"), fields);
        assertEquals("25", tree.get("unitPrice").textValue());
        assertEquals(Long.toString(Long.MAX_VALUE), tree.get("version").textValue());
        assertFalse(tree.has("reviewReason"));
        assertFalse(tree.has("ownerReference"));
        assertThrows(IllegalArgumentException.class, () -> ProductManagementResponse.from(null));
    }

    @Test void errorMappingIsStableAndNeverIncludesInternalRequestOrSqlValues() {
        assertEquals(400, ProductManagementErrors.from(new ProductManagementJson.InvalidInput()).status());
        assertEquals(403, ProductManagementErrors.from(new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "private owner")).status());
        assertEquals(503, ProductManagementErrors.from(new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "secret")).status());
        assertEquals(409, ProductManagementErrors.from(new ProductPersistenceException(ProductPersistenceException.Code.STALE_VERSION, "sql")).status());
        assertEquals(404, ProductManagementErrors.from(new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND, "uuid")).status());
        var unexpected = ProductManagementErrors.from(new RuntimeException("password sql owner"));
        assertEquals(500, unexpected.status());
        assertFalse(unexpected.message().contains("password"));
        assertFalse(unexpected.message().contains("sql"));
    }
}
