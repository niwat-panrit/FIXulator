package com.npsoftdev.fixsimulator.user;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of built-in roles and their associated permissions.
 *
 * <p>Two built-in roles exist:
 * <ul>
 *   <li><b>Admin</b> — can manage users and view system logs.</li>
 *   <li><b>Tester</b> — can use all FIX testing features.</li>
 * </ul>
 *
 * <p>A user's effective permissions are the union of all their assigned roles.
 */
public final class RoleRegistry implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ADMIN  = "Admin";
    public static final String TESTER = "Tester";

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            ADMIN, EnumSet.of(
                    Permission.VIEW_MANAGE_USERS,
                    Permission.VIEW_SYSTEM_LOGS
            ),
            TESTER, EnumSet.of(
                    Permission.VIEW_SEND_MANAGE_ORDERS,
                    Permission.VIEW_MANAGE_TRADES,
                    Permission.VIEW_FIX_ACTIVITIES,
                    Permission.VIEW_MANAGE_FIX_CONNECTIONS,
                    Permission.USE_VIEW_MANAGE_FIX_TEMPLATES,
                    Permission.USE_VIEW_MANAGE_DYNAMIC_VALUES,
                    Permission.USE_VIEW_MANAGE_VALUE_MAPPINGS
            )
    );

    /** Returns the ordered list of all valid role names. */
    public List<String> getRoleNames() {
        return List.of(ADMIN, TESTER);
    }

    /** Returns the permissions granted by {@code roleName}, or an empty set for unknown roles. */
    public Set<Permission> getPermissions(String roleName) {
        return ROLE_PERMISSIONS.getOrDefault(roleName, Set.of());
    }

    /**
     * Returns {@code true} when the user holds at least one role that grants {@code permission}.
     */
    public boolean hasPermission(User user, Permission permission) {
        if (user == null || permission == null) return false;
        for (String role : user.roles()) {
            if (getPermissions(role).contains(permission)) return true;
        }
        return false;
    }
}
