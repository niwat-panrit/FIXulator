package com.npsoftdev.fixsimulator.plugins.connection.api;

import java.net.BindException;

/**
 * A FIX session could not be started, with a message fit to show a user.
 *
 * <p>The common case by far is an acceptor whose listen port is already taken —
 * by another application, or by another acceptor session in this app. That is a
 * configuration mistake, not a fault: the session's configuration is kept, and
 * the user can free the port and press Listen again, or edit the session to use
 * a different port.</p>
 */
public class SessionStartException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean portInUse;

    public SessionStartException(String message, boolean portInUse, Throwable cause) {
        super(message, cause);
        this.portInUse = portInUse;
    }

    /** Whether the start failed because the port was already bound. */
    public boolean isPortInUse() {
        return portInUse;
    }

    /**
     * Whether {@code t}, or anything that caused it, is a failure to bind a port.
     * QuickFIX/J reports this as a {@code quickfix.RuntimeError} wrapping an
     * {@code IOException} wrapping a {@link BindException}, so the whole chain
     * has to be walked.
     */
    public static boolean isAddressInUse(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof BindException) return true;
            String message = c.getMessage();
            if (message != null && message.toLowerCase().contains("address already in use")) {
                return true;
            }
            if (c.getCause() == c) break;   // defensive: self-referencing cause
        }
        return false;
    }
}
