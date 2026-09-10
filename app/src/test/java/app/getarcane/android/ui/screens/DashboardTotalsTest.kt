package app.getarcane.android.ui.screens

import app.getarcane.android.core.DashboardStreamAggregateCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTotalsTest {
    @Test
    fun missingStreamAggregateUsesTheRestFallback() {
        val fallback = fallbackTotals()

        assertEquals(
            fallback,
            displayedDashboardTotals(
                streamAggregate = null,
                restFallback = fallback,
            ),
        )
    }

    @Test
    fun streamAggregateReplacesLiveCountsAndRetainsRestOnlyCounts() {
        val result = displayedDashboardTotals(
            streamAggregate = DashboardStreamAggregateCounts(
                runningContainers = 8,
                stoppedContainers = 2,
                totalContainers = 10,
                totalImages = 14,
            ),
            restFallback = fallbackTotals(),
        )

        assertEquals(
            DashTotals(running = 8, total = 10, images = 14, volumes = 4, updates = 3, stopped = 2),
            result,
        )
    }

    @Test
    fun completeImageUpdateSummaryCountRequiresEveryEnvironment() {
        assertEquals(4, listOf(3, 1).completeImageUpdateSummaryCount())
        assertEquals(0, emptyList<Int?>().completeImageUpdateSummaryCount())
        assertEquals(3, listOf(3, -1).completeImageUpdateSummaryCount())
        assertNull(listOf(3, null, 1).completeImageUpdateSummaryCount())
    }

    private fun fallbackTotals(): DashTotals =
        DashTotals(running = 5, total = 7, images = 11, volumes = 4, updates = 3, stopped = 2)
}
