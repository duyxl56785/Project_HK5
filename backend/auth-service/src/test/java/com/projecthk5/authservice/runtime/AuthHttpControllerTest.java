package com.projecthk5.authservice.runtime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

import com.projecthk5.authservice.persistence.Account;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthHttpControllerTest {
    private AuthSessionService service;
    private AuthHttpGate gate;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AuthSessionService.class);
        gate = mock(AuthHttpGate.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthHttpController(service, gate)).build();
    }

    @Test
    void registerEmits201WithoutCredentialsOrAutoLogin() throws Exception {
        when(service.register(anyString(), anyString())).thenReturn(
                new AuthSessionService.AccountView("account-id", "a@example.invalid",
                        Set.of(Account.Role.CUSTOMER)));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "https://trusted.example.invalid")
                .header("X-CSRF-Protection", "1")
                .content("{\"email\":\"A@example.invalid\",\"password\":\"good-password-2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/auth/me"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value("account-id"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(service).register("A@example.invalid", "good-password-2026");
    }

    @Test
    void rejectsUnknownRegisterFieldsAndMalformedBodyBeforePersistence() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.invalid\",\"password\":\"hello-world-2026\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void loginReturnsRefreshOnlyInSecureCookieAndNotJson() throws Exception {
        when(service.login(anyString(), anyString())).thenReturn(
                new AuthSessionService.SessionView("signed-access-only", "Bearer", 900,
                        new AuthSessionService.AccountView("id", "a@example.invalid",
                                Set.of(Account.Role.CUSTOMER)),
                        "opaque-refresh-secret", Instant.now().plusSeconds(3600)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.invalid\",\"password\":\"good-password-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(jsonPath("$.accessToken").value("signed-access-only"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void refreshWithoutCookieRejectsAndClearsCookie() throws Exception {
        when(service.refresh(null)).thenReturn(AuthSessionService.RefreshResult.invalid());
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void logoutIs204AndNeverReturnsAnAccessToken() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(content().string(""));
        verify(service).logout(null);
    }

    @Test
    void meRejectsMissingMalformedAndTamperedBearer() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_REQUIRED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_REQUEST_INVALID"));
        mvc.perform(get("/api/auth/me?access_token=fake").header("Authorization", "Bearer fake"))
                .andExpect(status().isBadRequest());
        when(service.me("bad-signed-token")).thenThrow(new JwtException("Invalid access token"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer bad-signed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_INVALID"));
    }

    @Test
    void meReturnsCurrentAccountOnlyAfterServiceVerification() throws Exception {
        when(service.me("verified-token")).thenReturn(new AuthSessionService.AccountView(
                "account-id", "a@example.invalid", Set.of(Account.Role.CUSTOMER)));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer verified-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("account-id"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void deniedOriginOrThrottleNeverRunsBusinessMutation() throws Exception {
        doThrow(new AuthHttpGate.HttpFailure(403, "AUTH_CSRF_REJECTED")).when(gate).check(any());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.invalid\",\"password\":\"whatever\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_REJECTED"));
        verifyNoInteractions(service);
    }
}
