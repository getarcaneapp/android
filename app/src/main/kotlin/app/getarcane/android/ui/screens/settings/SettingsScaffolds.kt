package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-bar + optional add-FAB scaffold for a settings list screen. Provides the content padding to
 * its [content]. Used by the admin list screens (Users, API Keys, Roles, Webhooks, registries).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListScaffold(
    title: String,
    onAdd: (() -> Unit)? = null,
    addContentDescription: String = "Add",
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { actions() },
            )
        },
        floatingActionButton = {
            if (onAdd != null) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = addContentDescription)
                }
            }
        },
        content = content,
    )
}

/** A simple single-button informational alert. Mirrors the iOS error `.alert(...)`. */
@Composable
fun InfoAlert(title: String, message: String, onDismiss: () -> Unit, confirmLabel: String = "OK") {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(confirmLabel) } },
    )
}

/** A two-button confirmation dialog. Mirrors the iOS `.confirmationDialog(...)`. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}
