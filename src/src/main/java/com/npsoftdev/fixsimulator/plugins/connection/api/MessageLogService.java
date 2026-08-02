package com.npsoftdev.fixsimulator.plugins.connection.api;

import java.util.List;

/**
 * Provides access to the raw FIX message log for a session.
 */
public interface MessageLogService {

    /** Direction of a FIX message relative to this simulator. */
    enum Direction { SENT, RECEIVED, SYSTEM }

    /** An immutable snapshot of a single logged FIX message. */
    record LogEntry(
            Direction direction,
            String msgType,
            String rawMessage,
            java.time.Instant timestamp
    ) {}

    /** Returns all logged messages for the session, newest first. */
    List<LogEntry> getMessages(String sessionId);

    /** Returns only messages with the given direction. */
    List<LogEntry> getMessages(String sessionId, Direction direction);

    /** Clears the in-memory message log for the session. */
    void clearLog(String sessionId);

    /**
     * Records a synthetic system-level annotation (not a raw FIX message).
     * Used for events such as cache restore on startup.
     *
     * @param sessionId the session this annotation belongs to
     * @param msgType   a short category tag (e.g. {@code "CACHE"})
     * @param summary   human-readable description of the event
     */
    void recordSystem(String sessionId, String msgType, String summary);
}
