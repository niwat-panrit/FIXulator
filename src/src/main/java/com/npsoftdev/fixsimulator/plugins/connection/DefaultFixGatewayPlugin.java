package com.npsoftdev.fixsimulator.plugins.connection;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.plugins.connection.api.FixMessageListener;
import com.npsoftdev.fixsimulator.plugins.connection.internal.GatewayConnectionService;
import com.npsoftdev.fixsimulator.plugins.connection.internal.GatewayMessageLogService;
import com.npsoftdev.fixsimulator.plugins.connection.internal.LiveSessionFacade;
import com.npsoftdev.fixsimulator.core.ui.BasePage;
import com.npsoftdev.fixsimulator.plugins.connection.api.MessageLogService;
import com.npsoftdev.fixsimulator.plugins.connection.api.SessionStartException;
import quickfix.*;
import quickfix.Dictionary;
import quickfix.field.MsgType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.npsoftdev.fixsimulator.core.plugin.NavSection;
import com.npsoftdev.fixsimulator.core.plugin.SimulatorPlugin;

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
 *   <li>Persists session configuration to {@code fix-gateway.cfg} after every
 *       add / update / delete so that changes survive application restarts.</li>
 * </ul>
 *
 * <p>Each FIX session is managed by its own dedicated {@link Connector} — a
 * {@link SocketInitiator} when the session dials out, a {@link SocketAcceptor}
 * when it listens. This means adding, editing, or deleting one session has zero
 * impact on any other session — no shared restart, no dropped connections.</p>
 *
 * <p><strong>Acceptor sessions bind their port as soon as they start</strong>,
 * while the session itself stays logged out until the user presses Connect —
 * mirroring how an initiator's connector runs without dialling. Two acceptor
 * sessions therefore cannot share a port.</p>
 */
public class DefaultFixGatewayPlugin implements SimulatorPlugin, Application {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(DefaultFixGatewayPlugin.class);

    // ── Nav ──────────────────────────────────────────────────────────────────
    private final String id;
    private final String label;
    private final String iconClass;
    private final NavSection section;
    private final Class<? extends BasePage> pageClass;

    // ── FIX (null when nav-only) ──────────────────────────────────────────────

    /**
     * Master settings used only for persistence — never passed to a running initiator.
     * Transient: {@link SessionSettings} is not serializable.
     */
    private transient SessionSettings settings;

    /**
     * Path to {@code fix-gateway.cfg}; written after every add / update / delete.
     * Transient so Wicket's page store never tries to serialise it.
     */
    private transient String configFilePath;

    /**
     * One dedicated {@link Connector} per session ID — a {@link SocketInitiator}
     * or a {@link SocketAcceptor} depending on the session's ConnectionType.
     * Editing session X stops and replaces only X's connector; all others keep
     * running. Transient: Connector is not serializable.
     */
    private transient Map<String, Connector> sessionConnectors;

    /**
     * Shared session-ID registry populated from {@link #onCreate} callbacks.
     * Exposed via {@link #getSessionIDs()} so dependent plugins can share it.
     */
    private final Map<String, SessionID> sessionIDs = new ConcurrentHashMap<>();

    /** Listeners notified on every {@code toApp} / {@code fromApp} call. */
    private final List<FixMessageListener> messageListeners = new CopyOnWriteArrayList<>();

    private GatewayConnectionService connectionService;
    private GatewayMessageLogService messageLogService;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Nav-only — no FIX engine. */
    public DefaultFixGatewayPlugin(String id, String label, String iconClass,
                                    NavSection section, Class<? extends BasePage> pageClass) {
        this(id, label, iconClass, section, pageClass, null, null);
    }

    /** Full-gateway constructor without persistence path. */
    public DefaultFixGatewayPlugin(String id, String label, String iconClass,
                                    NavSection section, Class<? extends BasePage> pageClass,
                                    SessionSettings settings) {
        this(id, label, iconClass, section, pageClass, settings, null);
    }

    /**
     * Full-gateway constructor with persistence path.
     *
     * @param configFilePath path to {@code fix-gateway.cfg}; changes are written here
     *                       after every add / update / delete.  May be {@code null}
     *                       to disable persistence.
     */
    public DefaultFixGatewayPlugin(String id, String label, String iconClass,
                                    NavSection section, Class<? extends BasePage> pageClass,
                                    SessionSettings settings, Path configFilePath) {
        this.id             = id;
        this.label          = label;
        this.iconClass      = iconClass;
        this.section        = section;
        this.pageClass      = pageClass;
        this.settings       = settings;
        this.configFilePath = configFilePath != null ? configFilePath.toString() : null;

        if (settings != null) {
            LiveSessionFacade facade = new LiveSessionFacade();
            connectionService = new GatewayConnectionService(
                    sessionIDs, facade, settings,
                    (Serializable & Consumer<com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest>) this::addSessionInternal,
                    (Serializable & BiConsumer<String, com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest>) this::updateSessionInternal,
                    (Serializable & Consumer<String>) this::deleteSessionInternal,
                    (Serializable & Consumer<String>) this::startConnectorIfStopped);
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

        sessionConnectors = new ConcurrentHashMap<>();

        // Start one dedicated connector per configured session.
        List<SessionID> sids = new ArrayList<>();
        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) sids.add(it.next());

        for (SessionID sid : sids) {
            try {
                startSessionConnector(sid, buildPerSessionSettings(sid));
            } catch (Exception e) {
                // One bad session must not stop the application from starting.
                // An acceptor whose port is already taken is the likely cause,
                // and the user needs the UI up in order to fix the port.
                log.error("FIX session failed to start and will be unavailable: {} — {}",
                        sid, e.getMessage(), e);
            }
        }

        app.setConnectionService(connectionService);
        app.setMessageLogService(messageLogService);
    }

    // ── Listener registration ─────────────────────────────────────────────────

    public void addMessageListener(FixMessageListener listener) {
        messageListeners.add(listener);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public GatewayConnectionService getConnectionService() { return connectionService; }
    public GatewayMessageLogService getMessageLogService() { return messageLogService; }

    public Map<String, SessionID> getSessionIDs() {
        return Collections.unmodifiableMap(sessionIDs);
    }

    public List<FixMessageListener> getMessageListeners() {
        return Collections.unmodifiableList(messageListeners);
    }

    // ── quickfix.Application ──────────────────────────────────────────────────

    @Override
    public void onCreate(SessionID sessionID) {
        sessionIDs.put(sessionID.toString(), sessionID);
        if (connectionService != null) connectionService.onSessionCreated(sessionID);
        log.info("FIX session created: {}", sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        sessionIDs.put(sessionID.toString(), sessionID);
        if (connectionService != null) connectionService.onLogon(sessionID);
        log.info("FIX session LOGON (connected): {}", sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        if (connectionService != null) connectionService.onLogout(sessionID);
        log.info("FIX session LOGOUT (disconnected): {}", sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.SENT, message);
        // Admin messages (Heartbeat, Logon, Logout, ResendRequest, etc.) logged at DEBUG
        // to avoid flooding the log; session lifecycle is already logged in onLogon/onLogout.
        log.debug("→ ADMIN [{}] type={}", sessionID, msgType(message));
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.RECEIVED, message);
        log.debug("← ADMIN [{}] type={}", sessionID, msgType(message));
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.SENT, message);
        String type = msgType(message);
        log.info("→ FIX SENT     [{}] type={} ({})", sessionID, type, msgTypeName(type));
        log.debug("→ FIX SENT     [{}] raw={}", sessionID, message);
        publishOutbound(sessionID, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        if (messageLogService != null)
            messageLogService.record(sessionID, MessageLogService.Direction.RECEIVED, message);
        String type = msgType(message);
        log.info("← FIX RECEIVED [{}] type={} ({})", sessionID, type, msgTypeName(type));
        log.debug("← FIX RECEIVED [{}] raw={}", sessionID, message);
        publishInbound(sessionID, message);
    }

    // ── Dynamic session management ────────────────────────────────────────────

    /**
     * Adds a new session by starting a dedicated initiator for it.
     * All existing sessions are completely unaffected.
     */
    private synchronized void addSessionInternal(
            com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req) {

        SessionID sid = new SessionID(req.beginString(), req.senderCompID(), req.targetCompID());
        log.info("Adding FIX session: {} ({}:{}, heartbeat={}s)",
                sid, req.host(), req.port(), req.heartbeatSecs());

        // Register in master settings for persistence.
        try {
            settings.set(sid, buildSessionDict(req));
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure FIX session: " + sid, e);
        }

        // Persist BEFORE starting. A start can fail for a reason the user can fix
        // later — an acceptor port already in use, most often — and losing the
        // configuration would force them to type it all in again to retry.
        persistSettings();

        // Start a dedicated connector for this session only.
        startConfiguredSession(sid, req);
    }

    /**
     * Replaces an existing session with a new configuration by stopping only that
     * session's initiator and starting a new one.
     *
     * <p>All other sessions keep running without interruption.</p>
     */
    private synchronized void updateSessionInternal(
            String oldSessionId,
            com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req) {

        log.info("Updating FIX session: {} → {}:{} {}→{}",
                oldSessionId, req.senderCompID(), req.targetCompID(), req.host(), req.port());
        // Stop only the connector for the old session — others are untouched.
        Connector old = sessionConnectors.remove(oldSessionId);
        if (old != null) old.stop(true);

        // Remove old session from master settings.
        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) {
            SessionID sid = it.next();
            if (sid.toString().equals(oldSessionId)) {
                removeFromSettings(settings, sid);
                break;
            }
        }

        // Register the new session in master settings.
        SessionID newSid = new SessionID(req.beginString(), req.senderCompID(), req.targetCompID());
        try {
            settings.set(newSid, buildSessionDict(req));
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure updated FIX session: " + newSid, e);
        }

        // Persist before starting, for the same reason as addSessionInternal. The
        // old connector was already stopped above, so editing an acceptor onto a
        // free port releases the old one and binds the new one.
        persistSettings();

        // Start a dedicated connector for the new session configuration.
        startConfiguredSession(newSid, req);
    }

    /**
     * Disconnects and permanently removes one session by stopping only that
     * session's initiator.
     *
     * <p>All other sessions keep running without interruption.</p>
     */
    private synchronized void deleteSessionInternal(String sessionId) {

        log.info("Deleting FIX session: {}", sessionId);
        // Stop only this session's connector — others are untouched.
        Connector connector = sessionConnectors.remove(sessionId);
        if (connector != null) {
            archiveSessionFiles(sessionId, settings);
            connector.stop(true);
        }

        // Remove from master settings.
        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) {
            SessionID sid = it.next();
            if (sid.toString().equals(sessionId)) {
                removeFromSettings(settings, sid);
                break;
            }
        }

        persistSettings();
    }

    // ── Per-session initiator helper ──────────────────────────────────────────

    /**
     * Creates, starts, and registers a dedicated {@link Connector} for the given
     * session: a {@link SocketAcceptor} when its ConnectionType is
     * {@code acceptor}, otherwise a {@link SocketInitiator}.  The session is left
     * in the logged-out state so that the user explicitly enables it via the UI
     * Connect button.
     *
     * <p><strong>The connector type must match the session's ConnectionType.</strong>
     * A {@code SocketInitiator} handed an acceptor session does not fail — it
     * silently creates no session at all, so {@link Application#onCreate} never
     * fires, the session never reaches {@link #sessionIDs}, and it disappears from
     * the UI while its configuration sits correctly in {@code fix-gateway.cfg}.</p>
     *
     * <p>{@link Connector#start()} is what creates the session and calls
     * {@link Application#onCreate} — for an acceptor this happens as it binds its
     * port. The lookup below must therefore come after {@code start()}; by that
     * point the session is in QFJ's registry and in {@link #sessionIDs}, so
     * {@link Session#logout()} can suppress it before the connector's background
     * threads fire.</p>
     */
    private void startSessionConnector(SessionID sid, SessionSettings perSessionSettings)
            throws ConfigError {

        boolean acceptor = isAcceptor(perSessionSettings, sid);
        log.info("Starting FIX {} for session: {}", acceptor ? "acceptor" : "initiator", sid);

        Connector connector = acceptor
                ? new SocketAcceptor(
                        this,
                        new FileStoreFactory(perSessionSettings),
                        perSessionSettings,
                        new SLF4JLogFactory(perSessionSettings),
                        new DefaultMessageFactory())
                : new SocketInitiator(
                        this,
                        new FileStoreFactory(perSessionSettings),
                        perSessionSettings,
                        new SLF4JLogFactory(perSessionSettings),
                        new DefaultMessageFactory());

        try {
            connector.start();
        } catch (Exception e) {
            // Release whatever the connector did manage to allocate; leaving a
            // half-started acceptor behind would hold threads and could keep a
            // partially bound port from being retried.
            try { connector.stop(true); } catch (Exception ignored) { }
            throw startFailure(sid, perSessionSettings, acceptor, e);
        }

        // The user decides when the session goes live: an initiator must not
        // auto-dial, and an acceptor must not accept a logon yet.
        Session qfSession = Session.lookupSession(sid);
        if (qfSession != null) qfSession.logout();

        sessionConnectors.put(sid.toString(), connector);
    }

    /**
     * Starts the connector for a freshly configured session, reporting any failure
     * as a {@link SessionStartException} so the UI can show it. The configuration
     * has already been persisted by the caller and is deliberately left in place:
     * a busy port is fixable, and the user should be able to retry rather than
     * retype the session.
     */
    private void startConfiguredSession(
            SessionID sid,
            com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req) {
        try {
            startSessionConnector(sid, buildPerSessionSettings(req));
        } catch (SessionStartException e) {
            throw e;
        } catch (ConfigError e) {
            throw new SessionStartException(
                    "FIX session " + sid + " was saved but its configuration was rejected: "
                    + e.getMessage(), false, e);
        }
    }

    /**
     * Translates a connector start failure into a {@link SessionStartException}
     * carrying a message worth showing a user.
     */
    private SessionStartException startFailure(SessionID sid, SessionSettings perSessionSettings,
                                               boolean acceptor, Exception cause) {
        if (SessionStartException.isAddressInUse(cause)) {
            String port = safeGetStr(perSessionSettings, sid,
                    acceptor ? "SocketAcceptPort" : "SocketConnectPort", "?");
            return new SessionStartException(
                    "Port " + port + " is already in use. Another application, or another "
                    + "acceptor session in FIXulator, is listening on it. Free the port and "
                    + "press Listen to retry, or edit this session to use a different port.",
                    true, cause);
        }
        return new SessionStartException(
                "FIX session " + sid + " could not be started: " + cause.getMessage(),
                false, cause);
    }

    /**
     * Stops every running connector, releasing any bound acceptor ports.
     * Used by tests and available for an orderly shutdown.
     */
    public synchronized void stopAllConnectors() {
        if (sessionConnectors == null) return;
        for (Connector c : sessionConnectors.values()) {
            try { c.stop(true); } catch (Exception e) { log.debug("Connector stop failed", e); }
        }
        sessionConnectors.clear();
    }

    /**
     * Starts this session's connector if it is not already running, and reports a
     * failure the caller can show to a user.
     *
     * <p>This is what makes a failed acceptor retryable. A session whose port was
     * taken has configuration but no connector, so pressing Listen comes back
     * through here and tries to bind again — succeeding once the port is free.</p>
     */
    public synchronized void startConnectorIfStopped(String sessionId) {
        if (settings == null) return;
        if (sessionConnectors == null) sessionConnectors = new ConcurrentHashMap<>();
        if (sessionConnectors.containsKey(sessionId)) return;   // already running

        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) {
            SessionID sid = it.next();
            if (sid.toString().equals(sessionId)) {
                try {
                    startSessionConnector(sid, buildPerSessionSettings(sid));
                } catch (SessionStartException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SessionStartException(
                            "FIX session " + sid + " could not be started: " + e.getMessage(),
                            false, e);
                }
                return;
            }
        }
    }

    /**
     * Whether the given session is configured as an acceptor. Defaults to
     * {@code false} (initiator) when the setting is absent or unreadable.
     * Package-private for testing.
     */
    static boolean isAcceptor(SessionSettings settings, SessionID sid) {
        try {
            return SessionFactory.ACCEPTOR_CONNECTION_TYPE
                    .equalsIgnoreCase(settings.getString(sid, SessionFactory.SETTING_CONNECTION_TYPE));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Per-session settings builders ─────────────────────────────────────────

    /**
     * Builds a standalone {@link SessionSettings} for one session by extracting
     * its configuration from the master settings.  Used during {@link #initialize}.
     *
     * <p>Reconstructs a {@link com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest}
     * from the master settings and delegates to the proven
     * {@link #buildPerSessionSettings(com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest)}
     * overload, guaranteeing the same code path as add/update.</p>
     */
    private SessionSettings buildPerSessionSettings(SessionID sid) throws ConfigError {
        String connectionType = safeGetStr(sid, "ConnectionType", "initiator");
        String beginString    = sid.getBeginString();
        String fixVersion     = beginString; // default for FIX 4.x
        if ("FIXT.1.1".equals(beginString)) {
            fixVersion = fromApplVerID(safeGetStr(sid, "DefaultApplVerID", "9"));
        }
        boolean isInitiator = "initiator".equalsIgnoreCase(connectionType);
        String host = isInitiator ? safeGetStr(sid, "SocketConnectHost", "localhost") : "0.0.0.0";
        int port = isInitiator
                ? Integer.parseInt(safeGetStr(sid, "SocketConnectPort", "9876"))
                : Integer.parseInt(safeGetStr(sid, "SocketAcceptPort",  "9876"));
        int     heartbeatSecs = Integer.parseInt(safeGetStr(sid, "HeartBtInt", "30"));
        boolean resetOnLogon  = "Y".equalsIgnoreCase(safeGetStr(sid, "ResetOnLogon", "N"));

        com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req =
                new com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest(
                        connectionType, fixVersion, beginString,
                        sid.getSenderCompID(), sid.getTargetCompID(),
                        host, port, heartbeatSecs, resetOnLogon);

        return buildPerSessionSettings(req);
    }

    private String safeGetStr(SessionID sid, String key, String fallback) {
        return safeGetStr(settings, sid, key, fallback);
    }

    private static String safeGetStr(SessionSettings from, SessionID sid, String key, String fallback) {
        try { return from.getString(sid, key); } catch (Exception e) { return fallback; }
    }

    private static String fromApplVerID(String applVerID) {
        return switch (applVerID) {
            case "7" -> "FIX.5.0";
            case "8" -> "FIX.5.0SP1";
            case "9" -> "FIX.5.0SP2";
            default  -> "FIX.5.0SP2";
        };
    }

    /**
     * Builds a standalone {@link SessionSettings} from a {@link com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest}.
     * Used when adding or updating a session.  Package-private for testing.
     */
    static SessionSettings buildPerSessionSettings(
            com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req)
            throws ConfigError {

        StringBuilder cfg = new StringBuilder();
        appendDefaultSection(cfg);
        cfg.append("\n[SESSION]\n");
        cfg.append("BeginString=").append(req.beginString()).append("\n");
        cfg.append("SenderCompID=").append(req.senderCompID()).append("\n");
        cfg.append("TargetCompID=").append(req.targetCompID()).append("\n");
        cfg.append("ConnectionType=").append(req.connectionType().toLowerCase()).append("\n");
        cfg.append("HeartBtInt=").append(req.heartbeatSecs()).append("\n");
        cfg.append("ResetOnLogon=").append(req.resetOnLogon() ? "Y" : "N").append("\n");
        if ("initiator".equalsIgnoreCase(req.connectionType())) {
            cfg.append("SocketConnectHost=").append(req.host()).append("\n");
            cfg.append("SocketConnectPort=").append(req.port()).append("\n");
        } else {
            cfg.append("SocketAcceptPort=").append(req.port()).append("\n");
        }
        if ("FIXT.1.1".equals(req.beginString())) {
            cfg.append("DefaultApplVerID=").append(toApplVerID(req.fixVersion())).append("\n");
        }
        return new SessionSettings(
                new java.io.ByteArrayInputStream(cfg.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void appendDefaultSection(StringBuilder cfg) {
        cfg.append("[DEFAULT]\n");
        cfg.append("ReconnectInterval=5\n");
        cfg.append("StartTime=00:00:00\n");
        cfg.append("EndTime=00:00:00\n");
        cfg.append("UseDataDictionary=N\n");
        cfg.append("CheckLatency=N\n");
        cfg.append("FileStorePath=data/fix-store\n");
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Writes all sessions from the master {@link SessionSettings} to
     * {@code fix-gateway.cfg} so that configuration survives restarts.
     * Package-private for testing.
     */
    void persistSettings() {
        if (configFilePath == null || settings == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# FIX Gateway configuration for FIX Simulator\n");
            sb.append("# Auto-generated by Connection Management — do not edit while the app is running.\n\n");

            sb.append("[DEFAULT]\n");
            appendDefaultKey(sb, "ConnectionType",    "initiator");
            appendDefaultKey(sb, "ReconnectInterval", "5");
            appendDefaultKey(sb, "StartTime",         "00:00:00");
            appendDefaultKey(sb, "EndTime",           "00:00:00");
            appendDefaultKey(sb, "HeartBtInt",        "30");
            appendDefaultKey(sb, "UseDataDictionary", "N");
            appendDefaultKey(sb, "ResetOnLogon",      "N");
            appendDefaultKey(sb, "CheckLatency",      "N");
            appendDefaultKey(sb, "FileStorePath",     "data/fix-store");
            appendDefaultKey(sb, "SocketConnectHost", null);
            sb.append('\n');

            Iterator<SessionID> it = settings.sectionIterator();
            while (it.hasNext()) {
                SessionID sid = it.next();
                sb.append("[SESSION]\n");
                sb.append("BeginString=").append(sid.getBeginString()).append('\n');
                sb.append("SenderCompID=").append(sid.getSenderCompID()).append('\n');
                sb.append("TargetCompID=").append(sid.getTargetCompID()).append('\n');
                appendSessionKey(sb, sid, "ConnectionType");
                appendSessionKey(sb, sid, "HeartBtInt");
                appendSessionKey(sb, sid, "ResetOnLogon");
                appendSessionKey(sb, sid, "SocketConnectHost");
                appendSessionKey(sb, sid, "SocketConnectPort");
                appendSessionKey(sb, sid, "SocketAcceptPort");
                appendSessionKey(sb, sid, "DefaultApplVerID");
                sb.append('\n');
            }

            Files.writeString(Paths.get(configFilePath), sb.toString());
        } catch (IOException e) {
            log.error("Failed to persist session config to {}: {}", configFilePath, e.getMessage(), e);
        }
    }

    private void appendDefaultKey(StringBuilder sb, String key, String fallback) {
        try {
            sb.append(key).append('=').append(settings.getString(key)).append('\n');
        } catch (Exception e) {
            if (fallback != null) sb.append(key).append('=').append(fallback).append('\n');
        }
    }

    private void appendSessionKey(StringBuilder sb, SessionID sid, String key) {
        try {
            String val = settings.getString(sid, key);
            try { if (val.equals(settings.getString(key))) return; } catch (Exception ignored) {}
            sb.append(key).append('=').append(val).append('\n');
        } catch (Exception ignored) {}
    }

    // ── Session dictionary builder ────────────────────────────────────────────

    private static Dictionary buildSessionDict(
            com.npsoftdev.fixsimulator.plugins.connection.api.ConnectionService.NewSessionRequest req)
            throws ConfigError {

        Dictionary dict = new Dictionary();
        dict.setString("ConnectionType",    req.connectionType().toLowerCase());
        dict.setString("HeartBtInt",        String.valueOf(req.heartbeatSecs()));
        dict.setString("ResetOnLogon",      req.resetOnLogon() ? "Y" : "N");
        dict.setString("UseDataDictionary", "N");
        dict.setString("CheckLatency",      "N");
        dict.setString("StartTime",         "00:00:00");
        dict.setString("EndTime",           "00:00:00");
        dict.setString("ReconnectInterval", "5");
        if ("initiator".equalsIgnoreCase(req.connectionType())) {
            dict.setString("SocketConnectHost", req.host());
            dict.setString("SocketConnectPort", String.valueOf(req.port()));
        } else {
            dict.setString("SocketAcceptPort", String.valueOf(req.port()));
        }
        if ("FIXT.1.1".equals(req.beginString())) {
            dict.setString("DefaultApplVerID", toApplVerID(req.fixVersion()));
        }
        return dict;
    }

    // ── Settings helpers ──────────────────────────────────────────────────────

    private static void archiveSessionFiles(String sessionId, SessionSettings settings) {
        try {
            String fileStorePath = settings.getString("FileStorePath");
            Path dir = Paths.get(fileStorePath);
            if (!Files.isDirectory(dir)) return;

            String filePrefix = sessionId.replaceAll("[:/\\\\*?\"<>|]", "-");
            long   timestamp  = System.currentTimeMillis() / 1000;

            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith(filePrefix))
                      .forEach(path -> {
                          try {
                              Files.move(path, path.resolveSibling(
                                      path.getFileName() + ".deleted." + timestamp));
                          } catch (IOException ex) { /* best-effort */ }
                      });
            }
        } catch (Exception ignored) { /* FileStorePath absent — nothing to archive */ }
    }

    private static void removeFromSettings(SessionSettings settings, SessionID sid) {
        try {
            Field f = SessionSettings.class.getDeclaredField("sections");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> sections = (Map<Object, Object>) f.get(settings);
            sections.remove(sid);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to remove session from QuickFIX/J settings: " + sid, e);
        }
    }

    private static String toApplVerID(String fixVersion) {
        return switch (fixVersion) {
            case "FIX.5.0"    -> "7";
            case "FIX.5.0SP1" -> "8";
            case "FIX.5.0SP2" -> "9";
            default            -> "9";
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Extracts MsgType (tag 35) from a FIX message; returns "?" on any error. */
    private static String msgType(Message message) {
        try { return message.getHeader().getString(MsgType.FIELD); } catch (Exception e) { return "?"; }
    }

    /**
     * Maps a FIX MsgType string to a human-readable name for logging.
     * Only the most common types are listed; anything else returns the raw type value.
     */
    private static String msgTypeName(String type) {
        return switch (type) {
            case MsgType.HEARTBEAT                      -> "Heartbeat";
            case MsgType.LOGON                          -> "Logon";
            case MsgType.LOGOUT                         -> "Logout";
            case MsgType.RESEND_REQUEST                 -> "ResendRequest";
            case MsgType.REJECT                         -> "Reject";
            case MsgType.SEQUENCE_RESET                 -> "SequenceReset";
            case MsgType.TEST_REQUEST                   -> "TestRequest";
            case MsgType.ORDER_SINGLE                   -> "NewOrderSingle";
            case MsgType.ORDER_CANCEL_REQUEST           -> "OrderCancelRequest";
            case MsgType.ORDER_CANCEL_REPLACE_REQUEST   -> "OrderCancelReplaceRequest";
            case MsgType.EXECUTION_REPORT               -> "ExecutionReport";
            case MsgType.ORDER_CANCEL_REJECT            -> "OrderCancelReject";
            default                                     -> type;
        };
    }

    private void publishOutbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onOutbound(sessionID, message));
    }

    private void publishInbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onInbound(sessionID, message));
    }
}
