package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.image.ImagePullOptions
import app.getarcane.sdk.models.project.PullProgressEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pull an image by `repo:tag`, streaming NDJSON layer progress. Mirrors iOS `PullImageView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullImageSheet(onDismiss: () -> Unit, onComplete: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var imageName by remember { mutableStateOf("") }
    var isPulling by remember { mutableStateOf(false) }
    var didComplete by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val layerOrder = remember { mutableStateListOf<String>() }
    val layers = remember { mutableStateMapOf<String, PullProgressEvent>() }
    var pullJob by remember { mutableStateOf<Job?>(null) }

    fun parseNameAndTag(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val beforeDigest = trimmed.substringBefore("@")
        val colonIdx = beforeDigest.lastIndexOf(':')
        if (colonIdx >= 0 && !beforeDigest.substring(colonIdx).contains("/")) {
            val name = beforeDigest.substring(0, colonIdx)
            val tag = beforeDigest.substring(colonIdx + 1)
            return name to tag.ifEmpty { null }
        }
        return beforeDigest to null
    }

    fun apply(event: PullProgressEvent) {
        event.error?.takeIf { it.isNotEmpty() }?.let { errorMessage = it; return }
        val id = event.id
        if (!id.isNullOrEmpty()) {
            if (!layers.containsKey(id)) layerOrder.add(id)
            layers[id] = event
        } else {
            event.status?.takeIf { it.isNotEmpty() }?.let { statusLine = it }
        }
        event.status?.let { status ->
            val lower = status.lowercase()
            if (lower.startsWith("status: ") || lower == "pull complete") {
                statusLine = if (lower == "pull complete") "Pull complete" else status
            }
        }
    }

    fun pull() {
        if (client == null) return
        val (image, tag) = parseNameAndTag(imageName)
        isPulling = true
        didComplete = false
        statusLine = "Connecting…"
        layerOrder.clear()
        layers.clear()
        errorMessage = null
        pullJob = scope.launch {
            try {
                client.images.pullStream(envId = envId, options = ImagePullOptions(imageName = image, tag = tag))
                    .collect { apply(it) }
                if (errorMessage == null) {
                    didComplete = true
                    statusLine = "Pull complete"
                    onComplete()
                }
            } catch (_: CancellationException) {
                statusLine = "Cancelled"
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
                statusLine = ""
            } finally {
                isPulling = false
                pullJob = null
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isPulling) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pull Image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = imageName,
                onValueChange = { imageName = it },
                label = { Text("Image") },
                placeholder = { Text("e.g. nginx:latest") },
                singleLine = true,
                enabled = !isPulling && !didComplete,
                modifier = Modifier.fillMaxWidth(),
            )

            if (statusLine.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (didComplete) Icons.Filled.CheckCircle else Icons.Filled.Download,
                        null,
                        tint = if (didComplete) ArcaneGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = if (didComplete) ArcaneGreen else MaterialTheme.colorScheme.onSurface)
                }
            }

            errorMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
                }
            }

            if (layerOrder.isNotEmpty()) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(layerOrder.toList(), key = { it }) { id ->
                        layers[id]?.let { LayerRow(it) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!didComplete) {
                    OutlinedButton(
                        onClick = { if (isPulling) pullJob?.cancel() else onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isPulling) "Stop" else "Cancel") }
                    Button(
                        onClick = { pull() },
                        enabled = imageName.isNotEmpty() && !isPulling,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isPulling) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Pull")
                    }
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun LayerRow(event: PullProgressEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                event.id.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                event.status.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val detail = event.progressDetail
        val total = detail?.total ?: 0L
        val current = detail?.current
        val statusLower = event.status?.lowercase().orEmpty()
        when {
            total > 0L && current != null ->
                LinearProgressIndicator(
                    progress = { (minOf(current, total).toFloat() / total.toFloat()) },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressTint(event.status),
                )
            statusLower.contains("complete") || statusLower.contains("exists") ->
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth(), color = ArcaneGreen)
        }
    }
}

private fun progressTint(status: String?): Color {
    val s = status?.lowercase() ?: ""
    return when {
        s.contains("download") -> ArcaneBlue
        s.contains("extract") -> ArcaneOrange
        s.contains("complete") -> ArcaneGreen
        else -> ArcaneBlue
    }
}
