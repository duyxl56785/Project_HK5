package com.projecthk5.authservice.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;

/**
 * Standalone credential-hash primitive for a later, reviewed Auth API.
 * Deliberately not a Spring bean/controller. Never store or log raw passwords.
 *
 * PBKDF2-HMAC-SHA256 avoids bcrypt's 72-byte input truncation. The format is
 * versioned to permit an explicit migration rather than a silent downgrade.
 */
public final class AuthPasswordHasher {
    private static final String VERSION = "pbkdf2-sha256-v1";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private final SecureRandom random = new SecureRandom();

    /**
     * A register password is 12-128 UTF-16 code units, excluding NUL. Valid
     * Unicode is passed unchanged, with no trim, normalization or case folding.
     */
    public String hashForRegistration(String password) {
        if (!validPassword(password, 12)) {
            throw new IllegalArgumentException("Invalid registration password");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = derive(password, salt);
        try {
            return VERSION + "$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(digest);
        } finally {
            java.util.Arrays.fill(salt, (byte) 0);
            java.util.Arrays.fill(digest, (byte) 0);
        }
    }

    /**
     * Login accepts 1-128 characters for any valid legacy-length password.
     * Unknown/malformed algorithms, parameters and encodings are never
     * upgraded or accepted as a successful credential verification.
     */
    public boolean verifyForLogin(String password, String encoded) {
        if (!validPassword(password, 1) || encoded == null || encoded.length() > 256) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])
                || !Integer.toString(ITERATIONS).equals(parts[1])) return false;
        byte[] salt;
        byte[] expected;
        try {
            salt = decodeCanonical(parts[2], SALT_BYTES);
            expected = decodeCanonical(parts[3], HASH_BYTES);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        try {
            byte[] actual = derive(password, salt);
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                java.util.Arrays.fill(actual, (byte) 0);
            }
        } finally {
            java.util.Arrays.fill(salt, (byte) 0);
            java.util.Arrays.fill(expected, (byte) 0);
        }
    }

    private static byte[] decodeCanonical(String text, int length) {
        byte[] bytes = Base64.getDecoder().decode(text);
        if (bytes.length != length || !Base64.getEncoder().encodeToString(bytes).equals(text)) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new IllegalArgumentException("Invalid password record");
        }
        return bytes;
    }

    private static byte[] derive(String password, byte[] salt) {
        // JDK21 SunJCE PBKDF2 accepts the full char[] (no bcrypt byte ceiling).
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException unavailable) {
            throw new IllegalStateException("Password hashing unavailable");
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private static boolean validPassword(String password, int minimum) {
        if (password == null || password.length() < minimum || password.length() > 128) return false;
        // Fail closed rather than allowing a provider to replace malformed UTF-16.
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            // HMAC zero-padding can make distinct trailing-NUL passwords equivalent.
            if (c == '\0') return false;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= password.length() || !Character.isLowSurrogate(password.charAt(i + 1)))
                    return false;
                i++;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
