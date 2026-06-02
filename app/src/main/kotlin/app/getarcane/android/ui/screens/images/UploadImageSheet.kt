package app.getarcane.android.ui.screens.images

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pick a tarball and upload it, streaming load progress. Mirrors iOS `UploadImageView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UploadImageSheet(onDismiss: () -> Unit, onComplete: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    var pickedSize by remember { mutableStateOf(0L) }
    var isUploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var output by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var name = uri.lastPathSegment ?: "image.tar"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        pickedUri = uri
        pickedName = name
        pickedSize = size
        output = null
        errorMessage = null
    }

    fun startUpload() {
        val uri = pickedUri
        if (client == null || uri == null) {
            errorMessage = "Invalid configuration"
            return
        }
        isUploading = true
        progress = 0f
        output = null
        errorMessage = null
        val aggregated = StringBuilder()
        uploadJob = scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Couldn't read the selected file.")

                client.images.uploadStream(envId = envId, content = bytes, filename = pickedName).collect { event ->
                    event.error?.takeIf { it.isNotEmpty() }?.let { errorMessage = it; return@collect }
                    val detail = event.progressDetail
                    val total = detail?.total ?: 0L
                    val current = detail?.current
                    if (total > 0L && current != null) {
                        progress = minOf(current, total).toFloat() / total.toFloat()
                    }
                    event.status?.takeIf { it.isNotEmpty() }?.let { aggregated.appendLine(it) }
                }
                progress = 1f
                output = aggregated.toString().trim().ifEmpty { "Upload complete." }
                onComplete()
            } catch (_: CancellationException) {
                errorMessage = "Cancelled"
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isUploading = false
                uploadJob = null
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isUploading) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Upload Image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            if (pickedUri != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pickedName, style = MaterialTheme.typography.bodyMedium)
                    Text(formatBytes(pickedSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { picker.launch("*/*") }, enabled = !isUploading) { Text("Change file…") }
            } else {
                OutlinedButton(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FolderZip, null, modifier = Modifier.size(18.dp))
                    Text("  Choose tarball…")
                }
            }
            Text(
                "Accepts .tar, .tar.gz, .tgz, .tar.xz. Server enforces a max size (default 500 MB).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isUploading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            output?.takeIf { it.isNotEmpty() }?.let {
                Text("RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                )
            }

            errorMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(18.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (output != null) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
                } else {
                    OutlinedButton(
                        onClick = { if (isUploading) uploadJob?.cancel() else onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isUploading) "Stop" else "Cancel") }
                    Button(
                        onClick = { startUpload() },
                        enabled = pickedUri != null && !isUploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isUploading) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Upload")
                    }
                }
            }
        }
    }
}
