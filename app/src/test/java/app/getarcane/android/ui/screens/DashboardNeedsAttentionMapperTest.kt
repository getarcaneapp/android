package app.getarcane.android.ui.screens

import app.getarcane.android.core.DashboardActionItem
import app.getarcane.android.core.DashboardActionItems
import app.getarcane.android.core.DashboardEnvironmentStreamState
import app.getarcane.android.core.DashboardSnapshot
import app.getarcane.android.core.DashboardSnapshotContainers
import app.getarcane.android.core.DashboardSnapshotImages
import app.getarcane.sdk.models.container.ContainerStatusCounts
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.image.ImageUsageCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardNeedsAttentionMapperTest {
    @Test
    fun actionItemsAddVulnerabilitiesAndApiKeysWithoutDroppingExistingRows() {
        var vulnerabilityTarget: DashboardActionTargetEnvironment? = null
        var openedApiKeys = false
        val items = buildNeedsAttentionItems(
            environments = listOf(Environment(id = "offline", name = "Offline", apiUrl = "", status = "error")),
            streamStates = mapOf(
                "0" to streamState(
                    id = "0",
                    name = "Local",
                    actionItems = listOf(
                        DashboardActionItem("actionable_vulnerabilities", 2, "warning"),
                        DashboardActionItem("expiring_keys", 1, "warning"),
                    ),
                ),
                "edge" to streamState(
                    id = "edge",
                    name = "Edge",
                    actionItems = listOf(
                        DashboardActionItem("actionable_vulnerabilities", 5, "critical"),
                        DashboardActionItem("expiring_keys", 3, "warning"),
                    ),
                ),
            ),
            totals = DashTotals(
                running = 3,
                total = 6,
                images = 8,
                volumes = 2,
                updates = 4,
                stopped = 3,
            ),
            failedActivities = emptyList(),
            onOpenEnvironment = {},
            onOpenContainers = {},
            onOpenUpdates = {},
            onOpenVulnerabilities = { vulnerabilityTarget = it },
            onOpenApiKeys = { openedApiKeys = true },
            onOpenActivities = {},
        )

        assertEquals(
            listOf(
                "offline-environments",
                "stopped-containers",
                "vulnerabilities",
                "image-updates",
                "expiring-keys",
            ),
            items.map { it.id },
        )
        assertEquals(7, items.first { it.id == "vulnerabilities" }.count)
        assertEquals(NeedsAttentionSeverity.Critical, items.first { it.id == "vulnerabilities" }.severity)
        assertEquals(4, items.first { it.id == "expiring-keys" }.count)

        items.first { it.id == "vulnerabilities" }.action()
        assertEquals(DashboardActionTargetEnvironment(id = "edge", name = "Edge"), vulnerabilityTarget)

        items.first { it.id == "expiring-keys" }.action()
        assertTrue(openedApiKeys)
    }

    @Test
    fun actionItemsIgnoreUnloadedErroredAndZeroCountStates() {
        val items = buildNeedsAttentionItems(
            environments = emptyList(),
            streamStates = mapOf(
                "pending" to streamState(
                    id = "pending",
                    name = "Pending",
                    hasLoaded = false,
                    actionItems = listOf(DashboardActionItem("actionable_vulnerabilities", 9, "critical")),
                ),
                "errored" to streamState(
                    id = "errored",
                    name = "Errored",
                    streamError = true,
                    actionItems = listOf(DashboardActionItem("expiring_keys", 4, "warning")),
                ),
                "zero" to streamState(
                    id = "zero",
                    name = "Zero",
                    actionItems = listOf(
                        DashboardActionItem("actionable_vulnerabilities", 0, "critical"),
                        DashboardActionItem("expiring_keys", 0, "warning"),
                    ),
                ),
            ),
            totals = null,
            failedActivities = emptyList(),
            onOpenEnvironment = {},
            onOpenContainers = {},
            onOpenUpdates = {},
            onOpenVulnerabilities = {},
            onOpenApiKeys = {},
            onOpenActivities = {},
        )

        assertEquals(emptyList<String>(), items.map { it.id })
    }

    private fun streamState(
        id: String,
        name: String,
        hasLoaded: Boolean = true,
        streamError: Boolean = false,
        actionItems: List<DashboardActionItem>,
    ): DashboardEnvironmentStreamState =
        DashboardEnvironmentStreamState(
            id = id,
            name = name,
            hasLoaded = hasLoaded,
            streamError = streamError,
            snapshot = DashboardSnapshot(
                containers = DashboardSnapshotContainers(
                    counts = ContainerStatusCounts(
                        runningContainers = 0,
                        stoppedContainers = 0,
                        totalContainers = 0,
                    ),
                ),
                images = DashboardSnapshotImages(),
                imageUsageCounts = ImageUsageCounts(
                    imagesInuse = 0,
                    imagesUnused = 0,
                    totalImages = 0,
                    totalImageSize = 0,
                ),
                actionItems = DashboardActionItems(actionItems),
            ),
        )
}
