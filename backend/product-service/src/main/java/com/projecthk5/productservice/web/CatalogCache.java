package com.projecthk5.productservice.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.security.ApiException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Redis cache for public Catalog reads only: fresh for 10 seconds, then reused for up to 5 minutes
 * while a dependency is unavailable. Writes and Cart/Order snapshots must never read through it.
 * If Redis itself fails, the read goes to the source.
 */
@Component
public class CatalogCache {
    static final Duration FRESH = Duration.ofSeconds(10);
    static final Duration STALE = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public CatalogCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public <T> T read(String key, JavaType type, Supplier<T> source) {
        String entry = get(key);
        Long storedAt = entry == null ? null : Long.valueOf(entry.substring(0, entry.indexOf('|')));
        if (storedAt != null && System.currentTimeMillis() - storedAt < FRESH.toMillis()) {
            T fresh = parse(entry, type);
            if (fresh != null) return fresh;
        }
        try {
            T value = source.get();
            put(key, value);
            return value;
        } catch (ProductRuleException | ApiException failure) {
            T stale = entry != null && unavailable(failure) ? parse(entry, type) : null;
            if (stale == null) throw failure;
            return stale;
        }
    }

    private static boolean unavailable(RuntimeException failure) {
        return failure instanceof ApiException api ? api.status() == HttpStatus.SERVICE_UNAVAILABLE
                : ((ProductRuleException) failure).getCode() == ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE;
    }

    private String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException unavailable) {
            return null;
        }
    }

    private void put(String key, Object value) {
        try {
            redis.opsForValue().set(key, System.currentTimeMillis() + "|" + json.writeValueAsString(value), STALE);
        } catch (DataAccessException | JsonProcessingException skipped) {
            // A missed write only costs the next read a trip to the source.
        }
    }

    private <T> T parse(String entry, JavaType type) {
        try {
            return json.readValue(entry.substring(entry.indexOf('|') + 1), type);
        } catch (JsonProcessingException corrupt) {
            return null;
        }
    }
}
