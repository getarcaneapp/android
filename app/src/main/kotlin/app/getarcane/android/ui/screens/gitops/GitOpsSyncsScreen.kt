package app.getarcane.android.ui.screens.gitops

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ResourceIcon
import app.getarcane.android.ui.screens.jobs.relativeTime
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.gitops.CreateGitOpsSync
import app.getarcane.sdk.models.gitops.GitOpsSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GitOpsSyncsScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<GitOpsSync>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<GitOpsSync?>(null) }
    var confirmDelete by remember { mutableStateOf<GitOpsSync?>(null) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.gitops.listSyncsPaginated(limit = 100, envId = envId).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    fun act(label: String, block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            actionMessage = try {
                block(); "$label completed"
            } catch (e: Throwable) {
                friendlyErrorMessage(e)
            }
            refreshKey++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitOps") },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(
                            Icons.Filled.Add,
                            "Create GitOps Sync"
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search gitops") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val s = state) {
                is Loadable.Loading -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                is Loadable.Error -> ContentUnavailable(
                    "Error",
                    Icons.AutoMirrored.Filled.CallSplit,
                    s.message,
                    "Refresh"
                ) { refreshKey++ }

                is Loadable.Success -> {
                    val q = search.trim()
                    val filtered = s.value
                        .filter {
                            q.isEmpty() || it.name.contains(
                                q,
                                true
                            ) || it.projectName.contains(q, true) || it.branch.contains(q, true)
                        }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    if (s.value.isEmpty()) {
                        ContentUnavailable("No GitOps Syncs", Icons.AutoMirrored.Filled.CallSplit)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            actionMessage?.let { msg ->
                                item(key = "action-msg") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = ArcaneGreen)
                                        Text(
                                            msg,
                                            color = ArcaneGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            items(filtered, key = { it.id }) { sync ->
                                SyncRow(
                                    sync = sync,
                                    onClick = { details = sync },
                                    onSync = {
                                        act("Sync") {
                                            client!!.gitops.performSync(
                                                sync.id,
                                                envId = envId
                                            )
                                        }
                                    },
                                    onDelete = { confirmDelete = sync },
                                )
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        GitOpsSyncFormSheet(
            onDismiss = { showCreate = false },
            onSubmit = { form ->
                showCreate = false
                act("Create") {
                    client!!.gitops.createSync(
                        CreateGitOpsSync(
                            name = form.name,
                            repositoryId = form.repositoryId,
                            branch = form.branch.ifBlank { "main" },
                            composePath = form.composePath,
                            autoSync = form.autoSync,
                        ),
                        envId = envId,
                    )
                }
            },
        )
    }

    details?.let { sync -> GitOpsSyncDetailsDialog(sync = sync, onDismiss = { details = null }) }

    confirmDelete?.let { sync ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${sync.name}?") },
            text = { Text("This permanently removes the GitOps sync configuration.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act("Delete") { client!!.gitops.deleteSync(sync.id, envId = envId) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SyncRow(
    sync: GitOpsSync,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = { ResourceIcon(Icons.AutoMirrored.Filled.CallSplit, ArcaneIndigo) },
            headlineContent = { Text(sync.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val sub = buildString {
                    append(sync.branch)
                    if (sync.projectName.isNotEmpty()) append(" · ${sync.projectName}")
                }
                Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                sync.lastSyncStatus?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = syncStatusColor(it)
                    )
                }
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Sync Now") },
                onClick = { menu = false; onSync() },
                leadingIcon = { Icon(Icons.Filled.Sync, null) })
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menu = false; onDelete() },
                leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

@Composable
private fun GitOpsSyncDetailsDialog(sync: GitOpsSync, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sync.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Branch", sync.branch)
                DetailLine("Compose Path", sync.composePath)
                if (sync.projectName.isNotEmpty()) DetailLine("Project", sync.projectName)
                DetailLine("Target Type", sync.targetType)
                DetailLine("Auto Sync", if (sync.autoSync) "Yes" else "No")
                DetailLine("Sync Interval", "${sync.syncInterval}s")
                sync.lastSyncStatus?.let { DetailLine("Last Sync Status", it) }
                sync.lastSyncAt?.let {
                    DetailLine(
                        "Last Sync",
                        relativeTime(it.toEpochMilliseconds())
                    )
                }
                sync.lastSyncCommit?.let { DetailLine("Last Commit", it) }
                sync.lastSyncError?.takeIf { it.isNotEmpty() }?.let {
                    Column {
                        Text(
                            "Last Error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
                    }
                }
                DetailLine("ID", sync.id)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun syncStatusColor(status: String): androidx.compose.ui.graphics.Color =
    when (status.lowercase()) {
        "success", "synced", "ok" -> ArcaneGreen
        "error", "failed" -> ArcaneRed
        "pending", "syncing", "in_progress" -> ArcaneOrange
        else -> ArcaneGray
    }

/** Collected GitOps sync form values. */
private data class GitOpsSyncForm(
    val name: String,
    val repositoryId: String,
    val branch: String,
    val composePath: String,
    val autoSync: Boolean,
)

/**
 * Create form for a GitOps sync. Mirrors the iOS `GitOpsSyncsView` create fields (name, repository
 * id, branch, path, enabled). Save is disabled until required fields are filled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitOpsSyncFormSheet(onDismiss: () -> Unit, onSubmit: (GitOpsSyncForm) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var repositoryId by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var composePath by remember { mutableStateOf("") }
    var autoSync by remember { mutableStateOf(true) }

    val canSave = name.trim().isNotEmpty() && repositoryId.trim().isNotEmpty() && composePath.trim()
        .isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Create GitOps Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    onSubmit(
                        GitOpsSyncForm(
                            name.trim(),
                            repositoryId.trim(),
                            branch.trim(),
                            composePath.trim(),
                            autoSync
                        )
                    )
                }, enabled = canSave) {
                    Text("Save")
                }
            }
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                repositoryId,
                { repositoryId = it },
                label = { Text("Repository ID *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                branch,
                { branch = it },
                label = { Text("Branch") },
                placeholder = { Text("main") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                composePath,
                { composePath = it },
                label = { Text("Path *") },
                placeholder = { Text("docker-compose.yml") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto Sync", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = autoSync, onCheckedChange = { autoSync = it })
            }
        }
    }
}
