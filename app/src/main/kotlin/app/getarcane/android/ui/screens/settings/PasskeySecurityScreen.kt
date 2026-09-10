package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.PasskeyLoginState
import app.getarcane.android.core.PasskeySecurityOwner
import app.getarcane.android.core.PasskeySecurityResult
import app.getarcane.android.ui.components.ClearSensitiveStateOnStop
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ProtectSensitiveWindow
import app.getarcane.android.ui.components.SensitiveOutlinedField
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.models.auth.MFAStatus
import app.getarcane.sdk.models.auth.PasskeyCapabilities
import app.getarcane.sdk.models.auth.PasskeySummary
import app.getarcane.sdk.models.auth.StepUpGrant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Self-service passkey and MFA management. Administrator user management remains separate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeySecurityScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val session = manager.authenticatedClientScope()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operationGeneration = remember { SensitiveOperationGeneration() }
    val passkeyOwner = remember { PasskeySecurityOwner() }
    var refresh by remember { mutableIntStateOf(0) }
    var mutationJob by remember { mutableStateOf<Job?>(null) }
    var loading by remember(session) { mutableStateOf(true) }
    var busy by remember(session) { mutableStateOf(false) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var passkeys by remember(session) { mutableStateOf<List<PasskeySummary>>(emptyList()) }
    var capabilities by remember(session) { mutableStateOf<PasskeyCapabilities?>(null) }
    var mfa by remember(session) { mutableStateOf<MFAStatus?>(null) }
    var stepUpGrant by remember(session) { mutableStateOf<StepUpGrant?>(null) }
    var stepUpPassword by remember(session) { mutableStateOf("") }
    var newPasskeyName by remember(session) { mutableStateOf("") }
    var renameTarget by remember(session) { mutableStateOf<PasskeySummary?>(null) }
    var renameValue by remember(session) { mutableStateOf("") }
    var deleteTarget by remember(session) { mutableStateOf<PasskeySummary?>(null) }
    var recoveryCodes by remember(session) { mutableStateOf<List<String>>(emptyList()) }
    val validGrant = validStepUpGrant(stepUpGrant, Clock.System.now())
    val anyBusy = busy || manager.passkeyBrowserInProgress
    val bridgeAvailable = manager.passkeyBridgeState == PasskeyLoginState.AVAILABLE

    fun invalidateSensitiveOperations() {
        operationGeneration.invalidate()
        mutationJob?.cancel()
        mutationJob = null
        busy = false
        stepUpGrant = null
        stepUpPassword = ""
        recoveryCodes = emptyList()
    }

    ClearSensitiveStateOnStop(::invalidateSensitiveOperations)
    DisposableEffect(session) {
        onDispose {
            invalidateSensitiveOperations()
            manager.cancelPasskeySecurityOperation(passkeyOwner)
        }
    }
    ProtectSensitiveWindow(active = stepUpPassword.isNotEmpty() || recoveryCodes.isNotEmpty())

    fun <T> completeMutation(
        block: suspend (ArcaneClient) -> T,
        onSuccess: (T) -> Unit = {},
    ) {
        val captured = session ?: return
        mutationJob?.cancel()
        val operation = operationGeneration.begin()
        busy = true
        error = null
        mutationJob = scope.launch {
            try {
                when (val result = runSensitiveMutation { block(captured.client) }) {
                    is SensitiveMutationResult.Success -> if (
                        operationGeneration.isCurrent(operation) && manager.isCurrent(captured)
                    ) {
                        onSuccess(result.value)
                        refresh++
                    }
                    SensitiveMutationResult.Cancelled -> Unit
                    is SensitiveMutationResult.Failure -> if (
                        operationGeneration.isCurrent(operation) && manager.isCurrent(captured)
                    ) {
                        error = friendlyErrorMessage(result.cause)
                    }
                }
            } finally {
                if (operationGeneration.isCurrent(operation) && manager.isCurrent(captured)) {
                    mutationJob = null
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(stepUpGrant?.expiresAt) {
        val captured = stepUpGrant ?: return@LaunchedEffect
        delay(stepUpExpiryDelayMillis(captured, Clock.System.now()))
        if (stepUpGrant == captured) stepUpGrant = null
    }

    LaunchedEffect(manager.passkeySecurityEvent?.id, session) {
        val event = manager.passkeySecurityEvent ?: return@LaunchedEffect
        if (event.owner !== passkeyOwner) return@LaunchedEffect
        when (val result = event.result) {
            is PasskeySecurityResult.Registration -> {
                newPasskeyName = ""
                refresh++
            }
            is PasskeySecurityResult.StepUp -> stepUpGrant = result.grant
            is PasskeySecurityResult.Failure -> error = result.message
            PasskeySecurityResult.Cancelled -> Unit
        }
        manager.consumePasskeySecurityEvent(event.id)
    }

    LaunchedEffect(session, refresh) {
        val captured = session ?: return@LaunchedEffect
        manager.refreshPasskeySupport()
        loading = true
        error = null
        val result = runSensitiveMutation {
            val loadedPasskeys = captured.client.passkeys.list()
            val loadedCapabilities = captured.client.passkeys.capabilities()
            val loadedMfa = captured.client.passkeys.mfaStatus()
            if (manager.isCurrent(captured)) {
                passkeys = loadedPasskeys
                capabilities = loadedCapabilities
                mfa = loadedMfa
            }
        }
        if (manager.isCurrent(captured)) {
            if (result is SensitiveMutationResult.Failure) error = friendlyErrorMessage(result.cause)
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passkeys & MFA") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (session == null) {
                Text("No signed-in account", Modifier.padding(16.dp))
                return@Column
            }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
                return@Column
            }
            error?.let { FormErrorRow(it) }

            SettingsSectionHeader("Step-up authorization")
            Text(
                if (validGrant == null) "Authorize sensitive account changes before continuing."
                else "Sensitive account changes are authorized for this session.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (validGrant == null) {
                if (capabilities?.hasLocalPassword == true) {
                    SensitiveOutlinedField(
                        value = stepUpPassword,
                        onValueChange = { stepUpPassword = it },
                        label = "Current password",
                        enabled = !busy,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Button(
                        enabled = stepUpPassword.isNotBlank() && !anyBusy,
                        onClick = {
                            val submitted = stepUpPassword
                            stepUpPassword = ""
                            completeMutation(
                                block = { client -> client.passkeys.passwordStepUp(submitted) },
                                onSuccess = { stepUpGrant = it },
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Authorize with password") }
                }
                if (passkeys.isNotEmpty()) {
                    OutlinedButton(
                        enabled = bridgeAvailable && !anyBusy,
                        onClick = {
                            error = null
                            manager.beginPasskeyStepUp(context, passkeyOwner)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Filled.VpnKey, null)
                        Text(" Authorize with passkey")
                    }
                }
            }

            SettingsSectionHeader("Passkeys")
            when (manager.passkeyBridgeState) {
                PasskeyLoginState.LOADING -> Text(
                    "Checking passkey support…",
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasskeyLoginState.UNAVAILABLE, PasskeyLoginState.ERROR -> Text(
                    "Passkey ceremonies are unavailable for this server connection. Password recovery remains available.",
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasskeyLoginState.AVAILABLE -> Unit
            }
            if (passkeys.isEmpty()) {
                Text("No passkeys enrolled", Modifier.padding(horizontal = 16.dp))
            }
            passkeys.forEach { passkey ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(passkey.name)
                        Text(passkey.rpId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { renameTarget = passkey; renameValue = passkey.name },
                        enabled = !anyBusy && validGrant != null,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename ${passkey.name}")
                    }
                    IconButton(
                        onClick = { deleteTarget = passkey },
                        enabled = !anyBusy && capabilities?.let {
                            canDeletePasskey(passkeys.size, it.canDeleteLastPasskey, validGrant != null)
                        } == true,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${passkey.name}")
                    }
                }
            }
            OutlinedTextField(
                value = newPasskeyName,
                onValueChange = { newPasskeyName = it },
                label = { Text("New passkey name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Button(
                enabled = capabilities?.let {
                    canBeginPasskeyEnrollment(
                        passkeyCount = it.passkeyCount,
                        canEnrollWithActiveSession = it.canEnrollWithActiveSession,
                        requiresStepUp = it.requiresStepUp,
                        hasStepUpGrant = validGrant != null,
                    )
                } == true && bridgeAvailable && !anyBusy,
                onClick = {
                    val name = newPasskeyName.trim().takeIf(String::isNotEmpty)
                    error = null
                    manager.beginPasskeyRegistration(
                        context,
                        name,
                        validGrant?.token,
                        passkeyOwner,
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Add passkey") }

            SettingsSectionHeader("Multi-factor authentication")
            Text(
                if (mfa?.enabled == true) {
                    "Enabled · ${mfa?.recoveryCodesRemaining ?: 0} recovery codes remaining"
                } else {
                    "Disabled"
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (mfa?.enabled == true) {
                OutlinedButton(
                    enabled = validGrant != null && !anyBusy,
                    onClick = {
                        val token = validGrant?.token ?: return@OutlinedButton
                        completeMutation(
                            block = { client -> client.passkeys.regenerateRecoveryCodes(token).codes },
                            onSuccess = { recoveryCodes = it },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Regenerate recovery codes") }
                TextButton(
                    enabled = validGrant != null && !anyBusy,
                    onClick = {
                        val token = validGrant?.token ?: return@TextButton
                        completeMutation(
                            block = { client -> client.passkeys.disableMfa(token) },
                            onSuccess = {
                                recoveryCodes = recoveryCodesAfterMfaState(recoveryCodes, enabled = false)
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Disable MFA") }
            } else {
                Button(
                    enabled = passkeys.isNotEmpty() && validGrant != null && !anyBusy,
                    onClick = {
                        val token = validGrant?.token ?: return@Button
                        completeMutation(
                            block = { client -> client.passkeys.enableMfa(token).codes },
                            onSuccess = { recoveryCodes = it },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Enable MFA") }
            }

            if (recoveryCodes.isNotEmpty()) {
                SettingsSectionHeader("Recovery codes")
                Text(
                    "Store these one-time codes safely. They will not be shown again.",
                    Modifier.padding(horizontal = 16.dp),
                )
                recoveryCodes.forEach { code ->
                    Text(
                        code,
                        modifier = Modifier.padding(horizontal = 16.dp).clearAndSetSemantics { },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                TextButton(
                    onClick = { recoveryCodes = emptyList() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("I've stored these codes") }
            }
            if (manager.passkeyBrowserInProgress) {
                TextButton(
                    onClick = manager::cancelPasskeyBrowserOperation,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Cancel passkey request") }
            }
        }
    }

    renameTarget?.let { passkey ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameTarget = null; renameValue = "" },
            title = { Text("Rename passkey") },
            text = {
                OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank() && validGrant != null && !anyBusy,
                    onClick = {
                        val token = validGrant?.token ?: return@TextButton
                        val name = renameValue.trim()
                        renameTarget = null
                        renameValue = ""
                        completeMutation(block = { client -> client.passkeys.rename(passkey.id, name, token) })
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null; renameValue = "" }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { passkey ->
        ConfirmDialog(
            title = "Delete passkey",
            message = "Delete ${passkey.name} from this account?",
            confirmLabel = "Delete",
            onConfirm = {
                val token = validGrant?.token
                val deleteAllowed = capabilities?.let {
                    canDeletePasskey(passkeys.size, it.canDeleteLastPasskey, token != null)
                } == true
                deleteTarget = null
                if (token != null && deleteAllowed) {
                    completeMutation(block = { client -> client.passkeys.delete(passkey.id, token) })
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
