package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.*;
import com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin;
import com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.service.TradeService;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import quickfix.ConfigError;
import quickfix.SessionSettings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FixSimulatorApplication extends WebApplication {

    private PluginRegistry pluginRegistry;

    // ── Services ──────────────────────────────────────────────────────────────
    // Populated by plugin initialize() hooks; see individual setters below.
    private ConnectionService connectionService;
    private MessageLogService messageLogService;
    private OrderService      orderService;
    private TradeService      tradeService;

    // ── WebApplication ────────────────────────────────────────────────────────

    @Override
    public Session newSession(Request request, Response response) {
        return new FixSimulatorSession(request);
    }

    @Override
    public Class<? extends Page> getHomePage() {
        return HomePage.class;
    }

    @Override
    public void init() {
        super.init();
        getMarkupSettings().setDefaultMarkupEncoding("UTF-8");

        // Allow Bootstrap + Bootstrap Icons CDN for stylesheets, scripts and fonts
        getCspSettings().blocking()
                .add(CSPDirective.STYLE_SRC,  CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.STYLE_SRC,  "https://cdn.jsdelivr.net")
                .add(CSPDirective.SCRIPT_SRC, CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.SCRIPT_SRC, "https://cdn.jsdelivr.net")
                .add(CSPDirective.FONT_SRC,   CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.FONT_SRC,   "https://cdn.jsdelivr.net");

        pluginRegistry = new PluginRegistry();
        registerBuiltInPlugins();

        pluginRegistry.getPlugins().forEach(p -> p.initialize(this));

        // Mount clean URLs for every page that has one
        pluginRegistry.getPlugins().stream()
                .filter(p -> p.getPageClass() != null)
                .forEach(p -> mountPage("/" + p.getId(), p.getPageClass()));
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public PluginRegistry getPluginRegistry() { return pluginRegistry; }

    public ConnectionService getConnectionService() { return connectionService; }
    public MessageLogService getMessageLogService() { return messageLogService; }
    public OrderService      getOrderService()      { return orderService; }
    public TradeService      getTradeService()      { return tradeService; }

    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setConnectionService(ConnectionService cs) { this.connectionService = cs; }

    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setMessageLogService(MessageLogService mls) { this.messageLogService = mls; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setOrderService(OrderService os) { this.orderService = os; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setTradeService(TradeService ts) { this.tradeService = ts; }

    // ── Plugin registration ───────────────────────────────────────────────────

    private void registerBuiltInPlugins() {
        // The gateway is instantiated first so we can hand it to the order manager.
        Path cfgPath = resolveConfigFilePath();
        DefaultFixGatewayPlugin gateway = new DefaultFixGatewayPlugin(
                "connections", "FIX Connections", "bi-hdd-network",
                NavSection.ADMIN, ConnectionManagementPage.class,
                loadFixSettings(cfgPath), cfgPath);

        // ── Overview ──────────────────────────────────────────────────────────
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "dashboard", "Dashboard", "bi-speedometer2",
                NavSection.OVERVIEW, HomePage.class));

        // ── FIX Testing ───────────────────────────────────────────────────────
        pluginRegistry.register(new DefaultOrderManagerPlugin(
                "orders", "Orders", "bi-card-list",
                NavSection.MONITORING, OrdersPage.class,
                gateway));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "trades", "Trades", "bi-arrow-left-right",
                NavSection.MONITORING, TradesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "raw-messages", "Raw FIX Messages", "bi-terminal",
                NavSection.MONITORING, RawMessagesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "message-log", "Message Log", "bi-journal-text",
                NavSection.MONITORING, MessageLogPage.class));

        // ── Administration ────────────────────────────────────────────────────
        pluginRegistry.register(gateway);           // registered after order-manager for nav order
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "users", "User Management", "bi-people",
                NavSection.ADMIN, UserManagementPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "system-logs", "System Logs", "bi-file-earmark-text",
                NavSection.ADMIN, SystemLogsPage.class));
    }

    // ── FIX settings ─────────────────────────────────────────────────────────

    /**
     * Resolves the writable path for {@code fix-gateway.cfg}.
     *
     * <ol>
     *   <li>If the classpath resource resolves to an actual file on disk (typical
     *       in development with {@code mvn jetty:run}), that file is used so that
     *       edits via the UI are immediately visible in the source tree.</li>
     *   <li>Otherwise falls back to {@code fix-gateway.cfg} in the working directory.</li>
     * </ol>
     */
    private Path resolveConfigFilePath() {
        try {
            URL url = getClass().getResource("/fix-gateway.cfg");
            if (url != null && "file".equals(url.getProtocol())) {
                return Paths.get(url.toURI());
            }
        } catch (Exception ignored) {}
        return Paths.get(System.getProperty("user.dir"), "fix-gateway.cfg");
    }

    /**
     * Loads FIX session settings from the resolved config file path, then from
     * the classpath resource, then from built-in defaults — whichever is found first.
     */
    private SessionSettings loadFixSettings(Path configFilePath) {
        try {
            if (configFilePath != null && Files.exists(configFilePath)) {
                try (InputStream is = Files.newInputStream(configFilePath)) {
                    return new SessionSettings(is);
                }
            }
            InputStream is = getClass().getResourceAsStream("/fix-gateway.cfg");
            if (is != null) return new SessionSettings(is);
            return buildDefaultSettings();
        } catch (ConfigError | IOException e) {
            throw new RuntimeException("Failed to load FIX settings", e);
        }
    }

    private SessionSettings buildDefaultSettings() throws ConfigError {
        String cfg = """
                [DEFAULT]
                ConnectionType=initiator
                ReconnectInterval=5
                StartTime=00:00:00
                EndTime=00:00:00
                HeartBtInt=30
                UseDataDictionary=N
                ResetOnLogon=Y

                [SESSION]
                BeginString=FIX.4.4
                SenderCompID=SIMULATOR
                TargetCompID=EXCHANGE
                SocketConnectHost=localhost
                SocketConnectPort=9876
                """;
        return new SessionSettings(new java.io.ByteArrayInputStream(cfg.getBytes()));
    }
}
