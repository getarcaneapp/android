package app.getarcane.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.getarcane.android.core.FleetUpdateSummary
import app.getarcane.android.core.fleetUpdateSummary
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

        assertEquals(
            FleetUpdateSummary(2, 1, 1, 1, 0, "1.2.3"),
            fleetUpdateSummary(job),
        )
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

        assertEquals(FleetUpdateSummary(1, 0, 0, 0, 0, null), fleetUpdateSummary(job))
    }

    @Test
    fun pendingRestartRemainsPendingInTheFleetSummary() {
        val job = EnvironmentUpdateJob(
            id = "job-3",
            status = EnvironmentUpdateJobStatus.PENDING_RESTART,
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.UPDATING),
            ),
        )

        assertEquals(FleetUpdateSummary(0, 0, 0, 0, 1, null), fleetUpdateSummary(job))
    }

    @Test
    fun resultSummaryAttributesMoreThanTwentyEnvironmentsExactlyOnce() {
        val results = (0 until 26).map { index ->
            EnvironmentUpdateResult(
                environmentId = index.toString(),
                environmentName = "Environment $index",
                status = when (index % 5) {
                    0 -> EnvironmentUpdateResultStatus.UPDATED
                    1 -> EnvironmentUpdateResultStatus.FAILED
                    2 -> EnvironmentUpdateResultStatus.SKIPPED_OFFLINE
                    3 -> EnvironmentUpdateResultStatus.SKIPPED_UP_TO_DATE
                    else -> EnvironmentUpdateResultStatus.PENDING
                },
            )
        }
        val job = EnvironmentUpdateJob(
            id = "job-26",
            status = EnvironmentUpdateJobStatus.COMPLETED,
            results = results,
        )

        assertEquals(FleetUpdateSummary(6, 5, 5, 5, 5, null), fleetUpdateSummary(job))
        assertEquals(26, results.map { it.environmentId }.distinct().size)
    }

    @Test
    fun completedUpToDateFleetIsNotReportedAsUpdated() {
        val job = EnvironmentUpdateJob(
            id = "job-current",
            status = EnvironmentUpdateJobStatus.COMPLETED,
            results = listOf(
                EnvironmentUpdateResult("0", "Manager", EnvironmentUpdateResultStatus.SKIPPED_UP_TO_DATE),
            ),
        )

        assertEquals(FleetUpdateSummary(0, 0, 0, 1, 0, null), fleetUpdateSummary(job))
    }
}
