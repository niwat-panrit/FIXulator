package com.npsoftdev.fixsimulator.core.ui;

import com.npsoftdev.fixsimulator.plugins.user.api.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.core.logging.SystemLogsPage;
import com.npsoftdev.fixsimulator.plugins.connection.ui.ConnectionManagementPage;
import com.npsoftdev.fixsimulator.plugins.connection.ui.FixActivityPage;
import com.npsoftdev.fixsimulator.plugins.order.ui.OrdersPage;
import com.npsoftdev.fixsimulator.plugins.order.ui.TradesPage;
import com.npsoftdev.fixsimulator.plugins.template.ui.DynamicValuesPage;
import com.npsoftdev.fixsimulator.plugins.template.ui.FixMessageTemplateFormPage;
import com.npsoftdev.fixsimulator.plugins.template.ui.FixMessageTemplatesPage;
import com.npsoftdev.fixsimulator.plugins.template.ui.ValueMappingsPage;
import com.npsoftdev.fixsimulator.plugins.user.ui.UserManagementPage;

class PagePermissionsTest {

    // ── Admin-only pages ──────────────────────────────────────────────────────

    @Test
    void userManagementPage_requiresViewManageUsers() {
        assertEquals(Permission.VIEW_MANAGE_USERS,
                PagePermissions.forPage(UserManagementPage.class));
    }

    @Test
    void systemLogsPage_requiresViewSystemLogs() {
        assertEquals(Permission.VIEW_SYSTEM_LOGS,
                PagePermissions.forPage(SystemLogsPage.class));
    }

    // ── Tester-only pages ─────────────────────────────────────────────────────

    @Test
    void ordersPage_requiresViewSendManageOrders() {
        assertEquals(Permission.VIEW_SEND_MANAGE_ORDERS,
                PagePermissions.forPage(OrdersPage.class));
    }

    @Test
    void tradesPage_requiresViewManageTrades() {
        assertEquals(Permission.VIEW_MANAGE_TRADES,
                PagePermissions.forPage(TradesPage.class));
    }

    @Test
    void fixActivityPage_requiresViewFixActivities() {
        assertEquals(Permission.VIEW_FIX_ACTIVITIES,
                PagePermissions.forPage(FixActivityPage.class));
    }

    @Test
    void connectionManagementPage_requiresViewManageFixConnections() {
        assertEquals(Permission.VIEW_MANAGE_FIX_CONNECTIONS,
                PagePermissions.forPage(ConnectionManagementPage.class));
    }

    @Test
    void fixMessageTemplatesPage_requiresUseViewManageFixTemplates() {
        assertEquals(Permission.USE_VIEW_MANAGE_FIX_TEMPLATES,
                PagePermissions.forPage(FixMessageTemplatesPage.class));
    }

    @Test
    void fixMessageTemplateFormPage_requiresUseViewManageFixTemplates() {
        assertEquals(Permission.USE_VIEW_MANAGE_FIX_TEMPLATES,
                PagePermissions.forPage(FixMessageTemplateFormPage.class));
    }

    @Test
    void dynamicValuesPage_requiresUseViewManageDynamicValues() {
        assertEquals(Permission.USE_VIEW_MANAGE_DYNAMIC_VALUES,
                PagePermissions.forPage(DynamicValuesPage.class));
    }

    @Test
    void valueMappingsPage_requiresUseViewManageValueMappings() {
        assertEquals(Permission.USE_VIEW_MANAGE_VALUE_MAPPINGS,
                PagePermissions.forPage(ValueMappingsPage.class));
    }

    // ── Open pages (no additional permission — any authenticated user) ─────────

    @Test
    void homePage_returnsNull_anyAuthenticatedUserAllowed() {
        assertNull(PagePermissions.forPage(HomePage.class),
                "HomePage must be accessible to any authenticated user");
    }

    @Test
    void unknownPage_returnsNull() {
        assertNull(PagePermissions.forPage(BasePage.class));
    }
}
