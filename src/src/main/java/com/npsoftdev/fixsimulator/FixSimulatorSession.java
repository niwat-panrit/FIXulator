package com.npsoftdev.fixsimulator;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

/**
 * Custom Wicket session that holds per-user UI state, most importantly
 * the ID of the currently selected (active) FIX session.
 */
public class FixSimulatorSession extends WebSession {

    private static final long serialVersionUID = 1L;

    /** Session ID string of the connection the user has selected as active. May be {@code null}. */
    private String activeSessionId;

    public FixSimulatorSession(Request request) {
        super(request);
    }

    /** Returns the ID of the active FIX session, or {@code null} if none selected yet. */
    public String getActiveSessionId() {
        return activeSessionId;
    }

    /** Stores {@code sessionId} as the active session and marks the Wicket session dirty. */
    public void setActiveSessionId(String sessionId) {
        this.activeSessionId = sessionId;
        dirty();
    }

    /** Convenience accessor so components can call {@code FixSimulatorSession.get()} without casting. */
    public static FixSimulatorSession get() {
        return (FixSimulatorSession) WebSession.get();
    }
}
