package com.projecthk5.authservice;

import com.projecthk5.authservice.persistence.Account;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntPredicate;
import static org.junit.jupiter.api.Assertions.*;

class EmailNormalizationTest {
    @Test
    void normalizationPreservesContractUnicodeSemantics() {
        assertEquals("i\u0307@example.invalid", Account.normalizeEmail("\u2003\u0130@EXAMPLE.INVALID\u2003"));
        assertEquals("\u03bf\u03c2@example.invalid", Account.normalizeEmail("\u039f\u03a3@EXAMPLE.INVALID"));
        assertEquals("\u03bf\u03c3a@example.invalid", Account.normalizeEmail("\u039f\u03a3A@EXAMPLE.INVALID"));
        assertEquals("\ud801\udc28@example.invalid", Account.normalizeEmail("\ud801\udc00@EXAMPLE.INVALID"));
        assertEquals("\u00df@example.invalid", Account.normalizeEmail("\u1e9e@EXAMPLE.INVALID"));
        assertNotEquals(Account.normalizeEmail("\u00df@example.invalid"), Account.normalizeEmail("ss@example.invalid"));
        assertNotEquals(Account.normalizeEmail("\u00e9@example.invalid"), Account.normalizeEmail("e\u0301@example.invalid"));
        assertEquals("\u00a0x@example.invalid\u00a0", Account.normalizeEmail("\u00a0X@EXAMPLE.INVALID\u00a0"));
    }

    @Test
    void migrationConstraintMatchesEveryJava21ScalarAndStripCharacter() throws Exception {
        assertEquals(21, Runtime.version().feature(), "Review the pinned Unicode set before upgrading Java");
        String migration;
        try (var source = getClass().getResourceAsStream("/db/migration/V2__enforce_java21_email_canonical_form.sql")) {
            migration = new String(Objects.requireNonNull(source).readAllBytes(), StandardCharsets.UTF_8);
        }
        String changing = ranges(cp -> {
            var value = new String(Character.toChars(cp));
            var lower = value.toLowerCase(Locale.ROOT);
            assertEquals(lower, lower.toLowerCase(Locale.ROOT));
            return !value.equals(lower);
        });
        assertTrue(migration.contains("email COLLATE \"C\" !~ U&'[" + changing + "]'"));
        var whitespace = new StringBuilder();
        for (int cp = 1; cp <= Character.MAX_CODE_POINT; cp++) {
            if (Character.isWhitespace(cp)) { whitespace.append(escape(cp)); }
        }
        assertTrue(migration.contains("btrim(email, U&'" + whitespace + "')"));
    }

    private static String ranges(IntPredicate included) {
        var result = new StringBuilder();
        for (int cp = 1; cp <= Character.MAX_CODE_POINT; cp++) {
            if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE || !included.test(cp)) { continue; }
            int first = cp;
            while (cp < Character.MAX_CODE_POINT && included.test(cp + 1)) { cp++; }
            result.append(escape(first));
            if (cp != first) { result.append('-').append(escape(cp)); }
        }
        return result.toString();
    }

    private static String escape(int cp) {
        return cp <= 0xffff ? String.format(Locale.ROOT, "\\%04X", cp) : String.format(Locale.ROOT, "\\+%06X", cp);
    }
}
