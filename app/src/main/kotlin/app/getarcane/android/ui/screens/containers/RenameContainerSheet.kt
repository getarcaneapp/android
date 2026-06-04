package app.getarcane.android.ui.screens.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.getarcane.android.ui.theme.ArcaneRed
import kotlinx.coroutines.launch

private val nameRegex = Regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]+$")

/**
 * Bottom-sheet for renaming a container. Validates against `^[a-zA-Z0-9][a-zA-Z0-9_.-]+$`. [onRename]
 * runs the request and returns an error message, or null on success (the sheet then dismisses). Port
 * of iOS `RenameContainerSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameContainerSheet(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: suspend (String) -> String?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf(currentName) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isValid = nameRegex.matches(newName)
    val hasChanged = newName != currentName

    ModalBottomSheet(onDismissRequest = { if (!submitting) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Rename Container", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it; errorMessage = null },
                label = { Text("Container name") },
                singleLine = true,
                isError = newName.isNotEmpty() && !isValid,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Must start with a letter or digit, and contain only letters, digits, underscores, dots, or hyphens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            errorMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = ArcaneRed)
                    Text(it, color = ArcaneRed, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { if (!submitting) onDismiss() }, enabled = !submitting) { Text("Cancel") }
                TextButton(
                    onClick = {
                        scope.launch {
                            submitting = true
                            errorMessage = null
                            val error = onRename(newName)
                            submitting = false
                            // On success the caller dismisses the sheet; on failure show the message.
                            if (error != null) errorMessage = error
                        }
                    },
                    enabled = isValid && hasChanged && !submitting,
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        }
    }
}
