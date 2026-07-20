package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.ConnectionManagementPage;
import com.npsoftdev.fixsimulator.pages.DynamicValuesPage;
import com.npsoftdev.fixsimulator.pages.ValueMappingsPage;
import com.npsoftdev.fixsimulator.pages.HomePage;
import com.npsoftdev.fixsimulator.pages.FixActivityPage;
import com.npsoftdev.fixsimulator.pages.LoginPage;
import com.npsoftdev.fixsimulator.pages.OrdersPage;
import com.npsoftdev.fixsimulator.pages.FixMessageTemplateFormPage;
import com.npsoftdev.fixsimulator.pages.FixMessageTemplatesPage;
import com.npsoftdev.fixsimulator.pages.PagePermissions;
import com.npsoftdev.fixsimulator.pages.BasePage;
import com.npsoftdev.fixsimulator.pages.SystemLogsPage;
import com.npsoftdev.fixsimulator.pages.TradesPage;
import com.npsoftdev.fixsimulator.pages.UserManagementPage;
import com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin;
import com.npsoftdev.fixsimulator.plugin.DefaultOrderManagerPlugin;
import com.npsoftdev.fixsimulator.plugin.NavSection;
import com.npsoftdev.fixsimulator.plugin.PluginRegistry;
import com.npsoftdev.fixsimulator.service.ConnectionService;
import com.npsoftdev.fixsimulator.service.LogFileService;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import com.npsoftdev.fixsimulator.service.OrderService;
import com.npsoftdev.fixsimulator.service.TradeService;
import com.npsoftdev.fixsimulator.template.DynamicValueRegistry;
import com.npsoftdev.fixsimulator.template.TemplateService;
import com.npsoftdev.fixsimulator.template.ValueMappingService;
import com.npsoftdev.fixsimulator.persistence.YamlPersistenceService;
import com.npsoftdev.fixsimulator.user.AuthService;
import com.npsoftdev.fixsimulator.user.DefaultRememberMeService;
import com.npsoftdev.fixsimulator.user.Permission;
import com.npsoftdev.fixsimulator.user.RememberMeService;
import com.npsoftdev.fixsimulator.user.UserRepository;
import org.apache.wicket.ISessionListener;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.Session;
import org.apache.wicket.authorization.Action;
import jakarta.servlet.http.Cookie;
import org.apache.wicket.authorization.IAuthorizationStrategy;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.component.IRequestableComponent;
import quickfix.ConfigError;
import quickfix.SessionSettings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FixSimulatorApplication extends WebApplication {

    /** Name of the persistent browser cookie that carries the remember-me token. */
    public static final String REMEMBER_ME_COOKIE = "FIXSIM_REMEMBER_ME";

    private PluginRegistry pluginRegistry;

    // ── Services ──────────────────────────────────────────────────────────────
    private ConnectionService    connectionService;
    private MessageLogService    messageLogService;
    private OrderService         orderService;
    private TradeService         tradeService;
    private TemplateService      templateService;
    private ValueMappingService  valueMappingService;
    private DynamicValueRegistry dynamicValueRegistry;
    private UserRepository       userRepository;
    private AuthService          authService;
    private RememberMeService    rememberMeService;
    private LogFileService       logFileService;

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

        // Allow Bootstrap + Bootstrap Icons CDN
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

        // Initialise persistent remember-me token store and purge stale entries
        rememberMeService = new DefaultRememberMeService(
                new YamlPersistenceService(resolveDataDirectory()));
        rememberMeService.purgeExpired();

        // Auto-login via remember-me cookie on every request when session is anonymous
        getRequestCycleListeners().add(new IRequestCycleListener() {
            @Override
            public void onBeginRequest(RequestCycle cycle) {
                FixSimulatorSession session = FixSimulatorSession.get();
                if (session.isAuthenticated()) return;

                WebRequest request = (WebRequest) cycle.getRequest();
                Cookie cookie = request.getCookie(REMEMBER_ME_COOKIE);
                if (cookie == null) return;

                rememberMeService.resolveToken(cookie.getValue()).ifPresent(username -> {
                    UserRepository repo = getUserRepository();
                    AuthService    auth = getAuthService();
                    if (repo == null || auth == null) return;
                    repo.findByUsername(username).filter(u -> u.isActive()).ifPresent(user -> {
                        if (auth.canStartSession(username)) {
                            session.bind();
                            session.signIn(user);
                            auth.registerSession(username, session.getId());
                        }
                    });
                });
            }
        });

        // Mount clean URLs for every page that has one
        pluginRegistry.getPlugins().stream()
                .filter(p -> p.getPageClass() != null)
                .forEach(p -> mountPage("/" + p.getId(), p.getPageClass()));

        mountPage("/fix-message-templates/form", FixMessageTemplateFormPage.class);
        mountPage("/login", LoginPage.class);

        // Release the session-limit slot when a Wicket session expires
        getSessionListeners().add(new ISessionListener() {
            @Override
            public void onUnbound(String sessionId) {
                AuthService auth = getAuthService();
                if (auth != null) auth.unregisterSessionById(sessionId);
            }
        });

        // Wire Wicket authorization strategy (depends on authService set by plugins)
        configureAuthorizationStrategy();
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    private void configureAuthorizationStrategy() {
        FixSimulatorApplication self = this;

        getSecuritySettings().setAuthorizationStrategy(new IAuthorizationStrategy() {
            @Override
            public <T extends IRequestableComponent> boolean isInstantiationAuthorized(
                    Class<T> componentClass) {
                // Allow everything that is not a protected page
                if (!BasePage.class.isAssignableFrom(componentClass)) return true;
                // Login page is always accessible
                if (LoginPage.class.isAssignableFrom(componentClass)) return true;

                FixSimulatorSession session = FixSimulatorSession.get();
                if (!session.isAuthenticated()) return false;

                Permission required = PagePermissions.forPage(componentClass);
                if (required == null) return true;  // any authenticated user

                AuthService auth = self.getAuthService();
                return auth != null && auth.hasPermission(session.getAuthenticatedUser(), required);
            }

            @Override
            public boolean isActionAuthorized(Component component, Action action) {
                return true;
            }

            @Override
            public boolean isResourceAuthorized(
                    org.apache.wicket.request.resource.IResource resource,
                    org.apache.wicket.request.mapper.parameter.PageParameters params) {
                return true;
            }
        });

        getSecuritySettings().setUnauthorizedComponentInstantiationListener(component -> {
            FixSimulatorSession session = FixSimulatorSession.get();
            if (!session.isAuthenticated()) {
                throw new RestartResponseAtInterceptPageException(LoginPage.class);
            }
            // Authenticated but lacks the required permission — go to dashboard
            throw new RestartResponseException(HomePage.class);
        });
    }

    // ── Service accessors ─────────────────────────────────────────────────────

    public PluginRegistry    getPluginRegistry()       { return pluginRegistry; }
    public ConnectionService getConnectionService()    { return connectionService; }
    public MessageLogService getMessageLogService()    { return messageLogService; }
    public OrderService      getOrderService()         { return orderService; }
    public TradeService      getTradeService()         { return tradeService; }
    public TemplateService      getTemplateService()      { return templateService; }
    public ValueMappingService  getValueMappingService()  { return valueMappingService; }
    public DynamicValueRegistry getDynamicValueRegistry() { return dynamicValueRegistry; }
    public UserRepository       getUserRepository()       { return userRepository; }
    public AuthService          getAuthService()          { return authService; }
    public RememberMeService    getRememberMeService()    { return rememberMeService; }
    public LogFileService       getLogFileService()       { return logFileService; }

    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setConnectionService(ConnectionService cs)  { this.connectionService = cs; }
    /** Called by {@link DefaultFixGatewayPlugin#initialize}. */
    public void setMessageLogService(MessageLogService mls) { this.messageLogService = mls; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setOrderService(OrderService os)            { this.orderService = os; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setTradeService(TradeService ts)            { this.tradeService = ts; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setTemplateService(TemplateService ts)      { this.templateService = ts; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setValueMappingService(ValueMappingService vms)    { this.valueMappingService = vms; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setDynamicValueRegistry(DynamicValueRegistry dvr)  { this.dynamicValueRegistry = dvr; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setUserRepository(UserRepository ur)               { this.userRepository = ur; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setAuthService(AuthService as)                     { this.authService = as; }
    /** Called by {@link DefaultOrderManagerPlugin#initialize}. */
    public void setLogFileService(LogFileService lfs)              { this.logFileService = lfs; }

    // ── Plugin registration ───────────────────────────────────────────────────

    private void registerBuiltInPlugins() {
        Path cfgPath = resolveConfigFilePath();
        DefaultFixGatewayPlugin gateway = new DefaultFixGatewayPlugin(
                "connections", "FIX Connections", "bi-hdd-network",
                NavSection.ADMIN, ConnectionManagementPage.class,
                loadFixSettings(cfgPath), cfgPath);

        // Gateway must be registered (and thus initialized) before the order manager
        // so that sessionIDs are populated before the cache restore runs.
        pluginRegistry.register(gateway);

        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "dashboard", "Dashboard", "bi-speedometer2",
                NavSection.OVERVIEW, HomePage.class));

        pluginRegistry.register(new DefaultOrderManagerPlugin(
                "orders", "Orders", "bi-card-list",
                NavSection.MONITORING, OrdersPage.class,
                gateway, resolveDataDirectory()));

        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "trades", "Trades", "bi-arrow-left-right",
                NavSection.MONITORING, TradesPage.class));
        pluginRegistry.register(new DefaultFixGatewayPlugin(
                "fix-activity", "FIX Activity", "bi-activity",
                NavSection.MONITORING, FixActivityPage.class));
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

    // ── Path helpers ──────────────────────────────────────────────────────────

    public Path resolveDataDirectory() {
        return Paths.get(System.getProperty("user.dir"), "data");
    }

    private Path resolveConfigFilePath() {
        try {
            URL url = getClass().getResource("/fix-gateway.cfg");
            if (url != null && "file".equals(url.getProtocol())) {
                return Paths.get(url.toURI());
            }
        } catch (Exception ignored) {}
        return Paths.get(System.getProperty("user.dir"), "fix-gateway.cfg");
    }

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
