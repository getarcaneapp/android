package app.getarcane.android.ui.screens.containers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerActionPolicyTest {
    private val all = ContainerDetailAction.entries.mapTo(mutableSetOf()) { it.permission } + "*"

    @Test
    fun runningContainerOffersLifecycleActionsAndAdvancedActions() {
        val actions = availableContainerActions(true, false, all, supportsPauseKill = true)

        assertTrue(ContainerDetailAction.Stop in actions)
        assertTrue(ContainerDetailAction.Restart in actions)
        assertTrue(ContainerDetailAction.Pause in actions)
        assertTrue(ContainerDetailAction.Kill in actions)
        assertTrue(ContainerDetailAction.Terminal in actions)
        assertFalse(ContainerDetailAction.Start in actions)
        assertFalse(ContainerDetailAction.Unpause in actions)
    }

    @Test
    fun pausedContainerOnlyOffersCompatibleRuntimeActions() {
        val actions = availableContainerActions(true, true, all, supportsPauseKill = true)

        assertTrue(ContainerDetailAction.Unpause in actions)
        assertTrue(ContainerDetailAction.Kill in actions)
        assertFalse(ContainerDetailAction.Stop in actions)
        assertFalse(ContainerDetailAction.Restart in actions)
        assertFalse(ContainerDetailAction.Terminal in actions)
    }

    @Test
    fun permissionsAndServerVersionRemoveUnavailableActions() {
        val actions = availableContainerActions(
            isRunning = true,
            isPaused = false,
            permissions = setOf("containers:stop", "containers:pause", "containers:kill"),
            supportsPauseKill = false,
        )

        assertEquals(setOf(ContainerDetailAction.Stop), actions)
    }

    @Test
    fun destructiveConfirmationsIdentifyResourceAndEnvironment() {
        assertNotNull(ContainerDetailAction.Kill.confirmationMessageRes)
        assertNotNull(ContainerDetailAction.Delete.confirmationMessageRes)
        assertNull(ContainerDetailAction.Start.confirmationMessageRes)
    }
}
