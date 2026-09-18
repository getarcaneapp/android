package app.getarcane.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
    @Test
    fun widthBreakpointsAreStableAtTheirBoundaries() {
        assertEquals(AdaptiveWidthClass.COMPACT, AdaptiveNavigation.widthClass(599))
        assertEquals(AdaptiveWidthClass.MEDIUM, AdaptiveNavigation.widthClass(600))
        assertEquals(AdaptiveWidthClass.MEDIUM, AdaptiveNavigation.widthClass(839))
        assertEquals(AdaptiveWidthClass.EXPANDED, AdaptiveNavigation.widthClass(840))
    }

    @Test
    fun largerNavigationSurfacesIncludeSecondaryAuthorizedTabs() {
        val standard = AdaptiveNavigation.availableTabs(
            isAdmin = false,
            supportsV2 = true,
            canReadVariables = false,
        )
        assertTrue(AppTab.Dashboard in standard)
        assertTrue(AppTab.Activities in standard)
        assertFalse(AppTab.Variables in standard)
        assertFalse(AppTab.Users in standard)

        val admin = AdaptiveNavigation.availableTabs(
            isAdmin = true,
            supportsV2 = true,
            canReadVariables = true,
        )
        assertTrue(AppTab.Users in admin)
        assertTrue(AppTab.SystemSettings in admin)
        assertTrue(AppTab.Networks in admin)
        assertTrue(AppTab.Variables in admin)
    }

    @Test
    fun secondaryDestinationsKeepTheirExistingSettingsNavigationHost() {
        assertTrue(AdaptiveNavigation.usesSettingsHost(AppTab.Users))
        assertTrue(AdaptiveNavigation.usesSettingsHost(AppTab.Roles))
        assertTrue(AdaptiveNavigation.usesSettingsHost(AppTab.Variables))
        assertFalse(AdaptiveNavigation.usesSettingsHost(AppTab.Containers))
        assertFalse(AdaptiveNavigation.usesSettingsHost(AppTab.Networks))
    }

    @Test
    fun serverPolicyCanReachFormerlyAdminOnlyDestination() {
        val available = AdaptiveNavigation.availableTabs(
            isAdmin = false,
            supportsV2 = true,
            canReadVariables = false,
            canAccess = { it == AppTab.Users },
        )

        assertEquals(listOf(AppTab.Users), available)
    }
}
