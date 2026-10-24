package com.projecthk5.authservice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.projecthk5.authservice.persistence.AccountRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Configuration-only tests: no public endpoint, filter or signing secret is activated. */
class AuthBootstrapConfigurationTest {
    private static String fixtureKey(int value) {
        byte[] raw = new byte[32];
        Arrays.fill(raw, (byte) value);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static Map<String, String> valid() {
        var env = new HashMap<String, String>();
        env.put(AuthBootstrapConfiguration.ISSUER, "https://identity.example.invalid");
        env.put(AuthBootstrapConfiguration.AUDIENCE, "smart-ecommerce-api");
        env.put(AuthBootstrapConfiguration.CURRENT_KID, "new-kid");
        env.put(AuthBootstrapConfiguration.CURRENT_KEY, fixtureKey(17));
        env.put(AuthBootstrapConfiguration.ACTIVE_STATUS, "ACTIVE");
        return env;
    }

    @Test
    void noImplicitIssuerKeyOrActiveStatusCanInitialize() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthBootstrapConfiguration.codecFromEnvironment(Map.of(), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> AuthBootstrapConfiguration.authorityFromEnvironment(mock(AccountRepository.class), Map.of()));
    }

    @Test
    void explicitCurrentAndPreviousKeysAreAcceptedWithoutFallback() {
        var env = valid();
        assertNotNull(AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
        assertNotNull(AuthBootstrapConfiguration.authorityFromEnvironment(mock(AccountRepository.class), env));
        env.put(AuthBootstrapConfiguration.PREVIOUS_KID, "old-kid");
        env.put(AuthBootstrapConfiguration.PREVIOUS_KEY, fixtureKey(18));
        assertNotNull(AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
    }

    @Test
    void invalidOrPartialRotationCannotBeEnabled() {
        var env = valid();
        env.put(AuthBootstrapConfiguration.PREVIOUS_KID, "old-kid");
        assertThrows(IllegalArgumentException.class,
                () -> AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
        env.put(AuthBootstrapConfiguration.PREVIOUS_KEY, fixtureKey(17));
        assertThrows(IllegalArgumentException.class,
                () -> AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
        env.put(AuthBootstrapConfiguration.PREVIOUS_KEY, fixtureKey(18));
        env.put(AuthBootstrapConfiguration.PREVIOUS_KID, "new-kid");
        assertThrows(IllegalArgumentException.class,
                () -> AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
    }

    @Test
    void weakMalformedOrPaddedKeysFailClosedWithoutSecretDisclosure() {
        var env = valid();
        for (String secret : new String[]{"not base64!", Base64.getEncoder().encodeToString(new byte[31]),
                " " + fixtureKey(17), "", fixtureKey(17).replace("=", "")}) {
            env.put(AuthBootstrapConfiguration.CURRENT_KEY, secret);
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> AuthBootstrapConfiguration.codecFromEnvironment(env, Clock.systemUTC()));
            assertEquals("Invalid Auth bootstrap configuration", failure.getMessage());
            if (!secret.isEmpty()) assertFalse(failure.getMessage().contains(secret));
        }
    }

    @Test
    void activeStatusMustBeExplicitExactAndBounded() {
        var env = valid();
        var accounts = mock(AccountRepository.class);
        for (String status : new String[]{"", " ACTIVE", "ACTIVE ", "x".repeat(33)}) {
            env.put(AuthBootstrapConfiguration.ACTIVE_STATUS, status);
            assertThrows(IllegalArgumentException.class,
                    () -> AuthBootstrapConfiguration.authorityFromEnvironment(accounts, env));
        }
    }
}
