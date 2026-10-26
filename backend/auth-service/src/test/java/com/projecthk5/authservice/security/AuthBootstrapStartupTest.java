package com.projecthk5.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.SignedJWT;
import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;

/** Spring startup boundary only; fixture environment never changes the host environment. */
class AuthBootstrapStartupTest {
    private static Map<String, String> validEnvironment() {
        return new HashMap<>(Map.of(
                AuthBootstrapConfiguration.ISSUER, "https://identity.example.invalid",
                AuthBootstrapConfiguration.AUDIENCE, "smart-ecommerce-api",
                AuthBootstrapConfiguration.CURRENT_KID, "fixture-current",
                AuthBootstrapConfiguration.CURRENT_KEY, Base64.getEncoder().encodeToString(new byte[32]),
                AuthBootstrapConfiguration.ACTIVE_STATUS, "QA_EXPLICIT_ACTIVE"));
    }

    private static ApplicationContextRunner context(Map<String, String> variables,
                                                     Map<String, String> properties) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var environment = new MockEnvironment() {
                        @Override
                        public Map<String, Object> getSystemEnvironment() {
                            return new HashMap<>(variables);
                        }
                    };
                    properties.forEach(environment::setProperty);
                    context.setEnvironment(environment);
                })
                .withUserConfiguration(AuthBootstrapConfiguration.class)
                .withBean(AccountRepository.class, () -> mock(AccountRepository.class));
    }

    @Test
    void explicitRuntimeEnvironmentCreatesBothBeans() {
        context(validEnvironment(), Map.of("hk5.auth.bootstrap.enabled", "true")).run(context ->
                assertThat(context).hasNotFailed()
                        .hasSingleBean(Hk5JwtCodec.class).hasSingleBean(CurrentAccountAuthority.class));
    }

    @Test
    void absentOptInCreatesNoSecurityBeansEvenWithCompleteEnvironment() {
        context(validEnvironment(), Map.of()).run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(Hk5JwtCodec.class).doesNotHaveBean(CurrentAccountAuthority.class));
        context(Map.of(), Map.of("hk5.auth.bootstrap.enabled", "false")).run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(Hk5JwtCodec.class)
                        .doesNotHaveBean(CurrentAccountAuthority.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {AuthBootstrapConfiguration.ISSUER, AuthBootstrapConfiguration.AUDIENCE,
            AuthBootstrapConfiguration.CURRENT_KID, AuthBootstrapConfiguration.CURRENT_KEY,
            AuthBootstrapConfiguration.ACTIVE_STATUS})
    void missingRuntimeSettingPreventsStartup(String setting) {
        var variables = validEnvironment();
        variables.remove(setting);
        assertStartupRejected(variables);
    }

    @Test
    void springPropertiesCannotSupplyMissingRuntimeSecrets() {
        var properties = validEnvironment();
        properties.put("hk5.auth.bootstrap.enabled", "true");
        context(Map.of(), properties).run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseMessage("Invalid Auth bootstrap configuration"));
    }

    @Test
    void nonStringEnvironmentValuesAreRejectedWithoutCoercion() {
        assertThrows(IllegalArgumentException.class, () -> AuthBootstrapConfiguration.codecFromEnvironment(
                Map.of(AuthBootstrapConfiguration.ISSUER, 42), Clock.systemUTC()));
    }

    @ParameterizedTest
    @CsvSource({"HK5_AUTH_JWT_ISSUER, ''", "HK5_AUTH_JWT_AUDIENCE, ' padded '",
            "HK5_AUTH_JWT_CURRENT_KID, invalid/kid", "HK5_AUTH_JWT_CURRENT_KEY_B64, not-base64!",
            "HK5_AUTH_JWT_CURRENT_KEY_B64, YQ==", "HK5_AUTH_ACTIVE_STATUS, ' ACTIVE '",
            "HK5_AUTH_ACTIVE_STATUS, aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidRuntimeSettingPreventsStartup(String setting, String value) {
        var variables = validEnvironment();
        variables.put(setting, value);
        assertStartupRejected(variables);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kid-only", "key-only", "same-kid", "same-key", "malformed-previous-key"})
    void invalidRotationPreventsStartup(String kind) {
        var variables = validEnvironment();
        byte[] previousKey = new byte[32];
        previousKey[0] = 1;
        variables.put(AuthBootstrapConfiguration.PREVIOUS_KID, "fixture-previous");
        variables.put(AuthBootstrapConfiguration.PREVIOUS_KEY, Base64.getEncoder().encodeToString(previousKey));
        switch (kind) {
            case "kid-only" -> variables.remove(AuthBootstrapConfiguration.PREVIOUS_KEY);
            case "key-only" -> variables.remove(AuthBootstrapConfiguration.PREVIOUS_KID);
            case "same-kid" -> variables.put(AuthBootstrapConfiguration.PREVIOUS_KID,
                    variables.get(AuthBootstrapConfiguration.CURRENT_KID));
            case "same-key" -> variables.put(AuthBootstrapConfiguration.PREVIOUS_KEY,
                    variables.get(AuthBootstrapConfiguration.CURRENT_KEY));
            case "malformed-previous-key" -> variables.put(AuthBootstrapConfiguration.PREVIOUS_KEY, "invalid!");
            default -> throw new AssertionError(kind);
        }
        assertStartupRejected(variables);
    }

    private static void assertStartupRejected(Map<String, String> variables) {
        context(variables, Map.of("hk5.auth.bootstrap.enabled", "true")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            var trace = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(trace));
            if (variables.containsKey(AuthBootstrapConfiguration.CURRENT_KEY)) {
                assertThat(trace.toString()).doesNotContain(variables.get(AuthBootstrapConfiguration.CURRENT_KEY));
            }
            if (variables.containsKey(AuthBootstrapConfiguration.PREVIOUS_KEY)) {
                assertThat(trace.toString()).doesNotContain(variables.get(AuthBootstrapConfiguration.PREVIOUS_KEY));
            }
        });
    }

    @Test
    void runtimeKeyringAcceptsOldAndNewSignsOnlyCurrentAndRetiresPrevious() {
        var oldEnvironment = validEnvironment();
        oldEnvironment.put(AuthBootstrapConfiguration.CURRENT_KID, "fixture-old");
        byte[] oldKey = new byte[32];
        oldKey[0] = 1;
        oldEnvironment.put(AuthBootstrapConfiguration.CURRENT_KEY, Base64.getEncoder().encodeToString(oldKey));
        var rotated = validEnvironment();
        rotated.put(AuthBootstrapConfiguration.PREVIOUS_KID, oldEnvironment.get(AuthBootstrapConfiguration.CURRENT_KID));
        rotated.put(AuthBootstrapConfiguration.PREVIOUS_KEY, oldEnvironment.get(AuthBootstrapConfiguration.CURRENT_KEY));
        UUID id = UUID.fromString("11111111-2222-4333-8444-555555555555");
        var accounts = mock(AccountRepository.class);
        when(accounts.findById(id)).thenReturn(Optional.of(new Account(id, "fixture@example.invalid",
                "fixture-not-a-credential", "QA_EXPLICIT_ACTIVE", Set.of(Account.Role.CUSTOMER), Instant.now())));
        var current = AuthBootstrapConfiguration.authorityFromEnvironment(accounts, rotated).requireCurrentForIssue(id);
        String oldToken = AuthBootstrapConfiguration.codecFromEnvironment(oldEnvironment, Clock.systemUTC()).issue(current);
        context(rotated, Map.of("hk5.auth.bootstrap.enabled", "true")).run(context -> {
            assertThat(context).hasNotFailed();
            var codec = context.getBean(Hk5JwtCodec.class);
            assertEquals(id.toString(), codec.verify(oldToken).subject());
            String newToken = codec.issue(current);
            assertEquals("fixture-current", SignedJWT.parse(newToken).getHeader().getKeyID());
            assertEquals(id.toString(), codec.verify(newToken).subject());
            var retired = AuthBootstrapConfiguration.codecFromEnvironment(validEnvironment(), Clock.systemUTC());
            assertThrows(JwtException.class, () -> retired.verify(oldToken));
            assertEquals(id.toString(), retired.verify(newToken).subject());
        });
    }
}
