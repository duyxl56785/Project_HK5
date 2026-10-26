package com.projecthk5.authservice.runtime;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/** Test-only single-instance guard. Distributed throttling is a deployment blocker. */
@Component
@ConditionalOnProperty(name = "hk5.auth.http.enabled", havingValue = "true")
public final class AuthHttpGate {
    private final Set<String> origins;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private record Window(long minute, int count) {}

    public AuthHttpGate(ConfigurableEnvironment environment) {
        Object raw = environment.getSystemEnvironment().get("HK5_AUTH_ALLOWED_ORIGINS");
        if (!(raw instanceof String list) || list.isBlank()) throw new IllegalArgumentException("Auth origin config required");
        var parsed = new HashSet<String>();
        for (String value : list.split(",", -1)) {
            if (value.isBlank() || !value.equals(value.strip()) || value.contains("*"))
                throw new IllegalArgumentException("Invalid Auth origin config");
            URI uri;
            try { uri = URI.create(value); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid Auth origin config"); }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()
                    || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                    || !value.equals(uri.toString()) || !parsed.add(value)) {
                throw new IllegalArgumentException("Invalid Auth origin config");
            }
        }
        origins = Set.copyOf(parsed);
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !origins.contains(origin)
                || !"1".equals(request.getHeader("X-CSRF-Protection"))) {
            throw new HttpFailure(403, "AUTH_CSRF_REJECTED");
        }
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) throw new HttpFailure(429, "AUTH_RATE_LIMITED");
        long minute = System.currentTimeMillis() / 60000L;
        if (windows.size() >= 4096 && !windows.containsKey(ip)) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
            if (windows.size() >= 4096) throw new HttpFailure(429, "AUTH_RATE_LIMITED");
        }
        var state = windows.compute(ip, (key, old) -> old == null || old.minute() != minute
                ? new Window(minute, 1) : new Window(minute, old.count() + 1));
        if (state.count() > 20) throw new HttpFailure(429, "AUTH_RATE_LIMITED");
    }

    public static final class HttpFailure extends RuntimeException {
        private final int status;
        private final String code;
        public HttpFailure(int status, String code) { super(code); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }
}
