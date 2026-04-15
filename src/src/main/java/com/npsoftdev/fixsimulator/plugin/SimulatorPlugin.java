package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.pages.BasePage;

import java.io.Serializable;

/**
 * Contract for every feature module (built-in or third-party) that wants to
 * participate in the FIX Simulator UI.
 *
 * <p>Register implementations with {@link PluginRegistry} during application
 * startup. The base application calls {@link #initialize(FixSimulatorApplication)}
 * on every registered plugin once the Wicket application is ready.</p>
 *
 * <h3>Minimal built-in plugin example</h3>
 * <pre>{@code
 * registry.register(new DefaultPlugin(
 *         "orders", "Orders", "bi-card-list",
 *         NavSection.MONITORING, OrdersPage.class));
 * }</pre>
 */
public interface SimulatorPlugin extends Serializable {

    /** Unique, stable identifier (e.g. {@code "orders"}). Used as a key in the registry. */
    String getId();

    /** Human-readable label shown in the sidebar (e.g. {@code "Orders"}). */
    String getLabel();

    /**
     * Bootstrap Icons class for the nav icon, <em>without</em> the leading {@code "bi "}
     * prefix (e.g. {@code "bi-card-list"}).
     */
    String getIconClass();

    /** Sidebar section this plugin belongs to. */
    NavSection getSection();

    /** Wicket page class this plugin contributes to the navigation. */
    Class<? extends BasePage> getPageClass();

    /**
     * Called once during {@link FixSimulatorApplication#init()} after all plugins
     * have been registered. Use this hook to mount additional pages, register
     * behaviours, or wire services.
     */
    default void initialize(FixSimulatorApplication app) {}
}
