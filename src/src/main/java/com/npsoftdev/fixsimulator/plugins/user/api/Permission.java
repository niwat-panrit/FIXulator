package com.npsoftdev.fixsimulator.plugins.user.api;

/**
 * Granular permissions granted to users via their assigned roles.
 */
public enum Permission {

    // ── Admin role permissions ─────────────────────────────────────────────────
    VIEW_MANAGE_USERS("Manage Users"),
    VIEW_SYSTEM_LOGS("View System Logs"),

    // ── Tester role permissions ────────────────────────────────────────────────
    VIEW_SEND_MANAGE_ORDERS("Manage Orders"),
    VIEW_MANAGE_TRADES("View/Manage Trades"),
    VIEW_FIX_ACTIVITIES("View FIX Activities"),
    VIEW_MANAGE_FIX_CONNECTIONS("Manage FIX Connections"),
    USE_VIEW_MANAGE_FIX_TEMPLATES("Manage FIX Templates"),
    USE_VIEW_MANAGE_DYNAMIC_VALUES("Manage Dynamic Values"),
    USE_VIEW_MANAGE_VALUE_MAPPINGS("Manage Value Mappings");

    private final String displayName;

    Permission(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
