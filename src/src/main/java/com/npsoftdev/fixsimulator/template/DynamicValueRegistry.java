package com.npsoftdev.fixsimulator.template;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Catalog and resolver for named dynamic values used in the {@code $(name)}
 * token syntax supported by {@link DefaultFixMessageBuilder}.
 *
 * <p>The registry tracks two categories:
 * <ul>
 *   <li><b>Built-in</b> — engine-provided tokens resolved programmatically
 *       (e.g. {@code order_id}, {@code timestamp}). These are read-only.</li>
 *   <li><b>Custom constants</b> — user-defined name/value pairs that substitute
 *       a static string (e.g. {@code firm_id} → {@code "MYCOMPANY"}).</li>
 * </ul>
 */
public interface DynamicValueRegistry extends Serializable {

    /** Returns all definitions (built-ins first, then custom), in registration order. */
    List<DynamicValueDefinition> listAll();

    /** Looks up a definition by name; returns empty if unknown. */
    Optional<DynamicValueDefinition> findByName(String name);

    /**
     * Registers a custom constant definition. Overwrites an existing custom
     * entry with the same name; built-in names cannot be overwritten.
     *
     * @throws IllegalArgumentException if {@code def} is marked built-in
     * @throws IllegalArgumentException if the name clashes with a built-in token
     */
    void define(DynamicValueDefinition def);

    /**
     * Removes a custom constant by name. No-op if absent.
     *
     * @throws IllegalArgumentException if the name belongs to a built-in token
     */
    void removeCustom(String name);

    /**
     * Resolves a custom constant token by name.
     *
     * @return the constant value, or empty for unknown names or built-in names
     *         (built-ins are resolved by {@link DefaultFixMessageBuilder} itself)
     */
    Optional<String> resolveConstant(String name);
}
