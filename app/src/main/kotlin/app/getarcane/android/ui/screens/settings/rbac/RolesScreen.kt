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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.screens.settings.CircleIcon
import app.getarcane.android.ui.screens.settings.InfoAlert
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.role.PermissionsManifest
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.user.hasAnyPermission
import app.getarcane.sdk.models.role.Permission
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class RolesData(val roles: List<Role>, val manifest: PermissionsManifest)

/** Roles list (built-in + custom). v2-only. Port of iOS `RolesView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesScreen(onOpenRole: (roleId: String) -> Unit, onCreateRole: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val rbacAvailable = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    val canManageRoles = manager.currentUser?.hasAnyPermission(
        listOf(Permission.Roles.LIST, Permission.Roles.READ),
    ) ?: false

    var state by remember { mutableStateOf<Loadable<RolesData>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, rbacAvailable) {
        if (!rbacAvailable || client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            coroutineScope {
                val rolesPage = async { client.roles.listPaginated(limit = 100) }
                val manifest = async { client.roles.availablePermissions() }
                Loadable.Success(RolesData(rolesPage.await().data, manifest.await()))
            }
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(
        title = "Roles",
        onAdd = if (rbacAvailable && canManageRoles) onCreateRole else null,
        addContentDescription = "Add role",
    ) { padding ->
        when {
            !rbacAvailable -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable(
                    "Roles Not Available",
                    Icons.Filled.LockOpen,
                    "Role-based access control requires Arcane v2 or newer.",
                )
            }
            !canManageRoles -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable(
                    "Admin Required",
                    Icons.Filled.Warning,
                    "You don't have permission to view roles.",
                )
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
                        val builtIns = s.value.roles.filter { it.builtIn }
                        val customs = s.value.roles.filter { !it.builtIn }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (builtIns.isNotEmpty()) {
                                stickyHeaderText("Built-in")
                                items(builtIns, key = { it.id }) { role ->
                                    RoleRow(role = role, onClick = { onOpenRole(role.id) }, onDelete = null)
                                }
                            }
                            if (customs.isNotEmpty()) {
                                stickyHeaderText("Custom")
                                items(customs, key = { it.id }) { role ->
                                    RoleRow(
                                        role = role,
                                        onClick = { onOpenRole(role.id) },
                                        onDelete = {
                                            scope.launch {
                                                try {
                                                    client?.roles?.delete(role.id)
                                                    refreshKey++
                                                } catch (e: ArcaneError.Conflict) {
                                                    actionError = e.detail ?: "This role can't be deleted because it would leave the system with no administrators."
                                                } catch (e: Throwable) {
                                                    actionError = friendlyErrorMessage(e)
                                                }
                                            }
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

    actionError?.let { msg ->
        InfoAlert(title = "Couldn't Delete Role", message = msg, onDismiss = { actionError = null })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.stickyHeaderText(text: String) {
    item(key = "header-$text") {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleRow(role: Role, onClick: () -> Unit, onDelete: (() -> Unit)?) {
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
            CircleIcon(role.iconVector, role.iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(role.displayNameOrId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                role.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${role.permissions.size} permissions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    if (role.assignedUserCount > 0) {
                        Text("· ${role.assignedUserCount} assigned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
            if (role.builtIn) Pill("Built-in", ArcaneGray)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onDelete != null) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}
