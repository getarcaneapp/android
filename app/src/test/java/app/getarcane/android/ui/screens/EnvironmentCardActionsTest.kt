package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentCardActionsTest {
    @Test
    fun prunePermissionIsResolvedForTheTargetEnvironment() {
        val user = User(
            id = "user",
            username = "operator",
            permissionsByEnv = mapOf(
                "0" to listOf(Permission.System.PRUNE),
                "1" to listOf(Permission.System.READ),
            ),
        )

        assertTrue(user.canPruneEnvironment("0"))
        assertFalse(user.canPruneEnvironment("1"))
        assertFalse(null.canPruneEnvironment("0"))
    }

    @Test
    fun userWithoutPrunePermissionKeepsSafeDashboardSurface() {
        val actions = environmentCardActions(canPrune = false)

        assertEquals(
            listOf(
                EnvironmentCardAction.UseEnvironment,
                EnvironmentCardAction.ViewSystemDetails,
                EnvironmentCardAction.Sync,
            ),
            actions,
        )
        assertFalse(actions.contains(EnvironmentCardAction.SystemPrune))
        assertFalse(actions.contains(EnvironmentCardAction.UpgradeArcane))
    }

    @Test
    fun environmentScopedPrunePermissionShowsSystemPruneWithoutDashboardUpgrade() {
        val actions = environmentCardActions(canPrune = true)

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
        assertFalse(actions.contains(EnvironmentCardAction.UpgradeArcane))
    }

    @Test
    fun activeCardHidesUseActionAndUpgradeRequiresPositiveAvailability() {
        val actions = environmentCardActions(canPrune = true)

        val active = visibleEnvironmentCardActions(actions, isActive = true, canUpgrade = false)
        assertFalse(active.contains(EnvironmentCardAction.UseEnvironment))
        assertFalse(active.contains(EnvironmentCardAction.UpgradeArcane))

        val remote = visibleEnvironmentCardActions(actions, isActive = false, canUpgrade = true)
        assertTrue(remote.contains(EnvironmentCardAction.UseEnvironment))
        assertTrue(remote.contains(EnvironmentCardAction.UpgradeArcane))
        assertEquals(EnvironmentCardAction.SystemPrune, remote[remote.lastIndex - 1])
        assertEquals(EnvironmentCardAction.UpgradeArcane, remote.last())
    }

    @Test
    fun offlineCardsKeepRecoveryAndReadActionsButDisableMutations() {
        assertTrue(isEnvironmentCardActionEnabled(EnvironmentCardAction.UseEnvironment, "offline", syncing = false))
        assertTrue(isEnvironmentCardActionEnabled(EnvironmentCardAction.ViewSystemDetails, "offline", syncing = false))
        assertTrue(isEnvironmentCardActionEnabled(EnvironmentCardAction.Sync, "offline", syncing = false))
        assertFalse(isEnvironmentCardActionEnabled(EnvironmentCardAction.Sync, "offline", syncing = true))
        assertFalse(isEnvironmentCardActionEnabled(EnvironmentCardAction.UpgradeArcane, "offline", syncing = false))
        assertFalse(isEnvironmentCardActionEnabled(EnvironmentCardAction.SystemPrune, "offline", syncing = false))
        assertTrue(isEnvironmentCardActionEnabled(EnvironmentCardAction.SystemPrune, "online", syncing = false))
    }
}
