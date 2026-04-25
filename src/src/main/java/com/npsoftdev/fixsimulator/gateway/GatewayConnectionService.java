package com.npsoftdev.fixsimulator.gateway;

import com.npsoftdev.fixsimulator.service.ConnectionService;
import quickfix.SessionID;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ConnectionService} implementation backed by live QuickFIX/J sessions.
 *
 * <p>Session state is updated via callbacks invoked by
 * {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin} from within
 * the QuickFIX/J {@code Application} lifecycle methods.</p>
 *
 * <p>All I/O operations are delegated to a {@link SessionFacade} so the class
 * can be fully exercised in unit tests without a running FIX engine.</p>
 */
public class GatewayConnectionService implements ConnectionService, Serializable {

    private static final long serialVersionUID = 1L;

    private enum SessionStatus { CREATED, CONNECTED, DISCONNECTED }

    private static final class SessionState implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        volatile SessionStatus status = SessionStatus.CREATED;

        SessionState(SessionID sid) {
            this.name = sid.getSenderCompID() + " \u2192 " + sid.getTargetCompID();
        }
    }

    /** Shared session-ID registry populated by {@link com.npsoftdev.fixsimulator.plugin.DefaultFixGatewayPlugin}. */
    private final Map<String, SessionID> sessionIDs;
    private final Map<String, SessionState> states = new ConcurrentHashMap<>();
    private final SessionFacade session;

    public GatewayConnectionService(Map<String, SessionID> sessionIDs, SessionFacade session) {
        this.sessionIDs = sessionIDs;
        this.session    = session;
    }

    // ── Callbacks from DefaultFixGatewayPlugin ────────────────────────────────

    public void onSessionCreated(SessionID sid) {
        states.put(sid.toString(), new SessionState(sid));
    }

    public void onLogon(SessionID sid) {
        states.computeIfAbsent(sid.toString(), k -> new SessionState(sid))
              .status = SessionStatus.CONNECTED;
    }

    public void onLogout(SessionID sid) {
        SessionState s = states.get(sid.toString());
        if (s != null) s.status = SessionStatus.DISCONNECTED;
    }

    // ── ConnectionService ─────────────────────────────────────────────────────

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

    private java.util.Optional<SessionID> resolve(String sessionId) {
        return java.util.Optional.ofNullable(sessionIDs.get(sessionId));
    }
}
