package com.npsoftdev.fixsimulator.pages;

/**
 * Utility for safely embedding Java strings inside JavaScript string literals
 * injected via Wicket's {@code AjaxCallListener#onPrecondition}.
 *
 * <p>The built-in {@code replace("'", "\\'")} pattern that was used throughout
 * the codebase did not handle the Unicode line terminators U+2028 and U+2029,
 * which are valid JS line terminators and can break a single-quoted string
 * literal in some JS engines.</p>
 */
final class JsEscape {

    private JsEscape() {}

    /**
     * Escapes {@code s} for safe embedding inside a JavaScript
     * <em>single-quoted</em> string literal.
     *
     * <p>Characters escaped:
     * <ul>
     *   <li>{@code \} → {@code \\}</li>
     *   <li>{@code '} → {@code \'}</li>
     *   <li>newline → {@code \n}</li>
     *   <li>carriage return → {@code \r}</li>
     *   <li>U+2028 (Line Separator) → {@code \u2028} (six literal ASCII chars)</li>
     *   <li>U+2029 (Paragraph Separator) → {@code \u2029}</li>
     * </ul>
     */
    static String forSingleQuotedLiteral(String s) {
        if (s == null) return "";
        return s.replace("\\",    "\\\\")
                .replace("'",     "\\'")
                .replace("\n",    "\\n")
                .replace("\r",    "\\r")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }
}
