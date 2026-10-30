package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.security.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CatalogCacheTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JavaType text = json.constructType(String.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CatalogCache cache = new CatalogCache(redis, json);

    @BeforeEach
    void wire() {
        when(redis.opsForValue()).thenReturn(values);
    }

    private void stored(long ageMillis) {
        when(values.get("k")).thenReturn((System.currentTimeMillis() - ageMillis) + "|\"cached\"");
    }

    @Test
    void freshEntryAnswersWithoutTheSource() {
        stored(1000);
        assertEquals("cached", cache.read("k", text, () -> {
            throw new AssertionError("source must not run");
        }));
    }

    @Test
    void agedEntryIsRefreshedFromTheSource() {
        stored(CatalogCache.FRESH.toMillis() + 1);
        assertEquals("new", cache.read("k", text, () -> "new"));
        verify(values).set(anyString(), any(String.class), any(Duration.class));
    }

    @Test
    void agedEntryServesWhileSellerIsUnavailable() {
        stored(CatalogCache.FRESH.toMillis() + 1);
        assertEquals("cached", cache.read("k", text, () -> {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "down");
        }));
        assertEquals("cached", cache.read("k", text, () -> {
            throw ApiException.unavailable("INVENTORY_UNAVAILABLE", "down");
        }));
    }

    @Test
    void anythingButUnavailabilityStillFails() {
        stored(CatalogCache.FRESH.toMillis() + 1);
        assertThrows(ApiException.class, () -> cache.read("k", text, () -> {
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "gone");
        }));
    }

    @Test
    void unavailableWithNothingCachedStillFails() {
        assertThrows(ProductRuleException.class, () -> cache.read("k", text, () -> {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "down");
        }));
    }

    @Test
    void redisFailureFallsBackToTheSource() {
        when(values.get("k")).thenThrow(new QueryTimeoutException("redis down"));
        doThrow(new QueryTimeoutException("redis down")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertEquals("new", cache.read("k", text, () -> "new"));
    }

    @Test
    void corruptEntryIsTreatedAsMissing() {
        when(values.get("k")).thenReturn(System.currentTimeMillis() + "|{broken");
        assertEquals("new", cache.read("k", text, () -> "new"));
        verify(values, never()).get("other");
    }
}
