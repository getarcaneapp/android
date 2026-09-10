package app.getarcane.android.ui.screens.settings.variables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.loadCompleteEnvironments
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ClearSensitiveStateOnStop
import app.getarcane.android.ui.components.ProtectSensitiveWindow
import app.getarcane.android.ui.components.SensitiveOutlinedField
import app.getarcane.android.ui.screens.settings.ConfirmDialog
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.variable.EnvironmentSyncStatus
import app.getarcane.sdk.models.variable.VariableSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standalone global-variable administration. [sessionKey] must include normalized server identity
 * and signed-in account identity. Replacing it rejects old asynchronous results and destroys every
 * non-saveable editor value held by this screen.
 */
@Composable
fun VariablesScreen(
    client: ArcaneClient?,
    currentUser: User?,
    sessionKey: String,
    onBack: () -> Unit,
) {
    val permissions = remember(currentUser) { variablePermissionPolicy(currentUser) }
    val store = remember(sessionKey) { VariablesStore(sessionKey) }
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshRevision by remember(sessionKey) { mutableStateOf(0L) }
    var query by remember(sessionKey) { mutableStateOf("") }
    var editorState by remember(sessionKey) { mutableStateOf(SensitiveEditorState(sessionKey)) }
    var editorError by remember(sessionKey) { mutableStateOf<String?>(null) }
    var conflict by remember(sessionKey) { mutableStateOf<VariableEditConflict?>(null) }
    var pendingDelete by remember(sessionKey) { mutableStateOf<VariableDisplay?>(null) }
    var saving by remember(sessionKey) { mutableStateOf(false) }
    var actionJob by remember { mutableStateOf<Job?>(null) }

    fun closeEditor() {
        editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Closed)
        editorError = null
        conflict = null
        saving = false
    }

    fun openEditor(variable: VariableDisplay?) {
        val draft = variable?.toEditorDraft() ?: VariableEditorDraft()
        editorState = reduceSensitiveEditor(
            editorState,
            SensitiveEditorEvent.Open(draft, variable?.initialEditorValue().orEmpty()),
        )
        editorError = null
        conflict = null
    }

    ClearSensitiveStateOnStop {
        if (editorState.editor?.isSecret == true) {
            actionJob?.cancel()
            actionJob = null
            closeEditor()
        }
    }

    LaunchedEffect(client, currentUser, sessionKey, permissions.canRead, refreshRevision) {
        actionJob?.cancel()
        actionJob = null
        closeEditor()
        pendingDelete = null
        val generation = store.beginLoad()
        if (!permissions.canRead || client == null) {
            store.dispatch(VariablesEvent.LoadSucceeded(generation, emptyList(), emptyList(), emptyList()))
            return@LaunchedEffect
        }
        try {
            val loaded = coroutineScope {
                val statuses = async { optionalLoad { client.variables.syncStatus() } }
                val environments = async {
                    optionalLoad { loadCompleteEnvironments { client.environments.list(it) } }
                }
                val variables = client.variables.list().map(GlobalVariableMapper)
                Triple(variables, environments.await().orEmpty(), statuses.await().orEmpty())
            }
            val retainedIds = loaded.first.flatMapTo(linkedSetOf(), VariableDisplay::environmentIds)
            store.dispatch(
                VariablesEvent.LoadSucceeded(
                    generation = generation,
                    variables = loaded.first,
                    environments = environmentChoices(loaded.second, retainedIds),
                    syncStatuses = loaded.third,
                ),
            )
            if (loaded.third.any { it.status == VariableSyncState.PENDING }) {
                val result = pollVariableSync(
                    initial = loaded.third,
                    isCurrent = { store.state.value.generation == generation },
                    pause = { delay(SYNC_POLL_DELAY_MS) },
                    loadStatus = { client.variables.syncStatus() },
                )
                store.dispatch(
                    VariablesEvent.SyncFinished(
                        generation,
                        result.statuses,
                        result.summary == SyncSummary.TIMED_OUT,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            store.dispatch(
                VariablesEvent.LoadFailed(
                    generation = generation,
                    message = friendlyErrorMessage(error),
                    unsupported = error is ArcaneError.NotFound,
                ),
            )
        }
    }

    fun startSync() {
        val activeClient = client ?: return
        val generation = store.beginSync()
        actionJob?.cancel()
        actionJob = scope.launch {
            try {
                val initial = activeClient.variables.sync()
                val result = pollVariableSync(
                    initial = initial,
                    isCurrent = { store.state.value.generation == generation },
                    pause = { delay(SYNC_POLL_DELAY_MS) },
                    loadStatus = { activeClient.variables.syncStatus() },
                )
                store.dispatch(
                    VariablesEvent.SyncFinished(
                        generation,
                        result.statuses,
                        result.summary == SyncSummary.TIMED_OUT,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                store.dispatch(VariablesEvent.ActionFailed(generation, friendlyErrorMessage(error)))
            }
        }
    }

    SettingsListScaffold(
        title = "Variables",
        onBack = onBack,
        onAdd = if (permissions.canCreate && client != null && !state.isUnsupported) {
            { openEditor(null) }
        } else null,
        addContentDescription = "Create variable",
        actions = {
            if (permissions.canSync && client != null && !state.isUnsupported) {
                IconButton(enabled = !state.isSyncing, onClick = ::startSync) {
                    if (state.isSyncing) CircularProgressIndicator(Modifier.padding(8.dp))
                    else Icon(Icons.Filled.Sync, contentDescription = "Sync variables")
                }
            }
            if (permissions.canRead && client != null) {
                IconButton(onClick = { refreshRevision++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh variables")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissions.canRead -> ContentUnavailable(
                    "Variables Access Required",
                    Icons.Filled.Lock,
                    "Your role cannot view global variables.",
                )
                client == null -> ContentUnavailable(
                    "Server Unavailable",
                    Icons.Filled.VpnKey,
                    "Connect to an Arcane server first.",
                )
                state.isLoading && state.variables.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isUnsupported -> ContentUnavailable(
                    "Variables Unavailable",
                    Icons.Filled.VpnKey,
                    "Update Arcane to manage scoped variables from the mobile app.",
                )
                state.errorMessage != null && state.variables.isEmpty() -> ContentUnavailable(
                    "Couldn't Load Variables",
                    Icons.Filled.VpnKey,
                    state.errorMessage,
                    "Try Again",
                ) { refreshRevision++ }
                else -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search variables") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    (state.actionMessage ?: state.errorMessage)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    VariablesList(
                        variables = filterVariables(state.variables, query, state.environments),
                        environments = state.environments,
                        statuses = state.syncStatuses,
                        canEdit = permissions.canUpdate,
                        canDelete = permissions.canDelete,
                        onEdit = ::openEditor,
                        onDelete = { pendingDelete = it },
                    )
                }
            }
        }
    }

    val activeEditor = editorState.editor
    if (activeEditor != null) {
        ProtectSensitiveWindow(activeEditor.isSecret)
        VariableEditorDialog(
            draft = activeEditor,
            editorValue = editorState.value,
            environments = environmentChoices(
                emptyList(),
                activeEditor.environmentIds,
            ).let { retained ->
                mergeEnvironmentChoices(state.environments, retained)
            },
            saving = saving,
            error = editorError,
            conflict = conflict,
            onDraftChange = { updated ->
                editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.DraftChanged(updated))
            },
            onEditorValueChange = {
                editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.ValueChanged(it))
            },
            onDismiss = ::closeEditor,
            onReloadConflict = {
                when (val activeConflict = conflict) {
                    is VariableEditConflict.Changed -> openEditor(activeConflict.latest)
                    VariableEditConflict.Deleted -> {
                        closeEditor()
                        refreshRevision++
                    }
                    null -> Unit
                }
            },
            onSave = {
                val submittedDraft = activeEditor
                val submittedValue = editorState.value
                val plan = variableWritePlan(submittedDraft, submittedValue)
                val invalid = plan.exceptionOrNull()
                if (invalid != null) {
                    editorError = invalid.message
                    editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Failed)
                    return@VariableEditorDialog
                }
                val activeClient = client
                if (activeClient == null) {
                    editorError = "No Arcane client is available."
                    editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Failed)
                    return@VariableEditorDialog
                }
                val generation = store.beginMutation()
                saving = true
                editorError = null
                actionJob?.cancel()
                actionJob = scope.launch {
                    try {
                        val latest = activeClient.variables.list().map(GlobalVariableMapper)
                        val editConflict = concurrentVariableEdit(submittedDraft, latest)
                        if (editConflict != null) {
                            when (editConflict) {
                                is VariableEditConflict.Changed -> store.dispatch(
                                    VariablesEvent.MutationFinished(generation, variable = editConflict.latest),
                                )
                                VariableEditConflict.Deleted -> store.dispatch(
                                    VariablesEvent.MutationFinished(generation, deletedId = submittedDraft.id),
                                )
                            }
                            conflict = editConflict
                            editorError = when (editConflict) {
                                is VariableEditConflict.Changed ->
                                    "This variable changed on the server. Reload it before saving."
                                VariableEditConflict.Deleted ->
                                    "This variable was deleted on the server. Close the editor and refresh."
                            }
                            editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Failed)
                            saving = false
                            return@launch
                        }
                        val response = when (val write = plan.getOrThrow()) {
                            is VariableWritePlan.Create -> activeClient.variables.create(write.request)
                            is VariableWritePlan.Update -> activeClient.variables.update(write.id, write.request)
                        }
                        editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Closed)
                        conflict = null
                        editorError = null
                        saving = false
                        store.dispatch(
                            VariablesEvent.MutationFinished(
                                generation = generation,
                                variable = response.variable?.toVariableDisplay(),
                                syncStatuses = response.syncResults,
                            ),
                        )
                        if (store.state.value.generation != generation) {
                            refreshRevision++
                            return@launch
                        }
                        val result = pollVariableSync(
                            initial = response.syncResults,
                            isCurrent = { store.state.value.generation == generation },
                            pause = { delay(SYNC_POLL_DELAY_MS) },
                            loadStatus = { activeClient.variables.syncStatus() },
                        )
                        store.dispatch(
                            VariablesEvent.MutationFinished(
                                generation = generation,
                                variable = response.variable?.toVariableDisplay(),
                                syncStatuses = result.statuses,
                                timedOut = result.summary == SyncSummary.TIMED_OUT,
                            ),
                        )
                        if (response.variable == null) refreshRevision++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        editorState = reduceSensitiveEditor(editorState, SensitiveEditorEvent.Failed)
                        editorError = friendlyErrorMessage(error)
                        saving = false
                        store.dispatch(VariablesEvent.ActionFailed(generation, friendlyErrorMessage(error)))
                    }
                }
            },
        )
    }

    pendingDelete?.let { variable ->
        ConfirmDialog(
            title = "Delete ${variable.key}?",
            message = "This removes the global variable from its scoped environments. This action cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                val activeClient = client ?: return@ConfirmDialog
                val generation = store.beginMutation()
                pendingDelete = null
                actionJob?.cancel()
                actionJob = scope.launch {
                    try {
                        val latest = activeClient.variables.list().map(GlobalVariableMapper)
                        val current = latest.firstOrNull { it.id == variable.id }
                        if (current == null) {
                            store.dispatch(VariablesEvent.MutationFinished(generation, deletedId = variable.id))
                            store.dispatch(
                                VariablesEvent.ActionFailed(generation, "This variable was already deleted on the server."),
                            )
                            return@launch
                        }
                        if (current.revision != variable.revision) {
                            store.dispatch(VariablesEvent.MutationFinished(generation, variable = current))
                            store.dispatch(
                                VariablesEvent.ActionFailed(
                                    generation,
                                    "This variable changed on the server. Review the latest version before deleting it.",
                                ),
                            )
                            return@launch
                        }
                        val response = activeClient.variables.delete(variable.id)
                        store.dispatch(
                            VariablesEvent.MutationFinished(
                                generation = generation,
                                deletedId = variable.id,
                                syncStatuses = response.syncResults,
                            ),
                        )
                        if (store.state.value.generation != generation) {
                            refreshRevision++
                            return@launch
                        }
                        val result = pollVariableSync(
                            response.syncResults,
                            isCurrent = { store.state.value.generation == generation },
                            pause = { delay(SYNC_POLL_DELAY_MS) },
                            loadStatus = { activeClient.variables.syncStatus() },
                        )
                        store.dispatch(
                            VariablesEvent.MutationFinished(
                                generation = generation,
                                deletedId = variable.id,
                                syncStatuses = result.statuses,
                                timedOut = result.summary == SyncSummary.TIMED_OUT,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        store.dispatch(VariablesEvent.ActionFailed(generation, friendlyErrorMessage(error)))
                    }
                }
            },
        )
    }
}

private val GlobalVariableMapper: (app.getarcane.sdk.models.variable.GlobalVariable) -> VariableDisplay =
    { it.toVariableDisplay() }

private const val SYNC_POLL_DELAY_MS = 2_000L

private suspend fun <T> optionalLoad(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    null
}

@Composable
private fun VariablesList(
    variables: List<VariableDisplay>,
    environments: List<EnvironmentChoice>,
    statuses: List<EnvironmentSyncStatus>,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: (VariableDisplay) -> Unit,
    onDelete: (VariableDisplay) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showSyncDetails by remember(statuses) { mutableStateOf(false) }
    if (variables.isEmpty()) {
        ContentUnavailable(
            "No Variables",
            Icons.Filled.VpnKey,
            "Create a variable and choose which environments receive it.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (statuses.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(syncSummaryLabel(statuses), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showSyncDetails = !showSyncDetails }) {
                        Text(if (showSyncDetails) "Hide details" else "Show details")
                    }
                }
                HorizontalDivider()
            }
        }
        if (showSyncDetails) {
            items(statuses, key = EnvironmentSyncStatus::environmentId) { status ->
                VariableSyncStatusRow(status)
            }
            item { HorizontalDivider() }
        }
        items(variables, key = VariableDisplay::id) { variable ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canEdit) Modifier.clickable { onEdit(variable) } else Modifier)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = variableAccessibilityLabel(variable)
                    },
                ) {
                    Text(variable.key, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (variable.isSecret) "Secret · value hidden" else variable.visibleValue.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(variableScopeLabel(variable, environments), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(variable.key)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy key ${variable.key}")
                }
                if (!variable.isSecret) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(variable.visibleValue.orEmpty())) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy value ${variable.key}")
                    }
                }
                if (canDelete) {
                    IconButton(onClick = { onDelete(variable) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${variable.key}")
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun VariableSyncStatusRow(status: EnvironmentSyncStatus) {
    val label = status.environmentName?.trim().orEmpty().ifEmpty { status.environmentId }
    val stateLabel = when (status.status) {
        VariableSyncState.SYNCED -> "Synced"
        VariableSyncState.PENDING -> "Pending"
        VariableSyncState.ERROR -> "Error"
        VariableSyncState.UNKNOWN -> "Unknown"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = when (status.status) {
                    VariableSyncState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        status.error?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun syncSummaryLabel(statuses: List<EnvironmentSyncStatus>): String {
    val errors = statuses.count { it.status == VariableSyncState.ERROR }
    val pending = statuses.count { it.status == VariableSyncState.PENDING }
    val unknown = statuses.count { it.status == VariableSyncState.UNKNOWN }
    return when {
        errors > 0 -> "$errors environment${if (errors == 1) "" else "s"} failed to sync"
        pending > 0 -> "$pending environment${if (pending == 1) "" else "s"} pending"
        unknown > 0 -> "$unknown environment${if (unknown == 1) "" else "s"} has unknown status"
        else -> "All environments synced"
    }
}

@Composable
private fun VariableEditorDialog(
    draft: VariableEditorDraft,
    editorValue: String,
    environments: List<EnvironmentChoice>,
    saving: Boolean,
    error: String?,
    conflict: VariableEditConflict?,
    onDraftChange: (VariableEditorDraft) -> Unit,
    onEditorValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onReloadConflict: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (draft.id == null) "New Variable" else "Edit Variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.key,
                    onValueChange = { onDraftChange(draft.copy(key = it)) },
                    label = { Text("Key") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Secret", Modifier.weight(1f))
                    Switch(
                        checked = draft.isSecret,
                        onCheckedChange = { onDraftChange(draft.copy(isSecret = it)) },
                        enabled = !saving,
                    )
                }
                if (draft.isSecret) {
                    SensitiveOutlinedField(
                        value = editorValue,
                        onValueChange = onEditorValueChange,
                        label = if (draft.id == null) "Value" else "Replacement value (optional)",
                        enabled = !saving,
                    )
                    Text(
                        if (draft.originallySecret) {
                            "The current secret cannot be revealed. Leave blank to keep it unchanged."
                        } else {
                            "Secret values are write-only and are never displayed or copied by the app."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    OutlinedTextField(
                        value = editorValue,
                        onValueChange = onEditorValueChange,
                        label = { Text("Value") },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Scope", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !saving) {
                        onDraftChange(draft.copy(allEnvironments = true, environmentIds = emptySet()))
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(draft.allEnvironments, onClick = null, enabled = !saving)
                    Text("All environments")
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !saving) {
                        onDraftChange(draft.copy(allEnvironments = false))
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(!draft.allEnvironments, onClick = null, enabled = !saving)
                    Text("Selected environments")
                }
                if (!draft.allEnvironments) {
                    if (environments.isEmpty()) {
                        Text("No environments available. Load environments or choose All environments.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            items(environments, key = EnvironmentChoice::id) { environment ->
                                val checked = environment.id in draft.environmentIds
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !saving) {
                                        val selected = draft.environmentIds.toMutableSet()
                                        if (checked) selected.remove(environment.id) else selected.add(environment.id)
                                        onDraftChange(draft.copy(environmentIds = selected))
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked, onCheckedChange = null, enabled = !saving)
                                    Text(environment.name)
                                }
                            }
                        }
                    }
                }
                Text(
                    "Scoped variables are materialized only into the selected environments.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (conflict != null) {
                    Button(onClick = onReloadConflict, enabled = !saving) {
                        Text(if (conflict is VariableEditConflict.Changed) "Reload server version" else "Close and refresh")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !saving && conflict == null) {
                if (saving) CircularProgressIndicator(Modifier.padding(4.dp)) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
