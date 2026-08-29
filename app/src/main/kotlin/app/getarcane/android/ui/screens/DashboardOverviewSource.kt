package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.base.arrayValue
import app.getarcane.sdk.models.base.boolValue
import app.getarcane.sdk.models.base.objectValue
import app.getarcane.sdk.models.base.stringValue
import app.getarcane.sdk.models.dashboard.ActionItemKind
import app.getarcane.sdk.models.dashboard.ActionItems
import app.getarcane.sdk.models.dashboard.DashboardEnvironmentOverview
import app.getarcane.sdk.models.dashboard.DashboardEnvironmentsOverview
import app.getarcane.sdk.models.dashboard.EnvironmentSnapshotState
import app.getarcane.sdk.models.environment.Environment

data class DashboardEnvironmentCardOverviewCounts(
    val running: Int,
    val stopped: Int,
    val images: Int,
)

internal fun DashboardEnvironmentsOverview.environmentsForDashboard(): List<Environment> =
    environments.mapNotNull { it.environmentForDashboard() }

internal fun DashboardEnvironmentsOverview.toDashTotals(
    volumes: Int? = null,
    updates: Int? = null,
): DashTotals =
    DashTotals(
        running = summary.containers.runningContainers,
        total = summary.containers.totalContainers,
        images = summary.imageUsageCounts.totalImages,
        volumes = volumes,
        updates = updates,
        stopped = summary.containers.stoppedContainers,
    )

internal fun ActionItems.imageUpdateActionCount(): Int =
    items.firstOrNull { it.kind == ActionItemKind.IMAGE_UPDATES }
        ?.count
        ?.coerceAtLeast(0)
        ?: 0

internal fun DashboardEnvironmentsOverview.imageUpdateActionCount(): Int? {
    if (environments.any { it.snapshotState == EnvironmentSnapshotState.ERROR }) return null
    return environments
        .filter { it.snapshotState == EnvironmentSnapshotState.READY }
        .sumOf { it.actionItems.imageUpdateActionCount() }
}

internal fun DashboardEnvironmentOverview.cardOverviewCounts(): DashboardEnvironmentCardOverviewCounts =
    DashboardEnvironmentCardOverviewCounts(
        running = containers.runningContainers,
        stopped = containers.stoppedContainers,
        images = imageUsageCounts.totalImages,
    )

internal fun DashboardEnvironmentOverview.environmentForDashboard(): Environment? {
    val fields = environment.objectValue ?: return null
    val id = fields["id"]?.stringValue?.takeIf { it.isNotBlank() } ?: return null
    return Environment(
        id = id,
        name = fields["name"]?.stringValue?.takeIf { it.isNotBlank() },
        apiUrl = fields["apiUrl"]?.stringValue.orEmpty(),
        status = fields["status"]?.stringValue ?: "active",
        enabled = fields["enabled"]?.boolValue ?: true,
        isEdge = fields["isEdge"]?.boolValue ?: false,
        edgeTransport = fields["edgeTransport"]?.stringValue,
        edgeSecurityMode = fields["edgeSecurityMode"]?.stringValue,
        edgeSessionId = fields["edgeSessionId"]?.stringValue,
        edgeAgentInstance = fields["edgeAgentInstance"]?.stringValue,
        edgeCapabilities = fields["edgeCapabilities"]?.arrayValue?.mapNotNull { it.stringValue },
        connected = fields["connected"]?.boolValue,
        apiKey = fields["apiKey"]?.stringValue,
    )
}
