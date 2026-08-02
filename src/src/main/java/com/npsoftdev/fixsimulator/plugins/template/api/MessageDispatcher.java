package com.npsoftdev.fixsimulator.plugins.template.api;

import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.io.Serializable;

/**
 * Narrow port for dispatching a built FIX message onto a session — the only
 * piece of transport surface {@link TemplateService} needs.
 *
 * <p>Production wiring binds this to
 * {@link com.npsoftdev.fixsimulator.plugins.connection.api.SessionFacade#sendToTarget}.
 * Tests inject a lambda that captures the outgoing message.</p>
 */
@FunctionalInterface
public interface MessageDispatcher extends Serializable {

    void dispatch(Message message, SessionID sessionID) throws SessionNotFound;
}
