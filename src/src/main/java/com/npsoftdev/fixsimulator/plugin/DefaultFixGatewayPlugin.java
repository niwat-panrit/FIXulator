package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.gateway.FixMessageListener;
import com.npsoftdev.fixsimulator.gateway.GatewayConnectionService;
import com.npsoftdev.fixsimulator.gateway.GatewayMessageLogService;
import com.npsoftdev.fixsimulator.gateway.LiveSessionFacade;
import com.npsoftdev.fixsimulator.pages.BasePage;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import quickfix.*;
import quickfix.field.MsgType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The default FIX gateway plugin.
 *
 * <p><strong>Responsibilities</strong>
 * <ul>
 *   <li>Owns the QuickFIX/J {@link Application} lifecycle (session creation,
 *       logon/logout, heartbeats, admin messages).</li>
 *   <li>Maintains the {@link GatewayConnectionService} and
 *       {@link GatewayMessageLogService}.</li>
 *   <li>Broadcasts every application-level message to registered
 *       {@link FixMessageListener FixMessageListeners} so that higher-level
 *       plugins (e.g. {@link DefaultOrderManagerPlugin}) can react without being
 *       coupled to the transport layer.</li>
 * </ul>
 *
 * <p>Order and trade concerns are intentionally <em>not</em> handled here;
 * register a {@link DefaultOrderManagerPlugin} to process those messages.</p>
 *
 * <p>When constructed without {@link SessionSettings} (nav-only constructor) the
 * FIX engine is never started — the plugin behaves as a plain nav entry.</p>
 */
public class DefaultFixGatewayPlugin implements SimulatorPlugin, Application {

    private static final long serialVersionUID = 1L;

    // ── Nav ──────────────────────────────────────────────────────────────────
    private final String id;
    private final String label;
    private final String iconClass;
    private final NavSection section;
    private final Class<? extends BasePage> pageClass;

    // ── FIX (null when nav-only) ──────────────────────────────────────────────
    private final SessionSettings settings;

    /**
     * Shared session-ID registry populated from {@link #onCreate}/{@link #onLogon}
     * callbacks.  Exposed via {@link #getSessionIDs()} so that dependent plugins
     * (like {@link DefaultOrderManagerPlugin}) can share the same live map.
     */
    private final Map<String, SessionID> sessionIDs = new ConcurrentHashMap<>();

    /** Listeners notified on every {@code toApp} / {@code fromApp} call. */
    private final List<FixMessageListener> messageListeners = new CopyOnWriteArrayList<>();

    private GatewayConnectionService connectionService;
    private GatewayMessageLogService messageLogService;

    /** Not serialisable — created fresh on each application start. */
    private transient Initiator initiator;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Nav-only — no FIX engine, identical behaviour to the old {@code DefaultPlugin}. */
    public DefaultFixGatewayPlugin(String id, String label, String iconClass,
                                    NavSection section, Class<? extends BasePage> pageClass) {
        this(id, label, iconClass, section, pageClass, null);
    }

    /**
     * Full-gateway constructor.  Pass non-null {@link SessionSettings} to
     * activate the FIX engine when {@link #initialize} is called.
     */
    public DefaultFixGatewayPlugin(String id, String label, String iconClass,
                                    NavSection section, Class<? extends BasePage> pageClass,
                                    SessionSettings settings) {
        this.id        = id;
        this.label     = label;
        this.iconClass = iconClass;
        this.section   = section;
        this.pageClass = pageClass;
        this.settings  = settings;

        if (settings != null) {
            LiveSessionFacade facade = new LiveSessionFacade();
            connectionService = new GatewayConnectionService(sessionIDs, facade, settings);
            messageLogService = new GatewayMessageLogService();
        }
    }

    // ── SimulatorPlugin ───────────────────────────────────────────────────────

    @Override public String getId()                           { return id; }
    @Override public String getLabel()                        { return label; }
    @Override public String getIconClass()                    { return iconClass; }
    @Override public NavSection getSection()                  { return section; }
    @Override public Class<? extends BasePage> getPageClass() { return pageClass; }

    @Override
    public void initialize(FixSimulatorApplication app) {
        if (settings == null) return;   // nav-only — nothing to start

        try {
            initiator = new SocketInitiator(
                    this,
                    new MemoryStoreFactory(),
                    settings,
                    new SLF4JLogFactory(settings),
                    new DefaultMessageFactory());
            initiator.start();
        } catch (ConfigError e) {
            throw new RuntimeException("FIX initiator failed to start for plugin '" + id + "'", e);
        }

        app.setConnectionService(connectionService);
        app.setMessageLogService(messageLogService);
    }

    // ── Listener registration ─────────────────────────────────────────────────

    /**
     * Registers a listener that will be notified on every application-level
     * FIX message.  Safe to call before {@link #initialize}.
     */
    public void addMessageListener(FixMessageListener listener) {
        messageListeners.add(listener);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public GatewayConnectionService getConnectionService() { return connectionService; }
    public GatewayMessageLogService getMessageLogService() { return messageLogService; }

    /**
     * Returns the live session-ID map (same reference used internally).
     * Dependent plugins may share this map to resolve session-ID strings.
     */
    public Map<String, SessionID> getSessionIDs() {
        return Collections.unmodifiableMap(sessionIDs);
    }

    /** Exposed for testing — allows tests to retrieve and drive registered listeners. */
    public List<FixMessageListener> getMessageListeners() {
        return Collections.unmodifiableList(messageListeners);
    }

    // ── quickfix.Application ──────────────────────────────────────────────────

    @Override
    public void onCreate(SessionID sessionID) {
        sessionIDs.put(sessionID.toString(), sessionID);
        if (connectionService != null) connectionService.onSessionCreated(sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        sessionIDs.put(sessionID.toString(), sessionID);
        if (connectionService != null) connectionService.onLogon(sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        if (connectionService != null) connectionService.onLogout(sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.SENT, message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.RECEIVED, message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.SENT, message);
        publishOutbound(sessionID, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.RECEIVED, message);
        publishInbound(sessionID, message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void publishOutbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onOutbound(sessionID, message));
    }

    private void publishInbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onInbound(sessionID, message));
    }
}
