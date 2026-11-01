package com.projecthk5.authservice.api;

import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.service.AccessTokenIssuer;
import com.projecthk5.authservice.service.AuthService;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String COOKIE = "refresh_token";
    private final AuthService auth;
    private final LoginThrottle throttle;
    private final Set<String> allowedOrigins;

    public AuthController(AuthService auth, LoginThrottle throttle, @Value("${ALLOWED_ORIGINS:}") String origins) {
        this.auth = auth;
        this.throttle = throttle;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(min = 12, max = 128) String password) {}
    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(min = 1, max = 128) String password) {}
    public record AccountResponse(String userId, String email, List<String> roles) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.getId().toString(), a.getEmail(), a.getRoles().stream().map(Enum::name).sorted().toList());
        }
    }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}

    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(HttpServletRequest http, @Valid @RequestBody RegisterRequest req) {
        requireBrowserOrigin(http);
        throttle.check("register:" + http.getRemoteAddr());
        Account account = auth.register(req.email(), req.password());
        return noStore(ResponseEntity.created(URI.create("/api/auth/me"))).body(AccountResponse.of(account));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(HttpServletRequest http, @Valid @RequestBody LoginRequest req) {
        requireBrowserOrigin(http);
        String key = "login:" + Account.normalizeEmail(req.email());
        throttle.check(key);
        AuthService.Session session;
        try {
            session = auth.login(req.email(), req.password());
        } catch (ApiException failed) {
            throttle.recordFailure(key);
            throw failed;
        }
        throttle.reset(key);
        return withSession(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest http, @CookieValue(name = COOKIE, required = false) String token) {
        requireBrowserOrigin(http);
        AuthService.RefreshResult result = auth.refresh(token);
        if (result.rejected()) {
            return noStore(ResponseEntity.status(HttpStatus.UNAUTHORIZED))
                    .header(HttpHeaders.SET_COOKIE, clearCookie())
                    .body(java.util.Map.of("code", "AUTH_REFRESH_INVALID", "message", "Session expired, please sign in again"));
        }
        return withSession(result.session());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, @CookieValue(name = COOKIE, required = false) String token) {
        requireBrowserOrigin(http);
        auth.logout(token);
        return noStore(ResponseEntity.noContent()).header(HttpHeaders.SET_COOKIE, clearCookie()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> me() {
        return noStore(ResponseEntity.ok()).body(AccountResponse.of(auth.current(Actor.current().id())));
    }

    private ResponseEntity<TokenResponse> withSession(AuthService.Session s) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), s.refreshExpiresAt()).getSeconds());
        String cookie = ResponseCookie.from(COOKIE, s.refreshToken()).httpOnly(true).secure(true).sameSite("Lax")
                .path("/api/auth").maxAge(maxAge).build().toString();
        return noStore(ResponseEntity.ok()).header(HttpHeaders.SET_COOKIE, cookie)
                .body(new TokenResponse(s.accessToken(), "Bearer", AccessTokenIssuer.TTL_SECONDS, AccountResponse.of(s.account())));
    }

    private static String clearCookie() {
        return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(true).sameSite("Lax").path("/api/auth").maxAge(0).build().toString();
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache");
    }

    private static ResponseEntity.HeadersBuilder<?> noStore(ResponseEntity.HeadersBuilder<?> builder) {
        return builder.header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache");
    }

    /** OD-05: cookie-bearing auth calls need an allowlisted Origin plus X-CSRF-Protection: 1. */
    private void requireBrowserOrigin(HttpServletRequest http) {
        String origin = http.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin) || !"1".equals(http.getHeader("X-CSRF-Protection")))
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_CSRF_REJECTED", "Request origin rejected");
    }
}
