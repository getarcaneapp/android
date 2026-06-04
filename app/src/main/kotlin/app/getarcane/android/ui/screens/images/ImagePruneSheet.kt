package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.sdk.models.image.ImagePruneReport
import kotlinx.coroutines.launch

private enum class PruneMode(val label: String, val apiValue: String, val description: String) {
    Dangling("Dangling Only", "dangling", "Removes only untagged images with no children."),
    All("All Unused", "all", "Removes every image not used by a container."),
    OlderThan(
        "Older Than...",
        "olderThan",
        "Removes unused images older than the given age (e.g. 24h, 7d)."
    ),
}

/** Prune options sheet (dangling / all / olderThan) with a result summary. Mirrors iOS `ImagePruneView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagePruneSheet(onDismiss: () -> Unit, onComplete: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mode by remember { mutableStateOf(PruneMode.Dangling) }
    var until by remember { mutableStateOf("24h") }
    var isPruning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun formatResult(report: ImagePruneReport): String {
        val count = report.imagesDeleted.size
        var msg =
            if (count == 0) "Nothing to remove." else "Removed $count image${if (count == 1) "" else "s"}."
        if (report.spaceReclaimed > 0) msg += " Freed ${formatBytes(report.spaceReclaimed)}."
        return msg
    }

    fun runPrune() {
        if (client == null) return
        isPruning = true
        scope.launch {
            try {
                val report = client.images.prune(
                    envId = envId,
                    mode = mode.apiValue,
                    until = if (mode == PruneMode.OlderThan) until else null,
                )
                resultMessage = formatResult(report)
                onComplete()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isPruning = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Prune Images",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "PRUNE MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PruneMode.entries.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = mode == m, onClick = { mode = m })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = mode == m, onClick = { mode = m })
                    Text(m.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (mode == PruneMode.OlderThan) {
                OutlinedTextField(
                    value = until,
                    onValueChange = { until = it },
                    label = { Text("Older than") },
                    placeholder = { Text("e.g. 24h, 7d") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            Button(
                onClick = { runPrune() },
                enabled = !isPruning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (isPruning) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                    Text("  Prune")
                }
            }
        }
    }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null; onDismiss() },
            title = { Text("Prune complete") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    resultMessage = null; onDismiss()
                }) { Text("OK") }
            },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Prune failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
        )
    }
}
