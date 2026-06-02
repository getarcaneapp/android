package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.role.Role
import app.getarcane.sdk.models.role.UserAssignmentInput
import app.getarcane.sdk.models.user.UpdateUser
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

/** Edit a user (email, display name, admin toggle) + link to role assignments. Port of iOS `UserDetailView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onEditRoleAssignments: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC

    var state by remember { mutableStateOf<Loadable<User>>(Loadable.Loading) }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (client == null) return@LaunchedEffect
        state = try {
            val user = client.users.get(userId)
            email = user.email ?: ""
            displayName = user.displayName ?: ""
            isAdmin = user.isAdmin
            Loadable.Success(user)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    val user = (state as? Loadable.Success)?.value
    val hasChanges = user != null && (
        email != (user.email ?: "") ||
            displayName != (user.displayName ?: "") ||
            isAdmin != user.isAdmin
        )

    fun save() {
        val u = user ?: return
        val c = client ?: return
        scope.launch {
            saving = true
            error = null
            try {
                c.users.update(
                    u.id,
                    UpdateUser(
                        displayName = displayName.ifEmpty { null },
                        email = email.ifEmpty { null },
                        roles = if (supportsV2) null else (if (isAdmin) listOf("admin") else emptyList()),
                    ),
                )
                if (supportsV2 && isAdmin != u.isAdmin) {
                    val assignments = if (isAdmin) listOf(UserAssignmentInput(roleId = Role.BuiltIn.ADMIN)) else emptyList()
                    c.users.setRoleAssignments(u.id, assignments)
                }
                onBack()
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.displayUsername ?: "User") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = !saving && hasChanges) { Text("Save") }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is Loadable.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is Loadable.Error -> FormErrorRow(s.message, Modifier.padding(padding))
            is Loadable.Success -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FormSectionHeader("User Info")
                    Row(label = "Username", value = s.value.displayUsername)
                    LabeledTextField("Email", email, { email = it }, keyboardType = KeyboardType.Email)
                    LabeledTextField("Display Name", displayName, { displayName = it })

                    FormSectionHeader("Roles")
                    LabeledToggle("Administrator", isAdmin, { isAdmin = it })
                    if (supportsV2) {
                        SettingsRow(
                            title = "Edit Role Assignments",
                            icon = Icons.Filled.PeopleAlt,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onEditRoleAssignments(s.value.id) },
                            trailing = { ChevronTrailing() },
                        )
                        SettingsSectionFooter("For per-environment roles or custom roles, use Edit Role Assignments.")
                    }

                    error?.let { FormErrorRow(it) }
                }
            }
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
