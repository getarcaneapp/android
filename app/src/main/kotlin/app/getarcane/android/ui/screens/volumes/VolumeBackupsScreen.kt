package app.getarcane.android.ui.screens.volumes

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.volume.BackupEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backups list for a volume with create / restore / download actions. Mirrors iOS
 * `VolumeBackupsView` (which surfaces Restore + Delete), extended with create + download per the
 * Android brief, all via the typed `client.volumes` backup endpoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeBackupsScreen(name: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var state by remember { mutableStateOf<Loadable<List<BackupEntry>>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.volumes.listBackups(envId = envId, name = name, query = SearchPaginationSort(limit = 100)).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun act(success: String? = null, block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            runCatching { block() }
                .onSuccess { success?.let { snackbar.showSnackbar(it) }; refreshKey++ }
                .onFailure { actionError = friendlyErrorMessage(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (creating) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            creating = true
                            if (client != null) scope.launch {
                                runCatching { client.volumes.createBackup(envId = envId, name = name) }
                                    .onSuccess { snackbar.showSnackbar("Backup created"); refreshKey++ }
                                    .onFailure { actionError = friendlyErrorMessage(it) }
                                creating = false
                            }
                        }) { Icon(Icons.Filled.Add, "Create backup") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Backup, s.message, "Retry") { refreshKey++ }
                is Loadable.Success -> {
                    val backups = s.value
                    if (backups.isEmpty()) {
                        ContentUnavailable("No Backups", Icons.Outlined.Backup, "Create a backup to protect this volume's data.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(backups, key = { it.id }) { backup ->
                                BackupRow(
                                    backup = backup,
                                    onRestore = { restoreTarget = backup },
                                    onDelete = { deleteTarget = backup },
                                    onDownload = {
                                        act {
                                            val bytes = client!!.volumes.downloadBackup(envId = envId, backupId = backup.id)
                                            val saved = saveToDownloads(context, "${name}-${backup.id}.tar.gz", bytes)
                                            withContext(Dispatchers.Main) {
                                                snackbar.showSnackbar(if (saved) "Saved to Downloads" else "Download failed")
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

    restoreTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore Backup") },
            text = { Text("Restore this backup over the volume? Current data will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    act("Backup restored") { client!!.volumes.restoreBackup(envId = envId, name = name, backupId = backup.id) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Backup") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    act("Backup deleted") { client!!.volumes.deleteBackup(envId = envId, backupId = backup.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    actionError?.let { msg ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Action Failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BackupRow(
    backup: BackupEntry,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(ArcaneTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Backup, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(backup.createdAt, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(backup.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Restore") }, onClick = { menu = false; onRestore() }, leadingIcon = { Icon(Icons.Filled.Restore, null) })
            DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() }, leadingIcon = { Icon(Icons.Filled.Download, null) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

/** Writes [bytes] to the public Downloads collection via MediaStore. Returns true on success. */
private fun saveToDownloads(context: android.content.Context, fileName: String, bytes: ByteArray): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, fileName).outputStream().use { it.write(bytes) }
            true
        }
    }.getOrDefault(false)
