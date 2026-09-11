package app.getarcane.android.ui.screens.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationStartResult
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.runSuspendCatching
import app.getarcane.android.core.shouldSubmitSavedOperation
import app.getarcane.android.ui.operations.OperationDetail
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.updater.UpdaterResult
import app.getarcane.sdk.models.updater.UpdaterStatus

internal sealed interface RunPhase {
    data object Starting : RunPhase
    data object Running : RunPhase
    data class Completed(val result: UpdaterResult) : RunPhase
    data class OutcomeUnknown(val message: String) : RunPhase
    data class Failed(val message: String) : RunPhase
}

internal data class UpdaterRunEvidence(
    val observedServerStart: Boolean,
    val successfulPostStartStatusProbe: Boolean,
    val successfulPostStartHistoryProbe: Boolean,
) {
    val hasReachabilityEvidence: Boolean = successfulPostStartStatusProbe || successfulPostStartHistoryProbe
    val shouldAvoidConnectivityFailure: Boolean = observedServerStart || hasReachabilityEvidence
}

internal fun updaterRunFailurePhase(error: Throwable, observedServerStart: Boolean): RunPhase =
    updaterRunFailurePhase(error, UpdaterRunEvidence(observedServerStart, false, false))

internal fun updaterRunFailurePhase(error: Throwable, evidence: UpdaterRunEvidence): RunPhase =
    if (evidence.shouldAvoidConnectivityFailure && error is ArcaneError.Transport) {
        RunPhase.OutcomeUnknown(
            if (evidence.observedServerStart) {
                "The updater started on the server, but the final response was interrupted. " +
                    "Refresh Updates or open Updater History to review the results."
            } else {
                "The updater request was interrupted, but Android could still reach the server. " +
                    "Refresh Updates or open Updater History to review the results."
            },
        )
    } else {
        RunPhase.Failed(friendlyErrorMessage(error))
    }

internal fun updaterRunPollingCompletedPhase(): RunPhase = RunPhase.OutcomeUnknown(
    "The updater is no longer reporting active work. Refresh Updates or open Updater History to review the results.",
)

internal fun hasNewUpdaterHistoryRecord(baselineIds: Set<String>?, observedIds: Set<String>): Boolean =
    baselineIds != null && observedIds.any { it !in baselineIds }

internal fun shouldContinuePollingAfterRunFailure(
    observedServerStart: Boolean,
    latestStatus: UpdaterRunStatusSnapshot?,
): Boolean = observedServerStart && latestStatus?.hasActiveWork == true

internal suspend fun runUpdaterRequestCatching(block: suspend () -> UpdaterResult): Result<UpdaterResult> =
    runSuspendCatching(block)

internal data class UpdaterRunStatusSnapshot(
    val updatingContainers: Int,
    val updatingProjects: Int,
    val containerIds: List<String>,
    val projectIds: List<String>,
) {
    val hasActiveWork: Boolean =
        updatingContainers > 0 || updatingProjects > 0 || containerIds.isNotEmpty() || projectIds.isNotEmpty()

    fun isNewActiveWorkComparedTo(baseline: UpdaterRunStatusSnapshot?): Boolean =
        baseline != null && hasActiveWork && this != baseline

    companion object {
        fun from(status: UpdaterStatus): UpdaterRunStatusSnapshot = UpdaterRunStatusSnapshot(
            status.updatingContainers,
            status.updatingProjects,
            status.containerIds,
            status.projectIds,
        )
    }
}

/** Durable updater entry point. The server Activity, not this screen, owns execution. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterRunScreen(onBack: () -> Unit, environmentId: EnvironmentId? = null, environmentName: String? = null) {
    val manager = LocalArcaneManager.current
    val store = LocalOperationStore.current
    val envId = environmentId ?: manager.activeEnvironmentId
    val envName = environmentName ?: manager.activeEnvironmentName
    var operationId by rememberSaveable(envId.rawValue) { mutableStateOf<String?>(null) }
    var startError by remember(envId.rawValue) { mutableStateOf<String?>(null) }

    LaunchedEffect(envId.rawValue) {
        if (!shouldSubmitSavedOperation(operationId)) return@LaunchedEffect
        when (val result = store.startUpdater(envId, envName)) {
            is OperationStartResult.Started -> operationId = result.operationId
            is OperationStartResult.Duplicate -> operationId = result.operationId
            is OperationStartResult.Rejected -> startError = result.message
        }
    }
    val record = store.operations.firstOrNull { it.operationId == operationId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Updater") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(startError ?: "Starting…")
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                OperationDetail(
                    record = record,
                    onBack = onBack,
                    onCancel = { store.cancel(record.operationId) },
                    onRetry = { store.retry(record.operationId) },
                    onDismiss = {
                        store.dismiss(record.operationId)
                        onBack()
                    },
                    onOpenActivity = { store.openActivityCenter(record.operationId) },
                    canCancel = store.canCancel(record),
                )
            }
        }
    }
}
