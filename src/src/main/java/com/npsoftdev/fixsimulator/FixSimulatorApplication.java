package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.ConnectionManagementPage;
import com.npsoftdev.fixsimulator.pages.DynamicValuesPage;
import com.npsoftdev.fixsimulator.pages.ValueMappingsPage;
import com.npsoftdev.fixsimulator.pages.HomePage;
import com.npsoftdev.fixsimulator.pages.FixActivityPage;
import com.npsoftdev.fixsimulator.pages.OrdersPage;
import com.npsoftdev.fixsimulator.pages.FixMessageTemplateFormPage;
import com.npsoftdev.fixsimulator.pages.FixMessageTemplatesPage;
import com.npsoftdev.fixsimulator.pages.SystemLogsPage;
import com.npsoftdev.fixsimulator.pages.TradesPage;
import com.npsoftdev.fixsimulator.pages.UserManagementPage;
import com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin;
import com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.service.TradeService;
import com.npsoftdev.fixsimulator.template.DynamicValueRegistry;
import com.npsoftdev.fixsimulator.template.TemplateService;
import com.npsoftdev.fixsimulator.template.ValueMappingService;
import com.npsoftdev.fixsimulator.user.UserRepository;
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
    private ConnectionService    connectionService;
    private MessageLogService    messageLogService;
    private OrderService         orderService;
    private TradeService         tradeService;
    private TemplateService      templateService;
    private ValueMappingService  valueMappingService;
    private DynamicValueRegistry dynamicValueRegistry;
    private UserRepository       userRepository;

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

        mountPage("/fix-message-templates/form", FixMessageTemplateFormPage.class);
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public PluginRegistry getPluginRegistry() { return pluginRegistry; }

    public ConnectionService getConnectionService() { return connectionService; }
    public MessageLogService getMessageLogService() { return messageLogService; }
    public OrderService      getOrderService()      { return orderService; }
    public TradeService      getTradeService()      { return tradeService; }
    public TemplateService      getTemplateService()      { return templateService; }
    public ValueMappingService  getValueMappingService()  { return valueMappingService; }
    public DynamicValueRegistry getDynamicValueRegistry() { return dynamicValueRegistry; }
    public UserRepository       getUserRepository()       { return userRepository; }

    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setConnectionService(ConnectionService cs) { this.connectionService = cs; }

    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setMessageLogService(MessageLogService mls) { this.messageLogService = mls; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setOrderService(OrderService os) { this.orderService = os; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setTradeService(TradeService ts) { this.tradeService = ts; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setTemplateService(TemplateService ts) { this.templateService = ts; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setValueMappingService(ValueMappingService vms)     { this.valueMappingService = vms; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setDynamicValueRegistry(DynamicValueRegistry dvr)   { this.dynamicValueRegistry = dvr; }

    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setUserRepository(UserRepository ur)                 { this.userRepository = ur; }

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
                gateway, resolveDataDirectory()));
        // Dynamic Orders page removed — order sending is integrated into the Orders page.
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "trades", "Trades", "bi-arrow-left-right",
                NavSection.MONITORING, TradesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "fix-activity", "FIX Activity", "bi-activity",
                NavSection.MONITORING, FixActivityPage.class));

        // ── Administration ────────────────────────────────────────────────────
        pluginRegistry.register(gateway);           // registered after order-manager for nav order
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "fix-message-templates", "FIX Message Templates", "bi-layout-text-window-reverse",
                NavSection.ADMIN, FixMessageTemplatesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "dynamic-values", "Dynamic Values", "bi-braces",
                NavSection.ADMIN, DynamicValuesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "value-mappings", "Value Mappings", "bi-table",
                NavSection.ADMIN, ValueMappingsPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "users", "User Management", "bi-people",
                NavSection.ADMIN, UserManagementPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "system-logs", "System Logs", "bi-file-earmark-text",
                NavSection.ADMIN, SystemLogsPage.class));
    }

    // ── FIX settings ─────────────────────────────────────────────────────────

    /**
     * Resolves the directory where YAML data files are stored.
     *
     * <p>Returns {@code <working-directory>/data}. The directory is created
     * lazily by each repository on first write, so no explicit creation is
     * needed here.</p>
     */
    private Path resolveDataDirectory() {
        return Paths.get(System.getProperty("user.dir"), "data");
    }

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
