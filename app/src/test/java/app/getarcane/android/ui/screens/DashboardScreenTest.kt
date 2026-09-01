package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.system.PruneAllResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DashboardScreenTest {
    @Test
    fun systemPruneResultIncludesBuildCacheWhenOnlyBuildCacheWasPruned() {
        val message = formatSystemPruneResult(
            PruneAllResult(
                buildCacheSpaceReclaimed = 1024,
                spaceReclaimed = 1024,
                success = true,
            ),
        )

        assertEquals("Pruned build cache. Freed 1.0 KB.", message)
        assertFalse(message.contains("No resources pruned"))
    }

    @Test
    fun systemPruneResultUsesPositiveSummaryWhenOnlyReclaimedBytesAreReported() {
        val message = formatSystemPruneResult(
            PruneAllResult(
                spaceReclaimed = 1024,
                success = true,
            ),
        )

        assertEquals("Pruned resources. Freed 1.0 KB.", message)
    }

    @Test
    fun systemPruneResultKeepsEmptyResultCopyWhenNothingWasPruned() {
        val message = formatSystemPruneResult(PruneAllResult(success = true))

        assertEquals("No resources pruned.", message)
    }
}
