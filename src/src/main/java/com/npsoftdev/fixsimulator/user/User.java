package com.npsoftdev.fixsimulator.user;

import java.io.Serializable;
import java.util.Objects;

/**
 * A user account in the FIX Simulator.
 *
 * <p>Instances are immutable. Use {@link #toBuilder()} to derive a modified
 * copy (e.g. when updating a password or toggling the active flag).</p>
 */
public final class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String  username;
    private final String  displayName;
    /** Bcrypt / plain-text hash. {@code null} means no password set. */
    private final String  passwordHash;
    private final String  role;
    private final boolean active;

    private User(Builder b) {
        this.username     = Objects.requireNonNull(b.username, "username");
        this.displayName  = b.displayName != null ? b.displayName : b.username;
        this.passwordHash = b.passwordHash;
        this.role         = b.role != null ? b.role : "OPERATOR";
        this.active       = b.active;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String  username()     { return username; }
    public String  displayName()  { return displayName; }
    public String  passwordHash() { return passwordHash; }
    public String  role()         { return role; }
    public boolean isActive()     { return active; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder()  { return new Builder(); }
    public Builder        toBuilder() {
        return new Builder()
                .username(username).displayName(displayName)
                .passwordHash(passwordHash).role(role).active(active);
    }

    public static final class Builder {
        private String  username;
        private String  displayName;
        private String  passwordHash;
        private String  role   = "OPERATOR";
        private boolean active = true;

        public Builder username(String v)     { this.username     = v; return this; }
        public Builder displayName(String v)  { this.displayName  = v; return this; }
        public Builder passwordHash(String v) { this.passwordHash = v; return this; }
        public Builder role(String v)         { this.role         = v; return this; }
        public Builder active(boolean v)      { this.active       = v; return this; }

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
    @Override public String toString()  { return "User{username=" + username + ", role=" + role + "}"; }
}
