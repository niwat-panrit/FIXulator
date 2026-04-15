package com.npsoftdev.fixsimulator.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central registry of {@link SimulatorPlugin} instances.
 *
 * <p>Plugins are registered in declaration order during
 * {@link com.npsoftdev.fixsimulator.FixSimulatorApplication#init()} and that
 * order is preserved in the sidebar navigation.</p>
 *
 * <p>The registry is stored on the application instance and is therefore
 * accessible anywhere via:
 * <pre>{@code
 * FixSimulatorApplication app = (FixSimulatorApplication) Application.get();
 * PluginRegistry registry = app.getPluginRegistry();
 * }</pre>
 * </p>
 */
public class PluginRegistry {

    private final List<SimulatorPlugin> plugins = new ArrayList<>();

    /** Appends a plugin to the registry. Call order determines nav order. */
    public void register(SimulatorPlugin plugin) {
        plugins.add(plugin);
    }

    /** Returns all registered plugins in registration order (unmodifiable). */
    public List<SimulatorPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    /** Returns all plugins belonging to the given {@link NavSection}, in order. */
    public List<SimulatorPlugin> getPluginsBySection(NavSection section) {
        return plugins.stream()
                .filter(p -> p.getSection() == section)
                .collect(Collectors.toList());
    }

    /** Looks up a plugin by its unique ID. */
    public Optional<SimulatorPlugin> findById(String id) {
        return plugins.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }
}
