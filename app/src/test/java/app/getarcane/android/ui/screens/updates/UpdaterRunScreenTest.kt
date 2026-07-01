package app.getarcane.android.ui.screens.updates

import app.getarcane.sdk.errors.ArcaneError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterRunScreenTest {
    @Test
    fun activeCancellationFromUpdaterRequestIsHandledAsFailureResult() = runBlocking {
        val result = runUpdaterRequestCatching {
            throw CancellationException("request timed out")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun transportFailureBeforeServerStartsRemainsFailure() {
        val phase = updaterRunFailurePhase(
            ArcaneError.Transport("timeout"),
            observedServerStart = false,
        )

        assertTrue(phase is RunPhase.Failed)
        assertEquals(
            "Couldn't reach the server. Check the URL and your connection.",
            (phase as RunPhase.Failed).message,
        )
    }

    @Test
    fun transportFailureAfterServerStartsShowsUnknownOutcome() {
        val phase = updaterRunFailurePhase(
            ArcaneError.Transport("timeout"),
            observedServerStart = true,
        )

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Response Interrupted", (phase as RunPhase.OutcomeUnknown).title)
        assertEquals(
            "The updater started on the server, but the final response was interrupted. " +
                "Refresh Updates or open Updater History to review the results.",
            phase.message,
        )
    }

    @Test
    fun cancellationFailureAfterServerEvidenceShowsUnknownOutcome() {
        val phase = updaterRunFailurePhase(
            CancellationException("request timed out"),
            evidence = UpdaterRunEvidence(
                observedServerStart = true,
                successfulPostStartStatusProbe = true,
                successfulPostStartHistoryProbe = true,
            ),
        )

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Response Interrupted", (phase as RunPhase.OutcomeUnknown).title)
        assertEquals(
            "The updater started on the server, but the final response was interrupted. " +
                "Refresh Updates or open Updater History to review the results.",
            phase.message,
        )
    }

    @Test
    fun transportFailureAfterSuccessfulPostStartProbeAvoidsConnectivityFailureCopy() {
        val phase = updaterRunFailurePhase(
            ArcaneError.Transport("timeout"),
            evidence = UpdaterRunEvidence(
                observedServerStart = false,
                successfulPostStartStatusProbe = true,
                successfulPostStartHistoryProbe = false,
            ),
        )

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Response Interrupted", (phase as RunPhase.OutcomeUnknown).title)
        assertEquals(
            "The updater request was interrupted, but Android could still reach the server. " +
                "Refresh Updates or open Updater History to review the results.",
            phase.message,
        )
    }

    @Test
    fun nonTransportFailureAfterSuccessfulPostStartProbeRemainsFailure() {
        val phase = updaterRunFailurePhase(
            ArcaneError.Decoding("bad payload"),
            evidence = UpdaterRunEvidence(
                observedServerStart = false,
                successfulPostStartStatusProbe = true,
                successfulPostStartHistoryProbe = true,
            ),
        )

        assertTrue(phase is RunPhase.Failed)
        assertEquals("The server returned an unexpected response.", (phase as RunPhase.Failed).message)
    }

    @Test
    fun activeStatusMatchingBaselineIsNotStartEvidence() {
        val baseline = UpdaterRunStatusSnapshot(
            updatingContainers = 1,
            updatingProjects = 0,
            containerIds = listOf("existing-container"),
            projectIds = emptyList(),
        )

        assertEquals(false, baseline.isNewActiveWorkComparedTo(baseline))
    }

    @Test
    fun newActiveStatusIsStartEvidence() {
        val baseline = UpdaterRunStatusSnapshot(
            updatingContainers = 0,
            updatingProjects = 0,
            containerIds = emptyList(),
            projectIds = emptyList(),
        )
        val observed = baseline.copy(updatingContainers = 1, containerIds = listOf("container-a"))

        assertEquals(true, observed.isNewActiveWorkComparedTo(baseline))
    }

    @Test
    fun activeStatusWithoutBaselineIsNotStartEvidence() {
        val observed = UpdaterRunStatusSnapshot(
            updatingContainers = 1,
            updatingProjects = 0,
            containerIds = listOf("pre-existing-container"),
            projectIds = emptyList(),
        )

        assertEquals(false, observed.isNewActiveWorkComparedTo(null))
    }

    @Test
    fun inactiveStatusAfterObservedStartUsesPollingOutcome() {
        val phase = updaterRunPollingCompletedPhase()

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Finished", (phase as RunPhase.OutcomeUnknown).title)
        assertEquals(
            "The updater is no longer reporting active work. Refresh Updates or open Updater History " +
                "to review the results.",
            phase.message,
        )
    }

    @Test
    fun interruptedObservationCompletionUsesResponseInterruptedOutcome() {
        val phase = updaterRunInterruptedObservationCompletedPhase(
            error = ArcaneError.Transport("timeout"),
            evidence = UpdaterRunEvidence(
                observedServerStart = true,
                successfulPostStartStatusProbe = true,
                successfulPostStartHistoryProbe = true,
            ),
        )

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Response Interrupted", (phase as RunPhase.OutcomeUnknown).title)
        assertEquals(
            "The updater started on the server, but the final response was interrupted. " +
                "Refresh Updates or open Updater History to review the results.",
            phase.message,
        )
    }

    @Test
    fun interruptedObservationCompletionWithoutErrorFallsBackToPollingOutcome() {
        val phase = updaterRunInterruptedObservationCompletedPhase(
            error = null,
            evidence = null,
        )

        assertTrue(phase is RunPhase.OutcomeUnknown)
        assertEquals("Updater Finished", (phase as RunPhase.OutcomeUnknown).title)
    }

    @Test
    fun newHistoryRecordIsServerStartEvidence() {
        val baseline = setOf("history-before-run")
        val observed = setOf("history-before-run", "history-created-by-run")

        assertEquals(true, hasNewUpdaterHistoryRecord(baseline, observed))
    }

    @Test
    fun historyWithoutBaselineIsNotServerStartEvidence() {
        assertEquals(false, hasNewUpdaterHistoryRecord(null, setOf("unrelated-history")))
    }

    @Test
    fun interruptedRequestContinuesObservationAfterNewHistoryEvenWhenFinalStatusInactive() {
        val evidence = UpdaterRunEvidence(
            observedServerStart = true,
            successfulPostStartStatusProbe = true,
            successfulPostStartHistoryProbe = true,
        )

        assertEquals(true, shouldObserveAfterRunFailure(ArcaneError.Transport("timeout"), evidence))
    }

    @Test
    fun requestCancellationAfterServerEvidenceContinuesObservation() {
        val evidence = UpdaterRunEvidence(
            observedServerStart = true,
            successfulPostStartStatusProbe = true,
            successfulPostStartHistoryProbe = true,
        )

        assertEquals(true, shouldObserveAfterRunFailure(CancellationException("request timed out"), evidence))
    }

    @Test
    fun interruptedRequestAfterOnlySuccessfulProbeContinuesBoundedObservation() {
        val evidence = UpdaterRunEvidence(
            observedServerStart = false,
            successfulPostStartStatusProbe = true,
            successfulPostStartHistoryProbe = false,
        )

        assertEquals(true, shouldObserveAfterRunFailure(ArcaneError.Transport("timeout"), evidence))
    }

    @Test
    fun nonInterruptedFailureDoesNotEnterObservation() {
        val evidence = UpdaterRunEvidence(
            observedServerStart = true,
            successfulPostStartStatusProbe = true,
            successfulPostStartHistoryProbe = true,
        )

        assertEquals(false, shouldObserveAfterRunFailure(ArcaneError.Decoding("bad payload"), evidence))
    }

    @Test
    fun interruptedRequestWithoutServerEvidenceDoesNotEnterObservation() {
        val evidence = UpdaterRunEvidence(
            observedServerStart = false,
            successfulPostStartStatusProbe = false,
            successfulPostStartHistoryProbe = false,
        )

        assertEquals(false, shouldObserveAfterRunFailure(ArcaneError.Transport("timeout"), evidence))
    }

    @Test
    fun summaryUsesItemFailuresWhenServerAggregateMissesThem() {
        val summary = updaterRunSummary(
            checked = 1,
            updated = 0,
            skipped = 0,
            failed = 0,
            duration = "1s",
            itemStatuses = listOf(UpdaterRunItemStatus.Failed),
        )

        assertEquals(1, summary.checked)
        assertEquals(0, summary.updated)
        assertEquals(0, summary.skipped)
        assertEquals(1, summary.failed)
        assertEquals(false, summary.success)
    }

    @Test
    fun summaryFallsBackToServerAggregateWhenNoItemsAreReturned() {
        val summary = updaterRunSummary(
            checked = 3,
            updated = 2,
            skipped = 0,
            failed = 1,
            duration = "5s",
            itemStatuses = emptyList(),
        )

        assertEquals(3, summary.checked)
        assertEquals(2, summary.updated)
        assertEquals(0, summary.skipped)
        assertEquals(1, summary.failed)
        assertEquals(false, summary.success)
    }

    @Test
    fun appliedItemWinsOverNonFatalErrorText() {
        val status = updaterRunItemStatus(
            error = "image digest unchanged after pull",
            updateApplied = true,
            updateAvailable = false,
            status = "updated",
        )

        assertEquals(UpdaterRunItemStatus.Updated, status)
    }

    @Test
    fun unchangedDigestMessageWithoutAppliedUpdateIsSkipped() {
        val status = updaterRunItemStatus(
            error = "image digest unchanged after pull",
            updateApplied = false,
            updateAvailable = false,
            status = "failed",
        )

        assertEquals(UpdaterRunItemStatus.Skipped, status)
    }


    @Test
    fun terminalNewHistoryRecordCompletesObservation() {
        val record = updaterHistoryRecord(id = "history-created-by-run", endTime = kotlinx.datetime.Instant.parse("2026-06-19T18:01:00Z"))

        assertEquals(true, record.hasTerminalUpdaterEvidence())
    }

    @Test
    fun newNonTerminalHistoryRecordDoesNotCompleteObservationImmediately() {
        val record = updaterHistoryRecord(id = "history-created-by-run")

        assertEquals(false, record.hasTerminalUpdaterEvidence())
    }

    @Test
    fun newHistoryRecordsOnlyIncludesRecordsMissingFromBaseline() {
        val baseline = setOf("history-before-run")
        val before = updaterHistoryRecord(id = "history-before-run")
        val after = updaterHistoryRecord(id = "history-created-by-run")

        assertEquals(listOf(after), newUpdaterHistoryRecords(baseline, listOf(before, after)))
    }

    private fun updaterHistoryRecord(
        id: String,
        status: String = "running",
        endTime: kotlinx.datetime.Instant? = null,
        updateApplied: Boolean = false,
        error: String? = null,
    ): UpdaterHistoryRecord =
        UpdaterHistoryRecord(
            id = id,
            resourceId = "container-1",
            resourceType = "container",
            resourceName = "web",
            status = status,
            startTime = kotlinx.datetime.Instant.parse("2026-06-19T18:00:00Z"),
            endTime = endTime,
            updateAvailable = false,
            updateApplied = updateApplied,
            error = error,
        )
}
