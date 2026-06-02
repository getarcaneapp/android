@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings.webhooks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.Pill
import app.getarcane.android.ui.screens.settings.SettingsListScaffold
import app.getarcane.android.ui.screens.settings.CircleIcon
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.webhook.CreateWebhook
import app.getarcane.sdk.models.webhook.UpdateWebhook
import app.getarcane.sdk.models.webhook.Webhook
import app.getarcane.sdk.models.webhook.WebhookCreated
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Webhooks list with create + token reveal + enable/disable + delete. Port of iOS `WebhooksView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<Webhook>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var createdWebhook by remember { mutableStateOf<WebhookCreated?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, envId.rawValue) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.webhooks.list(envId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(title = "Webhooks", onAdd = { showCreate = true }, addContentDescription = "Add webhook") { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    if (s.value.isEmpty()) {
                        ContentUnavailable("No Webhooks", Icons.Filled.Link, "Create a webhook to trigger actions via HTTP.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.value, key = { it.id }) { webhook ->
                                WebhookRow(
                                    webhook = webhook,
                                    onToggle = {
                                        scope.launch {
                                            try {
                                                client?.webhooks?.update(webhook.id, UpdateWebhook(enabled = !webhook.enabled), envId)
                                                refreshKey++
                                            } catch (e: Throwable) {
                                                error = friendlyErrorMessage(e)
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            try {
                                                client?.webhooks?.delete(webhook.id, envId)
                                                refreshKey++
                                            } catch (e: Throwable) {
                                                error = friendlyErrorMessage(e)
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

    if (showCreate) {
        CreateWebhookDialog(
            onDismiss = { showCreate = false },
            onCreated = { created -> showCreate = false; createdWebhook = created; refreshKey++ },
        )
    }

    createdWebhook?.let { created ->
        NewWebhookTokenDialog(name = created.name, token = created.token, onDismiss = { createdWebhook = null })
    }

    error?.let { msg -> app.getarcane.android.ui.screens.settings.InfoAlert("Error", msg, { error = null }) }
}

private fun webhookIcon(targetType: String): ImageVector = when (targetType) {
    "container" -> Icons.Filled.Inventory2
    "project" -> Icons.Filled.Folder
    "updater" -> Icons.Filled.Sync
    "gitops" -> Icons.Filled.Sync
    else -> Icons.Filled.Link
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookRow(webhook: Webhook, onToggle: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(webhook.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Pill(if (webhook.enabled) "Enabled" else "Disabled", if (webhook.enabled) ArcaneGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(webhookIcon(webhook.targetType), null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(webhook.targetType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ArrowCircleRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(webhook.actionType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            webhook.targetName?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text("${webhook.tokenPrefix}…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (webhook.enabled) "Disable" else "Enable") },
                onClick = { menu = false; onToggle() },
                leadingIcon = { Icon(if (webhook.enabled) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle, null) },
            )
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

private enum class TargetType(val wire: String, val label: String) {
    Container("container", "Container"),
    Project("project", "Project"),
    Updater("updater", "Updater"),
    GitOps("gitops", "GitOps"),
}

private fun actionsFor(target: TargetType): List<String> = when (target) {
    TargetType.Container -> listOf("update", "start", "stop", "restart", "redeploy")
    TargetType.Project -> listOf("update", "up", "down", "restart", "redeploy")
    TargetType.Updater -> listOf("run")
    TargetType.GitOps -> listOf("sync")
}

/** Create-webhook dialog with target pickers. Port of iOS `CreateWebhookView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateWebhookDialog(onDismiss: () -> Unit, onCreated: (WebhookCreated) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf(TargetType.Container) }
    var actionType by remember { mutableStateOf("update") }
    var targetId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var containers by remember { mutableStateOf<List<ContainerSummary>>(emptyList()) }
    var projects by remember { mutableStateOf<List<ProjectDetails>>(emptyList()) }
    var loadingTargets by remember { mutableStateOf(false) }

    val needsTargetId = targetType != TargetType.Updater

    LaunchedEffect(Unit) {
        if (client == null) return@LaunchedEffect
        loadingTargets = true
        coroutineScope {
            val c = async { runCatching { client.containers.list(envId = envId, query = SearchPaginationSort(start = 0, limit = 500)).data }.getOrDefault(emptyList()) }
            val p = async { runCatching { client.projects.list(envId = envId, query = SearchPaginationSort(start = 0, limit = 500)).data }.getOrDefault(emptyList()) }
            containers = c.await()
            projects = p.await()
        }
        loadingTargets = false
    }

    fun create() {
        val c = client ?: return
        scope.launch {
            loading = true; error = null
            try {
                val created = c.webhooks.create(
                    CreateWebhook(
                        name = name,
                        targetType = targetType.wire,
                        actionType = actionType,
                        targetId = if (needsTargetId) targetId else "",
                    ),
                    envId,
                )
                onCreated(created)
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
                    title = { Text("Create Webhook") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(
                            onClick = { create() },
                            enabled = name.isNotEmpty() && !loading && (!needsTargetId || targetId.isNotEmpty()),
                        ) { Text("Create") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormSectionHeader("Webhook Details")
                LabeledTextField("Name", name, { name = it })

                FormSectionHeader("Target")
                LabeledPicker(
                    label = "Target Type",
                    selected = targetType,
                    options = TargetType.entries,
                    optionLabel = { it.label },
                    onSelect = { targetType = it; actionType = actionsFor(it).first(); targetId = "" },
                )
                LabeledPicker(
                    label = "Action",
                    selected = actionType,
                    options = actionsFor(targetType),
                    optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                    onSelect = { actionType = it },
                )
                if (needsTargetId) {
                    if (loadingTargets) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Loading targets…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        }
                    } else {
                        when (targetType) {
                            TargetType.Container -> LabeledPicker(
                                label = "Container",
                                selected = targetId,
                                options = listOf("") + containers.map { it.id },
                                optionLabel = { id -> if (id.isEmpty()) "Select…" else containers.firstOrNull { it.id == id }?.displayName ?: id },
                                onSelect = { targetId = it },
                            )
                            TargetType.Project -> LabeledPicker(
                                label = "Project",
                                selected = targetId,
                                options = listOf("") + projects.map { it.id },
                                optionLabel = { id -> if (id.isEmpty()) "Select…" else projects.firstOrNull { it.id == id }?.name ?: id },
                                onSelect = { targetId = it },
                            )
                            TargetType.GitOps -> LabeledTextField("Stack ID", targetId, { targetId = it })
                            TargetType.Updater -> Unit
                        }
                    }
                }
                error?.let { FormErrorRow(it) }
            }
        }
    }
}

/** One-time webhook token reveal. Port of iOS `NewWebhookTokenView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewWebhookTokenDialog(name: String, token: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Webhook Created") }, actions = { TextButton(onClick = onDismiss) { Text("Done") } })
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircleIcon(Icons.Filled.Link, ArcaneGreen, size = 80)
                Text("Save Your Webhook Token", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "This token will only be shown once. Make sure to save it somewhere safe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(token, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Button(onClick = { copyToClipboard(context, token) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Text("  Copy to Clipboard")
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Webhook Token", text))
}
