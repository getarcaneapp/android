package app.getarcane.android.ui.screens.containers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.streaming.LogLine

/** Standalone logs screen (own back stack route). Wraps [LogsContent] in a Scaffold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(id: String, onBack: () -> Unit) {
    var clearSignal by remember { mutableIntStateOf(0) }
    var streaming by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { clearSignal++ }) { Icon(Icons.Filled.Delete, "Clear") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter logs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LogsContent(
                id = id,
                search = search,
                clearSignal = clearSignal,
                onStreamingChange = { streaming = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Embedded logs view used inside the container detail's Logs tab. */
@Composable
fun EmbeddedLogsView(id: String, title: String) {
    var clearSignal by remember { mutableIntStateOf(0) }
    LogsContent(id = id, search = "", clearSignal = clearSignal, onStreamingChange = {}, modifier = Modifier.fillMaxSize())
}

/**
 * Streaming log body: live tail with a Live/Paused FAB and a "N new" pill while paused. Mirrors iOS
 * `LogsView.content`.
 */
@Composable
private fun LogsContent(
    id: String,
    search: String,
    clearSignal: Int,
    onStreamingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val lines = remember { mutableStateListOf<LogLine>() }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var newWhilePaused by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(clearSignal) {
        if (clearSignal > 0) {
            lines.clear()
            newWhilePaused = 0
        }
    }

    LaunchedEffect(id) {
        if (client == null) return@LaunchedEffect
        onStreamingChange(true)
        try {
            client.containers.logs(envId = envId, id = id, follow = true, tail = "200").collect { line ->
                lines.add(line)
                if (lines.size > 5000) repeat(100) { if (lines.isNotEmpty()) lines.removeAt(0) }
                if (!autoScroll) newWhilePaused++
            }
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        } finally {
            onStreamingChange(false)
        }
    }

    val filtered = remember(lines.size, search) {
        if (search.isBlank()) lines.toList() else lines.filter { it.text.contains(search, ignoreCase = true) }
    }

    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            error?.let { ErrorBanner(it, modifier = Modifier.padding(12.dp), severity = app.getarcane.android.ui.components.BannerSeverity.Warning) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(filtered) { line -> LogLineRow(line) }
            }
        }

        // "N new" pill while paused.
        AnimatedVisibility(
            visible = !autoScroll && newWhilePaused > 0,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = ArcaneBlue,
                onClick = {
                    autoScroll = true
                    newWhilePaused = 0
                },
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                    Text("$newWhilePaused new", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Live / Paused toggle FAB.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            onClick = {
                autoScroll = !autoScroll
                if (autoScroll) newWhilePaused = 0
            },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (autoScroll) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    null,
                    tint = if (autoScroll) ArcaneGreen else ArcaneOrange,
                    modifier = Modifier.size(16.dp),
                )
                Text(if (autoScroll) "Live" else "Paused", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    val color = when (line.level?.lowercase()) {
        "error", "err", "fatal" -> ArcaneRed
        "warn", "warning" -> ArcaneOrange
        "debug" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.padding(vertical = 1.dp)) {
        line.timestamp?.let {
            Text(
                it.substringAfter('T').take(8),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color,
            overflow = TextOverflow.Clip,
        )
    }
}
