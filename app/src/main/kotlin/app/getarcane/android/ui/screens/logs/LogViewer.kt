package app.getarcane.android.ui.screens.logs

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.getarcane.android.core.AnsiSanitizer
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.BannerSeverity
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.buildAnsiAnnotatedString
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.streaming.LogLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LogExportAction(val title: String) {
    Copy("Copy retained logs"),
    Share("Share retained logs"),
    Export("Export retained logs"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogViewer(
    title: String,
    sourceId: String,
    sourceKind: String,
    permissionGranted: Boolean,
    permissionMessage: String,
    onBack: (() -> Unit)?,
    stream: (ArcaneClient, EnvironmentId) -> Flow<LogLine>,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentUserId = manager.currentUser?.id.orEmpty()
    val buffer = remember { BoundedLogBuffer() }
    val listState = rememberLazyListState()

    var snapshot by remember { mutableStateOf(LogRetentionSnapshot()) }
    var connection by remember { mutableStateOf<LogConnectionState>(LogConnectionState.Idle) }
    var followState by remember { mutableStateOf(LogFollowState()) }
    var search by remember { mutableStateOf("") }
    var showTimestamps by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var sourceKey by remember { mutableStateOf<String?>(null) }
    var streamGeneration by remember { mutableIntStateOf(0) }
    var pendingExportAction by remember { mutableStateOf<LogExportAction?>(null) }
    var exportTextForPicker by remember { mutableStateOf<String?>(null) }
    var exportLineCountForPicker by remember { mutableIntStateOf(0) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = exportTextForPicker
        val exportedLineCount = exportLineCountForPicker
        exportTextForPicker = null
        exportLineCountForPicker = 0
        if (uri == null) {
            operationMessage = "Export canceled. Retained logs were not changed."
        } else if (text != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                            writer.write(text)
                        } ?: error("The selected destination could not be opened.")
                    }
                    operationMessage = "Exported $exportedLineCount retained log lines."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    operationError = "Couldn't export logs: ${friendlyErrorMessage(e)}"
                }
            }
        }
    }

    LaunchedEffect(client, currentUserId, envId.rawValue, sourceKind, sourceId, permissionGranted, retryKey) {
        val activeClient = client
        val nextSourceKey =
            "${System.identityHashCode(activeClient)}:$currentUserId:${envId.rawValue}:$sourceKind:$sourceId:$permissionGranted"
        if (sourceKey != nextSourceKey) {
            buffer.clear()
            snapshot = buffer.snapshot()
            followState = LogFollowState()
            sourceKey = nextSourceKey
        }
        streamGeneration++
        val generation = streamGeneration
        if (activeClient == null || !permissionGranted) {
            connection = LogConnectionState.Idle
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            connection = LogConnectionState.Connecting
            operationError = null
            try {
                connection = LogConnectionState.Live
                stream(activeClient, envId).collect { line ->
                    if (generation != streamGeneration) return@collect
                    if (buffer.append(line)) {
                        snapshot = buffer.snapshot()
                        followState = followState.receive(1)
                    }
                }
                if (generation == streamGeneration) connection = LogConnectionState.Ended
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (generation == streamGeneration) {
                    connection = LogConnectionState.Failed(friendlyErrorMessage(e))
                }
            }
        }
    }

    val filteredLines = remember(snapshot, search) {
        val query = search.trim()
        if (query.isEmpty()) snapshot.lines else snapshot.lines.filter { entry ->
            val line = entry.line
            AnsiSanitizer.strip(line.text).contains(query, ignoreCase = true) ||
                line.service.orEmpty().contains(query, ignoreCase = true) ||
                line.level.orEmpty().contains(query, ignoreCase = true) ||
                line.timestamp.orEmpty().contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(listState, filteredLines.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            listState.isScrollInProgress to (filteredLines.isEmpty() || lastVisible >= filteredLines.lastIndex)
        }.collect { (scrolling, atBottom) ->
            followState = when {
                atBottom -> followState.resumeFromScroll()
                scrolling -> followState.pause()
                else -> followState
            }
        }
    }

    LaunchedEffect(filteredLines.lastOrNull()?.id, followState.isFollowing) {
        if (followState.isFollowing && filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.lastIndex)
        }
    }

    fun retainedText(): String = exportLogText(snapshot, showTimestamps)

    fun completeExport(action: LogExportAction) {
        val text = retainedText()
        if (text.isEmpty()) return
        operationError = null
        operationMessage = null
        when (action) {
            LogExportAction.Copy -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("$title retained logs", text))
                operationMessage = "Copied ${snapshot.lines.size} retained log lines."
            }
            LogExportAction.Share -> {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "$title retained logs")
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(intent, "Share retained logs").apply {
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Throwable) {
                    operationError = "Couldn't share logs: ${friendlyErrorMessage(e)}"
                }
            }
            LogExportAction.Export -> {
                exportTextForPicker = text
                exportLineCountForPicker = snapshot.lines.size
                try {
                    exportLauncher.launch("${sanitizedLogFilename(title)}-${System.currentTimeMillis()}.log")
                } catch (e: Throwable) {
                    exportTextForPicker = null
                    exportLineCountForPicker = 0
                    operationError = "Couldn't open an export destination: ${friendlyErrorMessage(e)}"
                }
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            LogStatusBar(
                connection = connection,
                snapshot = snapshot,
                followState = followState,
                onToggleFollow = {
                    followState = if (followState.isFollowing) followState.pause(manually = true) else followState.resume()
                },
                onRetry = { retryKey++ },
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                showTimestamps = showTimestamps,
                onShowTimestampsChange = { showTimestamps = it },
                onExport = { pendingExportAction = it },
                onClear = {
                    buffer.clear()
                    snapshot = buffer.snapshot()
                    followState = LogFollowState()
                    operationMessage = "Cleared retained logs from this viewer."
                },
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search retained logs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            operationError?.let {
                ErrorBanner(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), onRetry = { operationError = null })
            }
            (connection as? LogConnectionState.Failed)?.let { failed ->
                ErrorBanner(
                    "Log stream failed: ${failed.message}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    onRetry = { retryKey++ },
                )
            }
            operationMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            when {
                !permissionGranted -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(permissionMessage, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filteredLines.isEmpty() -> EmptyLogState(connection, search, onRetry = { retryKey++ })
                else -> SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(filteredLines, key = RetainedLogLine::id) { entry ->
                            LogLineRow(entry.line, showTimestamps)
                        }
                    }
                }
            }
        }
    }

    if (onBack != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title.ifEmpty { "Logs" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                )
            },
        ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
    } else {
        content()
    }

    pendingExportAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingExportAction = null },
            title = { Text(action.title) },
            text = {
                val scopeText = if (snapshot.hasDiscardedContent) {
                    "This includes the latest ${snapshot.lines.size} retained lines; ${snapshot.discardedLineCount} earlier lines were discarded and ${snapshot.truncatedLineCount} oversized lines were shortened."
                } else {
                    "This includes all ${snapshot.lines.size} lines currently retained by this viewer."
                }
                Text(
                    "Source: $sourceKind “$title” in ${manager.activeEnvironmentName}. " +
                        "$scopeText Logs can contain passwords, tokens, or other secrets. " +
                        "Review the destination before continuing.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportAction = null
                    completeExport(action)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pendingExportAction = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogStatusBar(
    connection: LogConnectionState,
    snapshot: LogRetentionSnapshot,
    followState: LogFollowState,
    onToggleFollow: () -> Unit,
    onRetry: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    showTimestamps: Boolean,
    onShowTimestampsChange: (Boolean) -> Unit,
    onExport: (LogExportAction) -> Unit,
    onClear: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (connection == LogConnectionState.Connecting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                when (connection) {
                    LogConnectionState.Idle -> "Disconnected"
                    LogConnectionState.Connecting -> "Connecting"
                    LogConnectionState.Live -> if (followState.isFollowing) "Live" else "Paused"
                    LogConnectionState.Ended -> "Stream ended"
                    is LogConnectionState.Failed -> "Connection failed"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                buildString {
                    append("${snapshot.lines.size} lines")
                    if (snapshot.hasDiscardedContent) append(" • bounded window")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.hasDiscardedContent) ArcaneOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (connection is LogConnectionState.Failed || connection == LogConnectionState.Ended) {
                IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, "Retry log stream") }
            }
            IconButton(onClick = onToggleFollow) {
                Icon(if (followState.isFollowing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (followState.isFollowing) "Pause following" else "Resume following")
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) { Icon(Icons.Filled.MoreVert, "Log options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Show timestamps") },
                        onClick = { onShowTimestampsChange(!showTimestamps) },
                        leadingIcon = { Checkbox(checked = showTimestamps, onCheckedChange = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy all retained") },
                        enabled = snapshot.lines.isNotEmpty(),
                        onClick = { onMenuOpenChange(false); onExport(LogExportAction.Copy) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Share retained…") },
                        enabled = snapshot.lines.isNotEmpty(),
                        onClick = { onMenuOpenChange(false); onExport(LogExportAction.Share) },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Export retained…") },
                        enabled = snapshot.lines.isNotEmpty(),
                        onClick = { onMenuOpenChange(false); onExport(LogExportAction.Export) },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear retained view", color = ArcaneRed) },
                        enabled = snapshot.lines.isNotEmpty(),
                        onClick = { onMenuOpenChange(false); onClear() },
                        leadingIcon = { Icon(Icons.Filled.Clear, null, tint = ArcaneRed) },
                    )
                }
            }
        }
    }
    AnimatedVisibility(visible = !followState.isFollowing && followState.newLinesWhilePaused > 0) {
        Surface(color = ArcaneBlue, shape = CircleShape, onClick = onToggleFollow, modifier = Modifier.padding(8.dp)) {
            Text(
                "${followState.newLinesWhilePaused} new • jump to latest",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EmptyLogState(connection: LogConnectionState, search: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            search.isNotBlank() -> Text("No retained log lines match this search.")
            connection == LogConnectionState.Connecting -> CircularProgressIndicator()
            connection is LogConnectionState.Failed -> ErrorBanner(connection.message, onRetry = onRetry, modifier = Modifier.padding(16.dp))
            connection == LogConnectionState.Ended -> Text("The log stream ended without retained output.")
            else -> Text("Connected and waiting for log output.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine, showTimestamps: Boolean) {
    val color = when (normalizedLogLevel(line.level)) {
        "ERROR" -> ArcaneRed
        "WARN" -> ArcaneOrange
        "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (showTimestamps) line.timestamp?.takeIf(String::isNotBlank)?.let {
            Text(
                it.substringAfter('T').take(12),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            buildAnsiAnnotatedString(line.text),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
            overflow = TextOverflow.Clip,
        )
    }
}
