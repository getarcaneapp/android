package app.getarcane.android.ui.screens.containers

import androidx.compose.runtime.Composable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.screens.logs.LogViewer
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission

/** Container log viewer shared by the standalone route and detail tab. */
@Composable
fun LogsScreen(id: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val envId = manager.activeEnvironmentId
    val allowed = manager.currentUser?.hasPermission(Permission.Containers.LOGS, envId.rawValue) == true
    LogViewer(
        title = "Container ${id.take(12)} logs",
        sourceId = id,
        sourceKind = "container",
        permissionGranted = allowed,
        permissionMessage = "You don't have permission to view logs for this container in ${manager.activeEnvironmentName}.",
        onBack = onBack,
    ) { client, environment ->
        client.containers.logs(envId = environment, id = id, follow = true, tail = "200", timestamps = true)
    }
}

/** Embedded log viewer used by container detail. */
@Composable
fun EmbeddedLogsView(id: String, title: String) {
    val manager = LocalArcaneManager.current
    val envId = manager.activeEnvironmentId
    val allowed = manager.currentUser?.hasPermission(Permission.Containers.LOGS, envId.rawValue) == true
    LogViewer(
        title = title,
        sourceId = id,
        sourceKind = "container",
        permissionGranted = allowed,
        permissionMessage = "You don't have permission to view logs for this container in ${manager.activeEnvironmentName}.",
        onBack = null,
    ) { client, environment ->
        client.containers.logs(envId = environment, id = id, follow = true, tail = "200", timestamps = true)
    }
}
