package app.getarcane.android.ui.screens.volumes

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.volume.FileEntry

/**
 * File browser for a volume. Navigates directory paths via `client.volumes.browse(name, path)`.
 * Mirrors iOS `VolumeBrowserView`, which lists entries for the current path and lets the user
 * descend into directories (iOS exposes a free-form path field; here tapping a folder builds it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeBrowserScreen(name: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var path by remember { mutableStateOf("/") }
    var state by remember { mutableStateOf<Loadable<List<FileEntry>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(path, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.volumes.browse(envId = envId, name = name, path = path))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun navigateUp() {
        if (path == "/" || path.isEmpty()) return
        val trimmed = path.trimEnd('/')
        path = trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (path == "/" || path.isBlank()) "Files" else path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (path != "/" && path.isNotBlank()) {
                        IconButton(onClick = { navigateUp() }) { Icon(Icons.Filled.ArrowUpward, "Up") }
                    }
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
                is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Folder, s.message, "Retry") { refreshKey++ }
                is Loadable.Success -> {
                    val entries = s.value.sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                    )
                    if (entries.isEmpty()) {
                        ContentUnavailable("No Files", Icons.Outlined.Folder, "This directory is empty.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(entries, key = { it.path }) { entry ->
                                FileRow(entry) {
                                    if (entry.isDirectory) {
                                        path = entry.path
                                        refreshing = false
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 60.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (entry.isDirectory) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            null,
            tint = if (entry.isDirectory) ArcaneTeal else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.isDirectory) "Directory" else formatBytes(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (entry.isDirectory) FontFamily.Default else FontFamily.Monospace,
            )
        }
    }
}
