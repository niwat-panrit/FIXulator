package com.npsoftdev.fixsimulator.plugins.template.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Descriptor for a named dynamic value that can be referenced in FIX message
 * templates via the {@code $(name)} or {@code $(name:param)} token syntax.
 *
 * <p>Built-in definitions (e.g. {@code order_id}, {@code timestamp}) are
 * resolved programmatically at send-time by {@link DefaultFixMessageBuilder}.
 * Custom definitions hold a static constant string that is substituted
 * verbatim.</p>
 *
 * @param name          token name used inside {@code $(...)} (alphanumeric + underscores)
 * @param description   human-readable description shown in the admin page
 * @param builtIn       {@code true} for engine-provided tokens that cannot be removed
 * @param exampleUsage  display-only example, e.g. {@code "$(order_id)"}
 * @param constantValue static replacement string for custom tokens; {@code null} for built-ins
 */
public record DynamicValueDefinition(
        String  name,
        String  description,
        boolean builtIn,
        String  exampleUsage,
        String  constantValue
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DynamicValueDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(exampleUsage, "exampleUsage");
        // constantValue is null for built-ins
    }

    /** Convenience factory for built-in tokens. */
    public static DynamicValueDefinition builtIn(String name, String description, String exampleUsage) {
        return new DynamicValueDefinition(name, description, true, exampleUsage, null);
    }

    /** Convenience factory for user-defined constant tokens. */
    public static DynamicValueDefinition constant(String name, String description, String constantValue) {
        return new DynamicValueDefinition(name, description, false, "$("+name+")", constantValue);
    }
}
