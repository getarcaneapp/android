package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationStartResult
import app.getarcane.sdk.models.image.ImagePullOptions

/** Submits an image pull to the app-scoped owner; dismissing this sheet never cancels it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullImageSheet(onDismiss: () -> Unit) {
    val manager = LocalArcaneManager.current
    val store = LocalOperationStore.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var imageName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pull Image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = imageName,
                onValueChange = { imageName = it; errorMessage = null },
                label = { Text("Image") },
                placeholder = { Text("e.g. nginx:latest") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val (image, tag) = parseImageReference(imageName)
                        when (val result = store.startImagePull(
                            environmentId = manager.activeEnvironmentId,
                            environmentName = manager.activeEnvironmentName,
                            imageReference = imageName.trim(),
                            options = ImagePullOptions(imageName = image, tag = tag),
                        )) {
                            is OperationStartResult.Rejected -> errorMessage = result.message
                            is OperationStartResult.Started -> {
                                store.openOperation(result.operationId)
                                onDismiss()
                            }
                            is OperationStartResult.Duplicate -> {
                                store.openOperation(result.operationId)
                                onDismiss()
                            }
                        }
                    },
                    enabled = imageName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Pull") }
            }
        }
    }
}

internal fun parseImageReference(raw: String): Pair<String, String?> {
    val beforeDigest = raw.trim().substringBefore("@")
    val colon = beforeDigest.lastIndexOf(':')
    if (colon >= 0 && !beforeDigest.substring(colon).contains('/')) {
        return beforeDigest.substring(0, colon) to beforeDigest.substring(colon + 1).ifEmpty { null }
    }
    return beforeDigest to null
}
