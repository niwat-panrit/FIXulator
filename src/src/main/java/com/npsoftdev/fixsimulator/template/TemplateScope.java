package com.npsoftdev.fixsimulator.template;

import quickfix.SessionID;

import java.io.Serializable;
import java.util.Objects;

/**
 * Visibility scope of a {@link FixMessageTemplate}.
 *
 * <p>A template can be either:
 * <ul>
 *   <li>{@link Global} — visible to every FIX session.</li>
 *   <li>{@link Session} — visible only to the session whose ID string matches.</li>
 * </ul>
 *
 * <p>Use {@link #appliesTo(SessionID)} when filtering templates for a given session;
 * {@link TemplateRepository#findVisibleTo(SessionID)} relies on this contract.</p>
 */
public sealed interface TemplateScope extends Serializable
        permits TemplateScope.Global, TemplateScope.Session {

    /** True if this template is visible to the given session. */
    boolean appliesTo(SessionID sessionID);

    /** Returns the singleton {@link Global} scope. */
    static TemplateScope global() {
        return Global.INSTANCE;
    }

    /** Returns a {@link Session} scope bound to the given session-ID string. */
    static TemplateScope session(String sessionId) {
        return new Session(sessionId);
    }

    /** Visible to every session. */
    final class Global implements TemplateScope {
        private static final long serialVersionUID = 1L;
        private static final Global INSTANCE = new Global();
        private Global() {}

        @Override public boolean appliesTo(SessionID sessionID) { return true; }
        @Override public String toString() { return "Global"; }

        // Serialization: always resolve to the singleton.
        private Object readResolve() { return INSTANCE; }
    }

    /** Visible only to the session whose ID string matches {@link #sessionId()}. */
    record Session(String sessionId) implements TemplateScope {
        private static final long serialVersionUID = 1L;

        public Session {
            Objects.requireNonNull(sessionId, "sessionId");
        }

        @Override
        public boolean appliesTo(SessionID sessionID) {
            return sessionID != null && sessionId.equals(sessionID.toString());
        }
    }
}
