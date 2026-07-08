package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabSelectionTest {
    private val defaultVisibleTabs = listOf(
        AppTab.Dashboard,
        AppTab.Containers,
        AppTab.Images,
        AppTab.Projects,
    )

    @Test
    fun restoreFallsBackToDashboardWhenNothingWasStored() {
        assertEquals(
            AppTab.Dashboard.id,
            MainTabSelection.restore(
                storedTabId = null,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun restoreKeepsStoredSelectableTab() {
        assertEquals(
            AppTab.Projects.id,
            MainTabSelection.restore(
                storedTabId = AppTab.Projects.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun restoreKeepsSettingsSelection() {
        assertEquals(
            MainTabSelection.SETTINGS_ID,
            MainTabSelection.restore(
                storedTabId = MainTabSelection.SETTINGS_ID,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = false,
            ),
        )
    }

    @Test
    fun restoreFallsBackWhenStoredTabIsNotAllowed() {
        assertEquals(
            AppTab.Dashboard.id,
            MainTabSelection.restore(
                storedTabId = AppTab.Swarm.id,
                visibleTabs = defaultVisibleTabs,
                isAdmin = false,
                supportsV2 = true,
            ),
        )
    }

    @Test
    fun reTappingSelectedTabShouldPopToRoot() {
        assertTrue(
            MainTabSelection.shouldPopToRootOnTap(
                selectedTabId = AppTab.Images.id,
                tappedTabId = AppTab.Images.id,
            ),
        )
    }

    @Test
    fun tappingDifferentTabShouldNotPopToRoot() {
        assertFalse(
            MainTabSelection.shouldPopToRootOnTap(
                selectedTabId = AppTab.Images.id,
                tappedTabId = AppTab.Projects.id,
            ),
        )
    }

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
