package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.*;
import com.npsoftdev.fixsimulator.plugin.DefaultPlugin;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import org.apache.wicket.Page;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.protocol.http.WebApplication;

public class FixSimulatorApplication extends WebApplication {

    private PluginRegistry pluginRegistry;

    @Override
    public Class<? extends Page> getHomePage() {
        return HomePage.class;
    }

    /** Returns the application-scoped plugin registry. */
    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    @Override
    public void init() {
        super.init();
        getMarkupSettings().setDefaultMarkupEncoding("UTF-8");

        // Allow Bootstrap + Bootstrap Icons CDN for stylesheets, scripts and fonts
        getCspSettings().blocking()
                .add(CSPDirective.STYLE_SRC,  CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.STYLE_SRC,  "https://cdn.jsdelivr.net")
                .add(CSPDirective.SCRIPT_SRC, "https://cdn.jsdelivr.net")
                .add(CSPDirective.FONT_SRC,   CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.FONT_SRC,   "https://cdn.jsdelivr.net");

        pluginRegistry = new PluginRegistry();
        registerBuiltInPlugins();

        // Call each plugin's initialize() hook after all are registered
        pluginRegistry.getPlugins().forEach(p -> p.initialize(this));
    }

    /**
     * Registers the built-in pages as plugins. Add new feature modules here,
     * or call {@link PluginRegistry#register} from an external bootstrap class.
     */
    private void registerBuiltInPlugins() {
        // ── Overview ──────────────────────────────────────────────────────────
        pluginRegistry.register(new DefaultPlugin(
                "dashboard", "Dashboard", "bi-speedometer2",
                NavSection.OVERVIEW, HomePage.class));

        // ── FIX Testing ───────────────────────────────────────────────────────
        pluginRegistry.register(new DefaultPlugin(
                "orders", "Orders", "bi-card-list",
                NavSection.MONITORING, OrdersPage.class));
        pluginRegistry.register(new DefaultPlugin(
                "trades", "Trades", "bi-arrow-left-right",
                NavSection.MONITORING, TradesPage.class));
        pluginRegistry.register(new DefaultPlugin(
                "raw-messages", "Raw FIX Messages", "bi-terminal",
                NavSection.MONITORING, RawMessagesPage.class));
        pluginRegistry.register(new DefaultPlugin(
                "message-log", "Message Log", "bi-journal-text",
                NavSection.MONITORING, MessageLogPage.class));

        // ── Administration ────────────────────────────────────────────────────
        pluginRegistry.register(new DefaultPlugin(
                "connections", "FIX Connections", "bi-hdd-network",
                NavSection.ADMIN, ConnectionManagementPage.class));
        pluginRegistry.register(new DefaultPlugin(
                "users", "User Management", "bi-people",
                NavSection.ADMIN, UserManagementPage.class));
        pluginRegistry.register(new DefaultPlugin(
                "system-logs", "System Logs", "bi-file-earmark-text",
                NavSection.ADMIN, SystemLogsPage.class));
    }
}
