package app.getarcane.android.ui.screens

import app.getarcane.android.core.DashboardActionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvironmentDashboardCardActionItemsTest {
    @Test
    fun summaryUsesFirstTwoPositiveActionItems() {
        val summary = dashboardCardActionItemSummary(
            listOf(
                DashboardActionItem("stopped_containers", 3, "warning"),
                DashboardActionItem("image_updates", 2, "warning"),
                DashboardActionItem("actionable_vulnerabilities", 5, "critical"),
                DashboardActionItem("expiring_keys", 1, "warning"),
            ),
        )

        assertEquals("3 Stopped · 2 Updates", summary)
    }

    @Test
    fun summarySkipsZeroCountsAndLabelsKnownKinds() {
        val summary = dashboardCardActionItemSummary(
            listOf(
                DashboardActionItem("stopped_containers", 0, "warning"),
                DashboardActionItem("actionable_vulnerabilities", 4, "critical"),
                DashboardActionItem("expiring_keys", 2, "warning"),
            ),
        )

        assertEquals("4 Vulnerabilities · 2 Expiring Keys", summary)
    }

    @Test
    fun summaryReturnsNullWhenThereAreNoPositiveActionItems() {
        val summary = dashboardCardActionItemSummary(
            listOf(
                DashboardActionItem("image_updates", 0, "warning"),
                DashboardActionItem("expiring_keys", -1, "warning"),
            ),
        )

        assertNull(summary)
    }

    @Test
    fun summaryFallsBackForUnknownKinds() {
        val summary = dashboardCardActionItemSummary(
            listOf(DashboardActionItem("custom_signal", 7, "warning")),
        )

        assertEquals("7 Custom Signal", summary)
    }

    @Test
    fun imageUpdateCountUsesSameActionItemSemanticsAsCardSummary() {
        val items = listOf(
            DashboardActionItem("image_updates", 4, "warning"),
            DashboardActionItem("stopped_containers", 3, "warning"),
            DashboardActionItem("image_updates", 2, "warning"),
            DashboardActionItem("image_updates", 0, "warning"),
        )

        assertEquals(6, dashboardCardImageUpdateCount(items))
    }

    @Test
    fun dashboardImageUpdateCountRequiresEveryVisibleEnvironment() {
        val actionItems = mapOf(
            "local" to listOf(DashboardActionItem("image_updates", 4, "warning")),
            "edge" to listOf(DashboardActionItem("image_updates", 7, "warning")),
        )

        assertEquals(11, dashboardImageUpdateCountByEnvironment(actionItems, listOf("local", "edge")))
        assertNull(dashboardImageUpdateCountByEnvironment(actionItems, listOf("local", "missing")))
    }
}
