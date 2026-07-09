package app.getarcane.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentCardActionsTest {
    @Test
    fun nonAdminActionsKeepSafeDashboardSurface() {
        val actions = environmentCardActions(isAdmin = false)

        assertEquals(
            listOf(
                EnvironmentCardAction.UseEnvironment,
                EnvironmentCardAction.ViewSystemDetails,
                EnvironmentCardAction.Sync,
            ),
            actions,
        )
        assertFalse(actions.contains(EnvironmentCardAction.UpgradeArcane))
        assertFalse(actions.contains(EnvironmentCardAction.SystemPrune))
    }

    @Test
    fun adminActionsIncludeMaintenanceEntries() {
        val actions = environmentCardActions(isAdmin = true)

        assertTrue(actions.contains(EnvironmentCardAction.UpgradeArcane))
        assertTrue(actions.contains(EnvironmentCardAction.SystemPrune))
    }
}
