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
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.sdk.models.settings.UpdateSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Build-provider settings editor (local / Depot). Port of iOS `BuildSettingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSettingsScreen(onBack: (() -> Unit)? = null) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var buildProvider by remember { mutableStateOf("local") }
    var buildTimeout by remember { mutableStateOf("1800") }
    var buildsDirectory by remember { mutableStateOf("") }
    var depotProjectId by remember { mutableStateOf("") }
    var depotToken by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(envId.rawValue) {
        if (client == null) return@LaunchedEffect
        loading = true
        try {
            val dict = client.settings.getSettings(envId).associate { it.key to it.value }
            buildProvider = dict["buildProvider"] ?: "local"
            buildTimeout = dict["buildTimeout"] ?: "1800"
            buildsDirectory = dict["buildsDirectory"] ?: ""
            depotProjectId = dict["depotProjectId"] ?: ""
            depotToken = dict["depotToken"] ?: ""
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
                    buildProvider = buildProvider,
                    buildsDirectory = buildsDirectory.ifEmpty { null },
                    buildTimeout = buildTimeout,
                    depotProjectId = if (buildProvider == "depot") depotProjectId.ifEmpty { null } else null,
                    depotToken = if (buildProvider == "depot") depotToken.ifEmpty { null } else null,
                )
                c.settings.updateSettings(body, envId)
                savedMessage = "Build settings saved"
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
                title = { Text("Builds") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            SettingsSectionHeader("Provider")
            LabeledPicker(
                label = "Build Provider",
                selected = buildProvider,
                options = listOf("local", "depot"),
                optionLabel = { if (it == "local") "Local" else "Depot" },
                onSelect = { buildProvider = it },
            )

            SettingsSectionHeader("Configuration")
            NumberSettingRow("Build Timeout (s)", buildTimeout) { buildTimeout = it }
            LabeledTextField("Builds Directory", buildsDirectory, { buildsDirectory = it }, placeholder = "/path/to/builds")

            if (buildProvider == "depot") {
                SettingsSectionHeader("Depot Credentials")
                LabeledTextField("Project ID", depotProjectId, { depotProjectId = it })
                LabeledTextField("Depot Token", depotToken, { depotToken = it }, isPassword = true)
            }

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
