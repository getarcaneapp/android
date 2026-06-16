package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabSelectionTest {
    private val defaultVisibleTabs = listOf(
        AppTab.Dashboard,
        AppTab.Containers,
        AppTab.Images,
        AppTab.Projects,
    )

    @Test
    fun keepsDashboardSelectedWhenItIsPinned() {
        assertEquals(
            AppTab.Dashboard.id,
            MainTabSelection.normalize(
                selectedTabId = AppTab.Dashboard.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun keepsHiddenButValidProgrammaticTabSelection() {
        assertEquals(
            AppTab.Updates.id,
            MainTabSelection.normalize(
                selectedTabId = AppTab.Updates.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun keepsSettingsSelectionOutsidePinnedTabs() {
        assertEquals(
            MainTabSelection.SETTINGS_ID,
            MainTabSelection.normalize(
                selectedTabId = MainTabSelection.SETTINGS_ID,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun fallsBackWhenSelectedTabIsUnknown() {
        assertEquals(
            AppTab.Dashboard.id,
            MainTabSelection.normalize(
                selectedTabId = "removed-tab",
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun fallsBackWhenSelectedTabIsNotAllowedForCurrentUser() {
        assertEquals(
            AppTab.Dashboard.id,
            MainTabSelection.normalize(
                selectedTabId = AppTab.Users.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = true,
            ),
        )
    }

    @Test
    fun keepsHiddenAdminTabWhenUserIsAllowed() {
        assertEquals(
            AppTab.Users.id,
            MainTabSelection.normalize(
                selectedTabId = AppTab.Users.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = true,
                supportsV2 = true,
            ),
        )
    }
}
