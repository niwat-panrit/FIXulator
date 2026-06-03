package com.npsoftdev.fixsimulator.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Process-local {@link DynamicValueRegistry}.
 *
 * <p>Pre-seeded with all built-in token names that {@link DefaultFixMessageBuilder}
 * knows how to resolve. Custom constant entries added at runtime live only for
 * the lifetime of the JVM.</p>
 */
public class InMemoryDynamicValueRegistry implements DynamicValueRegistry {

    private static final long serialVersionUID = 1L;

    /** Names that are engine-reserved and cannot be used for custom constants. */
    private static final Set<String> BUILT_IN_NAMES = Set.of(
            "order_id", "timestamp", "sending_time", "uuid", "sender", "target");

    /** Ordered map: built-ins first (added in constructor), then custom entries. */
    private final Map<String, DynamicValueDefinition> byName = new LinkedHashMap<>();

    public InMemoryDynamicValueRegistry() {
        seedBuiltIns();
    }

    private void seedBuiltIns() {
        add(DynamicValueDefinition.builtIn(
                "order_id",
                "Monotonically-increasing client order ID. Suitable for ClOrdID (tag 11) "
                + "and OrigClOrdID (tag 41).",
                "$(order_id)"));
        add(DynamicValueDefinition.builtIn(
                "timestamp",
                "Current UTC timestamp in FIX UTCTimestamp format (YYYYMMDD-HH:MM:SS.sss). "
                + "Suitable for TransactTime (tag 60).",
                "$(timestamp)"));
        add(DynamicValueDefinition.builtIn(
                "sending_time",
                "Current UTC timestamp for the FIX SendingTime header field (tag 52). "
                + "Same format as $(timestamp).",
                "$(sending_time)"));
        add(DynamicValueDefinition.builtIn(
                "uuid",
                "Random UUID. Useful for ExecID, QuoteReqID, and other unique identifier fields.",
                "$(uuid)"));
        add(DynamicValueDefinition.builtIn(
                "sender",
                "SenderCompID derived from the active FIX session (tag 49).",
                "$(sender)"));
        add(DynamicValueDefinition.builtIn(
                "target",
                "TargetCompID derived from the active FIX session (tag 56).",
                "$(target)"));
    }

    private void add(DynamicValueDefinition def) {
        byName.put(def.name(), def);
    }

    // ── DynamicValueRegistry ──────────────────────────────────────────────────

    @Override
    public List<DynamicValueDefinition> listAll() {
        return new ArrayList<>(byName.values());
    }

    @Override
    public Optional<DynamicValueDefinition> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public void define(DynamicValueDefinition def) {
        if (def.builtIn()) {
            throw new IllegalArgumentException("Cannot register a built-in definition via define().");
        }
        if (BUILT_IN_NAMES.contains(def.name())) {
            throw new IllegalArgumentException(
                    "'" + def.name() + "' is a built-in token name and cannot be overridden.");
        }
        byName.put(def.name(), def);
    }

    @Override
    public void removeCustom(String name) {
        if (BUILT_IN_NAMES.contains(name)) {
            throw new IllegalArgumentException(
                    "'" + name + "' is a built-in token and cannot be removed.");
        }
        byName.remove(name);
    }

    @Override
    public Optional<String> resolveConstant(String name) {
        DynamicValueDefinition def = byName.get(name);
        if (def == null || def.builtIn()) return Optional.empty();
        return Optional.ofNullable(def.constantValue());
    }
}
