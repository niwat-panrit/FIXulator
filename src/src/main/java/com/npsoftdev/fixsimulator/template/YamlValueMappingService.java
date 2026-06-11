package com.npsoftdev.fixsimulator.template;

import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * YAML-backed {@link ValueMappingService}.
 *
 * <p>All named lookup tables are persisted to {@code value-mappings.yaml}
 * inside the configured data directory. On first run (file absent) the
 * service seeds a small {@code symbol-to-isin} table so the built-in
 * {@link FieldValue.Derived} example works out of the box.</p>
 *
 * <h3>YAML format</h3>
 * <pre>{@code
 * mappings:
 *   symbol-to-isin:
 *     AAPL: US0378331005
 *     MSFT: US5949181045
 *   my-table:
 *     key1: value1
 * }</pre>
 */
public class YamlValueMappingService implements ValueMappingService {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(YamlValueMappingService.class);
    private static final String FILENAME = "value-mappings.yaml";

    private final YamlPersistenceService yaml;

    /** Outer key: mapping name. Inner key: lookup key. Inner value: mapped value. */
    private final Map<String, Map<String, String>> tables = new LinkedHashMap<>();

    public YamlValueMappingService(YamlPersistenceService yaml) {
        this.yaml = yaml;
        if (yaml.exists(FILENAME)) {
            load();
        } else {
            seedDefaults();
            persist();
        }
    }

    // ── ValueMappingService ───────────────────────────────────────────────────

    @Override
    public synchronized Optional<String> lookup(String mappingName, String key) {
        Map<String, String> table = tables.get(mappingName);
        if (table == null || key == null) return Optional.empty();
        return Optional.ofNullable(table.get(key));
    }

    @Override
    public synchronized void put(String mappingName, String key, String value) {
        tables.computeIfAbsent(mappingName, k -> new LinkedHashMap<>()).put(key, value);
        persist();
    }

    @Override
    public synchronized void remove(String mappingName, String key) {
        Map<String, String> table = tables.get(mappingName);
        if (table != null) {
            table.remove(key);
            persist();
        }
    }

    @Override
    public synchronized Set<String> mappingNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    @Override
    public synchronized Map<String, String> entries(String mappingName) {
        Map<String, String> table = tables.get(mappingName);
        return table != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(table))
                : Collections.emptyMap();
    }

    @Override
    public synchronized void createMapping(String mappingName) {
        tables.computeIfAbsent(mappingName, k -> new LinkedHashMap<>());
        persist();
    }

    @Override
    public synchronized void deleteMapping(String mappingName) {
        tables.remove(mappingName);
        persist();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void load() {
        try {
            MappingsDto dto = yaml.load(FILENAME, MappingsDto.class);
            if (dto.mappings != null) {
                dto.mappings.forEach((name, entries) ->
                        tables.put(name, new LinkedHashMap<>(entries)));
            }
            log.info("Loaded {} value mapping(s) from {}", tables.size(), FILENAME);
        } catch (IOException e) {
            log.error("Failed to load {}; starting with default value mappings", FILENAME, e);
            seedDefaults();
        }
    }

    private void persist() {
        MappingsDto dto = new MappingsDto();
        dto.mappings = new LinkedHashMap<>();
        tables.forEach((name, entries) -> dto.mappings.put(name, new LinkedHashMap<>(entries)));
        try {
            yaml.save(FILENAME, dto);
        } catch (IOException e) {
            log.error("Failed to save value mappings to {}", FILENAME, e);
        }
    }

    /** A few common tickers so {@code Derived(55, "symbol-to-isin")} works immediately. */
    private void seedDefaults() {
        Map<String, String> isin = new LinkedHashMap<>();
        isin.put("AAPL", "US0378331005");
        isin.put("MSFT", "US5949181045");
        isin.put("GOOG", "US02079K1079");
        isin.put("TSLA", "US88160R1014");
        isin.put("AMZN", "US0231351067");
        tables.put("symbol-to-isin", isin);
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    static class MappingsDto {
        public Map<String, Map<String, String>> mappings;
    }
}
