package com.npsoftdev.fixsimulator.plugins.template.api;

import quickfix.SessionID;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Visibility scope of a {@link FixMessageTemplate}.
 *
 * <p>A template can be either:
 * <ul>
 *   <li>{@link Global} — visible to every FIX session.</li>
 *   <li>{@link Sessions} — visible only to sessions whose IDs are in the set.</li>
 * </ul>
 *
 * <p>Use {@link #appliesTo(SessionID)} when filtering templates for a given session;
 * {@link TemplateRepository#findVisibleTo(SessionID)} relies on this contract.</p>
 */
public sealed interface TemplateScope extends Serializable
        permits TemplateScope.Global, TemplateScope.Sessions {

    /** True if this template is visible to the given session. */
    boolean appliesTo(SessionID sessionID);

    /** Returns the singleton {@link Global} scope. */
    static TemplateScope global() {
        return Global.INSTANCE;
    }

    /** Returns a {@link Sessions} scope visible to all of the given session IDs. */
    static TemplateScope sessions(List<String> sessionIds) {
        return new Sessions(sessionIds);
    }

    /**
     * Convenience factory for a single-session scope.
     * Kept for internal use (e.g. YAML migration of old single-{@code sessionId} entries).
     */
    static TemplateScope session(String sessionId) {
        return new Sessions(List.of(sessionId));
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

    /**
     * Visible only to sessions whose string ID is contained in {@link #sessionIds()}.
     * Supports one or more sessions.
     */
    final class Sessions implements TemplateScope {
        private static final long serialVersionUID = 1L;

        private final List<String> sessionIds;

        public Sessions(List<String> sessionIds) {
            Objects.requireNonNull(sessionIds, "sessionIds");
            this.sessionIds = List.copyOf(sessionIds); // defensive immutable copy
        }

        public List<String> sessionIds() { return sessionIds; }

        @Override
        public boolean appliesTo(SessionID sessionID) {
            return sessionID != null && sessionIds.contains(sessionID.toString());
        }

        @Override
        public String toString() {
            if (sessionIds.isEmpty()) return "Sessions (none)";
            if (sessionIds.size() == 1) return sessionIds.get(0);
            return "Sessions (" + sessionIds.size() + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sessions that)) return false;
            return sessionIds.equals(that.sessionIds);
        }

        @Override
        public int hashCode() { return sessionIds.hashCode(); }
    }
}
