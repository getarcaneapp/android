@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Key
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneYellow
import app.getarcane.sdk.models.apikey.APIKey
import app.getarcane.sdk.models.apikey.CreateAPIKey
import kotlinx.coroutines.launch

/** API keys list. Port of iOS `APIKeysView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<APIKey>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var createdKey by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.apiKeys.listPaginated(limit = 100).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    SettingsListScaffold(title = "API Keys", onAdd = { showCreate = true }, addContentDescription = "Add API key") { padding ->
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
                        ContentUnavailable("No API Keys", Icons.Outlined.Key)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.value, key = { it.id }) { key ->
                                ApiKeyRow(
                                    apiKey = key,
                                    onDelete = if (key.isStatic) null else {
                                        {
                                            scope.launch {
                                                try {
                                                    client?.apiKeys?.delete(key.id)
                                                    refreshKey++
                                                } catch (e: Throwable) {
                                                    actionError = friendlyErrorMessage(e)
                                                }
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
        CreateApiKeyDialog(
            onDismiss = { showCreate = false },
            onCreated = { rawKey -> showCreate = false; createdKey = rawKey; refreshKey++ },
        )
    }

    createdKey?.let { key ->
        NewApiKeyDialog(key = key, onDismiss = { createdKey = null })
    }

    actionError?.let { msg ->
        InfoAlert(title = "Couldn't Delete API Key", message = msg, onDismiss = { actionError = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyRow(apiKey: APIKey, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { if (onDelete != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(apiKey.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (apiKey.isStatic) Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            apiKey.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            apiKey.expiresAt?.let {
                Text("Expires: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onDelete != null) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}

/** Create-API-key dialog. Port of iOS `CreateAPIKeyView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApiKeyDialog(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create API Key") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(
                            onClick = {
                                val c = client ?: return@TextButton
                                scope.launch {
                                    loading = true; error = null
                                    try {
                                        val created = c.apiKeys.create(CreateAPIKey(name = name, description = description.ifEmpty { null }))
                                        onCreated(created.key)
                                    } catch (e: Throwable) {
                                        error = friendlyErrorMessage(e)
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            enabled = name.isNotEmpty() && !loading,
                        ) { Text("Create") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormSectionHeader("Key Details")
                LabeledTextField("Name", name, { name = it })
                LabeledTextField("Description (optional)", description, { description = it })
                error?.let { FormErrorRow(it) }
            }
        }
    }
}

/** One-time key reveal. Port of iOS `NewAPIKeyView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewApiKeyDialog(key: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("API Key Created") },
                    actions = { TextButton(onClick = onDismiss) { Text("Done") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CircleIcon(Icons.Filled.Key, ArcaneYellow, size = 80)
                Text("Save Your API Key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "This key will only be shown once. Make sure to save it somewhere safe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    key,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                )
                Button(onClick = { copyToClipboard(context, key) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Text("  Copy to Clipboard")
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("API Key", text))
}
