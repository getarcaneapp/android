@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings.registries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import app.getarcane.android.ui.screens.settings.CircleIcon
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.InfoAlert
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.screens.projects.CreateProjectScreen
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.template.CreateTemplateRegistry
import app.getarcane.sdk.models.template.Template
import app.getarcane.sdk.models.template.TemplateContent
import app.getarcane.sdk.models.template.TemplateRegistry
import app.getarcane.sdk.models.template.UpdateTemplateRegistry
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

/** Template registries list (admin-only) with create/edit/delete + template browser. Port of iOS `TemplateRegistriesView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateRegistriesScreen(onBack: (() -> Unit)? = null) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isAdmin = manager.currentUser?.isAdmin ?: false

    var state by remember { mutableStateOf<Loadable<List<TemplateRegistry>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TemplateRegistry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, isAdmin) {
        if (!isAdmin || client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.templates.listRegistries())
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(
        title = "Template Registries",
        onAdd = if (isAdmin) ({ showCreate = true }) else null,
        addContentDescription = "Add template registry",
        onBack = onBack,
        actions = {
            if (isAdmin) {
                IconButton(onClick = { showBrowser = true }) {
                    Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = "Browse templates")
                }
            }
        },
    ) { padding ->
        when {
            !isAdmin -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable("Admin Required", Icons.Filled.Lock, "Only administrators can manage template registries.")
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
                            ContentUnavailable(
                                "No Template Registries",
                                Icons.Filled.Description,
                                "Add a template registry to make project templates available from mobile. You can also browse templates once a registry is configured.",
                                "Add Registry",
                            ) { showCreate = true }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(s.value, key = { it.id }) { registry ->
                                    TemplateRegistryRow(
                                        registry = registry,
                                        onEdit = { editing = registry },
                                        onDelete = {
                                            scope.launch {
                                                try {
                                                    client?.templates?.deleteRegistry(registry.id)
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
        TemplateRegistryFormDialog(registry = null, onDismiss = { showCreate = false }, onSaved = { showCreate = false; refreshKey++ })
    }
    editing?.let { reg ->
        TemplateRegistryFormDialog(registry = reg, onDismiss = { editing = null }, onSaved = { editing = null; refreshKey++ })
    }
    if (showBrowser) {
        TemplateBrowserDialog(onDismiss = { showBrowser = false })
    }

    actionError?.let { msg -> InfoAlert("Couldn't Delete Registry", msg, { actionError = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateRegistryRow(registry: TemplateRegistry, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            CircleIcon(Icons.Filled.Description, ArcaneIndigo, size = 36)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(registry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(registry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                registry.lastFetchError?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = ArcaneRed)
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

/** Create/edit a template registry. Port of iOS `TemplateRegistryFormView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateRegistryFormDialog(registry: TemplateRegistry?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isEditing = registry != null

    var name by remember { mutableStateOf(registry?.name ?: "") }
    var url by remember { mutableStateOf(registry?.url ?: "") }
    var description by remember { mutableStateOf(registry?.description ?: "") }
    var enabled by remember { mutableStateOf(registry?.enabled ?: true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val hasChanges: Boolean = if (registry == null) {
        name.isNotEmpty() || url.isNotEmpty()
    } else {
        name != registry.name || url != registry.url || description != registry.description || enabled != registry.enabled
    }

    fun save() {
        val c = client ?: return
        scope.launch {
            loading = true; error = null
            try {
                if (registry != null) {
                    c.templates.updateRegistry(registry.id, UpdateTemplateRegistry(name = name, url = url, description = description, enabled = enabled))
                } else {
                    c.templates.createRegistry(CreateTemplateRegistry(name = name, url = url, description = description, enabled = enabled))
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
                    title = { Text(if (isEditing) "Edit Template Registry" else "Add Template Registry") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(onClick = { save() }, enabled = name.isNotEmpty() && url.isNotEmpty() && !loading && hasChanges) {
                            Text(if (isEditing) "Save" else "Add")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormSectionHeader("Template Registry")
                LabeledTextField("Name", name, { name = it })
                LabeledTextField("URL", url, { url = it })
                LabeledTextField("Description", description, { description = it })
                LabeledToggle("Enabled", enabled, { enabled = it })
                error?.let { FormErrorRow(it) }
            }
        }
    }
}

/** Browse all templates grouped by registry, with a compose/.env preview. Port of iOS `TemplateBrowserView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateBrowserDialog(onDismiss: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<Template>>>(Loadable.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<Template?>(null) }

    LaunchedEffect(reloadKey) {
        if (client == null) return@LaunchedEffect
        state = Loadable.Loading
        state = try {
            Loadable.Success(client.templates.listAll())
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Templates") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
                )
            },
        ) { padding ->
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { ErrorBanner(s.message, onRetry = { reloadKey++ }) }
                is Loadable.Success -> {
                    if (s.value.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(padding)) { ContentUnavailable("No Templates", Icons.Filled.Description) }
                    } else {
                        val grouped = s.value
                            .groupBy { it.registry?.name ?: (if (it.isRemote) "Remote" else "Local") }
                            .toSortedMap()
                        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                            grouped.forEach { (registryName, templates) ->
                                item(key = "h-$registryName") {
                                    Text(
                                        registryName.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                                    )
                                }
                                items(templates.sortedBy { it.name.lowercase() }, key = { it.id }) { template ->
                                    TemplateRow(template) { preview = template }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { template ->
        TemplatePreviewDialog(
            template = template,
            onDismiss = { preview = null },
            onDeployed = { preview = null; onDismiss() },
        )
    }
}

@Composable
private fun TemplateRow(template: Template, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIcon(Icons.Filled.Description, ArcaneIndigo, size = 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Compose/.env preview of a template with a Deploy action. Mirrors iOS `TemplatePreviewView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePreviewDialog(template: Template, onDismiss: () -> Unit, onDeployed: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client

    var content by remember { mutableStateOf<TemplateContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var showDeploy by remember { mutableStateOf(false) }

    LaunchedEffect(template.id) {
        if (client == null) return@LaunchedEffect
        loading = true
        try {
            content = client.templates.getContent(template.id)
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(template.name) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
                    actions = {
                        TextButton(onClick = { showDeploy = true }, enabled = content != null) { Text("Deploy") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    listOf("compose.yml", ".env").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = tab == index,
                            onClick = { tab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    error != null -> FormErrorRow(error!!)
                    else -> {
                        val text = if (tab == 0) content?.content ?: "" else content?.envContent ?: ""
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDeploy) {
        val c = content
        Dialog(onDismissRequest = { showDeploy = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CreateProjectScreen(
                onSuccess = { showDeploy = false; onDeployed() },
                onCancel = { showDeploy = false },
                prefilledName = template.name.lowercase().replace(" ", "-"),
                prefilledCompose = c?.content ?: "",
                prefilledEnv = c?.envContent ?: "",
                templateLabel = template.name,
            )
        }
    }
}
