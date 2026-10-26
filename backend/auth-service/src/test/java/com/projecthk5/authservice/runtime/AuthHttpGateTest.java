package com.projecthk5.authservice.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthHttpGateTest {
    private static AuthHttpGate withOrigins(String origins) {
        ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
        when(env.getSystemEnvironment()).thenReturn(Map.of("HK5_AUTH_ALLOWED_ORIGINS", origins));
        return new AuthHttpGate(env);
    }
    private static MockHttpServletRequest valid() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.17");
        request.addHeader("Origin", "https://trusted.example.invalid");
        request.addHeader("X-CSRF-Protection", "1");
        return request;
    }
    @Test
    void requiresExactOriginAndCsrfHeader() {
        AuthHttpGate gate = withOrigins("https://trusted.example.invalid");
        assertDoesNotThrow(() -> gate.check(valid()));
        var originMissing = valid();
        originMissing.removeHeader("Origin");
        assertEquals("AUTH_CSRF_REJECTED", assertThrows(AuthHttpGate.HttpFailure.class,
                () -> gate.check(originMissing)).code());
        var csrfMissing = valid();
        csrfMissing.removeHeader("X-CSRF-Protection");
        assertEquals(403, assertThrows(AuthHttpGate.HttpFailure.class,
                () -> gate.check(csrfMissing)).status());
        var forged = valid();
        forged.removeHeader("Origin");
        forged.addHeader("Origin", "https://evil.example.invalid");
        assertThrows(AuthHttpGate.HttpFailure.class, () -> gate.check(forged));
    }

    @Test
    void deniesInvalidOriginConfigAndLocalRateOverrun() {
        assertThrows(IllegalArgumentException.class, () -> withOrigins("*"));
        assertThrows(IllegalArgumentException.class,
                () -> withOrigins("https://trusted.example.invalid/path"));
        assertThrows(IllegalArgumentException.class,
                () -> withOrigins("https://trusted.example.invalid,https://trusted.example.invalid"));
        AuthHttpGate gate = withOrigins("https://trusted.example.invalid");
        for (int i = 0; i < 20; i++) gate.check(valid());
        assertEquals(429, assertThrows(AuthHttpGate.HttpFailure.class, () -> gate.check(valid())).status());
    }
}
