package app.getarcane.android.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.sdk.models.project.ProjectDetails
import kotlinx.coroutines.launch

/** Streaming-capable lifecycle action keys passed to the stream destination. */
internal object ProjectAction {
    const val UP = "up"
    const val REDEPLOY = "redeploy"
    const val PULL = "pull"
    const val BUILD = "build"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onStream: (id: String, action: String, title: String) -> Unit,
    onLogs: (id: String, title: String) -> Unit,
    onCompose: (id: String, title: String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<ProjectDetails>>(Loadable.Loading) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var actioning by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, refreshKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            Loadable.Success(client.projects.get(envId = envId, projectId = projectId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    // Re-fetch whenever the screen returns to the foreground (e.g. after a
    // streaming deploy / down / logs completes), mirroring iOS's reload on the
    // streaming action's onComplete + the detail's `.refreshable`.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        var resumedOnce = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (resumedOnce) refreshKey++ else resumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun simpleAction(label: String, block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            actioning = true
            actionStatus = "$label…"
            errorMessage = null
            try {
                block()
                actionStatus = "Done."
                refreshKey++
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
                actionStatus = null
            } finally {
                actioning = false
            }
        }
    }

    fun runArchive(unarchive: Boolean) {
        if (client == null) return
        scope.launch {
            actioning = true
            actionStatus = if (unarchive) "Unarchiving…" else "Archiving…"
            errorMessage = null
            try {
                if (unarchive) client.projects.unarchive(envId = envId, projectId = projectId)
                else client.projects.archive(envId = envId, projectId = projectId)
                onBack()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
                actionStatus = null
            } finally {
                actioning = false
            }
        }
    }

    fun runDelete(removeFiles: Boolean) {
        if (client == null) return
        confirmDelete = false
        scope.launch {
            actioning = true
            actionStatus = "Deleting…"
            errorMessage = null
            try {
                client.projects.destroy(
                    envId = envId,
                    projectId = projectId,
                    options = app.getarcane.sdk.models.project.DestroyProject(
                        removeFiles = removeFiles,
                        removeVolumes = false,
                    ),
                )
                onBack()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
                actionStatus = null
            } finally {
                actioning = false
            }
        }
    }

    val project = (state as? Loadable.Success)?.value
    val title = project?.displayName ?: "Project"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !actioning) { Icon(Icons.Filled.MoreVert, "Options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("View Compose File") },
                                onClick = { menu = false; onCompose(projectId, title) },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                            )
                            if (project?.isArchived == true) {
                                DropdownMenuItem(
                                    text = { Text("Unarchive Project") },
                                    onClick = { menu = false; runArchive(unarchive = true) },
                                    leadingIcon = { Icon(Icons.Filled.Unarchive, null) },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive Project") },
                                    onClick = { menu = false; runArchive(unarchive = false) },
                                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete Project") },
                                onClick = { menu = false; confirmDelete = true },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    val p = s.value
                    val isRunning = p.isRunning
                    val hasBuild = p.hasBuildDirective == true
                    LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { ProjectHeader(p) }

                        actionStatus?.let { status ->
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (actioning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        errorMessage?.let { msg ->
                            item { ErrorBanner(msg) }
                        }

                        // Lifecycle action buttons (iOS actionToolbar parity).
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isRunning) {
                                    ActionChip("Stop", Icons.Filled.Stop, MaterialTheme.colorScheme.error, !actioning) {
                                        simpleAction("Stopping") { client!!.projects.down(envId = envId, projectId = projectId) }
                                    }
                                    ActionChip("Restart", Icons.Filled.Refresh, ArcaneOrange, !actioning) {
                                        simpleAction("Restarting") { client!!.projects.restart(envId = envId, projectId = projectId) }
                                    }
                                } else {
                                    ActionChip("Deploy", Icons.Filled.PlayArrow, MaterialTheme.colorScheme.primary, !actioning) {
                                        onStream(projectId, ProjectAction.UP, "Deploy ${p.displayName}")
                                    }
                                }
                                ActionChip("Redeploy", Icons.Filled.SyncAlt, MaterialTheme.colorScheme.primary, !actioning) {
                                    onStream(projectId, ProjectAction.REDEPLOY, "Redeploy ${p.displayName}")
                                }
                                ActionChip("Pull", Icons.Filled.ArrowDownward, MaterialTheme.colorScheme.primary, !actioning) {
                                    onStream(projectId, ProjectAction.PULL, "Pull Images")
                                }
                                if (hasBuild) {
                                    ActionChip("Build", Icons.Filled.Build, ArcaneIndigo, !actioning) {
                                        onStream(projectId, ProjectAction.BUILD, "Build Images")
                                    }
                                }
                                ActionChip("Logs", Icons.AutoMirrored.Filled.Article, MaterialTheme.colorScheme.onSurfaceVariant, !actioning) {
                                    onLogs(projectId, p.displayName)
                                }
                            }
                        }

                        item {
                            DetailSection("Info") {
                                LabeledRow("Status", p.status.replaceFirstChar { it.uppercase() })
                                LabeledRow("Services", p.serviceCount.toString())
                                LabeledRow("Running", p.runningCount.toString())
                                LabeledRow("Created", p.createdAt)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Project") },
            text = { Text("Remove the project from Arcane, or also remove its files from disk.") },
            confirmButton = {
                Column {
                    TextButton(onClick = { runDelete(removeFiles = false) }) { Text("Delete") }
                    TextButton(onClick = { runDelete(removeFiles = true) }) { Text("Delete and Remove Files") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectHeader(project: ProjectDetails) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CachedAsyncImage(url = project.iconUrl, size = 56.dp, shape = CircleShape) {
            Box(
                Modifier.size(56.dp).background(ArcaneIndigo, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Layers, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val count = project.serviceCount
            Text(
                "$count service${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(status = project.status)
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text("  $label")
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        content()
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}
