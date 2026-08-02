package com.npsoftdev.fixsimulator.plugins.connection.internal;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.io.Serializable;
import com.npsoftdev.fixsimulator.plugins.connection.api.SessionFacade;

/**
 * Production {@link SessionFacade} that delegates to the live QuickFIX/J
 * {@link Session} static methods.
 *
 * <p>Must be serializable because {@link GatewayConnectionService} (which holds a
 * reference to this) is captured by Wicket page components that Wicket serialises
 * to its page store on every request.</p>
 */
public class LiveSessionFacade implements SessionFacade, Serializable {

    private static final long serialVersionUID = 1L;

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
    public void setNextSenderNum(SessionID sessionID, int nextNum) throws Exception {
        Session session = Session.lookupSession(sessionID);
        if (session != null) session.setNextSenderMsgSeqNum(nextNum);
    }

    @Override
    public void setNextTargetNum(SessionID sessionID, int nextNum) throws Exception {
        Session session = Session.lookupSession(sessionID);
        if (session != null) session.setNextTargetMsgSeqNum(nextNum);
    }

    @Override
    public void sendToTarget(Message message, SessionID sessionID) throws SessionNotFound {
        Session.sendToTarget(message, sessionID);
    }
}
