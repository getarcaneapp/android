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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.font.FontFamily
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
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.role.CreateOidcRoleMapping
import app.getarcane.sdk.models.role.OidcRoleMapping
import app.getarcane.sdk.models.role.OidcRoleMappingSource
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.role.UpdateOidcRoleMapping
import app.getarcane.sdk.models.user.hasAnyPermission
import app.getarcane.sdk.models.role.Permission
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class OidcData(
    val mappings: List<OidcRoleMapping>,
    val roles: List<Role>,
    val environments: List<Environment>,
)

/** OIDC claim → role mappings. v2-only, global-admin only. Port of iOS `OIDCRoleMappingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OidcRoleMappingsScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val rbacAvailable = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    // Managing OIDC mappings is a global-admin action (sudo).
    val canManage = manager.currentUser?.hasAnyPermission(listOf(Permission.SUDO)) ?: false

    var state by remember { mutableStateOf<Loadable<OidcData>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OidcRoleMapping?>(null) }

    LaunchedEffect(refreshKey, rbacAvailable) {
        if (!rbacAvailable || client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            coroutineScope {
                val mappings = async { client.oidcRoleMappings.list() }
                val roles = async { client.roles.listPaginated(limit = 100) }
                val envs = async { client.environments.list(SearchPaginationSort(start = 0, limit = 100)) }
                Loadable.Success(OidcData(mappings.await(), roles.await().data, envs.await().data))
            }
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    val data = (state as? Loadable.Success)?.value

    SettingsListScaffold(
        title = "OIDC Role Mappings",
        onAdd = if (rbacAvailable && canManage) ({ showCreate = true }) else null,
        addContentDescription = "Add mapping",
    ) { padding ->
        when {
            !rbacAvailable -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable("OIDC Role Mappings Not Available", Icons.Filled.LockOpen, "Requires Arcane v2 or newer.")
            }
            !canManage -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable("Admin Required", Icons.Filled.Warning, "Only global administrators can manage OIDC role mappings.")
            }
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshKey++ },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                when (val s = state) {
                    is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                    is Loadable.Success -> {
                        if (s.value.mappings.isEmpty()) {
                            ContentUnavailable("No Mappings", Icons.Filled.Key, "Map an SSO claim value to a role to grant it on login.")
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(s.value.mappings, key = { it.id }) { mapping ->
                                    MappingRow(
                                        mapping = mapping,
                                        role = s.value.roles.firstOrNull { it.id == mapping.roleId },
                                        environmentLabel = displayScopeLabel(mapping.environmentId, s.value.environments),
                                        onClick = { if (mapping.sourceKind == OidcRoleMappingSource.MANUAL) editing = mapping },
                                        onDelete = if (mapping.sourceKind == OidcRoleMappingSource.MANUAL) {
                                            {
                                                scope.launch {
                                                    try {
                                                        client?.oidcRoleMappings?.delete(mapping.id)
                                                        refreshKey++
                                                    } catch (e: Throwable) {
                                                        actionError = friendlyErrorMessage(e)
                                                    }
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate && data != null) {
        OidcMappingFormDialog(
            editing = null,
            roles = data.roles,
            environments = data.environments,
            onDismiss = { showCreate = false },
            onSaved = { showCreate = false; refreshKey++ },
        )
    }
    editing?.let { m ->
        if (data != null) {
            OidcMappingFormDialog(
                editing = m,
                roles = data.roles,
                environments = data.environments,
                onDismiss = { editing = null },
                onSaved = { editing = null; refreshKey++ },
            )
        }
    }

    actionError?.let { msg -> InfoAlertWrapper(msg) { actionError = null } }
}

@Composable
private fun InfoAlertWrapper(message: String, onDismiss: () -> Unit) {
    app.getarcane.android.ui.screens.settings.InfoAlert(
        title = "Couldn't Delete Mapping",
        message = message,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingRow(
    mapping: OidcRoleMapping,
    role: Role?,
    environmentLabel: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (onDelete != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                role?.iconVector ?: Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = role?.iconTint ?: ArcaneIndigo,
                modifier = Modifier.width(32.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mapping.claimValue, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(role?.displayNameOrId ?: mapping.roleId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(environmentLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mapping.sourceKind == OidcRoleMappingSource.ENV) {
                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onDelete != null) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}

private enum class MappingScope(val label: String) { Global("Global"), SpecificEnvironment("Specific Environment") }

/** Create/edit an OIDC mapping. Port of iOS `OIDCMappingFormSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OidcMappingFormDialog(
    editing: OidcRoleMapping?,
    roles: List<Role>,
    environments: List<Environment>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var claimValue by remember { mutableStateOf(editing?.claimValue ?: "") }
    var roleId by remember { mutableStateOf(editing?.roleId ?: "") }
    var mappingScope by remember { mutableStateOf(if (editing?.environmentId == null) MappingScope.Global else MappingScope.SpecificEnvironment) }
    var environmentId by remember { mutableStateOf(editing?.environmentId ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = claimValue.isNotEmpty() && roleId.isNotEmpty() &&
        (mappingScope == MappingScope.Global || environmentId.isNotEmpty()) && !saving

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null
            val envId = if (mappingScope == MappingScope.Global) null else environmentId
            try {
                if (editing != null) {
                    c.oidcRoleMappings.update(editing.id, UpdateOidcRoleMapping(claimValue = claimValue, roleId = roleId, environmentId = envId))
                } else {
                    c.oidcRoleMappings.create(CreateOidcRoleMapping(claimValue = claimValue, roleId = roleId, environmentId = envId))
                }
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
                    title = { Text(if (editing == null) "New Mapping" else "Edit Mapping") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = { TextButton(onClick = { save() }, enabled = canSave) { Text("Save") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormSectionHeader("Claim Value")
                LabeledTextField("e.g. docker-admins", claimValue, { claimValue = it })

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
                    selected = mappingScope,
                    options = MappingScope.entries,
                    optionLabel = { it.label },
                    onSelect = { mappingScope = it },
                )
                if (mappingScope == MappingScope.SpecificEnvironment) {
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
