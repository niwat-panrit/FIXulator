package com.npsoftdev.fixsimulator.plugins.user.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A user account in the FIX Simulator.
 *
 * <p>Instances are immutable. Use {@link #toBuilder()} to derive a modified
 * copy (e.g. when updating a password or toggling the active flag).</p>
 */
public final class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String       username;
    private final String       displayName;
    /** Bcrypt hash. {@code null} means no password set. */
    private final String       passwordHash;
    private final String       email;
    private final List<String> roles;
    private final boolean      active;
    /** Maximum concurrent sessions. 0 = unlimited. */
    private final int          maxSessions;
    /** IANA timezone ID (e.g. "Asia/Bangkok"). {@code null} defaults to UTC. */
    private final String       timezone;

    private User(Builder b) {
        this.username     = Objects.requireNonNull(b.username, "username");
        this.displayName  = b.displayName != null ? b.displayName : b.username;
        this.passwordHash = b.passwordHash;
        this.email        = b.email;
        this.roles        = b.roles != null
                ? Collections.unmodifiableList(List.copyOf(b.roles))
                : Collections.emptyList();
        this.active       = b.active;
        this.maxSessions  = Math.max(0, b.maxSessions);
        this.timezone     = b.timezone;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       username()     { return username; }
    public String       displayName()  { return displayName; }
    public String       passwordHash() { return passwordHash; }
    public String       email()        { return email; }
    public List<String> roles()        { return roles; }
    public boolean      isActive()     { return active; }
    /** Maximum concurrent sessions; {@code 0} means unlimited. */
    public int          maxSessions()  { return maxSessions; }
    /** IANA timezone ID, or {@code null} meaning UTC. */
    public String       timezone()     { return timezone; }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder()   { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
                .username(username).displayName(displayName)
                .passwordHash(passwordHash).email(email)
                .roles(roles).active(active).maxSessions(maxSessions)
                .timezone(timezone);
    }

    public static final class Builder {
        private String       username;
        private String       displayName;
        private String       passwordHash;
        private String       email;
        private List<String> roles       = Collections.emptyList();
        private boolean      active      = true;
        private int          maxSessions = 0;
        private String       timezone;

        public Builder username(String v)       { this.username     = v; return this; }
        public Builder displayName(String v)    { this.displayName  = v; return this; }
        public Builder passwordHash(String v)   { this.passwordHash = v; return this; }
        public Builder email(String v)          { this.email        = v; return this; }
        public Builder roles(List<String> v)    { this.roles        = v; return this; }
        public Builder active(boolean v)        { this.active       = v; return this; }
        public Builder maxSessions(int v)       { this.maxSessions  = v; return this; }
        public Builder timezone(String v)       { this.timezone     = v; return this; }

        public User build() { return new User(this); }
    }

    // ── Object ────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return username.equals(that.username);
    }

    @Override public int    hashCode() { return username.hashCode(); }
    @Override public String toString()  {
        return "User{username=" + username + ", roles=" + roles + "}";
    }
}
