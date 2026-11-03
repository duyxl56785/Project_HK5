package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R06: a Review comment holds at most 1000 characters, counted like Java String.length and the frontend. */
class ReviewCommentLimitTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean valid(String comment) {
        return VALIDATOR.validate(new ModerationController.ReviewRequest(UUID.randomUUID(), UUID.randomUUID(), 5, comment)).isEmpty();
    }

    @Test
    void acceptsExactly1000AndRejects1001() {
        assertTrue(valid("a".repeat(1000)));
        assertFalse(valid("a".repeat(1001)));
        assertTrue(valid("ạ".repeat(1000)));
        assertTrue(valid(null));
    }

    @Test
    void countsEmojiAsTwoUtf16UnitsLikeTheDatabaseCheck() {
        String emoji = "😀";
        assertTrue(valid(emoji.repeat(500)));
        assertFalse(valid(emoji.repeat(500) + "a"));
    }
}
