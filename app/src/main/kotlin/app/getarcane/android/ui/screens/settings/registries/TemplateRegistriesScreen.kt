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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.getarcane.android.core.COMPLETE_LIST_LIMIT
import app.getarcane.android.core.loadCompletePaginatedCollection
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.screens.settings.CircleIcon
import app.getarcane.android.ui.screens.settings.ConfirmDialog
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.InfoAlert
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.screens.projects.CreateProjectScreen
import app.getarcane.android.ui.screens.projects.ProjectAction
import app.getarcane.android.ui.screens.projects.StreamingActionScreen
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.template.CreateTemplateRegistry
import app.getarcane.sdk.models.template.Template
import app.getarcane.sdk.models.template.TemplateContent
import app.getarcane.sdk.models.template.TemplateRegistry
import app.getarcane.sdk.models.template.UpdateTemplateRegistry
import app.getarcane.sdk.models.base.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Template registries and permission-aware template browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateRegistriesScreen(onBack: (() -> Unit)? = null) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val permissions = templatePermissionPolicy(manager.currentUser, manager.activeEnvironmentId.rawValue)

    var state by remember { mutableStateOf<Loadable<List<TemplateRegistry>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TemplateRegistry?>(null) }
    var pendingDelete by remember { mutableStateOf<TemplateRegistry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, permissions.canList) {
        if (!permissions.canList || client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.templates.listRegistries())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(
        title = "Template Registries",
        onAdd = if (permissions.canCreateRegistry) ({ showCreate = true }) else null,
        addContentDescription = "Add template registry",
        onBack = onBack,
        actions = {
            if (permissions.canList) {
                IconButton(onClick = { showBrowser = true }) {
                    Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = "Browse templates")
                }
            }
        },
    ) { padding ->
        when {
            !permissions.canList -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentUnavailable("Templates Access Required", Icons.Filled.Lock, "Your role cannot list templates or registries.")
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
                                actionLabel = if (permissions.canCreateRegistry) "Add Registry" else null,
                                onAction = if (permissions.canCreateRegistry) ({ showCreate = true }) else null,
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(s.value, key = { it.id }) { registry ->
                                    TemplateRegistryRow(
                                        registry = registry,
                                        canEdit = permissions.canUpdateRegistry,
                                        canDelete = permissions.canDeleteRegistry,
                                        onEdit = { editing = registry },
                                        onDelete = { pendingDelete = registry },
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
        TemplateBrowserDialog(permissions = permissions, onDismiss = { showBrowser = false })
    }

    pendingDelete?.let { registry ->
        ConfirmDialog(
            title = "Delete Template Registry",
            message = "Delete ${registry.name}? This removes the configured template source.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    try {
                        client?.templates?.deleteRegistry(registry.id)
                        refreshKey++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        actionError = friendlyErrorMessage(e)
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    actionError?.let { msg -> InfoAlert("Couldn't Delete Registry", msg, { actionError = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateRegistryRow(
    registry: TemplateRegistry,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (canEdit) onEdit() },
                    onLongClick = { if (canEdit || canDelete) menu = true },
                )
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
            if (canEdit) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
            }
            if (canDelete) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
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
            } catch (e: CancellationException) {
                throw e
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

/** Complete searchable template browser with source filtering and stable selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateBrowserDialog(
    permissions: TemplatePermissionPolicy,
    onDismiss: () -> Unit,
) {
    val client = LocalArcaneManager.current.client
    var state by remember { mutableStateOf<Loadable<List<Template>>>(Loadable.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(TemplateSourceSelection.ALL) }
    var selectedIdentity by remember { mutableStateOf<TemplateIdentity?>(null) }

    LaunchedEffect(reloadKey, query, source, client) {
        if (client == null || !permissions.canList) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        state = Loadable.Loading
        state = try {
            val loaded = loadCompletePaginatedCollection("Template", Template::identity) {
                client.templates.listPaginated(
                    search = query.trim().ifEmpty { null },
                    sort = "name",
                    order = SortOrder.ASCENDING,
                    limit = COMPLETE_LIST_LIMIT,
                    source = source.sdkValue,
                )
            }
            Loadable.Success(filterTemplates(loaded, query, source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    val selectedTemplate = (state as? Loadable.Success<List<Template>>)?.value
        ?.firstOrNull { it.identity() == selectedIdentity }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Templates") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search templates") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TemplateSourceSelection.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = source == choice,
                            onClick = { source = choice },
                            shape = SegmentedButtonDefaults.itemShape(index, TemplateSourceSelection.entries.size),
                        ) { Text(choice.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
                when (val s = state) {
                    is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                        ErrorBanner(s.message, onRetry = { reloadKey++ })
                    }
                    is Loadable.Success -> {
                        if (s.value.isEmpty()) {
                            ContentUnavailable("No Matching Templates", Icons.Filled.Description)
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                groupTemplates(s.value).forEach { group ->
                                    item(key = "heading:${group.key}") {
                                        Text(
                                            group.title.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                                        )
                                    }
                                    items(group.templates, key = { it.identity().stableKey }) { template ->
                                        TemplateRow(template, permissions.canRead) { selectedIdentity = template.identity() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTemplate?.let { template ->
        TemplatePreviewDialog(
            template = template,
            canImport = permissions.canImport,
            canDeploy = permissions.canDeploy,
            onImported = { imported ->
                val current = (state as? Loadable.Success<List<Template>>)?.value.orEmpty()
                state = Loadable.Success((current.filterNot { it.identity() == imported.identity() } + imported))
                selectedIdentity = imported.identity()
            },
            onDismiss = { selectedIdentity = null },
            onDeployed = { selectedIdentity = null; onDismiss() },
        )
    }
}

@Composable
private fun TemplateRow(template: Template, canRead: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canRead, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIcon(Icons.Filled.Description, ArcaneIndigo, size = 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            val metadata = listOfNotNull(
                if (template.isRemote) "Remote" else "Local",
                template.metadata?.author?.takeIf(String::isNotBlank),
                template.metadata?.version?.takeIf(String::isNotBlank),
                template.metadata?.tags?.takeIf(List<String>::isNotEmpty)?.joinToString(" · "),
            )
            Text(metadata.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Retryable preview with explicit remote import and exact-content deployment handoff. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePreviewDialog(
    template: Template,
    canImport: Boolean,
    canDeploy: Boolean,
    onImported: (Template) -> Unit,
    onDismiss: () -> Unit,
    onDeployed: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var content by remember { mutableStateOf<TemplateContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadKey by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var showDeploy by remember { mutableStateOf(false) }
    var deploymentTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var displayedTemplate by remember(template.identity()) { mutableStateOf(template) }
    var importState by remember(template.identity()) { mutableStateOf<TemplateImportState>(TemplateImportState.Idle) }

    LaunchedEffect(displayedTemplate.identity(), loadKey) {
        if (client == null) return@LaunchedEffect
        loading = true; error = null
        try {
            content = client.templates.getContent(displayedTemplate.id)
        } catch (e: CancellationException) {
            throw e
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
                    title = { Text(displayedTemplate.name) },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
                    actions = {
                        if (displayedTemplate.isRemote && canImport) {
                            TextButton(
                                onClick = {
                                    val c = client ?: return@TextButton
                                    importState = beginTemplateImport(displayedTemplate)
                                    scope.launch {
                                        try {
                                            val imported = c.templates.download(displayedTemplate.id)
                                            val importedContent = c.templates.getContent(imported.id)
                                            displayedTemplate = importedContent.template
                                            content = importedContent
                                            importState = completeTemplateImport(displayedTemplate)
                                            onImported(displayedTemplate)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Throwable) {
                                            importState = failTemplateImport(importState, friendlyErrorMessage(e))
                                        }
                                    }
                                },
                                enabled = importState !is TemplateImportState.Importing,
                            ) {
                                Icon(Icons.Filled.CloudDownload, null)
                                Text("Import")
                            }
                        }
                        TextButton(onClick = { showDeploy = true }, enabled = canDeploy && content != null && !loading) { Text("Deploy") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TemplateMetadataSummary(displayedTemplate, content)
                (importState as? TemplateImportState.Failed)?.let { failed ->
                    ErrorBanner(failed.message)
                }
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
                    error != null -> ErrorBanner(error!!, onRetry = { loadKey++ })
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
                onSuccess = { projectId, projectName ->
                    showDeploy = false
                    deploymentTarget = projectId to projectName
                },
                onCancel = { showDeploy = false },
                prefilledName = displayedTemplate.name.trim().lowercase().replace(Regex("\\s+"), "-"),
                prefilledCompose = c?.content ?: "",
                prefilledEnv = c?.envContent ?: "",
                templateLabel = displayedTemplate.name,
                submitLabel = "Create & Deploy",
            )
        }
    }

    deploymentTarget?.let { (projectId, projectName) ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StreamingActionScreen(
                projectId = projectId,
                action = ProjectAction.UP,
                title = "Deploy $projectName",
                onDone = {
                    deploymentTarget = null
                    onDeployed()
                },
            )
        }
    }
}

@Composable
private fun TemplateMetadataSummary(template: Template, content: TemplateContent?) {
    val metadata = template.metadata
    val lines = listOfNotNull(
        template.description.takeIf(String::isNotBlank),
        metadata?.author?.takeIf(String::isNotBlank)?.let { "Author: $it" },
        metadata?.version?.takeIf(String::isNotBlank)?.let { "Version: $it" },
        metadata?.tags?.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = "Tags: "),
        metadata?.documentationUrl?.takeIf(String::isNotBlank)?.let { "Docs: $it" },
        content?.services?.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = "Services: "),
        content?.envVariables?.map { it.key }?.takeIf(List<String>::isNotEmpty)?.joinToString(prefix = "Variables: "),
    )
    if (lines.isNotEmpty()) {
        Text(
            lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
