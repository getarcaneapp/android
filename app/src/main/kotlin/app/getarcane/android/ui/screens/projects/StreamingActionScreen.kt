package app.getarcane.android.ui.screens.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.project.PullProgressEvent
import kotlinx.coroutines.flow.Flow

/** A single rendered line of stream output. */
private data class StreamLine(val text: String, val isError: Boolean)

private sealed interface StreamStatus {
    data object Running : StreamStatus
    data object Success : StreamStatus
    data class Failure(val message: String) : StreamStatus
}

/**
 * Live NDJSON output for a project lifecycle action (deploy / redeploy / pull / build). Collects the
 * SDK [Flow] into a [mutableStateListOf] and renders monospaced lines, coloring errors red. Mirrors
 * iOS `StreamingActionView` + `InstallStreamSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingActionScreen(
    projectId: String,
    action: String,
    title: String,
    onDone: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val lines = remember { mutableStateListOf<StreamLine>() }
    var status by remember { mutableStateOf<StreamStatus>(StreamStatus.Running) }
    var currentPhase by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun append(text: String, isError: Boolean) {
        lines.add(StreamLine(text, isError))
        if (lines.size > 2000) repeat(200) { if (lines.isNotEmpty()) lines.removeAt(0) }
    }

    LaunchedEffect(projectId, action) {
        if (client == null) {
            status = StreamStatus.Failure("No client available")
            return@LaunchedEffect
        }
        val stream: Flow<PullProgressEvent>? = when (action) {
            ProjectAction.UP -> client.projects.deployStream(envId = envId, projectId = projectId)
            ProjectAction.REDEPLOY -> client.projects.redeployStream(envId = envId, projectId = projectId)
            ProjectAction.PULL -> client.projects.pullImagesStream(envId = envId, projectId = projectId)
            ProjectAction.BUILD -> client.projects.buildStream(envId = envId, projectId = projectId)
            else -> null
        }
        if (stream == null) {
            status = StreamStatus.Failure("Unsupported streaming action")
            return@LaunchedEffect
        }
        try {
            stream.collect { event ->
                val isError = event.error != null
                val display = displayText(event)
                if (display.isNotEmpty()) append(display, isError)
                if (!isError) {
                    val phase = event.status?.trim()
                    if (!phase.isNullOrEmpty()) currentPhase = phase
                }
            }
            status = StreamStatus.Success
            currentPhase = "Complete"
        } catch (e: Throwable) {
            val message = friendlyErrorMessage(e)
            append(message, isError = true)
            status = StreamStatus.Failure(message)
            currentPhase = "Failed"
        }
    }

    // Keep the latest output in view.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    val terminal = status !is StreamStatus.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                actions = {
                    if (terminal) {
                        TextButton(onClick = onDone) { Text("Done") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatusHeader(status, currentPhase)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (line.isError) ArcaneRed else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(status: StreamStatus, currentPhase: String?) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (status) {
            is StreamStatus.Running -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(currentPhase ?: "Running…", style = MaterialTheme.typography.bodyMedium)
            }
            is StreamStatus.Success -> {
                Icon(Icons.Filled.CheckCircle, null, tint = ArcaneGreen, modifier = Modifier.size(20.dp))
                Text("Complete", style = MaterialTheme.typography.bodyMedium, color = ArcaneGreen)
            }
            is StreamStatus.Failure -> {
                Icon(Icons.Filled.Error, null, tint = ArcaneRed, modifier = Modifier.size(20.dp))
                Text(status.message, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
            }
        }
    }
}

/**
 * iOS `displayText(for:)` parity: prefer the human-readable `stream` line, else assemble
 * status / layer-id / progress.
 */
private fun displayText(event: PullProgressEvent): String {
    event.error?.let { return "Error: $it" }
    event.stream?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val parts = buildList {
        event.status?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (isEmpty()) event.id?.takeIf { it.isNotEmpty() }?.let { add("layer ${it.take(12)}") }
        event.progress?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }
    return parts.joinToString(" ")
}
