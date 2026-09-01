package app.getarcane.android.ui.screens.settings

import app.getarcane.android.nav.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRouteSafetyTest {
    @Test
    fun adminDetailRoutesResolveToTheirOwningTab() {
        assertEquals(AppTab.Users, settingsRouteAccessOwner(SettingsRoutes.USER_DETAIL))
        assertEquals(AppTab.Users, settingsRouteAccessOwner(SettingsRoutes.USER_ROLE_ASSIGNMENTS))
        assertEquals(AppTab.Notifications, settingsRouteAccessOwner(SettingsRoutes.NOTIFICATION_PROVIDER))
        assertEquals(AppTab.SystemSettings, settingsRouteAccessOwner(SettingsRoutes.SYSTEM_CATEGORY))
        assertEquals(AppTab.SystemSettings, settingsRouteAccessOwner(SettingsRoutes.UPGRADE))
        assertEquals(AppTab.Roles, settingsRouteAccessOwner(SettingsRoutes.ROLE_CREATE))
        assertEquals(AppTab.Roles, settingsRouteAccessOwner(SettingsRoutes.ROLE_DETAIL))
    }

    @Test
    fun authorizationLossResetsProtectedSettingsRoutes() {
        assertTrue(
            shouldResetUnauthorizedSettingsRoute(
                route = SettingsRoutes.USER_DETAIL,
                isAdmin = false,
                supportsV2 = true,
            ),
        )
        assertTrue(
            shouldResetUnauthorizedSettingsRoute(
                route = SettingsRoutes.ROLE_DETAIL,
                isAdmin = true,
                supportsV2 = false,
            ),
        )
        assertFalse(
            shouldResetUnauthorizedSettingsRoute(
                route = SettingsRoutes.ROLE_DETAIL,
                isAdmin = true,
                supportsV2 = true,
            ),
        )
        assertFalse(
            shouldResetUnauthorizedSettingsRoute(
                route = SettingsRoutes.APPEARANCE,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun onlyEnvironmentBoundDetailsResetWhenEnvironmentChanges() {
        assertTrue(isEnvironmentScopedSettingsDetail(SettingsRoutes.NOTIFICATION_PROVIDER))
        assertTrue(isEnvironmentScopedSettingsDetail(SettingsRoutes.SYSTEM_CATEGORY))
        assertTrue(isEnvironmentScopedSettingsDetail(SettingsRoutes.UPGRADE))
        assertFalse(isEnvironmentScopedSettingsDetail(SettingsRoutes.USER_DETAIL))
        assertFalse(isEnvironmentScopedSettingsDetail(SettingsRoutes.ROLE_DETAIL))
        assertFalse(isEnvironmentScopedSettingsDetail(AppTab.Notifications.id))
    }
}
