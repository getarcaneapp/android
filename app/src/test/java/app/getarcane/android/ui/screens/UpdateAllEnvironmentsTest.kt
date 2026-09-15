package app.getarcane.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.getarcane.sdk.models.system.EnvironmentUpdateJob
import app.getarcane.sdk.models.system.EnvironmentUpdateJobStatus
import app.getarcane.sdk.models.system.EnvironmentUpdateResult
import app.getarcane.sdk.models.system.EnvironmentUpdateResultStatus

class UpdateAllEnvironmentsTest {
    @Test
    fun updateAllActionOnlyShowsForAdmins() {
        assertTrue(shouldShowUpdateAllAction(isAdmin = true))
        assertFalse(shouldShowUpdateAllAction(isAdmin = false))
    }

    @Test
    fun lastRunSummaryCountsUpdatedFailedAndSkippedResults() {
        val job = EnvironmentUpdateJob(
            id = "job-1",
            status = EnvironmentUpdateJobStatus.COMPLETED,
            managerTargetVersion = "1.2.3",
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.UPDATED),
                EnvironmentUpdateResult("agent-1", "Agent 1", EnvironmentUpdateResultStatus.TRIGGERED),
                EnvironmentUpdateResult("agent-2", "Agent 2", EnvironmentUpdateResultStatus.FAILED),
                EnvironmentUpdateResult("agent-3", "Agent 3", EnvironmentUpdateResultStatus.SKIPPED_OFFLINE),
                EnvironmentUpdateResult("agent-4", "Agent 4", EnvironmentUpdateResultStatus.SKIPPED_UP_TO_DATE),
            ),
        )

        assertEquals("2 updated · 1 failed · 1 skipped · 1.2.3", updateAllLastRunSummary(job))
    }

    @Test
    fun lastRunSummaryHidesDigestTargets() {
        val job = EnvironmentUpdateJob(
            id = "job-2",
            status = EnvironmentUpdateJobStatus.COMPLETED,
            managerTargetVersion = "sha256:abcdef",
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.UPDATED),
            ),
        )

        assertEquals("1 updated", updateAllLastRunSummary(job))
    }

    @Test
    fun finishedMessagePrefersConnectionNote() {
        val job = EnvironmentUpdateJob(
            id = "job-3",
            status = EnvironmentUpdateJobStatus.PENDING_RESTART,
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.UPDATING),
            ),
        )

        assertEquals(
            "The Arcane manager is restarting. Check back in a minute.",
            updateAllFinishedMessage(job, "The Arcane manager is restarting. Check back in a minute."),
        )
    }
}
