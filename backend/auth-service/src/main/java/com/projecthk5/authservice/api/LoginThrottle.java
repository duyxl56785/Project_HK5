package com.projecthk5.authservice.api;

import com.projecthk5.security.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter: 10 attempts per key per 5 minutes.
 * shortcut: in-memory per instance, move to Redis when Auth runs more than one replica.
 */
@Component
public class LoginThrottle {
    static final int LIMIT = 10;
    static final long WINDOW_SECONDS = 300;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long start, int count) {}

    public void check(String key) {
        long now = Instant.now().getEpochSecond();
        Window w = windows.get(key);
        if (w != null && now - w.start() < WINDOW_SECONDS && w.count() >= LIMIT) {
            long retry = WINDOW_SECONDS - (now - w.start());
            throw new RateLimited(retry);
        }
        if (key.startsWith("register:")) recordFailure(key);
    }

    public void recordFailure(String key) {
        long now = Instant.now().getEpochSecond();
        windows.merge(key, new Window(now, 1), (old, one) ->
                now - old.start() >= WINDOW_SECONDS ? one : new Window(old.start(), old.count() + 1));
    }

    public void reset(String key) { windows.remove(key); }

    public static class RateLimited extends ApiException {
        private final long retryAfter;
        RateLimited(long retryAfter) {
            super(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "Too many attempts, try again later");
            this.retryAfter = retryAfter;
        }
        @Override public Map<String, String> headers() { return Map.of("Retry-After", Long.toString(retryAfter)); }
    }
}
