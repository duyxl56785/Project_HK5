package com.projecthk5.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Internal credential check only. Caller/callee and operations are trusted host configuration. */
public final class InternalPairVerifier {
    private final String caller, callee;
    private final Set<Scope> scopes;
    private final Supplier<Credentials> source;
    private final Clock clock;

    public InternalPairVerifier(String caller, String callee, Set<Scope> scopes, Supplier<Credentials> source, Clock clock) {
        if (caller == null || caller.isBlank() || callee == null || callee.isBlank() || scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("Explicit service pair and scopes required");
        }
        this.caller = caller; this.callee = callee; this.scopes = Set.copyOf(scopes);
        this.source = Objects.requireNonNull(source); this.clock = Objects.requireNonNull(clock);
        if (scopes.stream().map(scope -> scope.method() + " " + scope.path()).distinct().count() != scopes.size()) {
            throw new IllegalArgumentException("Ambiguous internal operation scope");
        }
    }

    public VerifiedOperation verify(String supplied, String method, String path) {
        if (!valid(supplied)) throw denied(401, "INTERNAL_AUTH_INVALID");
        Credentials credentials;
        try { credentials = Objects.requireNonNull(source.get()); }
        catch (RuntimeException unavailable) { throw denied(503, "INTERNAL_AUTH_UNAVAILABLE"); }
        boolean current = equal(supplied, credentials.current);
        boolean previous = credentials.previous != null && clock.instant().isBefore(credentials.previousValidUntil)
                && equal(supplied, credentials.previous);
        if (!current && !previous) throw denied(401, "INTERNAL_AUTH_INVALID");
        Scope allowed = scopes.stream().filter(scope -> scope.method().equals(method) && scope.path().equals(path))
                .findFirst().orElseThrow(() -> denied(403, "INTERNAL_FORBIDDEN"));
        return new VerifiedOperation(caller, callee, allowed.operation());
    }

    public record Scope(String method, String path, String operation) {
        public Scope {
            if (method == null || !Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE").contains(method)
                    || path == null || !path.startsWith("/internal/") || path.contains("?") || path.contains("#")
                    || path.contains("%") || path.contains("//") || path.contains("..")
                    || operation == null || operation.isBlank()) throw new IllegalArgumentException("Invalid internal operation scope");
        }
    }

    public static final class Credentials {
        private final String current, previous;
        private final Instant previousValidUntil;
        public Credentials(String current, String previous, Instant previousValidUntil) {
            if (!valid(current) || (previous == null) != (previousValidUntil == null)
                    || previous != null && (!valid(previous) || equal(current, previous))) {
                throw new IllegalArgumentException("Invalid internal credential configuration");
            }
            this.current = current; this.previous = previous; this.previousValidUntil = previousValidUntil;
        }
        @Override public String toString() { return "Credentials[redacted]"; }
    }

    public static final class VerifiedOperation {
        private final String caller, callee, operation;
        private VerifiedOperation(String caller, String callee, String operation) {
            this.caller = caller; this.callee = callee; this.operation = operation;
        }
        public String caller() { return caller; }
        public String callee() { return callee; }
        public String operation() { return operation; }
    }

    public static final class Failure extends RuntimeException {
        private final int status;
        private final String code;
        private Failure(int status, String code) { super(code); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }

    private static Failure denied(int status, String code) { return new Failure(status, code); }
    private static boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= 4096
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0 && value.indexOf('\0') < 0
                && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.getBytes(StandardCharsets.UTF_8).length >= 32;
    }
    private static boolean equal(String left, String right) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            return MessageDigest.isEqual(hash.digest(left.getBytes(StandardCharsets.UTF_8)), hash.digest(right.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
