package com.npsoftdev.fixsimulator.plugins.connection.api;

import quickfix.Message;
import quickfix.SessionID;

/**
 * Observer for FIX application-level messages flowing through
 * {@link com.npsoftdev.fixsimulator.plugins.connection.DefaultFixGatewayPlugin}.
 *
 * <p>Listeners are registered via
 * {@link com.npsoftdev.fixsimulator.plugins.connection.DefaultFixGatewayPlugin#addMessageListener}.
 * The gateway calls these methods from within the QuickFIX/J
 * {@code toApp} / {@code fromApp} callbacks, so implementations must be
 * thread-safe.</p>
 *
 * <p>Both methods have default no-op implementations — override only what you need.</p>
 */
public interface FixMessageListener {

    /** Called after every outbound application-level message ({@code toApp}). */
    default void onOutbound(SessionID sessionID, Message message) {}

    /** Called after every inbound application-level message ({@code fromApp}). */
    default void onInbound(SessionID sessionID, Message message) {}
}
