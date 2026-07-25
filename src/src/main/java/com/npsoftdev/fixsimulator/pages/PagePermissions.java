package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.user.Permission;

import java.util.HashMap;
import java.util.Map;

/**
 * Static lookup: page class → minimum {@link Permission} required.
 *
 * <p>{@code null} means "any authenticated user may access this page"
 * (no role-level permission needed — authentication alone is sufficient).</p>
 */
public final class PagePermissions {

    private static final Map<Class<? extends BasePage>, Permission> REQUIRED = new HashMap<>();

    static {
        REQUIRED.put(UserManagementPage.class,        Permission.VIEW_MANAGE_USERS);
        REQUIRED.put(SystemLogsPage.class,            Permission.VIEW_SYSTEM_LOGS);
        REQUIRED.put(OrdersPage.class,                Permission.VIEW_SEND_MANAGE_ORDERS);
        REQUIRED.put(TradesPage.class,                Permission.VIEW_MANAGE_TRADES);
        REQUIRED.put(FixActivityPage.class,           Permission.VIEW_FIX_ACTIVITIES);
        REQUIRED.put(ConnectionManagementPage.class,  Permission.VIEW_MANAGE_FIX_CONNECTIONS);
        REQUIRED.put(FixMessageTemplatesPage.class,   Permission.USE_VIEW_MANAGE_FIX_TEMPLATES);
        REQUIRED.put(FixMessageTemplateFormPage.class, Permission.USE_VIEW_MANAGE_FIX_TEMPLATES);
        REQUIRED.put(DynamicValuesPage.class,         Permission.USE_VIEW_MANAGE_DYNAMIC_VALUES);
        REQUIRED.put(ValueMappingsPage.class,         Permission.USE_VIEW_MANAGE_VALUE_MAPPINGS);
        REQUIRED.put(OrderTemplatesPage.class,        Permission.USE_VIEW_MANAGE_FIX_TEMPLATES);
        // HomePage → null (no specific permission required, any authenticated user)
    }

    /**
     * Returns the required permission for the given page class,
     * or {@code null} when any authenticated user may access it.
     */
    public static Permission forPage(Class<?> pageClass) {
        return REQUIRED.get(pageClass);
    }

    private PagePermissions() {}
}
