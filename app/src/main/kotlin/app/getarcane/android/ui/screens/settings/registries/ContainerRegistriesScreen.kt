@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings.registries

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import app.getarcane.android.ui.screens.settings.CircleIcon
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.InfoAlert
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.sdk.models.containerregistry.ContainerRegistry
import app.getarcane.sdk.models.containerregistry.CreateContainerRegistry
import app.getarcane.sdk.models.containerregistry.UpdateContainerRegistry
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

/** Container registries list (admin-only) with create/edit/delete. Port of iOS `ContainerRegistriesView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerRegistriesScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isAdmin = manager.currentUser?.isAdmin ?: false

    var state by remember { mutableStateOf<Loadable<List<ContainerRegistry>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ContainerRegistry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, isAdmin) {
        if (!isAdmin || client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.registries.listPaginated(limit = 100).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(
        title = "Container Registries",
        onAdd = if (isAdmin) ({ showCreate = true }) else null,
        addContentDescription = "Add registry",
    ) { padding ->
        when {
            !isAdmin -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable("Admin Required", Icons.Filled.Lock, "Only administrators can manage container registries.")
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
                        if (s.value.isEmpty()) {
                            ContentUnavailable("No Container Registries", Icons.Filled.Cloud)
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(s.value, key = { it.id }) { registry ->
                                    RegistryRow(
                                        registry = registry,
                                        onEdit = { editing = registry },
                                        onDelete = {
                                            scope.launch {
                                                try {
                                                    client?.registries?.delete(registry.id)
                                                    refreshKey++
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

    if (showCreate) {
        RegistryFormDialog(registry = null, onDismiss = { showCreate = false }, onSaved = { showCreate = false; refreshKey++ })
    }
    editing?.let { reg ->
        RegistryFormDialog(registry = reg, onDismiss = { editing = null }, onSaved = { editing = null; refreshKey++ })
    }

    actionError?.let { msg -> InfoAlert("Couldn't Delete Registry", msg, { actionError = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistryRow(registry: ContainerRegistry, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleIcon(Icons.Filled.Cloud, ArcanePurple, size = 36)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(registry.url, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                registry.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!registry.enabled) Pill("Disabled", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

private fun String?.nilIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

/** Create/edit a container registry (generic or AWS ECR). Port of iOS `RegistryFormView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistryFormDialog(registry: ContainerRegistry?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isEditing = registry != null

    var url by remember { mutableStateOf(registry?.url ?: "") }
    var username by remember { mutableStateOf(registry?.username ?: "") }
    var token by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(registry?.description ?: "") }
    var enabled by remember { mutableStateOf(registry?.enabled ?: true) }
    var insecure by remember { mutableStateOf(registry?.insecure ?: false) }
    // The picker is generic vs ecr; legacy "custom" maps to generic.
    var registryType by remember { mutableStateOf(registry?.registryType ?: "generic") }
    var awsAccessKeyId by remember { mutableStateOf(registry?.awsAccessKeyId ?: "") }
    var awsSecretAccessKey by remember { mutableStateOf("") }
    var awsRegion by remember { mutableStateOf(registry?.awsRegion ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isAws = registryType == "ecr"
    val pickerValue = if (registryType == "ecr") "ecr" else "generic"

    val hasChanges: Boolean = if (registry == null) {
        url.isNotEmpty()
    } else {
        val typeMatch = registryType == registry.registryType ||
            (pickerValue == "generic" && (registry.registryType == "generic" || registry.registryType == "custom"))
        url != registry.url ||
            username != registry.username ||
            description != (registry.description ?: "") ||
            enabled != registry.enabled ||
            insecure != registry.insecure ||
            !typeMatch ||
            awsAccessKeyId != (registry.awsAccessKeyId ?: "") ||
            awsRegion != (registry.awsRegion ?: "") ||
            token.isNotEmpty() ||
            awsSecretAccessKey.isNotEmpty()
    }

    fun save() {
        val c = client ?: return
        scope.launch {
            loading = true; error = null
            try {
                if (registry != null) {
                    c.registries.update(
                        registry.id,
                        UpdateContainerRegistry(
                            url = url,
                            username = username.nilIfEmpty(),
                            token = token.nilIfEmpty(),
                            description = description.nilIfEmpty(),
                            insecure = insecure,
                            enabled = enabled,
                            registryType = registryType.nilIfEmpty(),
                            awsAccessKeyId = awsAccessKeyId.nilIfEmpty(),
                            awsSecretAccessKey = awsSecretAccessKey.nilIfEmpty(),
                            awsRegion = awsRegion.nilIfEmpty(),
                        ),
                    )
                } else {
                    c.registries.create(
                        CreateContainerRegistry(
                            url = url,
                            username = username,
                            token = token,
                            description = description.nilIfEmpty(),
                            insecure = insecure,
                            enabled = enabled,
                            registryType = registryType.ifEmpty { "custom" },
                            awsAccessKeyId = awsAccessKeyId,
                            awsSecretAccessKey = awsSecretAccessKey,
                            awsRegion = awsRegion,
                        ),
                    )
                }
                onSaved()
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "Edit Registry" else "Add Registry") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(onClick = { save() }, enabled = url.isNotEmpty() && !loading && hasChanges) {
                            Text(if (isEditing) "Save" else "Add")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                FormSectionHeader("Registry Details")
                LabeledTextField("URL (e.g. registry.example.com)", url, { url = it })
                LabeledTextField("Description", description, { description = it })
                LabeledPicker(
                    label = "Type",
                    selected = pickerValue,
                    options = listOf("generic", "ecr"),
                    optionLabel = { if (it == "ecr") "AWS ECR" else "Generic" },
                    onSelect = { registryType = it },
                )
                LabeledToggle("Enabled", enabled, { enabled = it })
                LabeledToggle("Insecure", insecure, { insecure = it })

                if (!isAws) {
                    FormSectionHeader("Credentials (optional)")
                    LabeledTextField("Username", username, { username = it })
                    LabeledTextField(if (isEditing) "New token or password" else "Token or password", token, { token = it }, isPassword = true)
                } else {
                    FormSectionHeader("AWS ECR")
                    LabeledTextField("Access Key ID", awsAccessKeyId, { awsAccessKeyId = it })
                    LabeledTextField(if (isEditing) "New Secret Access Key" else "Secret Access Key", awsSecretAccessKey, { awsSecretAccessKey = it }, isPassword = true)
                    LabeledTextField("Region (e.g. us-east-1)", awsRegion, { awsRegion = it })
                }

                error?.let { FormErrorRow(it) }
            }
        }
    }
}
