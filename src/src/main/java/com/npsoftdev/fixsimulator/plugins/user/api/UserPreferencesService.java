package com.npsoftdev.fixsimulator.plugins.user.api;

import java.util.Optional;

/**
 * Persists per-user UI preferences across sessions and application restarts.
 */
public interface UserPreferencesService {

    /**
     * Returns the FIX session ID that the user last had selected, or empty if
     * none was ever saved (or the stored value was cleared).
     */
    Optional<String> getLastActiveSession(String username);

    /**
     * Records {@code sessionId} as the user's most-recently selected FIX session.
     * Pass {@code null} to clear the stored value.
     */
    void setLastActiveSession(String username, String sessionId);
}
