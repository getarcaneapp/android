package app.getarcane.android.ui.screens.projects

import androidx.compose.runtime.Composable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.screens.logs.LogViewer
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission

/** Project log viewer with the same retention, search, copy/share/export, and follow behavior as containers. */
@Composable
fun ProjectLogsScreen(projectId: String, title: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val envId = manager.activeEnvironmentId
    val allowed = manager.currentUser?.hasPermission(Permission.Projects.LOGS, envId.rawValue) == true
    LogViewer(
        title = title.ifEmpty { "Project logs" },
        sourceId = projectId,
        sourceKind = "project",
        permissionGranted = allowed,
        permissionMessage = "You don't have permission to view logs for this project in ${manager.activeEnvironmentName}.",
        onBack = onBack,
    ) { client, environment ->
        client.projects.logs(envId = environment, projectId = projectId, follow = true, tail = "200", timestamps = true)
    }
}
