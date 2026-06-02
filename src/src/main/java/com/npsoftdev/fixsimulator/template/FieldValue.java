package com.npsoftdev.fixsimulator.template;

import java.io.Serializable;
import java.util.Objects;

/**
 * The value-source for a single FIX field inside a {@link FixMessageTemplate}.
 *
 * <p>Each variant defers a different responsibility:
 * <ul>
 *   <li>{@link Literal} — fixed value baked into the template.</li>
 *   <li>{@link UserInput} — supplied per-request via the overrides map, keyed by {@link UserInput#name()}.</li>
 *   <li>{@link Placeholder} — resolved at send-time by {@link PlaceholderResolver}.</li>
 *   <li>{@link Derived} — looked up via {@link ValueMappingService} using the value of another field.</li>
 * </ul>
 *
 * <p>The sealed hierarchy lets the {@link FixMessageBuilder} exhaustively
 * pattern-match on variants and lets callers add a new variant only by editing
 * this file (and the builder).</p>
 */
public sealed interface FieldValue extends Serializable
        permits FieldValue.Literal, FieldValue.UserInput,
                FieldValue.Placeholder, FieldValue.Derived {

    /** Constant value embedded directly in the template. */
    record Literal(String value) implements FieldValue {
        private static final long serialVersionUID = 1L;
        public Literal { Objects.requireNonNull(value, "value"); }
    }

    /**
     * Value supplied at send-time via the overrides map.
     *
     * @param name          override key (e.g. {@code "symbol"})
     * @param defaultValue  value used when the override is missing or blank; may be null
     */
    record UserInput(String name, String defaultValue) implements FieldValue {
        private static final long serialVersionUID = 1L;
        public UserInput { Objects.requireNonNull(name, "name"); }
        public UserInput(String name) { this(name, null); }
    }

    /** Value generated at send-time by the placeholder resolver. */
    record Placeholder(PlaceholderType type) implements FieldValue {
        private static final long serialVersionUID = 1L;
        public Placeholder { Objects.requireNonNull(type, "type"); }
    }

    /**
     * Value looked up in {@link ValueMappingService} using the live value of {@code sourceTag}.
     *
     * <p>Example: {@code new Derived(55, "symbol-to-isin")} writes the ISIN
     * corresponding to whatever value tag 55 (Symbol) ends up with.</p>
     *
     * @param sourceTag    the FIX tag whose resolved value is the lookup key
     * @param mappingName  named mapping table (see {@link ValueMappingService#mappingNames()})
     */
    record Derived(int sourceTag, String mappingName) implements FieldValue {
        private static final long serialVersionUID = 1L;
        public Derived { Objects.requireNonNull(mappingName, "mappingName"); }
    }
}
