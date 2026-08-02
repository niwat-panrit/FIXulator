package com.npsoftdev.fixsimulator.plugins.user.api;

import java.util.List;
import java.util.Optional;

/**
 * Handles user authentication and session tracking.
 */
public interface AuthService {

    /**
     * Authenticates a user by username and plaintext password.
     *
     * @return the authenticated {@link User} if credentials are valid and the
     *         account is active; {@link Optional#empty()} otherwise.
     */
    Optional<User> authenticate(String username, String password);

    /**
     * Returns {@code true} when the user has the given permission via at least
     * one of their assigned roles.
     */
    boolean hasPermission(User user, Permission permission);

    /** Returns the ordered list of all available role names. */
    List<String> getRoleNames();

    /**
     * Returns {@code true} when the user may open a new session.
     * Always {@code true} when the user's {@code maxSessions} is 0 (unlimited).
     */
    boolean canStartSession(String username);

    /** Records a new active session for {@code username}. */
    void registerSession(String username, String wicketSessionId);

    /** Removes a session by its owner username and session ID. */
    void unregisterSession(String username, String wicketSessionId);

    /**
     * Removes a session by its ID without knowing the owning username.
     * Called by the Wicket session-expiry listener.
     */
    void unregisterSessionById(String wicketSessionId);

    /** Returns the number of currently tracked sessions for the user. */
    int getActiveSessionCount(String username);
}
