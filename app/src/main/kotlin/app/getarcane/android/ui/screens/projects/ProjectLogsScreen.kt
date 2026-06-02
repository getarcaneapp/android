package app.getarcane.android.ui.screens.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.streaming.LogLine

/** Live project log stream. Mirrors the iOS `LogsView` opened from the project detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectLogsScreen(projectId: String, title: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val lines = remember { mutableStateListOf<LogLine>() }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        if (client == null) return@LaunchedEffect
        try {
            client.projects.logs(envId = envId, projectId = projectId, follow = true, tail = "200").collect { line ->
                lines.add(line)
                if (lines.size > 5000) repeat(100) { if (lines.isNotEmpty()) lines.removeAt(0) }
            }
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        }
    }

    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "Logs" }, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(if (autoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Toggle follow")
                    }
                    IconButton(onClick = { lines.clear() }) { Icon(Icons.Filled.Delete, "Clear") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { ErrorBanner(it, modifier = Modifier.padding(12.dp)) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(lines) { line -> LogLineRow(line) }
            }
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    val color = when (line.level?.lowercase()) {
        "error", "err", "fatal" -> ArcaneRed
        "warn", "warning" -> ArcaneOrange
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row {
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
