package app.getarcane.android.ui.screens.settings.rbac

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.screens.settings.FormSectionFooter
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.role.CreateRole
import app.getarcane.sdk.models.role.PermissionsManifest
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.role.UpdateRole
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class RoleDetailMode { Create, Edit, ReadOnly }

/**
 * Create / edit / view a role with the searchable permission picker. Port of iOS `RoleDetailView`.
 * Pass [roleId] = null for create mode; built-in roles open in [RoleDetailMode.ReadOnly].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleDetailScreen(
    roleId: String?,
    mode: RoleDetailMode,
    onClose: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isReadOnly = mode == RoleDetailMode.ReadOnly

    // Loads the manifest (always) and the role (when editing/viewing).
    var loadState by remember { mutableStateOf<Loadable<Pair<Role?, PermissionsManifest>>>(Loadable.Loading) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val expanded = remember { mutableStateListOf<String>() }
    var search by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadedRole by remember { mutableStateOf<Role?>(null) }

    LaunchedEffect(roleId) {
        if (client == null) return@LaunchedEffect
        loadState = try {
            coroutineScope {
                val manifest = async { client.roles.availablePermissions() }
                val role = if (roleId != null) async { client.roles.get(roleId) } else null
                val r = role?.await()
                if (r != null) {
                    name = r.name
                    description = r.description ?: ""
                    selected.clear(); selected.addAll(r.permissions)
                    loadedRole = r
                }
                Loadable.Success(r to manifest.await())
            }
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    val hasChanges: Boolean = run {
        val r = loadedRole
        if (r == null) name.isNotEmpty() && selected.isNotEmpty()
        else name != r.name || description != (r.description ?: "") || selected.toSet() != r.permissions.toSet()
    }

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null
            val perms = selected.toList()
            val desc = description.ifEmpty { null }
            try {
                if (loadedRole != null) {
                    c.roles.update(loadedRole!!.id, UpdateRole(name = name, description = desc, permissions = perms))
                } else {
                    c.roles.create(CreateRole(name = name, description = desc, permissions = perms))
                }
                onClose()
            } catch (e: ArcaneError.Validation) {
                error = formatValidationFields(e.fields)
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == RoleDetailMode.Create) "New Role" else (loadedRole?.displayNameOrId ?: "Role")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            if (mode == RoleDetailMode.Create) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (mode == RoleDetailMode.Create) "Cancel" else "Back",
                        )
                    }
                },
                actions = {
                    if (!isReadOnly) {
                        TextButton(
                            onClick = { save() },
                            enabled = !saving && hasChanges && name.isNotEmpty() && selected.isNotEmpty(),
                        ) { Text(if (mode == RoleDetailMode.Create) "Create" else "Save") }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = loadState) {
            is Loadable.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is Loadable.Error -> FormErrorRow(s.message, Modifier.padding(padding))
            is Loadable.Success -> {
                val manifest = s.value.second
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search permissions") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item(key = "role-info") { FormSectionHeader("Role Info") }
                    if (isReadOnly) {
                        item(key = "ro-name") { ReadOnlyField("Name", name) }
                        if (description.isNotEmpty()) item(key = "ro-desc") { ReadOnlyField("Description", description) }
                    } else {
                        item(key = "name") { LabeledTextField("Name", name, { name = it }) }
                        item(key = "desc") { LabeledTextField("Description (optional)", description, { description = it }, singleLine = false, minLines = 2) }
                    }

                    permissionPicker(
                        manifest = manifest,
                        selected = selected.toSet(),
                        onToggle = { perm, isOn -> if (isOn) { if (perm !in selected) selected.add(perm) } else selected.remove(perm) },
                        onToggleResource = { actions, selectAll ->
                            if (selectAll) actions.forEach { if (it.permission !in selected) selected.add(it.permission) }
                            else actions.forEach { selected.remove(it.permission) }
                        },
                        expanded = expanded.toSet(),
                        onExpandChange = { key, exp -> if (exp) { if (key !in expanded) expanded.add(key) } else expanded.remove(key) },
                        isReadOnly = isReadOnly,
                        search = search,
                    )

                    item(key = "footer") {
                        FormSectionFooter(if (isReadOnly) "Built-in roles cannot be edited." else "Select which actions this role can perform.")
                    }
                    error?.let { msg -> item(key = "error") { FormErrorRow(msg) } }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
