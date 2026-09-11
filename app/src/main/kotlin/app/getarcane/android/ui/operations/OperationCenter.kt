package app.getarcane.android.ui.operations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationRecord
import app.getarcane.android.core.OperationState

/** App-wide projection of the single operation owner. */
@Composable
fun OperationHost(content: @Composable () -> Unit) {
    val store = LocalOperationStore.current
    val context = LocalContext.current
    var handledPermissionGeneration by rememberSaveable {
        mutableIntStateOf(store.notificationPermissionRequestGeneration)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { store.refreshNotificationProjection() }

    LaunchedEffect(store.notificationPermissionRequestGeneration) {
        val generation = store.notificationPermissionRequestGeneration
        if (Build.VERSION.SDK_INT >= 33 && generation > handledPermissionGeneration) {
            handledPermissionGeneration = generation
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        val active = store.operations.filter(OperationRecord::isActive)
        if (active.isNotEmpty()) {
            OperationIndicator(
                count = active.size,
                reconnecting = active.any { it.state == OperationState.RECONNECTING },
                onClick = store::openCenter,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            )
        }
    }

    if (store.isCenterOpen) {
        OperationCenterSheet()
    }
}

@Composable
private fun OperationIndicator(
    count: Int,
    reconnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            Column(Modifier.weight(1f)) {
                Text(if (count == 1) "1 operation in progress" else "$count operations in progress", fontWeight = FontWeight.SemiBold)
                if (reconnecting) Text("Reconnecting to server activity", style = MaterialTheme.typography.bodySmall)
            }
            Text("View", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationCenterSheet() {
    val store = LocalOperationStore.current
    val selected = store.operations.firstOrNull { it.operationId == store.selectedOperationId }
    ModalBottomSheet(
        onDismissRequest = store::closeCenter,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (selected == null) {
            OperationList()
        } else {
            OperationDetail(
                record = selected,
                onBack = store::showOperationList,
                onCancel = { store.cancel(selected.operationId) },
                onRetry = { store.retry(selected.operationId) },
                onDismiss = { store.dismiss(selected.operationId) },
                onOpenActivity = { store.openActivityCenter(selected.operationId) },
                canCancel = store.canCancel(selected),
            )
        }
    }
}

@Composable
private fun OperationList() {
    val store = LocalOperationStore.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("Operations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        store.unavailableMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        if (store.operations.isEmpty()) {
            Text("No recent operations", modifier = Modifier.padding(vertical = 32.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    store.operations.sortedWith(
                        compareByDescending<OperationRecord> { it.isActive }
                            .thenByDescending { it.updatedAtEpochMs },
                    ),
                    key = { it.operationId },
                ) { record ->
                    Card(onClick = { store.openOperation(record.operationId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(record.title, fontWeight = FontWeight.SemiBold)
                            Text(record.state.displayName(), style = MaterialTheme.typography.bodySmall)
                            record.progressPercent?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OperationDetail(
    record: OperationRecord,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenActivity: () -> Unit,
    canCancel: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack) { Text("Back to operations") }
        Text(record.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(record.state.displayName(), modifier = Modifier.padding(top = 4.dp))
        record.progressPercent?.let {
            LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }
        record.detailMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canCancel) OutlinedButton(onClick = onCancel) { Text("Cancel") }
            if (record.state == OperationState.UNKNOWN || record.state == OperationState.INTERRUPTED) {
                OutlinedButton(onClick = onRetry) { Text("Check again") }
            }
            if (record.serverActivityId != null) OutlinedButton(onClick = onOpenActivity) { Text("Activity Center") }
            if (record.isTerminalLike) Button(onClick = onDismiss) { Text("Dismiss") }
        }
        if (record.lines.isNotEmpty()) {
            Text("Output", fontWeight = FontWeight.SemiBold)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(vertical = 8.dp)) {
                itemsIndexed(record.lines) { index, line ->
                    Text(
                        line.text,
                        color = if (line.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun OperationState.displayName(): String = when (this) {
    OperationState.QUEUED -> "Queued"
    OperationState.STARTING -> "Starting"
    OperationState.RUNNING -> "Running"
    OperationState.RECONNECTING -> "Reconnecting"
    OperationState.CANCEL_REQUESTED -> "Cancellation requested"
    OperationState.SUCCESS -> "Completed"
    OperationState.FAILURE -> "Failed"
    OperationState.CANCELLED -> "Cancelled"
    OperationState.INTERRUPTED -> "Interrupted"
    OperationState.UNKNOWN -> "Outcome unknown"
    OperationState.CLEARED -> "Cleared"
}
