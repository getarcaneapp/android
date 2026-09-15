package app.getarcane.android.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationKind
import app.getarcane.android.core.OperationStartResult
import app.getarcane.android.core.ProjectDeployPreferenceValues
import app.getarcane.android.core.shouldSubmitSavedOperation
import app.getarcane.android.core.toSdkDeployOptions
import app.getarcane.android.ui.operations.OperationDetail

/** Project lifecycle operation backed by the app-scoped durable operation owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamingActionScreen(
    projectId: String,
    action: String,
    title: String,
    deployOptions: ProjectDeployPreferenceValues? = null,
    onDone: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val store = LocalOperationStore.current
    // The route starts work as a side effect. Keep the selected durable operation across activity
    // recreation so restoring this screen observes the existing operation instead of submitting it
    // again after rotation, background eviction, or process recreation.
    var operationId by rememberSaveable(projectId, action) { mutableStateOf<String?>(null) }
    var startError by remember(projectId, action) { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId, action, deployOptions) {
        if (!shouldSubmitSavedOperation(operationId)) return@LaunchedEffect
        val kind = when (action) {
            ProjectAction.UP -> OperationKind.PROJECT_DEPLOY
            ProjectAction.REDEPLOY -> OperationKind.PROJECT_REDEPLOY
            ProjectAction.PULL -> OperationKind.PROJECT_PULL
            ProjectAction.BUILD -> OperationKind.PROJECT_BUILD
            else -> null
        }
        if (kind == null) {
            startError = "Unsupported project operation"
            return@LaunchedEffect
        }
        when (val result = store.startProject(
            kind = kind,
            environmentId = manager.activeEnvironmentId,
            environmentName = manager.activeEnvironmentName,
            projectId = projectId,
            projectName = title.removePrefix("Deploy ").removePrefix("Redeploy ")
                .removePrefix("Pull images ").removePrefix("Build images "),
            options = deployOptions?.toSdkDeployOptions(),
        )) {
            is OperationStartResult.Started -> operationId = result.operationId
            is OperationStartResult.Duplicate -> operationId = result.operationId
            is OperationStartResult.Rejected -> startError = result.message
        }
    }

    BackHandler(onBack = onDone)
    val record = store.operations.firstOrNull { it.operationId == operationId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (record != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                OperationDetail(
                    record = record,
                    onBack = onDone,
                    onCancel = { store.cancel(record.operationId) },
                    onRetry = { store.retry(record.operationId) },
                    onDismiss = {
                        store.dismiss(record.operationId)
                        onDone()
                    },
                    onOpenActivity = { store.openActivityCenter(record.operationId) },
                    canCancel = store.canCancel(record),
                )
            }
        } else {
            Text(startError ?: "Starting…", modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}
