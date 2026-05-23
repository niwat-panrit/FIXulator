package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.gateway.FixMessageListener;
import com.npsoftdev.fixsimulator.gateway.GatewayConnectionService;
import com.npsoftdev.fixsimulator.gateway.GatewayMessageLogService;
import com.npsoftdev.fixsimulator.gateway.LiveSessionFacade;
import com.npsoftdev.fixsimulator.pages.BasePage;
import com.npsoftdev.fixsimulator.service.MessageLogService;
import quickfix.*;
import quickfix.Dictionary;
import quickfix.field.MsgType;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
            // Cast method references to Serializable so Wicket can serialise the page store.
            connectionService = new GatewayConnectionService(
                    sessionIDs, facade, settings,
                    (Serializable & Consumer<com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest>) this::addSessionInternal,
                    (Serializable & BiConsumer<String, com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest>) this::updateSessionInternal,
                    (Serializable & Consumer<String>) this::deleteSessionInternal);
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
            disableAutoConnect();
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

    // ── Dynamic session creation ──────────────────────────────────────────────

    /**
     * Adds a new FIX session at runtime using QuickFIX/J's dynamic-session API.
     * Existing sessions are unaffected — no restart required.
     */
    private synchronized void addSessionInternal(
            com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest req) {

        if (initiator == null) throw new IllegalStateException("Initiator not started");

        // FIX 5.0+ uses FIXT.1.1 as the transport-layer BeginString
        SessionID sid = new SessionID(req.beginString(), req.senderCompID(), req.targetCompID());
        try {
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
                dict.setString("SocketAcceptPort",  String.valueOf(req.port()));
            }
            // FIX 5.0+ requires DefaultApplVerID so QuickFIX/J knows the application version
            if ("FIXT.1.1".equals(req.beginString())) {
                dict.setString("DefaultApplVerID", toApplVerID(req.fixVersion()));
            }
            settings.set(sid, dict);

            // QuickFIX/J 2.3.1 has no createDynamicSession — stop and restart
            // the initiator so it picks up the new session from settings.
            initiator.stop(true);
            initiator = new SocketInitiator(this, new MemoryStoreFactory(), settings,
                    new SLF4JLogFactory(settings), new DefaultMessageFactory());
            initiator.start();
            disableAutoConnect();
        } catch (Exception e) {
            throw new RuntimeException("Failed to add FIX session: " + sid, e);
        }
    }

    /**
     * Disconnects the given session (defensive), archives any on-disk QuickFIX/J
     * session files, removes the session from {@link SessionSettings}, and restarts
     * the initiator so the change takes effect immediately.
     */
    private synchronized void deleteSessionInternal(String sessionId) {

        if (initiator == null) throw new IllegalStateException("Initiator not started");

        // Locate the SessionID in the live settings (before removal).
        SessionID targetSid = null;
        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) {
            SessionID sid = it.next();
            if (sid.toString().equals(sessionId)) {
                targetSid = sid;
                break;
            }
        }

        // Defensively log out — GatewayConnectionService already sent a logout, but
        // the session may have started reconnecting in the brief interval since then.
        if (targetSid != null) {
            Session qfSession = Session.lookupSession(targetSid);
            if (qfSession != null) qfSession.logout();
        }

        // Archive any on-disk QuickFIX/J session files (no-op with MemoryStoreFactory).
        archiveSessionFiles(sessionId, settings);

        // Remove from settings then restart the initiator.
        if (targetSid != null) removeFromSettings(settings, targetSid);

        try {
            initiator.stop(true);
            initiator = new SocketInitiator(this, new MemoryStoreFactory(), settings,
                    new SLF4JLogFactory(settings), new DefaultMessageFactory());
            initiator.start();
            disableAutoConnect();
        } catch (ConfigError e) {
            throw new RuntimeException(
                    "Failed to restart initiator after deleting session: " + sessionId, e);
        }
    }

    /**
     * Renames every QuickFIX/J FileStore file whose name starts with the session's
     * file prefix by appending {@code .deleted.{unix_timestamp}}.  This preserves
     * the last sequence-number state while clearly marking the session as deleted.
     *
     * <p>This is a best-effort operation: if {@code FileStorePath} is not configured
     * (e.g. when using {@code MemoryStoreFactory}) or the files do not exist, the
     * method returns silently.</p>
     */
    private static void archiveSessionFiles(String sessionId, SessionSettings settings) {
        try {
            String fileStorePath = settings.getString("FileStorePath");
            Path dir = Paths.get(fileStorePath);
            if (!Files.isDirectory(dir)) return;

            // QuickFIX/J derives the file prefix from SessionID.toString() by replacing
            // reserved filesystem characters with '-'.
            String filePrefix = sessionId.replaceAll("[:/\\\\*?\"<>|]", "-");
            long   timestamp  = System.currentTimeMillis() / 1000;

            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith(filePrefix))
                      .forEach(path -> {
                          try {
                              Files.move(path, path.resolveSibling(
                                      path.getFileName() + ".deleted." + timestamp));
                          } catch (IOException ex) {
                              // Best-effort — log and continue.
                          }
                      });
            }
        } catch (Exception ignored) {
            // FileStorePath absent or inaccessible — nothing to archive.
        }
    }

    /**
     * Removes the old session from {@link SessionSettings} and adds the new one,
     * then restarts the initiator so the change takes effect.
     */
    private synchronized void updateSessionInternal(
            String oldSessionId,
            com.npsoftdev.fixsimulator.service.ConnectionService.NewSessionRequest req) {

        if (initiator == null) throw new IllegalStateException("Initiator not started");

        // Find and remove the old session from settings using the iterator.
        Iterator<SessionID> it = settings.sectionIterator();
        while (it.hasNext()) {
            SessionID sid = it.next();
            if (sid.toString().equals(oldSessionId)) {
                removeFromSettings(settings, sid);
                break;
            }
        }

        // Add the new session and restart (reuses existing addSessionInternal logic).
        addSessionInternal(req);
    }

    /**
     * Removes a single session entry from {@link SessionSettings} by accessing its
     * private {@code sections} map via reflection — QuickFIX/J 2.x has no public
     * remove API for individual sessions.
     */
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

    /**
     * Calls {@link Session#logout()} on every session immediately after the initiator
     * starts, so sessions stay in DISCONNECTED state until the user explicitly clicks
     * Connect on the Connection Management page.
     */
    private void disableAutoConnect() {
        initiator.getSessions().forEach(sid -> {
            Session session = Session.lookupSession(sid);
            if (session != null) session.logout();
        });
    }

    /** Maps a FIX application version string to its QuickFIX/J ApplVerID numeric code. */
    private static String toApplVerID(String fixVersion) {
        return switch (fixVersion) {
            case "FIX.5.0"    -> "7";
            case "FIX.5.0SP1" -> "8";
            case "FIX.5.0SP2" -> "9";
            default            -> "9";   // default to most recent
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void publishOutbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onOutbound(sessionID, message));
    }

    private void publishInbound(SessionID sessionID, Message message) {
        messageListeners.forEach(l -> l.onInbound(sessionID, message));
    }
}
