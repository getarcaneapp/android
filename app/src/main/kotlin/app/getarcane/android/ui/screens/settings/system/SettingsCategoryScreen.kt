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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import app.getarcane.android.ui.screens.settings.LabeledToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Generic per-category settings editor. Port of iOS `SettingsCategoryView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(categoryId: String, onBack: () -> Unit) {
    val category = remember(categoryId) { systemSettingsCategories.first { it.id == categoryId } }
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    val settings = remember { mutableStateMapOf<String, String>() }
    var originalSettings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categoryId, envId.rawValue) {
        if (client == null) return@LaunchedEffect
        loading = true
        try {
            val dtos = client.settings.getSettings(envId)
            val map = dtos.associate { it.key to it.value }
            settings.clear(); settings.putAll(map)
            originalSettings = map
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        } finally {
            loading = false
        }
    }

    val hasChanges = category.fields.any { settings[it.key] != originalSettings[it.key] }

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null; savedMessage = null
            val changed = category.fields
                .mapNotNull { f -> (settings[f.key] ?: "").let { v -> if (v != originalSettings[f.key]) f.key to v else null } }
                .toMap()
            if (changed.isEmpty()) { saving = false; return@launch }
            try {
                c.settings.updateSettings(updateSettingsFrom(changed), envId)
                originalSettings = originalSettings.toMutableMap().apply { putAll(changed) }
                savedMessage = "Settings saved"
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
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading && settings.isEmpty()) {
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
            category.fields.forEach { field -> SettingRow(field, settings) }

            if (hasChanges) {
                Button(
                    onClick = { save() },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.CheckCircle, null)
                        Text("  Save Changes")
                    }
                }
                OutlinedButton(
                    onClick = { category.fields.forEach { settings[it.key] = originalSettings[it.key] ?: "" } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Discard Changes") }
            }

            error?.let { FormErrorRow(it) }
            savedMessage?.let { FormSuccessRow(it) }
        }
    }
}

@Composable
private fun SettingRow(field: SettingFieldDef, settings: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>) {
    val value = settings[field.key] ?: ""
    when (val type = field.type) {
        is SettingFieldType.Boolean -> LabeledToggle(field.label, value.equals("true", ignoreCase = true), { settings[field.key] = it.toString() })
        is SettingFieldType.Number -> NumberRow(field.label, value) { settings[field.key] = it }
        is SettingFieldType.Password -> {
            OutlinedTextField(
                value = value,
                onValueChange = { settings[field.key] = it },
                label = { Text(field.label) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        is SettingFieldType.Select -> {
            val current = if (type.options.contains(value)) value else (type.options.firstOrNull() ?: "")
            LabeledPicker(
                label = field.label,
                selected = current,
                options = type.options,
                optionLabel = { it },
                onSelect = { settings[field.key] = it },
            )
        }
        is SettingFieldType.Text -> {
            OutlinedTextField(
                value = value,
                onValueChange = { settings[field.key] = it },
                label = { Text(field.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
