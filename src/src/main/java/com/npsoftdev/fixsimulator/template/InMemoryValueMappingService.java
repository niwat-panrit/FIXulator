package com.npsoftdev.fixsimulator.template;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ValueMappingService} suitable for testing and the
 * initial release. Comes pre-seeded with a small {@code "symbol-to-isin"}
 * table so the Derived field spec works out of the box.
 *
 * <p>For persistence, implement {@link ValueMappingService} against a file
 * or database and swap the instance constructed in
 * {@link com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin}.</p>
 */
public class InMemoryValueMappingService implements ValueMappingService {

    private static final long serialVersionUID = 1L;

    private final Map<String, Map<String, String>> tables = new ConcurrentHashMap<>();

    public InMemoryValueMappingService() {
        seedDefaults();
    }

    @Override
    public Optional<String> lookup(String mappingName, String key) {
        Map<String, String> table = tables.get(mappingName);
        if (table == null || key == null) return Optional.empty();
        return Optional.ofNullable(table.get(key));
    }

    @Override
    public void put(String mappingName, String key, String value) {
        tables.computeIfAbsent(mappingName, k -> new ConcurrentHashMap<>())
              .put(key, value);
    }

    @Override
    public void remove(String mappingName, String key) {
        Map<String, String> table = tables.get(mappingName);
        if (table != null) table.remove(key);
    }

    @Override
    public Set<String> mappingNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    /**
     * A few common tickers so {@code Derived(55, "symbol-to-isin")} returns
     * meaningful values immediately. Override or clear in tests as needed.
     */
    private void seedDefaults() {
        put("symbol-to-isin", "AAPL",  "US0378331005");
        put("symbol-to-isin", "MSFT",  "US5949181045");
        put("symbol-to-isin", "GOOG",  "US02079K1079");
        put("symbol-to-isin", "TSLA",  "US88160R1014");
        put("symbol-to-isin", "AMZN",  "US0231351067");
    }
}
