@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.getarcane.android.ui.screens.settings.notifications

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.screens.settings.SettingsSectionFooter
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.sdk.models.notification.NotificationProvider
import app.getarcane.sdk.models.notification.NotificationSettings
import kotlinx.coroutines.launch

/** Notification providers list with per-provider status + delete. Port of iOS `NotificationSettingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onOpenProvider: (NotificationProvider) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<NotificationSettings>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey, envId.rawValue) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.notifications.listSettings(envId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun configured(provider: NotificationProvider): NotificationSettings? =
        (state as? Loadable.Success)?.value?.firstOrNull { it.provider == provider }

    Scaffold(topBar = { TopAppBar(title = { Text("Notifications") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "header") { SettingsSectionHeader("Notification Providers") }
                        items(NotificationProvider.entries, key = { it.name }) { provider ->
                            val existing = configured(provider)
                            ProviderRow(
                                provider = provider,
                                existing = existing,
                                onClick = { onOpenProvider(provider) },
                                onDelete = if (existing != null) {
                                    {
                                        scope.launch {
                                            try {
                                                client?.notifications?.deleteSettings(provider, envId)
                                                refreshKey++
                                            } catch (e: Throwable) {
                                                error = friendlyErrorMessage(e)
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        item(key = "footer") {
                            SettingsSectionFooter("Configure providers to receive notifications for container events.")
                        }
                    }
                }
            }
        }
    }

    error?.let { msg ->
        app.getarcane.android.ui.screens.settings.InfoAlert("Error", msg, { error = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRow(
    provider: NotificationProvider,
    existing: NotificationSettings?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (onDelete != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(provider.iconVector, contentDescription = null, tint = provider.iconTint, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                val (label, color) = when {
                    existing == null -> "Not Configured" to MaterialTheme.colorScheme.onSurfaceVariant
                    existing.enabled -> "Enabled" to ArcaneGreen
                    else -> "Disabled" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onDelete != null) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}
