package app.getarcane.android.ui.screens.updates

import app.getarcane.sdk.errors.ArcaneError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterRunScreenTest {
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
        assertEquals(
            "The updater started on the server, but the final response was interrupted. " +
                "Refresh Updates or open Updater History to review the results.",
            (phase as RunPhase.OutcomeUnknown).message,
        )
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
}
