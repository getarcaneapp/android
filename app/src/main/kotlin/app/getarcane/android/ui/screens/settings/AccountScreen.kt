package app.getarcane.android.ui.screens.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ClearSensitiveStateOnStop
import app.getarcane.android.ui.components.ProtectSensitiveWindow
import app.getarcane.android.ui.components.SensitiveOutlinedField
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.user.isAdmin
import coil.compose.AsyncImage
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Signed-in self-service account area, intentionally separate from administrator user management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenSecurity: (() -> Unit)? = null,
) {
    val manager = LocalArcaneManager.current
    val user = manager.currentUser
    val session = manager.authenticatedClientScope()
    val scope = rememberCoroutineScope()

    var displayName by remember(user?.id) { mutableStateOf(user?.displayName.orEmpty()) }
    var email by remember(user?.id) { mutableStateOf(user?.email.orEmpty()) }
    var password by remember(user?.id) { mutableStateOf(AccountPasswordDraft()) }
    var saving by remember(user?.id) { mutableStateOf(false) }
    var errorMessage by remember(user?.id) { mutableStateOf<String?>(null) }
    var successMessage by remember(user?.id) { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<AccountExitAction?>(null) }
    var avatarBytes by remember(user?.id) { mutableStateOf<ByteArray?>(null) }
    var gravatarUrl by remember(user?.id) { mutableStateOf<String?>(null) }

    ClearSensitiveStateOnStop { password = AccountPasswordDraft() }
    ProtectSensitiveWindow(active = password.hasInput)

    LaunchedEffect(user?.updatedAt, user?.displayName, user?.email) {
        val activeUser = user ?: return@LaunchedEffect
        displayName = activeUser.displayName.orEmpty()
        email = activeUser.email.orEmpty()
    }

    LaunchedEffect(session, user?.updatedAt, manager.activeEnvironmentId) {
        val captured = session ?: return@LaunchedEffect
        val activeUser = user ?: return@LaunchedEffect
        avatarBytes = null
        gravatarUrl = null
        val customAvatar = try {
            captured.client.users.getAvatar(activeUser.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (!manager.isCurrent(captured)) return@LaunchedEffect
        if (customAvatar != null) {
            avatarBytes = customAvatar
            return@LaunchedEffect
        }
        val normalizedEmail = activeUser.email?.trim()?.lowercase().orEmpty()
        if (normalizedEmail.isEmpty()) return@LaunchedEffect
        val gravatarEnabled = try {
            captured.client.settings.getSettings(manager.activeEnvironmentId)
                .firstOrNull { it.key == "enableGravatar" }
                ?.value
                ?.equals("true", ignoreCase = true) == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        if (gravatarEnabled && manager.isCurrent(captured)) {
            gravatarUrl = "https://www.gravatar.com/avatar/${sha256(normalizedEmail)}?s=160&d=404"
        }
    }

    if (user == null || session == null) {
        Scaffold(topBar = { AccountTopBar(onBack, false, {}, {}) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No signed-in account")
            }
        }
        return
    }

    val isOidcUser = !user.oidcSubjectId.isNullOrBlank()
    val plan = accountSavePlan(user, displayName, email, password)

    Scaffold(
        topBar = {
            AccountTopBar(
                onBack = onBack,
                saving = saving,
                onSave = {
                    if (!plan.canSave) return@AccountTopBar
                    scope.launch {
                        saving = true
                        errorMessage = null
                        successMessage = null
                        when (
                            val outcome = saveAccountChanges(
                                plan = plan,
                                updateProfile = { update -> session.client.auth.updateProfile(update) },
                                publishProfile = { updated -> manager.acceptCurrentUserUpdate(session, updated) },
                                changePassword = { current, new ->
                                    session.client.auth.changePassword(current, new)
                                },
                            )
                        ) {
                            is AccountSaveOutcome.Success -> {
                                if (manager.isCurrent(session)) {
                                    if (outcome.passwordChanged) password = AccountPasswordDraft()
                                    successMessage = "Account updated"
                                }
                            }
                            is AccountSaveOutcome.Failure -> {
                                if (manager.isCurrent(session)) {
                                    errorMessage = friendlyErrorMessage(outcome.cause)
                                }
                            }
                        }
                        if (manager.isCurrent(session)) saving = false
                    }
                },
                canSave = plan.canSave,
                onMenu = { menuExpanded = true },
            )
            Box {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Sign Out") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        onClick = { menuExpanded = false; confirmation = AccountExitAction.SIGN_OUT },
                    )
                    DropdownMenuItem(
                        text = { Text("Sign Out & Change Server") },
                        leadingIcon = { Icon(Icons.Filled.Link, null) },
                        onClick = { menuExpanded = false; confirmation = AccountExitAction.CHANGE_SERVER },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AccountIdentity(user.displayUsername, user.username, user.isAdmin, isOidcUser, avatarBytes, gravatarUrl)

            SettingsSectionHeader("Profile")
            if (isOidcUser) {
                AccountValueRow("Display Name", user.displayName)
                AccountValueRow("Email", user.email)
                SettingsSectionFooter("Your profile is managed by your identity provider.")
            } else {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; successMessage = null },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; successMessage = null },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = plan.emailError != null,
                    supportingText = plan.emailError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )

                SettingsSectionHeader("Change Password")
                PasswordField("Current password", password.current) { password = password.copy(current = it) }
                PasswordField("New password", password.new) { password = password.copy(new = it) }
                PasswordField("Confirm new password", password.confirmation) {
                    password = password.copy(confirmation = it)
                }
                SettingsSectionFooter(plan.passwordError ?: "Leave blank to keep your current password.")
            }

            if (manager.supportsPost26MobileFeatures && onOpenSecurity != null) {
                SettingsSectionHeader("Security")
                SettingsRow(
                    title = "Passkeys & MFA",
                    icon = Icons.Filled.VpnKey,
                    iconColor = ArcaneTeal,
                    onClick = onOpenSecurity,
                    trailing = { ChevronTrailing() },
                )
            }

            SettingsSectionHeader("Language")
            AccountValueRow("Language", "English")
            SettingsSectionFooter("Language selection is coming in a future update.")

            errorMessage?.let { FormErrorRow(it) }
            successMessage?.let { FormSuccessRow(it) }
        }
    }

    confirmation?.let { action ->
        ConfirmDialog(
            title = if (action == AccountExitAction.SIGN_OUT) "Sign Out" else "Change Server",
            message = if (action == AccountExitAction.SIGN_OUT) {
                "You'll be signed out of this server."
            } else {
                "You'll be signed out and asked for a new server URL."
            },
            confirmLabel = if (action == AccountExitAction.SIGN_OUT) "Sign Out" else "Sign Out & Change Server",
            onConfirm = {
                if (action == AccountExitAction.SIGN_OUT) manager.logout() else manager.changeServer()
            },
            onDismiss = { confirmation = null },
        )
    }
}

private enum class AccountExitAction { SIGN_OUT, CHANGE_SERVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTopBar(
    onBack: () -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
    onMenu: () -> Unit,
    canSave: Boolean = false,
) {
    TopAppBar(
        title = { Text("Account") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (saving) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Button(onClick = onSave, enabled = canSave) { Text("Save") }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Sign out options", tint = ArcaneRed)
            }
        },
    )
}

@Composable
private fun AccountIdentity(
    displayName: String,
    username: String,
    isAdmin: Boolean,
    isOidcUser: Boolean,
    avatarBytes: ByteArray?,
    gravatarUrl: String?,
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountAvatar(displayName, avatarBytes, gravatarUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("@$username", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isAdmin) Pill("Admin", ArcaneIndigo, filled = true)
                if (isOidcUser) Pill("SSO", ArcaneTeal, filled = true)
            }
        }
    }
}

@Composable
private fun AccountAvatar(displayName: String, avatarBytes: ByteArray?, gravatarUrl: String?) {
    val bitmap = remember(avatarBytes) {
        avatarBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    val modifier = Modifier.size(56.dp).clip(CircleShape)
    when {
        bitmap != null -> Image(bitmap, contentDescription = "Profile picture", modifier, contentScale = ContentScale.Crop)
        gravatarUrl != null -> AsyncImage(
            model = gravatarUrl,
            contentDescription = "Profile picture",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        else -> Box(modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(accountInitials(displayName), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AccountValueRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value?.takeIf(String::isNotBlank) ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    SensitiveOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

internal fun accountInitials(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifEmpty { "?" }

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
