package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.container.ContainerStatusCounts
import app.getarcane.sdk.models.dashboard.ActionItem
import app.getarcane.sdk.models.dashboard.ActionItemKind
import app.getarcane.sdk.models.dashboard.ActionItemSeverity
import app.getarcane.sdk.models.dashboard.ActionItems
import app.getarcane.sdk.models.dashboard.DashboardEnvironmentOverview
import app.getarcane.sdk.models.dashboard.DashboardEnvironmentsOverview
import app.getarcane.sdk.models.dashboard.DashboardEnvironmentsSummary
import app.getarcane.sdk.models.dashboard.DashboardSnapshotSettings
import app.getarcane.sdk.models.dashboard.EnvironmentSnapshotState
import app.getarcane.sdk.models.image.ImageUsageCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardOverviewSourceTest {
    @Test
    fun environmentForDashboardUsesOverviewEnvironmentWithSafeDefaults() {
        val row = overviewRow(
            environment = JsonValue.Obj(
                mapOf(
                    "id" to JsonValue.Str("edge"),
                    "name" to JsonValue.Str("Edge"),
                    "enabled" to JsonValue.Bool(false),
                    "connected" to JsonValue.Bool(true),
                    "edgeCapabilities" to JsonValue.Arr(listOf(JsonValue.Str("stats"), JsonValue.Number(2.0))),
                ),
            ),
        )

        val environment = row.environmentForDashboard()!!

        assertEquals("edge", environment.id)
        assertEquals("Edge", environment.name)
        assertEquals("", environment.apiUrl)
        assertEquals("active", environment.status)
        assertEquals(false, environment.enabled)
        assertEquals(true, environment.connected)
        assertEquals(listOf("stats"), environment.edgeCapabilities)
    }

    @Test
    fun environmentForDashboardIgnoresOverviewRowsWithoutIds() {
        assertNull(overviewRow(environment = JsonValue.Obj(mapOf("name" to JsonValue.Str("Missing")))).environmentForDashboard())
        assertNull(overviewRow(environment = JsonValue.Null).environmentForDashboard())
    }

    @Test
    fun overviewBuildsTileTotalsAndCardFallbackCounts() {
        val overview = DashboardEnvironmentsOverview(
            summary = DashboardEnvironmentsSummary(
                containers = ContainerStatusCounts(
                    runningContainers = 7,
                    stoppedContainers = 3,
                    totalContainers = 10,
                ),
                imageUsageCounts = ImageUsageCounts(
                    imagesInuse = 4,
                    imagesUnused = 5,
                    totalImages = 9,
                    totalImageSize = 123,
                ),
            ),
            environments = listOf(
                overviewRow(
                    containers = ContainerStatusCounts(2, 1, 3),
                    imageUsageCounts = ImageUsageCounts(2, 4, 6, 99),
                ),
            ),
        )

        assertEquals(
            DashTotals(running = 7, total = 10, images = 9, volumes = 5, updates = 6, stopped = 3),
            overview.toDashTotals(volumes = 5, updates = 6),
        )
        assertEquals(
            DashboardEnvironmentCardOverviewCounts(running = 2, stopped = 1, images = 6),
            overview.environments.single().cardOverviewCounts(),
        )
    }

    @Test
    fun overviewActionItemsMapToDashboardCardSummaryItems() {
        val row = overviewRow(
            actionItems = ActionItems(
                listOf(
                    ActionItem(ActionItemKind.IMAGE_UPDATES, 11, ActionItemSeverity.WARNING),
                    ActionItem(ActionItemKind.EXPIRING_KEYS, 2, ActionItemSeverity.CRITICAL),
                ),
            ),
        )

        assertEquals("Image updates available · 2 Expiring Keys", dashboardCardActionItemSummary(row.cardActionItems()))
    }

    private fun overviewRow(
        environment: JsonValue = JsonValue.Obj(mapOf("id" to JsonValue.Str("0"), "name" to JsonValue.Str("Local"))),
        containers: ContainerStatusCounts = ContainerStatusCounts(0, 0, 0),
        imageUsageCounts: ImageUsageCounts = ImageUsageCounts(0, 0, 0, 0),
        actionItems: ActionItems = ActionItems(),
    ): DashboardEnvironmentOverview =
        DashboardEnvironmentOverview(
            environment = environment,
            containers = containers,
            imageUsageCounts = imageUsageCounts,
            actionItems = actionItems,
            settings = DashboardSnapshotSettings(),
            snapshotState = EnvironmentSnapshotState.READY,
        )
}
