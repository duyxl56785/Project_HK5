package com.projecthk5.authservice.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.projecthk5.authservice.security.CurrentAccountAuthority;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opt-in, UNRELEASED Auth HTTP candidate: off without hk5.auth.http.enabled=true.
 * A servlet principal filter, distributed throttle, proxy boundary and Gateway E2E
 * remain explicit review gates. Never enable this candidate in production.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "hk5.auth.http.enabled", havingValue = "true")
public final class AuthHttpController {
    private static final String REFRESH = "refresh_token";
    private final AuthSessionService service;
    private final AuthHttpGate gate;

    public AuthHttpController(AuthSessionService service, AuthHttpGate gate) {
        this.service = service;
        this.gate = gate;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
        gate.check(request);
        requireFields(body, Set.of("email", "password"));
        var account = service.register(body.get("email").textValue(), body.get("password").textValue());
        return privateResponse(ResponseEntity.status(201)
                .header(HttpHeaders.LOCATION, "/api/auth/me").body(accountJson(account)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
        gate.check(request);
        requireFields(body, Set.of("email", "password"));
        return sessionResponse(service.login(body.get("email").textValue(), body.get("password").textValue()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request,
            @RequestBody(required = false) String body,
            @CookieValue(name = REFRESH, required = false) String rawToken) {
        gate.check(request);
        if (body != null && !body.isEmpty()) throw new AuthHttpGate.HttpFailure(400, "VALIDATION_FAILED");
        var result = service.refresh(rawToken);
        if (!result.accepted()) {
            return privateResponse(ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearCookie())
                    .body(Map.of("code", "AUTH_REFRESH_INVALID", "message", "Invalid refresh session")));
        }
        return sessionResponse(result.session());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
            @RequestBody(required = false) String body,
            @CookieValue(name = REFRESH, required = false) String rawToken) {
        gate.check(request);
        if (body != null && !body.isEmpty()) throw new AuthHttpGate.HttpFailure(400, "VALIDATION_FAILED");
        service.logout(rawToken);
        return privateResponse(ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie()).build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        if (request.getParameter("access_token") != null) {
            throw new AuthHttpGate.HttpFailure(400, "AUTH_REQUEST_INVALID");
        }
        var values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (values.isEmpty()) throw new AuthHttpGate.HttpFailure(401, "AUTH_ACCESS_REQUIRED");
        if (values.size() != 1) throw new AuthHttpGate.HttpFailure(400, "AUTH_REQUEST_INVALID");
        String bearer = values.get(0);
        if (!bearer.startsWith("Bearer ") || bearer.length() < 9
                || bearer.substring(7).contains(" ") || bearer.length() > 8200) {
            throw new AuthHttpGate.HttpFailure(400, "AUTH_REQUEST_INVALID");
        }
        return privateResponse(ResponseEntity.ok(accountJson(service.me(bearer.substring(7)))));
    }

    private ResponseEntity<?> sessionResponse(AuthSessionService.SessionView session) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), session.familyExpiresAt()).getSeconds());
        String cookie = ResponseCookie.from(REFRESH, session.refreshToken()).httpOnly(true)
                .secure(true).sameSite("Lax").path("/api/auth")
                .maxAge(Math.min(maxAge, AuthSessionService.REFRESH_FAMILY_SECONDS)).build().toString();
        return privateResponse(ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(Map.of(
                "accessToken", session.accessToken(), "tokenType", session.tokenType(),
                "expiresIn", session.expiresIn(), "account", accountJson(session.account()))));
    }

    private static String clearCookie() {
        return ResponseCookie.from(REFRESH, "").httpOnly(true).secure(true)
                .sameSite("Lax").path("/api/auth").maxAge(0).build().toString();
    }

    private static Map<String, Object> accountJson(AuthSessionService.AccountView value) {
        return Map.of("userId", value.userId(), "email", value.email(),
                "roles", value.roles().stream().map(Enum::name).sorted().toList());
    }

    private static void requireFields(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject() || body.size() != fields.size()) {
            throw new AuthHttpGate.HttpFailure(400, "VALIDATION_FAILED");
        }
        var names = body.fieldNames();
        while (names.hasNext()) {
            if (!fields.contains(names.next())) throw new AuthHttpGate.HttpFailure(400, "VALIDATION_FAILED");
        }
        for (var name : fields) {
            if (!body.has(name) || !body.get(name).isTextual()) {
                throw new AuthHttpGate.HttpFailure(400, "VALIDATION_FAILED");
            }
        }
    }

    private static ResponseEntity<?> privateResponse(ResponseEntity<?> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response.getBody());
    }

    @ExceptionHandler({AuthHttpGate.HttpFailure.class, AuthSessionService.AuthFailure.class,
            CurrentAccountAuthority.DecisionFailure.class, JwtException.class,
            DataAccessException.class, PersistenceException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<?> error(Exception exception) {
        int status = 503;
        String code = "AUTHORIZATION_DEPENDENCY_UNAVAILABLE";
        if (exception instanceof AuthHttpGate.HttpFailure error) {
            status = error.status(); code = error.code();
        } else if (exception instanceof AuthSessionService.AuthFailure error) {
            status = error.httpStatus(); code = error.code();
        } else if (exception instanceof CurrentAccountAuthority.DecisionFailure error) {
            status = error.httpStatus(); code = error.code();
        } else if (exception instanceof JwtException) {
            status = 401; code = "AUTH_ACCESS_INVALID";
        } else if (exception instanceof HttpMessageNotReadableException) {
            status = 400; code = "VALIDATION_FAILED";
        }
        var response = ResponseEntity.status(status);
        if (status == 429) response.header(HttpHeaders.RETRY_AFTER, "60");
        if ("AUTH_ACCESS_REQUIRED".equals(code) || "AUTH_ACCESS_INVALID".equals(code)) {
            response.header(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer realm=\"smart-ecommerce\"" +
                            ("AUTH_ACCESS_INVALID".equals(code) ? ", error=\"invalid_token\"" : ""));
        }
        return privateResponse(response.body(Map.of(
                "code", code, "message", "Authentication request rejected")));
    }
}
