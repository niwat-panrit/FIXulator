package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.user.User;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

/**
 * Custom Wicket session that holds per-user UI state, most importantly
 * the ID of the currently selected (active) FIX session and the
 * authenticated user principal.
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

    /** The authenticated user, or {@code null} when not signed in. */
    private User authenticatedUser;

    // ── FIX Activity page filter state ────────────────────────────────────────

    /** Remembered direction filter for the FIX Activity page ("All", "Sent", "Received"). */
    private String activityDirection = "All";

    /** Whether heartbeat messages are hidden on the FIX Activity page. Defaults to {@code true}. */
    private boolean activityHideHeartbeats = true;

    public FixSimulatorSession(Request request) {
        super(request);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /** Returns {@code true} when a user is currently signed in. */
    public boolean isAuthenticated() {
        return authenticatedUser != null;
    }

    /** Returns the authenticated user, or {@code null} when not signed in. */
    public User getAuthenticatedUser() {
        return authenticatedUser;
    }

    /**
     * Signs in the given user and marks the session dirty so it is persisted.
     * Call {@link com.npsoftdev.fixsimulator.user.AuthService#registerSession} separately
     * to track this session in the session-limit counter.
     */
    public void signIn(User user) {
        this.authenticatedUser = user;
        dirty();
    }

    /**
     * Signs out the current user and marks the session dirty.
     * Call {@link com.npsoftdev.fixsimulator.user.AuthService#unregisterSession} first
     * to release the session-limit slot, then call this.
     */
    public void signOut() {
        this.authenticatedUser = null;
        dirty();
    }

    // ── Active FIX session ────────────────────────────────────────────────────

    /** Returns the ID of the active FIX session, or {@code null} if none selected yet. */
    public String getActiveSessionId() {
        return activeSessionId;
    }

    /** Stores {@code sessionId} as the active session and marks the Wicket session dirty. */
    public void setActiveSessionId(String sessionId) {
        this.activeSessionId = sessionId;
        dirty();
    }

    // ── Template capture flow ─────────────────────────────────────────────────

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

    public String getActivityDirection() { return activityDirection; }

    public void setActivityDirection(String direction) {
        this.activityDirection = direction;
        dirty();
    }

    public boolean isActivityHideHeartbeats() { return activityHideHeartbeats; }

    public void setActivityHideHeartbeats(boolean hide) {
        this.activityHideHeartbeats = hide;
        dirty();
    }

    /** Convenience accessor so components can call {@code FixSimulatorSession.get()} without casting. */
    public static FixSimulatorSession get() {
        return (FixSimulatorSession) WebSession.get();
    }
}
