package com.npsoftdev.fixsimulator.template;

import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * YAML-backed {@link DynamicValueRegistry}.
 *
 * <p>Only <em>custom</em> constant entries are written to
 * {@code dynamic-values.yaml}; built-in tokens are always seeded in code so
 * they cannot be accidentally removed by editing the file.</p>
 *
 * <p>On load, built-in definitions are registered first (preserving their
 * insertion order), then custom entries from the file are appended. The
 * resulting {@link #listAll()} order is therefore: built-ins first, then
 * user-defined constants in the order they were created.</p>
 *
 * <h3>YAML format</h3>
 * <pre>{@code
 * # Only custom constant values are stored here.
 * # Built-in tokens (order_id, timestamp, etc.) are always available.
 * customValues:
 *   - name: firm_id
 *     description: My firm's unique identifier
 *     constantValue: MYCOMPANY
 * }</pre>
 */
public class YamlDynamicValueRegistry implements DynamicValueRegistry {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(YamlDynamicValueRegistry.class);
    private static final String FILENAME = "dynamic-values.yaml";

    /** Names reserved by the engine; cannot be used for custom constants. */
    private static final Set<String> BUILT_IN_NAMES = Set.of(
            "order_id", "timestamp", "sending_time", "uuid", "sender", "target");

    private final YamlPersistenceService yaml;

    /** Ordered: built-ins first, then custom. */
    private final Map<String, DynamicValueDefinition> byName = new LinkedHashMap<>();

    public YamlDynamicValueRegistry(YamlPersistenceService yaml) {
        this.yaml = yaml;
        seedBuiltIns();
        loadCustom();
    }

    // ── DynamicValueRegistry ──────────────────────────────────────────────────

    @Override
    public synchronized List<DynamicValueDefinition> listAll() {
        return new ArrayList<>(byName.values());
    }

    @Override
    public synchronized Optional<DynamicValueDefinition> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public synchronized void define(DynamicValueDefinition def) {
        if (def.builtIn()) {
            throw new IllegalArgumentException("Cannot register a built-in definition via define().");
        }
        if (BUILT_IN_NAMES.contains(def.name())) {
            throw new IllegalArgumentException(
                    "'" + def.name() + "' is a built-in token name and cannot be overridden.");
        }
        byName.put(def.name(), def);
        persistCustom();
    }

    @Override
    public synchronized void removeCustom(String name) {
        if (BUILT_IN_NAMES.contains(name)) {
            throw new IllegalArgumentException(
                    "'" + name + "' is a built-in token and cannot be removed.");
        }
        byName.remove(name);
        persistCustom();
    }

    @Override
    public synchronized Optional<String> resolveConstant(String name) {
        DynamicValueDefinition def = byName.get(name);
        if (def == null || def.builtIn()) return Optional.empty();
        return Optional.ofNullable(def.constantValue());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

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

    private void loadCustom() {
        if (!yaml.exists(FILENAME)) return;
        try {
            CustomValuesDto dto = yaml.load(FILENAME, CustomValuesDto.class);
            if (dto.customValues != null) {
                for (CustomValueDto cv : dto.customValues) {
                    if (BUILT_IN_NAMES.contains(cv.name)) {
                        log.warn("Skipping custom value '{}': name is reserved for a built-in token", cv.name);
                        continue;
                    }
                    byName.put(cv.name,
                            DynamicValueDefinition.constant(cv.name, cv.description, cv.constantValue));
                }
            }
            long customCount = byName.values().stream().filter(d -> !d.builtIn()).count();
            log.info("Loaded {} custom dynamic value(s) from {}", customCount, FILENAME);
        } catch (IOException e) {
            log.error("Failed to load {}; custom dynamic values will not be restored", FILENAME, e);
        }
    }

    private void persistCustom() {
        CustomValuesDto dto = new CustomValuesDto();
        dto.customValues = new ArrayList<>();
        for (DynamicValueDefinition def : byName.values()) {
            if (def.builtIn()) continue;
            CustomValueDto cv = new CustomValueDto();
            cv.name          = def.name();
            cv.description   = def.description();
            cv.constantValue = def.constantValue();
            dto.customValues.add(cv);
        }
        try {
            yaml.save(FILENAME, dto);
        } catch (IOException e) {
            log.error("Failed to save dynamic values to {}", FILENAME, e);
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    static class CustomValuesDto {
        public List<CustomValueDto> customValues;
    }

    static class CustomValueDto {
        public String name;
        public String description;
        public String constantValue;
    }
}
