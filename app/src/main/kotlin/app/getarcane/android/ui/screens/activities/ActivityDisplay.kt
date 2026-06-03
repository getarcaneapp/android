package app.getarcane.android.ui.screens.activities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.activity.ActivityMessageLevel
import app.getarcane.sdk.models.activity.ActivityStartedBy
import app.getarcane.sdk.models.activity.ActivityStatus
import app.getarcane.sdk.models.activity.ActivityType
import kotlinx.datetime.Instant

/**
 * Status filter chips shown above the activity list. Port of the iOS `ActivityStatusFilter`.
 */
enum class ActivityStatusFilter(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.Inventory2),
    ACTIVE("Active", Icons.Filled.Bolt),
    QUEUED("Queued", Icons.Filled.Schedule),
    RUNNING("Running", Icons.Filled.PlayCircle),
    FAILED("Failed", Icons.Filled.Dangerous),
    COMPLETED("Completed", Icons.Filled.CheckCircle),
    CANCELLED("Cancelled", Icons.Filled.Block);

    fun matches(activity: Activity): Boolean = when (this) {
        ALL -> true
        ACTIVE -> activity.status == ActivityStatus.QUEUED || activity.status == ActivityStatus.RUNNING
        QUEUED -> activity.status == ActivityStatus.QUEUED
        RUNNING -> activity.status == ActivityStatus.RUNNING
        FAILED -> activity.status == ActivityStatus.FAILED
        COMPLETED -> activity.status == ActivityStatus.SUCCESS
        CANCELLED -> activity.status == ActivityStatus.CANCELLED
    }
}

/** True for queued/running activities, which may be cancelled. */
val Activity.isCancellable: Boolean
    get() = status == ActivityStatus.QUEUED || status == ActivityStatus.RUNNING

/** Primary row title: the resource name when present, else the humanized type name. */
val Activity.displayTitle: String
    get() {
        val name = resourceName?.trim().orEmpty()
        return name.ifEmpty { type.displayName }
    }

/** Secondary row line: humanized resource type, optionally suffixed with the current step. */
val Activity.subtitle: String
    get() {
        val resource = resourceType?.activityDisplayName ?: type.displayName
        return if (step.isEmpty()) resource else "$resource - $step"
    }

/** The environment ID this activity should be addressed by (falls back to local Docker). */
val Activity.sourceEnvironmentKey: String
    get() {
        val trimmedSource = sourceEnvironmentId?.trim().orEmpty()
        if (trimmedSource.isNotEmpty()) return trimmedSource
        val trimmedEnvironment = environmentId.trim()
        return trimmedEnvironment.ifEmpty { EnvironmentId.LOCAL_DOCKER.rawValue }
    }

/** Timestamp used for sorting: latest of updatedAt / endedAt / startedAt. */
val Activity.sortTime: Instant
    get() = updatedAt ?: endedAt ?: startedAt

/** Semantic tint for the activity's status. */
@Composable
fun Activity.statusTint(): Color = when (status) {
    ActivityStatus.RUNNING -> ArcaneBlue
    ActivityStatus.QUEUED -> ArcaneOrange
    ActivityStatus.SUCCESS -> ArcaneGreen
    ActivityStatus.FAILED -> ArcaneRed
    ActivityStatus.CANCELLED, ActivityStatus.UNKNOWN -> ArcaneGray
}

/** Icon representing the activity's type. */
fun Activity.typeIcon(): ImageVector = when {
    type.wire.startsWith("project_") -> Icons.Filled.Layers
    type.wire.startsWith("image_") -> Icons.Filled.PhotoLibrary
    type.wire.startsWith("container_") -> Icons.Filled.Inventory2
    type == ActivityType.VULNERABILITY_SCAN -> Icons.Filled.Security
    type == ActivityType.SYSTEM_PRUNE -> Icons.Filled.DeleteSweep
    type == ActivityType.AUTO_UPDATE -> Icons.Filled.Autorenew
    type == ActivityType.RESOURCE_ACTION -> Icons.AutoMirrored.Filled.PlaylistAddCheck
    else -> Icons.Filled.History
}

/** Humanized name for an [ActivityType] (e.g. `project_deploy` -> "Project Deploy"). */
val ActivityType.displayName: String
    get() = wire.activityDisplayName

/** Icon for an activity message's severity. */
fun ActivityMessageLevel.icon(): ImageVector = when (this) {
    ActivityMessageLevel.ERROR -> Icons.Filled.Dangerous
    ActivityMessageLevel.WARNING -> Icons.Filled.Warning
    ActivityMessageLevel.SUCCESS -> Icons.Filled.CheckCircle
    ActivityMessageLevel.INFO, ActivityMessageLevel.UNKNOWN -> Icons.Filled.Info
}

/** Tint for an activity message's severity. */
@Composable
fun ActivityMessageLevel.tint(): Color = when (this) {
    ActivityMessageLevel.ERROR -> ArcaneRed
    ActivityMessageLevel.WARNING -> ArcaneOrange
    ActivityMessageLevel.SUCCESS -> ArcaneGreen
    ActivityMessageLevel.INFO, ActivityMessageLevel.UNKNOWN -> ArcaneGray
}

/** Friendly label for whoever started the activity. */
val ActivityStartedBy.displayLabel: String
    get() {
        val trimmed = displayName?.trim().orEmpty()
        return trimmed.ifEmpty { username }
    }

/** snake_case -> Title Case (e.g. `image_pull` -> "Image Pull"). */
val String.activityDisplayName: String
    get() = split("_")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
