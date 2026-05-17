package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.ConnectionService;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    /** May be {@code null} when constructed without settings (e.g. in tests). */
    private final SessionSettings settings;

    // ── Constructor ───────────────────────────────────────────────────────────

    public GatewayConnectionService(Map<String, SessionID> sessionIDs,
                                    SessionFacade session,
                                    SessionSettings settings) {
        this.sessionIDs = sessionIDs;
        this.session    = session;
        this.settings   = settings;
    }

    // ── Callbacks from DefaultFixGatewayPlugin ────────────────────────────────

    public void onSessionCreated(SessionID sid) {
        SessionState state = new SessionState(sid);
        populateFromSettings(state, sid);
        states.put(sid.toString(), state);
    }

    public void onLogon(SessionID sid) {
        states.computeIfAbsent(sid.toString(), k -> {
            SessionState s = new SessionState(sid);
            populateFromSettings(s, sid);
            return s;
        }).status = SessionStatus.CONNECTED;
    }

    public void onLogout(SessionID sid) {
        SessionState s = states.get(sid.toString());
        if (s != null) s.status = SessionStatus.DISCONNECTED;
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
        resolve(sessionId).ifPresent(session::logon);
    }

    @Override
    public void disconnect(String sessionId) {
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
    public void resetSequence(String sessionId) {
        resolve(sessionId).ifPresent(sid -> {
            try {
                session.reset(sid);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset sequence for session: " + sessionId, e);
            }
        });
    }

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
