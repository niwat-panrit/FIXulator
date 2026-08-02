package com.npsoftdev.fixsimulator.plugins.template.api;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * One field in a {@link FixMessageTemplate}: which FIX tag, and how its value
 * is produced at send-time (see {@link FieldValue}).
 *
 * <p>The order of {@code FieldSpec}s inside a template's field list determines
 * FIX field order on the wire for body tags. Header tags are routed to the
 * message header regardless of position.</p>
 */
public record FieldSpec(int tag, FieldValue value) implements Serializable {

    private static final long serialVersionUID = 1L;

    public FieldSpec {
        if (tag <= 0) throw new IllegalArgumentException("tag must be positive, was " + tag);
        Objects.requireNonNull(value, "value");
    }

    /** Convenience: literal value spec. */
    public static FieldSpec literal(int tag, String value) {
        return new FieldSpec(tag, new FieldValue.Literal(value));
    }

    /** Convenience: per-request user-input spec. */
    public static FieldSpec userInput(int tag, String name) {
        return new FieldSpec(tag, new FieldValue.UserInput(name));
    }

    /** Convenience: per-request user-input spec with default. */
    public static FieldSpec userInput(int tag, String name, String defaultValue) {
        return new FieldSpec(tag, new FieldValue.UserInput(name, defaultValue));
    }

    /** Convenience: enumeration spec (no pre-selected default). */
    public static FieldSpec enumeration(int tag, String name, List<String> options) {
        return new FieldSpec(tag, new FieldValue.Enumeration(name, options, null));
    }

    /** Convenience: enumeration spec with a pre-selected default. */
    public static FieldSpec enumeration(int tag, String name, List<String> options, String defaultOption) {
        return new FieldSpec(tag, new FieldValue.Enumeration(name, options, defaultOption));
    }

    /** Convenience: placeholder spec. */
    public static FieldSpec placeholder(int tag, PlaceholderType type) {
        return new FieldSpec(tag, new FieldValue.Placeholder(type));
    }

    /** Convenience: derived (mapped) spec. */
    public static FieldSpec derived(int tag, int sourceTag, String mappingName) {
        return new FieldSpec(tag, new FieldValue.Derived(sourceTag, mappingName));
    }
}
