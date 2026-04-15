package com.npsoftdev.fixsimulator.service;

import java.util.List;

/**
 * Provides access to application-level system events and log entries.
 */
public interface SystemLogService {

    /** Severity levels for system log entries. */
    enum Level { DEBUG, INFO, WARN, ERROR }

    /** An immutable snapshot of a single system log entry. */
    record LogEntry(
            Level level,
            String logger,
            String message,
            java.time.Instant timestamp
    ) {}

    /** Returns all system log entries, newest first. */
    List<LogEntry> getEntries();

    /** Returns entries at or above the given severity level. */
    List<LogEntry> getEntries(Level minLevel);

    /** Clears the in-memory log buffer. */
    void clearEntries();
}
