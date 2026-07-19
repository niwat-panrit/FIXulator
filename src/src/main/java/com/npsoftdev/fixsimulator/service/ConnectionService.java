package com.npsoftdev.fixsimulator.service;

import java.io.Serializable;
import java.util.List;

/**
 * Manages FIX protocol session lifecycle and sequence numbers.
 */
public interface ConnectionService {

    /**
     * A snapshot of one FIX session's configuration and live state,
     * used to populate the Connection Management page.
     */
    record SessionDetails(
            String sessionId,
            String name,
            String fixVersion,        // e.g. "FIX.4.4"
            String senderCompID,
            String targetCompID,
            String connectionType,    // "Initiator" | "Acceptor"
            String hostPort,          // e.g. "localhost:9876"
            int    heartbeatSecs,
            String status,
            int    txSeq,
            int    rxSeq
    ) implements Serializable {}

    /**
     * Returns a {@link SessionDetails} snapshot for every registered session,
     * combining static configuration with live sequence numbers and status.
     */
    List<SessionDetails> listSessions();

    /** Returns the IDs of all configured FIX sessions. */
    List<String> listSessionIds();

    /** Returns the human-readable name of a session (e.g. "OrderRouter-01"). */
    String getSessionName(String sessionId);

    /** Returns the connection status string for a session (e.g. "CONNECTED"). */
    String getStatus(String sessionId);

    /** Initiates a FIX connection for the given session. */
    void connect(String sessionId);

    /** Terminates the FIX connection for the given session. */
    void disconnect(String sessionId);

    /** Returns the current outbound (TX) sequence number. */
    int getTxSequence(String sessionId);

    /** Returns the current inbound (RX) sequence number. */
    int getRxSequence(String sessionId);

    /** Resets TX and RX sequence numbers to 1. */
    void resetSequence(String sessionId);

    /** Sets the next outbound (TX) sequence number for the given session. */
    void setTxSequence(String sessionId, int nextNum);

    /** Sets the next inbound (RX) sequence number for the given session. */
    void setRxSequence(String sessionId, int nextNum);

    /** Parameters for creating a new FIX session at runtime. */
    record NewSessionRequest(
            String  connectionType,   // "Initiator" | "Acceptor"
            String  fixVersion,       // application version, e.g. "FIX.4.4" or "FIX.5.0"
            String  beginString,      // transport version: same as fixVersion for FIX 4.x; "FIXT.1.1" for FIX 5.0+
            String  senderCompID,
            String  targetCompID,
            String  host,             // connect host (initiator) or bind host (acceptor)
            int     port,
            int     heartbeatSecs,
            boolean resetOnLogon
    ) {}

    /** Adds and starts a new FIX session without restarting existing sessions. */
    void addSession(NewSessionRequest request);

    /** Removes the existing session identified by {@code sessionId} and replaces it with a new one. */
    void updateSession(String sessionId, NewSessionRequest request);

    /**
     * Disconnects (if currently connected) and permanently removes the session.
     * Any on-disk QuickFIX/J session files are archived with a {@code .deleted.{unix_timestamp}} suffix.
     */
    void deleteSession(String sessionId);

    /**
     * Parses {@code rawMessage} (fields delimited by {@code delimiter}) into a FIX
     * {@link quickfix.Message} and sends it to the given session.
     *
     * <p>When {@code replaceSessionFields} is {@code true} (the normal case), engine-owned
     * tags (8, 9, 10, 34, 49, 52, 56) are stripped and QuickFIX/J fills them from the live
     * session configuration.</p>
     *
     * <p>When {@code replaceSessionFields} is {@code false} (override mode), the user's
     * values for tags 49 (SenderCompID) and 56 (TargetCompID) are applied via the QFJ
     * {@code toApp} callback so they reach the wire verbatim.  Tags 8, 9, 10, 34, and 52
     * are still managed by QFJ.</p>
     *
     * @throws IllegalArgumentException if the session is not registered or tag 35 is absent
     * @throws RuntimeException         wrapping {@link quickfix.SessionNotFound} if the
     *                                  session is not connected
     */
    void sendRaw(String sessionId, String rawMessage, String delimiter, boolean replaceSessionFields);
}
