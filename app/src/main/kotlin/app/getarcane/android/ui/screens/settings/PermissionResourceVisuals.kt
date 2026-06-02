package app.getarcane.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneMint
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.android.ui.theme.ArcaneYellow

/** Icon for a permission resource key. Mirrors iOS `PermissionPickerView.iconForResource`. */
fun permissionResourceIcon(key: String): ImageVector = when (key) {
    "containers" -> Icons.Filled.Inventory2
    "images" -> Icons.Filled.Image
    "projects" -> Icons.Filled.Layers
    "volumes" -> Icons.Filled.Storage
    "networks" -> Icons.Filled.Lan
    "swarm" -> Icons.Filled.Hub
    "users" -> Icons.Filled.People
    "roles" -> Icons.Filled.People
    "apikeys" -> Icons.Filled.VpnKey
    "settings" -> Icons.Filled.Tune
    "environments" -> Icons.Filled.Storage
    "registries" -> Icons.Filled.Cloud
    "templates" -> Icons.Filled.Description
    "git-repositories" -> Icons.Filled.CallMerge
    "gitops" -> Icons.Filled.Sync
    "webhooks" -> Icons.Filled.Webhook
    "system" -> Icons.Filled.Settings
    "vulnerabilities" -> Icons.Filled.BugReport
    "image-updates" -> Icons.Filled.Sync
    "events" -> Icons.Filled.Warning
    "dashboard" -> Icons.Filled.BarChart
    "jobs" -> Icons.Filled.CalendarMonth
    "notifications" -> Icons.Filled.NotificationsActive
    "customize" -> Icons.Filled.Brush
    "build-workspaces" -> Icons.Filled.Build
    else -> Icons.Filled.Lock
}

/** Tint for a permission resource key. Mirrors iOS `PermissionPickerView.colorForResource`. */
fun permissionResourceColor(key: String): Color = when (key) {
    "containers", "images", "projects", "users", "authentication" -> ArcaneBlue
    "volumes" -> ArcaneOrange
    "networks" -> ArcaneTeal
    "swarm" -> ArcaneMint
    "registries" -> ArcanePurple
    "templates", "git-repositories", "gitops" -> ArcaneIndigo
    "webhooks" -> ArcaneGreen
    "system", "settings" -> ArcaneGray
    "vulnerabilities" -> ArcaneRed
    "roles" -> ArcanePurple
    "apikeys" -> ArcaneYellow
    "events" -> ArcaneRed
    "jobs" -> ArcanePink
    else -> ArcaneGray
}
