package app.getarcane.android.ui.screens.projects

import androidx.compose.ui.graphics.Color
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.StatusUnknown
import app.getarcane.sdk.models.project.ProjectDetails

/** Display name for a project — iOS `ProjectDetails.displayName` is just `name`. */
internal val ProjectDetails.displayName: String
    get() = name

/** Whether the project is fully running. */
internal val ProjectDetails.isRunning: Boolean
    get() = status.equals("running", ignoreCase = true)

/** Whether the project is stopped/exited (used for the "Stopped" section). */
internal val ProjectDetails.isStopped: Boolean
    get() = status.lowercase().let { it == "stopped" || it == "exited" }

/**
 * Semantic status color, mirroring iOS `ProjectDetails.statusColor` /
 * `ProjectsView.projectPreview`: running -> green, stopped/exited -> red,
 * partial/partially running -> orange, else gray.
 */
internal val ProjectDetails.statusColor: Color
    get() = when (status.lowercase()) {
        "running" -> ArcaneGreen
        "stopped", "exited" -> ArcaneRed
        "partial", "partially running" -> ArcaneOrange
        else -> StatusUnknown
    }
