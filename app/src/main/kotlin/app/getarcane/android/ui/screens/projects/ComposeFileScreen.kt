package app.getarcane.android.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.project.UpdateProject
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class PendingWorkspaceExit { BACK, RELOAD }
private enum class WorkspaceCreateKind { FILE, FOLDER }

/**
 * Existing-project file workspace. The public signature intentionally stays compatible with the
 * original read-only Compose viewer so the Projects navigation route can adopt it atomically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeFileScreen(
    projectId: String,
    projectName: String,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val environmentName = manager.activeEnvironmentName
    val user = manager.currentUser
    val canUpdateNow = user?.hasPermission(Permission.Projects.UPDATE, envId.rawValue) ?: false
    val scope = rememberCoroutineScope()

    var details by remember(projectId) { mutableStateOf<ProjectDetails?>(null) }
    var baseline by remember(projectId) { mutableStateOf<ProjectWorkspaceSession?>(null) }
    var session by remember(projectId) { mutableStateOf<ProjectWorkspaceSession?>(null) }
    var loading by remember(projectId) { mutableStateOf(true) }
    var saving by remember(projectId) { mutableStateOf(false) }
    var loadError by remember(projectId) { mutableStateOf<String?>(null) }
    var actionError by remember(projectId) { mutableStateOf<String?>(null) }
    var fileLoadingPath by remember(projectId) { mutableStateOf<String?>(null) }
    var fileError by remember(projectId) { mutableStateOf<String?>(null) }
    var fileReloadKey by remember(projectId) { mutableIntStateOf(0) }
    var reloadKey by remember(projectId) { mutableIntStateOf(0) }
    var addMenu by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<WorkspaceCreateKind?>(null) }
    var renamePath by remember { mutableStateOf<String?>(null) }
    var movePath by remember { mutableStateOf<String?>(null) }
    var deletePath by remember { mutableStateOf<String?>(null) }
    var pendingExit by remember { mutableStateOf<PendingWorkspaceExit?>(null) }
    var conflictMessage by remember { mutableStateOf<String?>(null) }
    var showVariables by remember { mutableStateOf(false) }

    fun requestBack() {
        if (session?.isDirty == true) pendingExit = PendingWorkspaceExit.BACK else onBack()
    }

    fun requestReload() {
        if (session?.isDirty == true) pendingExit = PendingWorkspaceExit.RELOAD else reloadKey++
    }

    BackHandler(onBack = ::requestBack)

    LaunchedEffect(client, envId.rawValue, projectId, reloadKey) {
        if (client == null) {
            loading = false
            loadError = "No Arcane client is available."
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        fileError = null
        try {
            val loaded = coroutineScope {
                val detailsRequest = async { client.projects.compose(envId = envId, projectId = projectId) }
                val workspaceRequest = async { client.projects.workspace(envId = envId, projectId = projectId) }
                detailsRequest.await() to workspaceRequest.await()
            }
            val loadedDetails = loaded.first
            val loadedSession = projectWorkspaceSession(
                details = loadedDetails,
                workspace = loaded.second,
                canUpdate = canUpdateNow,
            )
            details = loadedDetails
            baseline = loadedSession
            session = loadedSession
        } catch (error: CancellationException) {
            throw error
        } catch (error: ArcaneError.NotFound) {
            loadError = "This Arcane server does not support the project file workspace."
        } catch (error: Throwable) {
            loadError = friendlyErrorMessage(error)
        } finally {
            loading = false
        }
    }

    val selectedPath = session?.selectedPath
    val selectedEntry = session?.selectedEntry
    val selectedDocument = session?.selectedDocument

    LaunchedEffect(client, envId.rawValue, projectId, selectedPath, fileReloadKey) {
        val path = selectedPath ?: return@LaunchedEffect
        val current = session ?: return@LaunchedEffect
        val entry = current.entries[path] ?: return@LaunchedEffect
        if (entry.isDirectory || entry.kind != ProjectWorkspaceEntryKind.TEXT || current.documents[path] != null) {
            return@LaunchedEffect
        }
        if (client == null) return@LaunchedEffect
        fileLoadingPath = path
        fileError = null
        try {
            val response = client.projects.workspaceFile(envId = envId, projectId = projectId, relativePath = path)
            if (session?.selectedPath == path) {
                session = session?.withFileContent(
                    path = path,
                    content = response.content,
                    mimeType = response.mimeType,
                    size = response.size,
                    serverEditable = response.editable,
                    serverReadOnlyReason = response.readOnlyReason,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (session?.selectedPath == path) fileError = friendlyErrorMessage(error)
        } finally {
            if (fileLoadingPath == path) fileLoadingPath = null
        }
    }

    fun applySession(result: Result<ProjectWorkspaceSession>) {
        result.onSuccess { session = it }.onFailure { actionError = it.message ?: "The file operation failed." }
    }

    fun save() {
        val activeClient = client ?: return
        val current = session ?: return
        if (!current.isDirty || saving || !canUpdateNow) return
        val workspaceChanges = projectWorkspaceSdkChanges(current.workspaceChangeSet())
        if (workspaceChanges.isEmpty() && current.dirtyCoreDocuments().isEmpty()) return
        scope.launch {
            saving = true
            actionError = null
            var working = current
            var workingBaseline = baseline ?: current
            var workspaceSaved = false
            try {
                if (workspaceChanges.isNotEmpty()) {
                    val response = activeClient.projects.updateWorkspace(
                        envId = envId,
                        projectId = projectId,
                        fileTreeRevision = working.revision,
                        changes = workspaceChanges,
                    )
                    val loadedDetails = details ?: return@launch
                    val authoritativeEntries = projectWorkspaceSession(
                        details = loadedDetails,
                        workspace = response,
                        canUpdate = canUpdateNow,
                    ).entries
                    working = working
                        .committedWorkspace(response.fileTreeRevision, authoritativeEntries)
                        .copy(treeTruncated = response.fileTreeTruncated)
                    workingBaseline = working.baselineAfterWorkspaceCommit(workingBaseline)
                    session = working
                    baseline = workingBaseline
                    workspaceSaved = true
                }

                val core = working.dirtyCoreDocuments()
                if (core.isNotEmpty()) {
                    val composePath = working.corePaths.firstOrNull { it != ".env" }
                    val updatedDetails = activeClient.projects.update(
                        envId = envId,
                        projectId = projectId,
                        request = UpdateProject(
                            composeContent = composePath?.let(core::get)?.content,
                            envContent = core[".env"]?.content,
                        ),
                    )
                    details = updatedDetails
                    working = working
                        .copy(revision = updatedDetails.fileTreeRevision ?: working.revision)
                        .committedCore()
                    session = working
                    baseline = working
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ArcaneError.Conflict) {
                val prefix = if (workspaceSaved) "Other file operations were saved, but Compose or .env was not. " else ""
                conflictMessage = prefix + (error.detail ?: "The project files changed on the server while you were editing.")
            } catch (error: Throwable) {
                val prefix = if (workspaceSaved) {
                    "Other file operations were saved. Compose and .env edits are still unsaved: "
                } else {
                    ""
                }
                actionError = prefix + friendlyErrorMessage(error)
            } finally {
                saving = false
            }
        }
    }

    val displayedTitle = details?.displayName ?: projectName.ifBlank { "Project" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$displayedTitle Files", maxLines = 1)
                        if (session?.isDirty == true) {
                            Text("Unsaved changes", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = ::requestReload, enabled = !loading && !saving) {
                        Icon(Icons.Filled.Refresh, "Reload files")
                    }
                    Box {
                        IconButton(
                            onClick = { addMenu = true },
                            enabled = session?.let { selectedParentEditable(it) } == true && canUpdateNow && !saving,
                        ) { Icon(Icons.Filled.Add, "Create file or folder") }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("New File") },
                                leadingIcon = { Icon(Icons.Filled.InsertDriveFile, null) },
                                onClick = { addMenu = false; createKind = WorkspaceCreateKind.FILE },
                            )
                            DropdownMenuItem(
                                text = { Text("New Folder") },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = { addMenu = false; createKind = WorkspaceCreateKind.FOLDER },
                            )
                        }
                    }
                    IconButton(
                        onClick = ::save,
                        enabled = session?.isDirty == true && !saving && canUpdateNow,
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Save, "Save project files")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && session == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loadError != null && session == null -> Box(Modifier.padding(16.dp)) {
                    ErrorBanner(loadError!!, onRetry = { reloadKey++ })
                }
                else -> {
                    val current = session
                    if (current != null) {
                        ProjectWorkspaceContent(
                            session = current,
                            canUpdateNow = canUpdateNow,
                            selectedEntry = selectedEntry,
                            selectedDocument = selectedDocument,
                            fileLoading = fileLoadingPath == selectedPath,
                            fileError = fileError,
                            actionError = actionError,
                            onSelect = { path ->
                                session = session?.select(path)
                                fileError = null
                                fileReloadKey++
                            },
                            onEdit = { text ->
                                val path = session?.selectedPath ?: return@ProjectWorkspaceContent
                                applySession(requireNotNull(session).edit(path, text))
                            },
                            onRename = { renamePath = selectedPath },
                            onMove = { movePath = selectedPath },
                            onDelete = { deletePath = selectedPath },
                            onVariables = { showVariables = true },
                            onRetryFile = { fileReloadKey++ },
                            structuralActionsAllowed = selectedPath !in current.corePaths,
                        )
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        WorkspaceTextInputDialog(
            title = if (kind == WorkspaceCreateKind.FILE) "New File" else "New Folder",
            label = "Name",
            initialValue = "",
            onDismiss = { createKind = null },
            onConfirm = { name ->
                val current = session ?: return@WorkspaceTextInputDialog
                val parent = selectedParentPath(current)
                val result = if (kind == WorkspaceCreateKind.FILE) current.createFile(parent, name)
                else current.createFolder(parent, name)
                createKind = null
                applySession(result)
            },
        )
    }

    renamePath?.let { path ->
        WorkspaceTextInputDialog(
            title = "Rename ${workspaceBaseName(path)}",
            label = "New name",
            initialValue = workspaceBaseName(path),
            onDismiss = { renamePath = null },
            onConfirm = { name -> renamePath = null; session?.rename(path, name)?.let(::applySession) },
        )
    }

    movePath?.let { path ->
        WorkspaceTextInputDialog(
            title = "Move ${workspaceBaseName(path)}",
            label = "Destination folder (blank for project root)",
            initialValue = workspaceParentPath(path).orEmpty(),
            onDismiss = { movePath = null },
            onConfirm = { parent -> movePath = null; session?.move(path, parent.ifBlank { null })?.let(::applySession) },
        )
    }

    deletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deletePath = null },
            title = { Text("Delete ${workspaceBaseName(path)}?") },
            text = {
                Text("Delete $path from project $displayedTitle in $environmentName? Folders and their contents are removed recursively when you save.")
            },
            confirmButton = {
                TextButton(onClick = { deletePath = null; session?.delete(path)?.let(::applySession) }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deletePath = null }) { Text("Cancel") } },
        )
    }

    pendingExit?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingExit = null },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your edits and staged file operations have not been saved to $displayedTitle.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = null
                    when (pending) {
                        PendingWorkspaceExit.BACK -> onBack()
                        PendingWorkspaceExit.RELOAD -> {
                            session = baseline
                            reloadKey++
                        }
                    }
                }) { Text(if (pending == PendingWorkspaceExit.BACK) "Discard and Leave" else "Discard and Reload") }
            },
            dismissButton = { TextButton(onClick = { pendingExit = null }) { Text("Keep Editing") } },
        )
    }

    conflictMessage?.let { message ->
        fun rebaseKeepingLocal() {
            val current = session ?: return
            val activeClient = client ?: return
            val originalConflict = conflictMessage
            conflictMessage = null
            scope.launch {
                saving = true
                try {
                    val latestDetails = activeClient.projects.compose(envId = envId, projectId = projectId)
                    val latestWorkspace = activeClient.projects.workspace(envId = envId, projectId = projectId)
                    var latest = projectWorkspaceSession(latestDetails, latestWorkspace, canUpdateNow)
                    val changes = current.workspaceChangeSet()
                    changes.documentUpdates.forEach { document ->
                        val serverPath = changes.serverPathForDocumentUpdate(document.path)
                        val response = activeClient.projects.workspaceFile(
                            envId = envId,
                            projectId = projectId,
                            relativePath = serverPath,
                        )
                        latest = latest.withFileContent(
                            path = serverPath,
                            content = response.content,
                            mimeType = response.mimeType,
                            size = response.size,
                            serverEditable = response.editable,
                            serverReadOnlyReason = response.readOnlyReason,
                        )
                    }
                    val rebased = current.replayDraftOnto(latest).getOrThrow()
                    details = latestDetails
                    baseline = latest
                    session = rebased
                    actionError = "Your draft was rebased onto the latest server files. Review it before saving again."
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    actionError = "Your draft is still preserved and could not be safely rebased: ${friendlyErrorMessage(error)}"
                    conflictMessage = originalConflict
                } finally {
                    saving = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = { conflictMessage = null },
            title = { Text("Project Files Changed") },
            text = { Text("$message\n\nYour unsaved changes are still available on this device.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = ::rebaseKeepingLocal) { Text("Rebase My Draft") }
                    TextButton(onClick = {
                        conflictMessage = null
                        session = baseline
                        reloadKey++
                    }) { Text("Reload Server Version") }
                }
            },
        )
    }

    if (showVariables) {
        val current = session
        val document = current?.selectedDocument
        if (current != null && document != null) {
            RenderComposeView(
                initialCompose = document.content,
                initialEnv = current.documents[".env"]?.content.orEmpty(),
                onApply = { resolved ->
                    showVariables = false
                    applySession(current.edit(document.path, resolved))
                },
                onCancel = { showVariables = false },
            )
        } else {
            showVariables = false
        }
    }
}

@Composable
private fun ProjectWorkspaceContent(
    session: ProjectWorkspaceSession,
    canUpdateNow: Boolean,
    selectedEntry: ProjectWorkspaceEntryModel?,
    selectedDocument: ProjectWorkspaceDocument?,
    fileLoading: Boolean,
    fileError: String?,
    actionError: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onVariables: () -> Unit,
    onRetryFile: () -> Unit,
    structuralActionsAllowed: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        session.rootReadOnlyReason?.let { WorkspaceNotice(it, Icons.Filled.Lock) }
        if (!canUpdateNow && session.rootReadOnlyReason == null) {
            WorkspaceNotice("Your project update permission changed. Unsaved edits are preserved, but saving is disabled.", Icons.Filled.Lock)
        }
        if (session.dirtyCoreDocuments().isNotEmpty()) {
            WorkspaceNotice(
                "Arcane does not currently provide optimistic conflict detection for Compose or .env; saving those fields is last-write-wins.",
                Icons.Filled.Warning,
            )
        }
        if (session.treeTruncated) {
            WorkspaceNotice("The server truncated this file tree. Some project files are not shown; reload after reducing the project tree size.", Icons.Filled.Warning)
        }
        actionError?.let { Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { ErrorBanner(it) } }

        Text(
            "FILES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(0.42f)) {
            items(session.orderedEntries(), key = ProjectWorkspaceEntryModel::relativePath) { entry ->
                ProjectWorkspaceTreeRow(
                    entry = entry,
                    selected = entry.relativePath == session.selectedPath,
                    onClick = { onSelect(entry.relativePath) },
                )
            }
        }
        HorizontalDivider()
        ProjectWorkspaceEditor(
            entry = selectedEntry,
            document = selectedDocument,
            canUpdateNow = canUpdateNow,
            loading = fileLoading,
            error = fileError,
            onEdit = onEdit,
            onRename = onRename,
            onMove = onMove,
            onDelete = onDelete,
            onVariables = onVariables,
            onRetry = onRetryFile,
            structuralActionsAllowed = structuralActionsAllowed,
            modifier = Modifier.fillMaxWidth().weight(0.58f),
        )
    }
}

@Composable
private fun ProjectWorkspaceTreeRow(
    entry: ProjectWorkspaceEntryModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = workspaceEntryIcon(entry.kind)
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = (12 + entry.depth * 18).dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (entry.kind == ProjectWorkspaceEntryKind.SYMLINK && entry.linkTarget != null) {
                Text("→ ${entry.linkTarget}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (!entry.editable && !entry.isDirectory) Icon(Icons.Filled.Lock, "Read-only", Modifier.size(15.dp))
        if (entry.size > 0 && !entry.isDirectory) Text(formatWorkspaceSize(entry.size), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProjectWorkspaceEditor(
    entry: ProjectWorkspaceEntryModel?,
    document: ProjectWorkspaceDocument?,
    canUpdateNow: Boolean,
    loading: Boolean,
    error: String?,
    onEdit: (String) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onVariables: () -> Unit,
    onRetry: () -> Unit,
    structuralActionsAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (entry == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Select a project file.") }
            return
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.relativePath, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1)
                entry.readOnlyReason?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (isComposeWorkspacePath(entry.relativePath) && document != null) {
                IconButton(onClick = onVariables) { Icon(Icons.Filled.DataObject, "Resolve variables") }
            }
            if (entry.structuralEditable && canUpdateNow && structuralActionsAllowed) {
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Rename") }
                IconButton(onClick = onMove) { Icon(Icons.Filled.DriveFileMove, "Move") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
        HorizontalDivider()
        when {
            entry.isDirectory -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Folder · choose a file to view or edit its contents.")
            }
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.padding(16.dp)) { ErrorBanner(error, onRetry = onRetry) }
            document != null -> {
                BasicTextField(
                    value = document.content,
                    onValueChange = onEdit,
                    enabled = document.editable && canUpdateNow,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
            else -> WorkspaceUnavailableFile(entry)
        }
    }
}

@Composable
private fun WorkspaceUnavailableFile(entry: ProjectWorkspaceEntryModel) {
    val message = when (entry.kind) {
        ProjectWorkspaceEntryKind.BINARY -> "Binary files can be browsed in the tree but cannot be edited as text."
        ProjectWorkspaceEntryKind.TOO_LARGE -> "This file is too large for safe mobile text editing."
        ProjectWorkspaceEntryKind.SYMLINK -> "Symbolic links are shown for context and cannot be edited."
        ProjectWorkspaceEntryKind.SPECIAL -> "This special file cannot be opened in the text editor."
        else -> entry.readOnlyReason ?: "No text content is available for this file."
    }
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(workspaceEntryIcon(entry.kind), null, Modifier.size(36.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WorkspaceNotice(message: String, icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkspaceTextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = label.startsWith("Destination") || value.isNotBlank()) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun selectedParentPath(session: ProjectWorkspaceSession): String? =
    session.selectedEntry?.let { if (it.isDirectory) it.relativePath else workspaceParentPath(it.relativePath) }

private fun selectedParentEditable(session: ProjectWorkspaceSession): Boolean {
    val parent = selectedParentPath(session) ?: return session.rootEditable
    return session.entries[parent]?.let { it.isDirectory && it.editable } == true
}

private fun isComposeWorkspacePath(path: String): Boolean {
    val name = workspaceBaseName(path).lowercase()
    return name == "compose.yml" || name == "compose.yaml" || name == "docker-compose.yml" || name == "docker-compose.yaml"
}

private fun workspaceEntryIcon(kind: ProjectWorkspaceEntryKind): ImageVector = when (kind) {
    ProjectWorkspaceEntryKind.DIRECTORY -> Icons.Filled.Folder
    ProjectWorkspaceEntryKind.SYMLINK -> Icons.Filled.Link
    ProjectWorkspaceEntryKind.BINARY, ProjectWorkspaceEntryKind.TOO_LARGE, ProjectWorkspaceEntryKind.SPECIAL -> Icons.Filled.Warning
    ProjectWorkspaceEntryKind.TEXT -> Icons.Filled.Description
}

private fun formatWorkspaceSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1_024} KB"
    else -> "${bytes / 1_048_576} MB"
}
