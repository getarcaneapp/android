package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminTabNavigationTest {
    @Test
    fun drillDownAdminTabsUseTheirOwnSettingsNavigationRoot() {
        listOf(
            AppTab.Users,
            AppTab.Notifications,
            AppTab.SystemSettings,
            AppTab.Roles,
        ).forEach { tab ->
            assertEquals(tab, primaryAdminNavigationRoot(tab.id))
        }
    }

    @Test
    fun tabsWithoutAffectedDrillDownsKeepTheirExistingHost() {
        AppTab.entries
            .filterNot {
                it in setOf(
                    AppTab.Users,
                    AppTab.Notifications,
                    AppTab.SystemSettings,
                    AppTab.Roles,
                )
            }
            .forEach { tab ->
                assertNull(tab.id, primaryAdminNavigationRoot(tab.id))
            }

        assertNull(primaryAdminNavigationRoot("removed-admin-tab"))
    }
}
