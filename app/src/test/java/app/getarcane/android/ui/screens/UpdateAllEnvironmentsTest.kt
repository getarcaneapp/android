package app.getarcane.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAllEnvironmentsTest {
    @Test
    fun updateAllActionOnlyShowsForAdmins() {
        assertTrue(shouldShowUpdateAllAction(isAdmin = true))
        assertFalse(shouldShowUpdateAllAction(isAdmin = false))
    }

    @Test
    fun lastRunSummaryCountsUpdatedFailedAndSkippedResults() {
        val job = EnvironmentUpdateJob(
            status = EnvironmentUpdateJobStatus.Completed,
            managerTargetVersion = "1.2.3",
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.Updated),
                EnvironmentUpdateResult("agent-1", "Agent 1", EnvironmentUpdateResultStatus.Triggered),
                EnvironmentUpdateResult("agent-2", "Agent 2", EnvironmentUpdateResultStatus.Failed),
                EnvironmentUpdateResult("agent-3", "Agent 3", EnvironmentUpdateResultStatus.SkippedOffline),
                EnvironmentUpdateResult("agent-4", "Agent 4", EnvironmentUpdateResultStatus.SkippedUpToDate),
            ),
        )

        assertEquals("2 updated · 1 failed · 1 skipped · 1.2.3", updateAllLastRunSummary(job))
    }

    @Test
    fun lastRunSummaryHidesDigestTargets() {
        val job = EnvironmentUpdateJob(
            status = EnvironmentUpdateJobStatus.Completed,
            managerTargetVersion = "sha256:abcdef",
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.Updated),
            ),
        )

        assertEquals("1 updated", updateAllLastRunSummary(job))
    }

    @Test
    fun finishedMessagePrefersConnectionNote() {
        val job = EnvironmentUpdateJob(
            status = EnvironmentUpdateJobStatus.PendingRestart,
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.Updating),
            ),
        )

        assertEquals(
            "The Arcane manager is restarting. Check back in a minute.",
            updateAllFinishedMessage(job, "The Arcane manager is restarting. Check back in a minute."),
        )
    }
}
