package com.npsoftdev.fixsimulator.template;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

/**
 * Named lookup tables consulted by {@link FieldValue.Derived} field specs.
 *
 * <p>Typical use case: map a Symbol (tag 55) to the corresponding ISIN
 * (tag 48) via a {@code "symbol-to-isin"} mapping. Each mapping is identified
 * by a stable name and holds a flat string→string table.</p>
 *
 * <p>Implementations decide their backing store (memory, file, database).
 * The in-memory implementation is suitable for first-pass testing; persistence
 * is a follow-up concern.</p>
 */
public interface ValueMappingService extends Serializable {

    /** Returns the mapped value for {@code key} in {@code mappingName}, if any. */
    Optional<String> lookup(String mappingName, String key);

    /** Adds or replaces an entry in {@code mappingName}; creates the table if absent. */
    void put(String mappingName, String key, String value);

    /** Removes an entry; no-op if absent. */
    void remove(String mappingName, String key);

    /** Returns the set of known mapping names. */
    Set<String> mappingNames();
}
