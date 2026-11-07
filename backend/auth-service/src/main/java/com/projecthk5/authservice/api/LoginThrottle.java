package com.projecthk5.authservice.api;

import com.projecthk5.security.ApiException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter per 5 minutes: 10 attempts per key, 50 failed logins per client address.
 * Expired windows are dropped once the map grows, so unique keys cannot hold memory forever.
 * shortcut: in-memory per instance, move to Redis when Auth runs more than one replica.
 */
@Component
public class LoginThrottle {
    static final int LIMIT = 10;
    static final int CLIENT_LIMIT = 50;
    static final long WINDOW_SECONDS = 300;
    static final int SWEEP_AT = 10_000;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    private record Window(long start, int count) {}

    public LoginThrottle() { this(Clock.systemUTC()); }

    LoginThrottle(Clock clock) { this.clock = clock; }

    public void check(String key) {
        long now = clock.instant().getEpochSecond();
        Window w = windows.get(key);
        int limit = key.startsWith("login-client:") ? CLIENT_LIMIT : LIMIT;
        if (w != null && now - w.start() < WINDOW_SECONDS && w.count() >= limit) {
            long retry = WINDOW_SECONDS - (now - w.start());
            throw new RateLimited(retry);
        }
        if (key.startsWith("register:")) recordFailure(key);
    }

    public void recordFailure(String key) {
        long now = clock.instant().getEpochSecond();
        if (windows.size() >= SWEEP_AT) windows.values().removeIf(w -> now - w.start() >= WINDOW_SECONDS);
        windows.merge(key, new Window(now, 1), (old, one) ->
                now - old.start() >= WINDOW_SECONDS ? one : new Window(old.start(), old.count() + 1));
    }

    public void reset(String key) { windows.remove(key); }

    int size() { return windows.size(); }

    public static class RateLimited extends ApiException {
        private final long retryAfter;
        RateLimited(long retryAfter) {
            super(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED", "Too many attempts, try again later");
            this.retryAfter = retryAfter;
        }
        @Override public Map<String, String> headers() { return Map.of("Retry-After", Long.toString(retryAfter)); }
    }
}
