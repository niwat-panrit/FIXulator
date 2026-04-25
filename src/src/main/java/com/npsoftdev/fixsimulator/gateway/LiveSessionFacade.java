package com.npsoftdev.fixsimulator.gateway;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;

/**
 * Production {@link SessionFacade} that delegates to the live QuickFIX/J
 * {@link Session} static methods.
 */
public class LiveSessionFacade implements SessionFacade {

    @Override
    public void logon(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        if (session != null) session.logon();
    }

    @Override
    public void logout(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        if (session != null) session.logout();
    }

    @Override
    public int getExpectedSenderNum(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        try {
            return session != null ? session.getExpectedSenderNum() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getExpectedTargetNum(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        try {
            return session != null ? session.getExpectedTargetNum() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void reset(SessionID sessionID) throws Exception {
        Session session = Session.lookupSession(sessionID);
        if (session != null) session.reset();
    }

    @Override
    public void sendToTarget(Message message, SessionID sessionID) throws SessionNotFound {
        Session.sendToTarget(message, sessionID);
    }
}
