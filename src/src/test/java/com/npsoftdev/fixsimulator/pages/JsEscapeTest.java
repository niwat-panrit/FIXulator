package com.npsoftdev.fixsimulator.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsEscapeTest {

    // ── Basic passthrough ─────────────────────────────────────────────────────

    @Test
    void plainAlphanumeric_unchanged() {
        assertEquals("AAPL", JsEscape.forSingleQuotedLiteral("AAPL"));
    }

    @Test
    void null_returnsEmptyString() {
        assertEquals("", JsEscape.forSingleQuotedLiteral(null));
    }

    @Test
    void empty_returnsEmpty() {
        assertEquals("", JsEscape.forSingleQuotedLiteral(""));
    }

    // ── Single quote escaping ─────────────────────────────────────────────────

    @Test
    void singleQuote_isEscaped() {
        assertEquals("\\'", JsEscape.forSingleQuotedLiteral("'"));
    }

    @Test
    void nameWithSingleQuote_escapedForSafeEmbedding() {
        // "O'Brien" → "O\'Brien"
        assertEquals("O\\'Brien", JsEscape.forSingleQuotedLiteral("O'Brien"));
    }

    // ── Backslash escaping ────────────────────────────────────────────────────

    @Test
    void backslash_isDoubled() {
        assertEquals("\\\\", JsEscape.forSingleQuotedLiteral("\\"));
    }

    @Test
    void backslashBeforeQuote_bothEscapedCorrectly() {
        // "\'" in input → "\\\\\\'" in output (backslash doubled, then quote escaped)
        assertEquals("\\\\\\'", JsEscape.forSingleQuotedLiteral("\\'"));
    }

    // ── Newline / carriage-return escaping ────────────────────────────────────

    @Test
    void newline_isEscaped() {
        assertEquals("line1\\nline2", JsEscape.forSingleQuotedLiteral("line1\nline2"));
    }

    @Test
    void carriageReturn_isEscaped() {
        assertEquals("line1\\rline2", JsEscape.forSingleQuotedLiteral("line1\rline2"));
    }

    // ── Unicode line-terminator escaping (security-critical) ─────────────────

    @Test
    void unicodeLineSeparator_u2028_isEscaped() {
        // U+2028 is a JS line terminator that breaks single-quoted string literals
        String input    = "before\u2028after";
        String expected = "before\\u2028after";   // six literal ASCII chars, not the actual char
        assertEquals(expected, JsEscape.forSingleQuotedLiteral(input));
    }

    @Test
    void unicodeParagraphSeparator_u2029_isEscaped() {
        String input    = "before\u2029after";
        String expected = "before\\u2029after";
        assertEquals(expected, JsEscape.forSingleQuotedLiteral(input));
    }

    // ── Confirm-dialog injection scenario ────────────────────────────────────

    @Test
    void confirmDialogScenario_injectionAttemptIsSafe() {
        // Attacker-controlled name tries to break out of JS confirm dialog
        String malicious = "x'); alert(document.cookie); confirm('";
        String escaped   = JsEscape.forSingleQuotedLiteral(malicious);
        // All single quotes must be escaped with a preceding backslash
        // so the string literal cannot terminate early
        assertTrue(escaped.contains("\\'"), "Single quotes must be escaped with backslash");
        // No bare (unescaped) single quote should remain.
        // An unescaped quote would be a ' not preceded by \
        for (int i = 0; i < escaped.length(); i++) {
            if (escaped.charAt(i) == '\'') {
                assertTrue(i > 0 && escaped.charAt(i - 1) == '\\',
                        "Unescaped single quote found at index " + i + " in: " + escaped);
            }
        }
    }
}
