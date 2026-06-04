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

    /**
     * Transient raw FIX message string set by the template-list page when the user
     * requests "Create Template from FIX Message".  Consumed (and cleared) by the
     * form page on its first load so it is never re-used across navigations.
     */
    private String pendingFixMessage;

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

    /** Stores a raw FIX message to be parsed into a template on the next page load. */
    public void setPendingFixMessage(String raw) {
        this.pendingFixMessage = raw;
        dirty();
    }

    /**
     * Returns and clears the pending FIX message.
     * Returns {@code null} if no message is waiting.
     */
    public String takePendingFixMessage() {
        String v = pendingFixMessage;
        pendingFixMessage = null;
        dirty();
        return v;
    }

    /** Convenience accessor so components can call {@code FixSimulatorSession.get()} without casting. */
    public static FixSimulatorSession get() {
        return (FixSimulatorSession) WebSession.get();
    }
}
