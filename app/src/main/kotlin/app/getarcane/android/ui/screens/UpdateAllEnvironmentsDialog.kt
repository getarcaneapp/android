package app.getarcane.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationStartResult
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.operations.OperationDetail
import app.getarcane.sdk.models.system.EnvironmentUpdateJob
import app.getarcane.sdk.models.system.EnvironmentUpdateJobStatus
import app.getarcane.sdk.models.system.EnvironmentUpdateResultStatus
import app.getarcane.sdk.models.user.isGlobalAdmin

internal fun shouldShowUpdateAllAction(isAdmin: Boolean): Boolean = isAdmin

internal fun updateAllLastRunSummary(job: EnvironmentUpdateJob): String {
    if (job.status == EnvironmentUpdateJobStatus.FAILED) return job.error ?: "Failed"
    val results = job.results.orEmpty()
    val updated = results.count { it.status == EnvironmentUpdateResultStatus.UPDATED || it.status == EnvironmentUpdateResultStatus.TRIGGERED }
    val failed = results.count { it.status == EnvironmentUpdateResultStatus.FAILED }
    val skipped = results.count { it.status == EnvironmentUpdateResultStatus.SKIPPED_OFFLINE }
    return buildList {
        add("$updated updated")
        if (failed > 0) add("$failed failed")
        if (skipped > 0) add("$skipped skipped")
        job.managerTargetVersion?.takeIf { it.isNotBlank() && ':' !in it && it.length <= 20 }?.let(::add)
    }.joinToString(" · ")
}

internal fun updateAllFinishedMessage(job: EnvironmentUpdateJob, note: String?): String =
    note ?: when {
        job.status == EnvironmentUpdateJobStatus.COMPLETED &&
            job.results.orEmpty().none { it.status == EnvironmentUpdateResultStatus.FAILED } -> "All environments updated"
        job.status == EnvironmentUpdateJobStatus.FAILED -> job.error ?: "Fleet update failed"
        else -> updateAllLastRunSummary(job)
    }

/** Fleet update surface backed by the one app-scoped operation owner and SDK job contract. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAllEnvironmentsDialog(
    environmentCount: Int,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val store = LocalOperationStore.current
    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false
    var operationId by remember { mutableStateOf<String?>(null) }
    var startError by remember { mutableStateOf<String?>(null) }

    val record = store.operations.firstOrNull { it.operationId == operationId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Update All") },
                actions = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") } },
            )
        },
    ) { padding ->
        when {
            !isAdmin -> ContentUnavailable("Admins Only", Icons.Filled.Lock, "Updating all environments requires an administrator account.")
            record != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                OperationDetail(
                    record = record,
                    onBack = onDismiss,
                    onCancel = {},
                    onRetry = { store.retry(record.operationId) },
                    onDismiss = { onComplete(); onDismiss() },
                    onOpenActivity = {},
                    canCancel = false,
                )
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(startError ?: "Update all $environmentCount environments to the latest Arcane release?")
                    Button(
                        onClick = {
                            when (val result = store.startFleetUpdate()) {
                                is OperationStartResult.Started -> operationId = result.operationId
                                is OperationStartResult.Duplicate -> operationId = result.operationId
                                is OperationStartResult.Rejected -> startError = result.message
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Update All") }
                }
            }
        }
    }
}
