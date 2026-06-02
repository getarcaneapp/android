package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.role.UserAssignmentInput
import app.getarcane.sdk.models.user.CreateUser
import kotlinx.coroutines.launch

/** Create-user sheet. Port of iOS `CreateUserView`, presented as a full-screen dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun create() {
        val c = client ?: return
        scope.launch {
            loading = true
            error = null
            try {
                val created = c.users.create(
                    CreateUser(
                        username = username,
                        password = password,
                        displayName = null,
                        email = email.ifEmpty { null },
                        roles = if (supportsV2) null else (if (isAdmin) listOf("admin") else listOf("user")),
                    ),
                )
                if (supportsV2 && isAdmin) {
                    try {
                        c.users.setRoleAssignments(created.id, listOf(UserAssignmentInput(roleId = Role.BuiltIn.ADMIN)))
                    } catch (e: Throwable) {
                        error = "User created, but admin role could not be assigned: ${friendlyErrorMessage(e)}"
                        onCreated()
                        return@launch
                    }
                }
                onCreated()
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create User") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        TextButton(
                            onClick = { create() },
                            enabled = username.isNotEmpty() && password.isNotEmpty() && !loading,
                        ) { Text("Create") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                FormSectionHeader("Credentials")
                LabeledTextField("Username", username, { username = it })
                LabeledTextField("Password", password, { password = it }, isPassword = true)

                FormSectionHeader("Profile")
                LabeledTextField("Email (optional)", email, { email = it }, keyboardType = KeyboardType.Email)

                FormSectionHeader("Roles")
                LabeledToggle("Administrator", isAdmin, { isAdmin = it })

                error?.let { FormErrorRow(it) }
            }
        }
    }
}
