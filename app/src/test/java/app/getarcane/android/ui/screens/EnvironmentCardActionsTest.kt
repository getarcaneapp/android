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
        assertFalse(actions.contains(EnvironmentCardAction.SystemPrune))
        assertFalse(actions.map { it.label }.contains("Upgrade Arcane"))
    }

    @Test
    fun adminActionsKeepSystemPruneWithoutDashboardUpgrade() {
        val actions = environmentCardActions(isAdmin = true)

        assertEquals(
            listOf(
                EnvironmentCardAction.UseEnvironment,
                EnvironmentCardAction.ViewSystemDetails,
                EnvironmentCardAction.Sync,
                EnvironmentCardAction.SystemPrune,
            ),
            actions,
        )
        assertTrue(actions.contains(EnvironmentCardAction.SystemPrune))
        assertFalse(actions.map { it.label }.contains("Upgrade Arcane"))
    }
}
