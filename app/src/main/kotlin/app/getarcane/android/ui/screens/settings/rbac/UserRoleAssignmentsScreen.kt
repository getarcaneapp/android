@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings.rbac

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermIdentity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.InfoAlert
import app.getarcane.android.ui.screens.settings.LabeledPicker
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.role.RoleAssignment
import app.getarcane.sdk.models.role.RoleAssignmentSource
import app.getarcane.sdk.models.role.UserAssignmentInput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class AssignmentsData(
    val assignments: List<RoleAssignment>,
    val roles: List<Role>,
    val environments: List<Environment>,
)

/** Per-user role assignments grouped by scope, with add/remove. v2-only. Port of iOS `UserRoleAssignmentsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoleAssignmentsScreen(userId: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<AssignmentsData>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            coroutineScope {
                val a = async { client.users.getRoleAssignments(userId) }
                val r = async { client.roles.listPaginated(limit = 100) }
                val e = async { client.environments.list(SearchPaginationSort(start = 0, limit = 100)) }
                Loadable.Success(AssignmentsData(a.await(), r.await().data, e.await().data))
            }
        } catch (ex: Throwable) {
            Loadable.Error(friendlyErrorMessage(ex))
        }
        refreshing = false
    }

    val data = (state as? Loadable.Success)?.value

    fun remove(assignment: RoleAssignment) {
        val c = client ?: return
        val current = data?.assignments ?: return
        val remaining = current
            .filter { it.sourceKind == RoleAssignmentSource.MANUAL && it.id != assignment.id }
            .map { UserAssignmentInput(roleId = it.roleId, environmentId = it.environmentId) }
        scope.launch {
            try {
                c.users.setRoleAssignments(userId, remaining)
                refreshKey++
            } catch (e: ArcaneError.Conflict) {
                actionError = e.detail ?: "Cannot remove this assignment — at least one global administrator must remain."
            } catch (e: Throwable) {
                actionError = friendlyErrorMessage(e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Role Assignments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showAdd = true },
                        enabled = data?.roles?.isNotEmpty() == true,
                    ) { Text("Add") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    if (s.value.assignments.isEmpty()) {
                        ContentUnavailable(
                            "No Role Assignments",
                            Icons.Filled.HelpOutline,
                            "This user has no roles assigned and cannot perform actions.",
                        )
                    } else {
                        AssignmentsList(s.value, onRemove = ::remove)
                    }
                }
            }
        }
    }

    if (showAdd && data != null) {
        AddRoleAssignmentDialog(
            userId = userId,
            roles = data.roles,
            environments = data.environments,
            existing = data.assignments,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; refreshKey++ },
        )
    }

    actionError?.let { msg -> InfoAlert("Couldn't Update Assignments", msg, { actionError = null }) }
}

@Composable
private fun AssignmentsList(data: AssignmentsData, onRemove: (RoleAssignment) -> Unit) {
    val global = data.assignments.filter { it.environmentId == null }
    val perEnv = data.assignments.filter { it.environmentId != null }.groupBy { it.environmentId ?: "" }
    LazyColumn(Modifier.fillMaxSize()) {
        if (global.isNotEmpty()) {
            headerItem("Global")
            assignmentItems(global, data.roles, onRemove)
        }
        perEnv.keys.sorted().forEach { envId ->
            headerItem(displayScopeLabel(envId, data.environments))
            assignmentItems(perEnv[envId] ?: emptyList(), data.roles, onRemove)
        }
    }
}

private fun LazyListScope.headerItem(text: String) {
    item(key = "h-$text") {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.assignmentItems(
    assignments: List<RoleAssignment>,
    roles: List<Role>,
    onRemove: (RoleAssignment) -> Unit,
) {
    items(assignments, key = { it.id }) { assignment ->
        AssignmentRow(
            assignment = assignment,
            role = roles.firstOrNull { it.id == assignment.roleId },
            onRemove = if (assignment.sourceKind == RoleAssignmentSource.MANUAL) ({ onRemove(assignment) }) else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentRow(assignment: RoleAssignment, role: Role?, onRemove: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { if (onRemove != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                role?.iconVector ?: Icons.Filled.PermIdentity,
                contentDescription = null,
                tint = role?.iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(role?.displayNameOrId ?: assignment.roleId, style = MaterialTheme.typography.bodyLarge)
                if (assignment.sourceKind == RoleAssignmentSource.OIDC) {
                    Text("From SSO", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (assignment.sourceKind == RoleAssignmentSource.OIDC) {
                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}

private enum class Scope(val label: String) { Global("Global"), SpecificEnvironment("Specific Environment") }

/** Add a role assignment. Port of iOS `AddRoleAssignmentSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoleAssignmentDialog(
    userId: String,
    roles: List<Role>,
    environments: List<Environment>,
    existing: List<RoleAssignment>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var roleId by remember { mutableStateOf("") }
    var assignScope by remember { mutableStateOf(Scope.Global) }
    var environmentId by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = roleId.isNotEmpty() && (assignScope == Scope.Global || environmentId.isNotEmpty()) && !saving

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null
            val envId = if (assignScope == Scope.Global) null else environmentId
            val inputs = existing
                .filter { it.sourceKind == RoleAssignmentSource.MANUAL }
                .map { UserAssignmentInput(roleId = it.roleId, environmentId = it.environmentId) }
                .toMutableList()
            val newInput = UserAssignmentInput(roleId = roleId, environmentId = envId)
            if (inputs.none { it.roleId == newInput.roleId && it.environmentId == newInput.environmentId }) {
                inputs.add(newInput)
            }
            try {
                c.users.setRoleAssignments(userId, inputs)
                onSaved()
            } catch (e: ArcaneError.Validation) {
                error = formatValidationFields(e.fields)
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Assignment") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = { TextButton(onClick = { save() }, enabled = canSave) { Text("Add") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormSectionHeader("Role")
                LabeledPicker(
                    label = "Role",
                    selected = roleId,
                    options = listOf("") + roles.map { it.id },
                    optionLabel = { id -> if (id.isEmpty()) "Choose…" else roles.firstOrNull { it.id == id }?.displayNameOrId ?: id },
                    onSelect = { roleId = it },
                )
                FormSectionHeader("Scope")
                LabeledPicker(
                    label = "Scope",
                    selected = assignScope,
                    options = Scope.entries,
                    optionLabel = { it.label },
                    onSelect = { assignScope = it },
                )
                if (assignScope == Scope.SpecificEnvironment) {
                    LabeledPicker(
                        label = "Environment",
                        selected = environmentId,
                        options = listOf("") + environments.map { it.id },
                        optionLabel = { id -> if (id.isEmpty()) "Choose…" else environments.firstOrNull { it.id == id }?.name ?: "Environment $id" },
                        onSelect = { environmentId = it },
                    )
                }
                error?.let { FormErrorRow(it) }
            }
        }
    }
}
