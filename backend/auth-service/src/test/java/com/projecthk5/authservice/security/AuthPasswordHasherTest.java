package com.projecthk5.authservice.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthPasswordHasherTest {
    private final AuthPasswordHasher hasher = new AuthPasswordHasher();

    @Test
    void trailingNulCannotAuthenticateAsTheAcceptedPassword() {
        String password = "abcdefghijkl";
        String encoded = hasher.hashForRegistration(password);
        assertTrue(hasher.verifyForLogin(password, encoded));
        assertFalse(hasher.verifyForLogin(password + '\0', encoded));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000abcdefghijkl", "abcdef\u0000ghijkl", "abcdefghijkl\u0000"})
    void nulAtAnyPositionIsRejectedForRegistrationAndLogin(String password) {
        String encoded = hasher.hashForRegistration("abcdefghijkl");
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> hasher.hashForRegistration(password)),
                () -> assertFalse(hasher.verifyForLogin(password, encoded)));
    }

    @Test
    void acceptedRegistrationPasswordHashesAndVerifiesWithoutPersistingRawValue() {
        String password = "Correct-Horse-Battery-2026!";
        String encoded = hasher.hashForRegistration(password);
        assertTrue(encoded.startsWith("pbkdf2-sha256-v1$600000$"));
        assertEquals(4, encoded.split("\\$", -1).length);
        assertFalse(encoded.contains(password));
        assertTrue(hasher.verifyForLogin(password, encoded));
        assertFalse(hasher.verifyForLogin("Incorrect-Horse-Battery-2026!", encoded));
        assertNotEquals(encoded, hasher.hashForRegistration(password), "A new salt is mandatory");
    }

    @Test
    void multiByteAndLongPasswordNeverSilentlyTruncateAtBcrypt72Bytes() {
        // Java's 128-code-unit ceiling still permits passwords much longer than 72 UTF-8 bytes.
        String password = "🔐".repeat(30) + "long_password🔐".repeat(4);
        assertTrue(password.length() <= 128);
        String encoded = hasher.hashForRegistration(password);
        assertTrue(hasher.verifyForLogin(password, encoded));
        assertFalse(hasher.verifyForLogin(password.substring(0, password.length() - 1) + "X", encoded));
        assertFalse(hasher.verifyForLogin(password.substring(0, 60), encoded));
    }

    @Test
    void full128CodeUnitPasswordWorksAndExcessInputIsRejectedRatherThanTruncated() {
        String password = "🔐".repeat(64);
        String encoded = hasher.hashForRegistration(password);
        assertTrue(hasher.verifyForLogin(password, encoded));
        assertFalse(hasher.verifyForLogin(password.substring(0, 126), encoded));
        assertFalse(hasher.verifyForLogin(password + "x", encoded));
        assertThrows(IllegalArgumentException.class, () -> hasher.hashForRegistration(password + "x"));
    }

    @Test
    void registrationRejectsWrongLengthOrMalformedUnicodeWithoutNormalization() {
        for (String password : new String[]{"", "12345678901", "x".repeat(129),
                "abcdefghijk\uD800", "abcdefghijk\uDC00", "abcdefghijk\uD800x"}) {
            assertThrows(IllegalArgumentException.class, () -> hasher.hashForRegistration(password));
        }
        assertThrows(IllegalArgumentException.class, () -> hasher.hashForRegistration(null));
        String spaced = "  ABC😀def 123 ";
        String encoded = hasher.hashForRegistration(spaced);
        assertTrue(hasher.verifyForLogin(spaced, encoded));
        assertFalse(hasher.verifyForLogin(spaced.strip(), encoded));
    }

    @Test
    void malformedStoredRecordsCannotSelectWeakWorkFactorOrDifferentAlgorithm() {
        String encoded = hasher.hashForRegistration("a-valid-password");
        String[] parts = encoded.split("\\$", -1);
        assertFalse(hasher.verifyForLogin("a-valid-password", "bcrypt$12$hash"));
        assertFalse(hasher.verifyForLogin("a-valid-password", encoded.replace("$600000$", "$1$")));
        assertFalse(hasher.verifyForLogin("a-valid-password", encoded.replace("pbkdf2-sha256-v1", "pbkdf2-sha256-v0")));
        assertFalse(hasher.verifyForLogin("a-valid-password", encoded + "$extra"));
        assertFalse(hasher.verifyForLogin("a-valid-password", encoded + " "));
        assertFalse(hasher.verifyForLogin("a-valid-password", parts[0] + "$" + parts[1] + "$"
                + Base64.getEncoder().encodeToString(new byte[1]) + "$" + parts[3]));
        assertFalse(hasher.verifyForLogin("a-valid-password", null));
        assertFalse(hasher.verifyForLogin(null, encoded));
        assertFalse(hasher.verifyForLogin("x".repeat(129), encoded));
        assertFalse(hasher.verifyForLogin("\uD800", encoded));
        assertFalse(hasher.verifyForLogin("\uDC00", encoded));
        assertFalse(hasher.verifyForLogin("abcdefghijk\uD800x", encoded));
    }

    @Test
    void tamperedSaltOrDigestCannotAuthenticate() {
        String password = "a-valid-password";
        String[] parts = hasher.hashForRegistration(password).split("\\$", -1);
        for (int field : new int[]{2, 3}) {
            String[] tampered = parts.clone();
            byte[] bytes = Base64.getDecoder().decode(parts[field]);
            bytes[0] ^= 1;
            tampered[field] = Base64.getEncoder().encodeToString(bytes);
            assertFalse(hasher.verifyForLogin(password, String.join("$", tampered)));
        }
    }

    @Test
    void loginSupportsLegacyShortLengthOnlyWhenMatchingAnExistingHash() {
        String encoded = hasher.hashForRegistration("the-long-original");
        assertFalse(hasher.verifyForLogin("short", encoded));
        assertFalse(hasher.verifyForLogin("", encoded));
    }
}
