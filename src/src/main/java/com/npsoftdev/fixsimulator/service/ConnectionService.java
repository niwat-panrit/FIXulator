package com.npsoftdev.fixsimulator.service;

import java.util.List;

/**
 * Manages FIX protocol session lifecycle and sequence numbers.
 */
public interface ConnectionService {

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
}
