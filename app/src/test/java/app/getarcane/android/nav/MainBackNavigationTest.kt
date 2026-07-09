package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class MainBackNavigationTest {
    @Test
    fun dashboardRootLetsActivityHandleBack() {
        assertEquals(
            MainBackNavigation.Action.LetActivityHandle,
            MainBackNavigation.resolve(AppTab.Dashboard.id),
        )
    }

    @Test
    fun dashboardHostedDetailSwitchesBackToDashboardBeforeActivityExit() {
        assertEquals(
            MainBackNavigation.Action.SwitchToDashboard,
            MainBackNavigation.resolve(
                selectedTabId = AppTab.Dashboard.id,
                hasDashboardOpenTarget = true,
            ),
        )
    }

    @Test
    fun nonDashboardTabRootSwitchesToDashboardBeforeActivityExit() {
        assertEquals(
            MainBackNavigation.Action.SwitchToDashboard,
            MainBackNavigation.resolve(AppTab.Containers.id),
        )
    }

    @Test
    fun settingsRootSwitchesToDashboardBeforeActivityExit() {
        assertEquals(
            MainBackNavigation.Action.SwitchToDashboard,
            MainBackNavigation.resolve("settings"),
        )
    }

    @Test
    fun unavailableOrUnknownRootFallsBackToDashboard() {
        assertEquals(
            MainBackNavigation.Action.SwitchToDashboard,
            MainBackNavigation.resolve("removed-admin-tab"),
        )
    }
}
