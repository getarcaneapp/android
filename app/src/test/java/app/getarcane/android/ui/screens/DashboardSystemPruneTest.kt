package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.system.PruneAllResult
import app.getarcane.sdk.models.system.PruneBuildCacheMode
import app.getarcane.sdk.models.system.PruneContainerMode
import app.getarcane.sdk.models.system.PruneImageMode
import app.getarcane.sdk.models.system.PruneNetworkMode
import app.getarcane.sdk.models.system.PruneVolumeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSystemPruneTest {
    @Test
    fun activityServerTreatsSuccessfulResponseAsAcceptanceNotCompletion() {
        val presentation = systemPrunePresentation(
            result = PruneAllResult(success = true),
            supportsActivities = true,
        )

        assertSame(SystemPrunePresentation.ActivityAccepted, presentation)
    }

    @Test
    fun activityServerStillSurfacesImmediateFailureAsError() {
        val presentation = systemPrunePresentation(
            result = PruneAllResult(success = false, errors = listOf("denied")),
            supportsActivities = true,
        ) as SystemPrunePresentation.Completed

        assertTrue(presentation.isError)
        assertEquals("System prune failed. Errors: denied", presentation.message)
    }

    @Test
    fun synchronousServerReportsNothingToPruneAsSuccess() {
        val presentation = systemPrunePresentation(
            result = PruneAllResult(success = true),
            supportsActivities = false,
        ) as SystemPrunePresentation.Completed

        assertFalse(presentation.isError)
        assertEquals("No resources pruned.", presentation.message)
    }

    @Test
    fun partialSynchronousResultIsNeverPresentedAsSuccess() {
        val presentation = systemPrunePresentation(
            result = PruneAllResult(
                networksDeleted = listOf("network-id"),
                success = false,
                errors = listOf("Container pruning failed"),
            ),
            supportsActivities = false,
        ) as SystemPrunePresentation.Completed

        assertTrue(presentation.isError)
        assertEquals(
            "Pruned 1 network with errors. Errors: Container pruning failed",
            presentation.message,
        )
    }

    @Test
    fun buildCacheOnlyAndComponentBytesProducePositiveSummary() {
        val message = formatSystemPruneResult(
            PruneAllResult(
                buildCacheSpaceReclaimed = 1024,
                success = true,
            ),
        )

        assertEquals("Pruned build cache. Freed 1.0 KB.", message)
    }

    @Test
    fun serverDefaultsSeedEverySupportedModeAndSafeAgeFallbacks() {
        val defaults = systemPruneDefaults(
            mapOf(
                "pruneContainerMode" to "olderThan",
                "pruneContainerUntil" to "48h",
                "pruneImageMode" to "dangling",
                "pruneImageUntil" to "",
                "pruneVolumeMode" to "all",
                "pruneNetworkMode" to "unused",
                "pruneNetworkUntil" to "7d",
                "pruneBuildCacheMode" to "all",
            ),
        )

        assertEquals(PruneContainerMode.OLDER_THAN, defaults.containerMode)
        assertEquals("48h", defaults.containerUntil)
        assertEquals(PruneImageMode.DANGLING, defaults.imageMode)
        assertEquals("24h", defaults.imageUntil)
        assertEquals(PruneVolumeMode.ALL, defaults.volumeMode)
        assertEquals(PruneNetworkMode.UNUSED, defaults.networkMode)
        assertEquals("7d", defaults.networkUntil)
        assertEquals(PruneBuildCacheMode.ALL, defaults.buildCacheMode)
        assertEquals("24h", defaults.buildCacheUntil)
    }

    @Test
    fun unknownServerDefaultsRemainNonDestructive() {
        val defaults = systemPruneDefaults(
            mapOf(
                "pruneContainerMode" to "future-mode",
                "pruneImageMode" to "future-mode",
                "pruneVolumeMode" to "future-mode",
                "pruneNetworkMode" to "future-mode",
                "pruneBuildCacheMode" to "future-mode",
            ),
        )

        assertEquals(PruneContainerMode.NONE, defaults.containerMode)
        assertEquals(PruneImageMode.NONE, defaults.imageMode)
        assertEquals(PruneVolumeMode.NONE, defaults.volumeMode)
        assertEquals(PruneNetworkMode.NONE, defaults.networkMode)
        assertEquals(PruneBuildCacheMode.NONE, defaults.buildCacheMode)
    }
}
