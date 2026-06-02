package app.getarcane.android.ui.screens.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.FormSuccessRow
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.sdk.models.settings.UpdateSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Local-auth + OIDC settings editor. Port of iOS `AuthenticationSettingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationSettingsScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var authLocalEnabled by remember { mutableStateOf(true) }
    var authSessionTimeout by remember { mutableStateOf("1440") }
    var authPasswordPolicy by remember { mutableStateOf("strong") }

    var oidcEnabled by remember { mutableStateOf(false) }
    var oidcProviderName by remember { mutableStateOf("") }
    var oidcProviderLogoUrl by remember { mutableStateOf("") }
    var oidcIssuerUrl by remember { mutableStateOf("") }
    var oidcClientId by remember { mutableStateOf("") }
    var oidcClientSecret by remember { mutableStateOf("") }
    var oidcScopes by remember { mutableStateOf("openid email profile") }
    var oidcAdminClaim by remember { mutableStateOf("") }
    var oidcAdminValue by remember { mutableStateOf("") }
    var oidcSkipTlsVerify by remember { mutableStateOf(false) }
    var oidcAutoRedirect by remember { mutableStateOf(false) }
    var oidcMergeAccounts by remember { mutableStateOf(false) }

    var oidcEnvForced by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(envId.rawValue) {
        if (client == null) return@LaunchedEffect
        loading = true
        try {
            val dict = client.settings.getSettings(envId).associate { it.key to it.value }
            authLocalEnabled = dict["authLocalEnabled"]?.equals("true", true) == true
            authSessionTimeout = dict["authSessionTimeout"] ?: "1440"
            authPasswordPolicy = dict["authPasswordPolicy"] ?: "strong"
            oidcEnabled = dict["oidcEnabled"]?.equals("true", true) == true
            oidcProviderName = dict["oidcProviderName"] ?: ""
            oidcIssuerUrl = dict["oidcIssuerUrl"] ?: ""
            oidcClientId = dict["oidcClientId"] ?: ""
            oidcClientSecret = dict["oidcClientSecret"] ?: ""
            oidcScopes = dict["oidcScopes"] ?: "openid email profile"
            oidcAdminClaim = dict["oidcAdminClaim"] ?: ""
            oidcAdminValue = dict["oidcAdminValue"] ?: ""
            oidcSkipTlsVerify = dict["oidcSkipTlsVerify"]?.equals("true", true) == true
            oidcAutoRedirect = dict["oidcAutoRedirectToProvider"]?.equals("true", true) == true
            oidcMergeAccounts = dict["oidcMergeAccounts"]?.equals("true", true) == true
            oidcProviderLogoUrl = dict["oidcProviderLogoUrl"] ?: ""
            // The manager already tracks OIDC env-forced status.
            oidcEnvForced = manager.oidc?.envForced ?: false
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        } finally {
            loading = false
        }
    }

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null; savedMessage = null
            try {
                val body = UpdateSettings(
                    authLocalEnabled = authLocalEnabled.toString(),
                    authPasswordPolicy = authPasswordPolicy,
                    authSessionTimeout = authSessionTimeout,
                    oidcEnabled = oidcEnabled.toString(),
                    oidcMergeAccounts = oidcMergeAccounts.toString(),
                    oidcSkipTlsVerify = oidcSkipTlsVerify.toString(),
                    oidcAutoRedirectToProvider = oidcAutoRedirect.toString(),
                    oidcClientId = oidcClientId.ifEmpty { null },
                    oidcClientSecret = oidcClientSecret.ifEmpty { null },
                    oidcIssuerUrl = oidcIssuerUrl.ifEmpty { null },
                    oidcScopes = oidcScopes.ifEmpty { null },
                    oidcAdminClaim = oidcAdminClaim.ifEmpty { null },
                    oidcAdminValue = oidcAdminValue.ifEmpty { null },
                    oidcProviderName = oidcProviderName.ifEmpty { null },
                    oidcProviderLogoUrl = oidcProviderLogoUrl.ifEmpty { null },
                )
                c.settings.updateSettings(body, envId)
                savedMessage = "Authentication settings saved"
                launch { delay(3000); savedMessage = null }
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
                title = { Text("Authentication") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsSectionHeader("Local Authentication")
            LabeledToggle("Local Auth Enabled", authLocalEnabled, { authLocalEnabled = it })

            SettingsSectionHeader("Session")
            NumberSettingRow("Session Timeout (min)", authSessionTimeout) { authSessionTimeout = it }
            LabeledPicker(
                label = "Password Policy",
                selected = authPasswordPolicy,
                options = listOf("basic", "standard", "strong"),
                optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                onSelect = { authPasswordPolicy = it },
            )

            if (oidcEnvForced) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Lock, null, tint = ArcaneOrange)
                    Column {
                        Text("Configured by Environment Variables", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "OIDC settings are managed via OS environment variables on the server and cannot be modified here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSectionHeader("OIDC Provider")
            LabeledToggle("OIDC Enabled", oidcEnabled, { oidcEnabled = it }, enabled = !oidcEnvForced)
            LabeledTextField("Provider Name", oidcProviderName, { oidcProviderName = it })
            LabeledTextField("Provider Logo URL", oidcProviderLogoUrl, { oidcProviderLogoUrl = it }, keyboardType = KeyboardType.Uri)
            LabeledTextField("Issuer URL", oidcIssuerUrl, { oidcIssuerUrl = it }, keyboardType = KeyboardType.Uri)
            LabeledTextField("Client ID", oidcClientId, { oidcClientId = it })
            LabeledTextField("Client Secret", oidcClientSecret, { oidcClientSecret = it }, isPassword = true)
            LabeledTextField("Scopes", oidcScopes, { oidcScopes = it })

            SettingsSectionHeader("OIDC Options")
            LabeledTextField("Admin Claim", oidcAdminClaim, { oidcAdminClaim = it })
            LabeledTextField("Admin Value", oidcAdminValue, { oidcAdminValue = it })
            LabeledToggle("Skip TLS Verify", oidcSkipTlsVerify, { oidcSkipTlsVerify = it }, enabled = !oidcEnvForced)
            LabeledToggle("Auto-Redirect to Provider", oidcAutoRedirect, { oidcAutoRedirect = it }, enabled = !oidcEnvForced)
            LabeledToggle("Merge Accounts", oidcMergeAccounts, { oidcMergeAccounts = it }, enabled = !oidcEnvForced)

            error?.let { FormErrorRow(it) }
            savedMessage?.let { FormSuccessRow(it) }

            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
            }
        }
    }
}

@Composable
private fun NumberSettingRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            modifier = Modifier.width(120.dp),
        )
    }
}
