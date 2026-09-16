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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.getarcane.android.R
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationStartResult
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.operations.OperationDetail
import app.getarcane.sdk.models.user.isGlobalAdmin

internal fun shouldShowUpdateAllAction(isAdmin: Boolean): Boolean = isAdmin

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
                title = { Text(stringResource(R.string.update_all_title)) },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.a11y_close))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !isAdmin -> ContentUnavailable(
                stringResource(R.string.update_all_admin_only_title),
                Icons.Filled.Lock,
                stringResource(R.string.update_all_admin_only_message),
            )
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
                    Text(
                        startError ?: pluralStringResource(
                            R.plurals.update_all_confirmation,
                            environmentCount,
                            environmentCount,
                        ),
                    )
                    Button(
                        onClick = {
                            when (val result = store.startFleetUpdate()) {
                                is OperationStartResult.Started -> operationId = result.operationId
                                is OperationStartResult.Duplicate -> operationId = result.operationId
                                is OperationStartResult.Rejected -> startError = result.message
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.update_all_action)) }
                }
            }
        }
    }
}
