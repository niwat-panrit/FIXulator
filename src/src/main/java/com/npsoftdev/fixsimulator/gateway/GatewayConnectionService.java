package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.ConnectionService;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.field.MsgType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * {@link ConnectionService} implementation backed by live QuickFIX/J sessions.
 *
 * <p>Static session details (FIX version, comp IDs, connection type, host/port,
 * heartbeat) are captured once in {@link #onSessionCreated} from the
 * {@link SessionSettings}.  Live fields (status, sequence numbers) are read on
 * demand in {@link #listSessions()}.</p>
 *
 * <p>All I/O operations are delegated to a {@link SessionFacade} so the class
 * can be fully exercised in unit tests without a running FIX engine.</p>
 */
public class GatewayConnectionService implements ConnectionService, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GatewayConnectionService.class);

    // ── Internal state per session ────────────────────────────────────────────

    private enum SessionStatus { CREATED, CONNECTED, DISCONNECTED }

    private static final class SessionState implements Serializable {
        private static final long serialVersionUID = 1L;

        // Derived once from SessionID
        final String name;
        final String fixVersion;
        final String senderCompID;
        final String targetCompID;

        // Derived once from SessionSettings (empty string if unavailable)
        String connectionType = "";
        String hostPort       = "";
        int    heartbeatSecs  = 0;

        volatile SessionStatus status = SessionStatus.CREATED;

        SessionState(SessionID sid) {
            this.name         = sid.getSenderCompID() + " \u2192 " + sid.getTargetCompID();
            this.fixVersion   = sid.getBeginString();
            this.senderCompID = sid.getSenderCompID();
            this.targetCompID = sid.getTargetCompID();
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Shared session-ID registry populated by {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final Map<String, SessionID>    sessionIDs;
    private final Map<String, SessionState> states = new ConcurrentHashMap<>();
    private final SessionFacade             session;

    /** May be {@code null} when constructed without settings (e.g. in tests). Transient: {@link SessionSettings} is not serializable. */
    private transient SessionSettings settings;

    /** Delegates dynamic session creation to {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final Consumer<NewSessionRequest> sessionAdder;

    /** Delegates session update (remove old + add new) to {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final BiConsumer<String, NewSessionRequest> sessionUpdater;

    /** Delegates session deletion to {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final Consumer<String> sessionDeleter;

    /**
     * Tracks which sessions the user has explicitly connected (via the Connect button).
     * Each session has its own dedicated initiator, so this is purely informational.
     */
    private final Set<String> enabledSessions = ConcurrentHashMap.newKeySet();

    // ── Constructor ───────────────────────────────────────────────────────────

    /** Test-friendly constructor — {@code addSession}, {@code updateSession}, and {@code deleteSession} are not supported. */
    public GatewayConnectionService(Map<String, SessionID> sessionIDs,
                                    SessionFacade session,
                                    SessionSettings settings) {
        this(sessionIDs, session, settings,
                req -> { throw new UnsupportedOperationException("addSession not wired"); },
                (sid, req) -> { throw new UnsupportedOperationException("updateSession not wired"); },
                sid -> { throw new UnsupportedOperationException("deleteSession not wired"); });
    }

    public GatewayConnectionService(Map<String, SessionID> sessionIDs,
                                    SessionFacade session,
                                    SessionSettings settings,
                                    Consumer<NewSessionRequest> sessionAdder,
                                    BiConsumer<String, NewSessionRequest> sessionUpdater,
                                    Consumer<String> sessionDeleter) {
        this.sessionIDs     = sessionIDs;
        this.session        = session;
        this.settings       = settings;
        this.sessionAdder   = sessionAdder;
        this.sessionUpdater = sessionUpdater;
        this.sessionDeleter = sessionDeleter;
    }

    // ── Callbacks from DefaultFixGatewayPlugin ────────────────────────────────

    public void onSessionCreated(SessionID sid) {
        SessionState state = new SessionState(sid);
        populateFromSettings(state, sid);
        states.put(sid.toString(), state);
        log.info("FIX session registered: {} ({}, {})", sid, state.connectionType, state.hostPort);
    }

    public void onLogon(SessionID sid) {
        states.computeIfAbsent(sid.toString(), k -> {
            SessionState s = new SessionState(sid);
            populateFromSettings(s, sid);
            return s;
        }).status = SessionStatus.CONNECTED;
        log.info("FIX session CONNECTED: {}", sid);
    }

    public void onLogout(SessionID sid) {
        SessionState s = states.get(sid.toString());
        if (s != null) s.status = SessionStatus.DISCONNECTED;
        log.info("FIX session DISCONNECTED: {}", sid);
    }

    // ── ConnectionService ─────────────────────────────────────────────────────

    @Override
    public List<SessionDetails> listSessions() {
        List<SessionDetails> result = new ArrayList<>();
        for (Map.Entry<String, SessionState> e : states.entrySet()) {
            String       sid   = e.getKey();
            SessionState state = e.getValue();
            result.add(new SessionDetails(
                    sid,
                    state.name,
                    state.fixVersion,
                    state.senderCompID,
                    state.targetCompID,
                    state.connectionType,
                    state.hostPort,
                    state.heartbeatSecs,
                    getStatus(sid),
                    getTxSequence(sid),
                    getRxSequence(sid)
            ));
        }
        return result;
    }

    @Override
    public List<String> listSessionIds() {
        return new ArrayList<>(states.keySet());
    }

    @Override
    public String getSessionName(String sessionId) {
        SessionState s = states.get(sessionId);
        return s != null ? s.name : sessionId;
    }

    @Override
    public String getStatus(String sessionId) {
        SessionState s = states.get(sessionId);
        if (s == null) return "UNKNOWN";
        return switch (s.status) {
            case CONNECTED    -> "CONNECTED";
            case DISCONNECTED -> "DISCONNECTED";
            default           -> "CREATED";
        };
    }

    @Override
    public void connect(String sessionId) {
        log.info("User-initiated CONNECT for session: {}", sessionId);
        enabledSessions.add(sessionId);
        resolve(sessionId).ifPresent(session::logon);
    }

    @Override
    public void disconnect(String sessionId) {
        log.info("User-initiated DISCONNECT for session: {}", sessionId);
        enabledSessions.remove(sessionId);
        resolve(sessionId).ifPresent(session::logout);
    }

    @Override
    public int getTxSequence(String sessionId) {
        return resolve(sessionId)
                .map(session::getExpectedSenderNum)
                .orElse(0);
    }

    @Override
    public int getRxSequence(String sessionId) {
        return resolve(sessionId)
                .map(session::getExpectedTargetNum)
                .orElse(0);
    }

    @Override
    public void setTxSequence(String sessionId, int nextNum) {
        log.info("Setting TX sequence for session {} → nextSenderNum={}", sessionId, nextNum);
        resolve(sessionId).ifPresent(sid -> {
            try {
                session.setNextSenderNum(sid, nextNum);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set TX sequence for session: " + sessionId, e);
            }
        });
    }

    @Override
    public void setRxSequence(String sessionId, int nextNum) {
        log.info("Setting RX sequence for session {} → nextTargetNum={}", sessionId, nextNum);
        resolve(sessionId).ifPresent(sid -> {
            try {
                session.setNextTargetNum(sid, nextNum);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set RX sequence for session: " + sessionId, e);
            }
        });
    }

    @Override
    public void addSession(NewSessionRequest request) {
        log.info("Adding session: {}→{} {}:{}", request.senderCompID(), request.targetCompID(),
                request.host(), request.port());
        sessionAdder.accept(request);
    }

    @Override
    public void updateSession(String sessionId, NewSessionRequest request) {
        log.info("Updating session {}: {}→{} {}:{}", sessionId,
                request.senderCompID(), request.targetCompID(), request.host(), request.port());
        // Remove stale in-memory state so the restarted initiator registers fresh entries.
        states.remove(sessionId);
        sessionIDs.remove(sessionId);
        enabledSessions.remove(sessionId);
        sessionUpdater.accept(sessionId, request);
    }

    @Override
    public void deleteSession(String sessionId) {
        log.info("Deleting session: {}", sessionId);
        // Disconnect first so a clean FIX Logout is sent before the session is torn down.
        if ("CONNECTED".equals(getStatus(sessionId))) {
            disconnect(sessionId);
        }
        // Purge local state before the plugin restarts the initiator.
        states.remove(sessionId);
        sessionIDs.remove(sessionId);
        enabledSessions.remove(sessionId);
        // Delegate archive + QFJ settings removal + initiator restart to the plugin.
        sessionDeleter.accept(sessionId);
    }

    /** Returns the set of session IDs the user has explicitly connected (via Connect button). */
    public Set<String> getEnabledSessionIds() {
        return Collections.unmodifiableSet(enabledSessions);
    }

    @Override
    public void resetSequence(String sessionId) {
        log.info("Resetting sequence numbers for session: {}", sessionId);
        resolve(sessionId).ifPresent(sid -> {
            try {
                session.reset(sid);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset sequence for session: " + sessionId, e);
            }
        });
    }

    @Override
    public void sendRaw(String sessionId, String rawMessage, String delimiter) {
        SessionID sid = resolve(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session: " + sessionId));

        // Normalise delimiter → SOH
        String soh = (delimiter == null || delimiter.isEmpty() || "\u0001".equals(delimiter))
                ? rawMessage
                : rawMessage.replace(delimiter, "\u0001");
        if (!soh.endsWith("\u0001")) soh += "\u0001";

        Message message = new Message();
        String msgType = null;

        for (String pair : soh.split("\u0001", -1)) {
            int eq = pair.indexOf('=');
            if (eq < 1) continue;
            int tag;
            try { tag = Integer.parseInt(pair.substring(0, eq).trim()); }
            catch (NumberFormatException e) { continue; }
            String value = pair.substring(eq + 1);

            if (tag == MsgType.FIELD) { msgType = value; continue; }
            if (ENGINE_OWNED_TAGS.contains(tag)) continue;

            if (STANDARD_HEADER_TAGS.contains(tag)) {
                message.getHeader().setString(tag, value);
            } else {
                message.setString(tag, value);
            }
        }

        if (msgType == null) throw new IllegalArgumentException("FIX message is missing MsgType (tag 35)");
        message.getHeader().setString(MsgType.FIELD, msgType);

        try {
            log.info("Sending raw FIX message (MsgType={}) to session {}", msgType, sessionId);
            session.sendToTarget(message, sid);
        } catch (SessionNotFound e) {
            throw new RuntimeException("Session is not connected: " + sessionId, e);
        }
    }

    /** Tags the QFJ engine always overwrites; excluded from user-supplied raw messages. */
    private static final Set<Integer> ENGINE_OWNED_TAGS = Set.of(8, 9, 10, 34, 49, 52, 56);

    /** Standard FIX header tags (excluding engine-owned ones) that belong in the message header. */
    private static final Set<Integer> STANDARD_HEADER_TAGS = Set.of(
            43,  // PossDupFlag
            50,  // SenderSubID
            57,  // TargetSubID
            97,  // PossResend
            115, // OnBehalfOfCompID
            116, // DeliverToCompID
            122, // OrigSendingTime
            128, // DeliverToCompID (FIXT)
            129, // DeliverToSubID
            142, // SenderLocationID
            143, // TargetLocationID
            144, // OnBehalfOfLocationID
            145  // DeliverToLocationID
    );

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<SessionID> resolve(String sessionId) {
        return Optional.ofNullable(sessionIDs.get(sessionId));
    }

    private void populateFromSettings(SessionState state, SessionID sid) {
        if (settings == null) return;
        try {
            String type = settings.getString(sid, "ConnectionType");
            state.connectionType = capitalize(type);

            if ("initiator".equalsIgnoreCase(type)) {
                String host = safeGet(sid, "SocketConnectHost", "?");
                String port = safeGet(sid, "SocketConnectPort", "?");
                state.hostPort = host + ":" + port;
            } else {
                String port = safeGet(sid, "SocketAcceptPort", "?");
                state.hostPort = "0.0.0.0:" + port;
            }

            String hb = safeGet(sid, "HeartBtInt", "0");
            state.heartbeatSecs = Integer.parseInt(hb);

        } catch (Exception ignored) {}
    }

    private String safeGet(SessionID sid, String key, String fallback) {
        try {
            return settings.getString(sid, key);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
