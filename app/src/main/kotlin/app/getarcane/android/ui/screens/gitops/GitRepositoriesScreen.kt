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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ResourceIcon
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.sdk.models.gitops.CreateGitRepository
import app.getarcane.sdk.models.gitops.GitRepository
import app.getarcane.sdk.models.gitops.UpdateGitRepository
import kotlinx.coroutines.launch

/**
 * Git Repositories list. Mirrors the iOS `GitRepositoriesView` (a `DynamicResourceListView` over
 * `customize/git-repositories`): list + search + add form + per-row Test Connection / Delete +
 * details. Uses the typed `client.gitops` repository endpoints.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GitRepositoriesScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<GitRepository>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GitRepository?>(null) }
    var details by remember { mutableStateOf<GitRepository?>(null) }
    var confirmDelete by remember { mutableStateOf<GitRepository?>(null) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.gitops.listRepositoriesPaginated(limit = 100).data)
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
                title = { Text("Git Repositories") },
                actions = {
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "Add Git Repository") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search git repositories") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Error", Icons.AutoMirrored.Filled.CallSplit, s.message, "Refresh") { refreshKey++ }
                is Loadable.Success -> {
                    val q = search.trim()
                    val filtered = s.value
                        .filter { q.isEmpty() || it.name.contains(q, true) || it.url.contains(q, true) }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    if (s.value.isEmpty()) {
                        ContentUnavailable("No Git Repositories", Icons.AutoMirrored.Filled.CallSplit)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            actionMessage?.let { msg ->
                                item(key = "action-msg") {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = ArcaneGreen, modifier = Modifier.padding(0.dp))
                                        Text(msg, color = ArcaneGreen, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            items(filtered, key = { it.id }) { repo ->
                                RepoRow(
                                    repo = repo,
                                    onClick = { details = repo },
                                    onEdit = { editing = repo },
                                    onTest = { act("Test Connection") { client!!.gitops.testRepository(repo.id) } },
                                    onDelete = { confirmDelete = repo },
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
        GitRepositoryFormSheet(
            title = "Add Git Repository",
            existing = null,
            onDismiss = { showCreate = false },
            onSubmit = { form ->
                showCreate = false
                act("Create") {
                    client!!.gitops.createRepository(
                        CreateGitRepository(
                            name = form.name,
                            url = form.url,
                            authType = form.authType,
                            username = form.username.ifBlank { null },
                            token = form.token.ifBlank { null },
                            sshKey = form.sshKey.ifBlank { null },
                            description = form.description.ifBlank { null },
                            enabled = form.enabled,
                        ),
                    )
                }
            },
        )
    }

    editing?.let { repo ->
        GitRepositoryFormSheet(
            title = "Edit Git Repository",
            existing = repo,
            onDismiss = { editing = null },
            onSubmit = { form ->
                editing = null
                act("Update") {
                    client!!.gitops.updateRepository(
                        repo.id,
                        UpdateGitRepository(
                            name = form.name,
                            url = form.url,
                            authType = form.authType,
                            username = form.username.ifBlank { null },
                            token = form.token.ifBlank { null },
                            sshKey = form.sshKey.ifBlank { null },
                            description = form.description.ifBlank { null },
                            enabled = form.enabled,
                        ),
                    )
                }
            },
        )
    }

    details?.let { repo ->
        GitRepositoryDetailsDialog(repo = repo, onDismiss = { details = null })
    }

    confirmDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${repo.name}?") },
            text = { Text("This permanently removes the git repository configuration.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    act("Delete") { client!!.gitops.deleteRepository(repo.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RepoRow(repo: GitRepository, onClick: () -> Unit, onEdit: () -> Unit, onTest: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            leadingContent = { ResourceIcon(Icons.AutoMirrored.Filled.CallSplit, ArcaneIndigo) },
            headlineContent = { Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(repo.url, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Text(if (repo.enabled) "ENABLED" else "DISABLED", style = MaterialTheme.typography.labelSmall, color = if (repo.enabled) ArcaneGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
            DropdownMenuItem(text = { Text("Test Connection") }, onClick = { menu = false; onTest() }, leadingIcon = { Icon(Icons.Filled.VerifiedUser, null) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

@Composable
private fun GitRepositoryDetailsDialog(repo: GitRepository, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(repo.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("URL", repo.url)
                DetailLine("Auth Type", repo.authType)
                repo.username?.let { DetailLine("Username", it) }
                repo.sshHostKeyVerification?.let { DetailLine("SSH Host Key Verification", it) }
                repo.description?.let { DetailLine("Description", it) }
                DetailLine("Enabled", if (repo.enabled) "Yes" else "No")
                DetailLine("ID", repo.id)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
