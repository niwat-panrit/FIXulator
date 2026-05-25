package com.npsoftdev.fixsimulator.gateway;

import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

/**
 * Thin abstraction over QuickFIX/J static session operations.
 *
 * <p>Production code uses {@link LiveSessionFacade}.  Tests inject a mock or stub
 * so that no running FIX engine is required.</p>
 */
public interface SessionFacade {

    void logon(SessionID sessionID);

    void logout(SessionID sessionID);

    /** Returns the next expected outbound sequence number, or 0 if the session is unavailable. */
    int getExpectedSenderNum(SessionID sessionID);

    /** Returns the next expected inbound sequence number, or 0 if the session is unavailable. */
    int getExpectedTargetNum(SessionID sessionID);

    /** Resets sequence numbers to 1. */
    void reset(SessionID sessionID) throws Exception;

    /** Sets the next outbound (TX) sequence number. */
    void setNextSenderNum(SessionID sessionID, int nextNum) throws Exception;

    /** Sets the next inbound (RX) sequence number. */
    void setNextTargetNum(SessionID sessionID, int nextNum) throws Exception;

    void sendToTarget(Message message, SessionID sessionID) throws SessionNotFound;
}
