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
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Generic per-category settings editor. Port of iOS `SettingsCategoryView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    environmentId: EnvironmentId,
    environmentName: String,
    onBack: () -> Unit,
) {
    val category = remember(categoryId) { systemSettingsCategories.firstOrNull { it.id == categoryId } }
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val canWrite = manager.currentUser?.hasPermission(Permission.Settings.WRITE) == true

    val settings = remember { mutableStateMapOf<String, String>() }
    var originalSettings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categoryId, environmentId.rawValue) {
        if (client == null) return@LaunchedEffect
        loading = true
        try {
            val dtos = client.settings.getSettings(environmentId)
            val map = dtos.associate { it.key to it.value }
            settings.clear(); settings.putAll(map)
            originalSettings = map
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = friendlyErrorMessage(e)
        } finally {
            loading = false
        }
    }

    val fields = category?.fields.orEmpty()
    val hasChanges = fields.any { settings[it.key] != originalSettings[it.key] }

    fun save() {
        val c = client ?: return
        val captured = manager.authenticatedClientScope() ?: return
        scope.launch {
            saving = true; error = null; savedMessage = null
            val changed = fields
                .mapNotNull { f -> (settings[f.key] ?: "").let { v -> if (v != originalSettings[f.key]) f.key to v else null } }
                .toMap()
            if (changed.isEmpty()) { saving = false; return@launch }
            val validationErrors = validateSettingChanges(fields, changed)
            if (validationErrors.isNotEmpty()) {
                error = validationErrors.joinToString("\n")
                saving = false
                return@launch
            }
            try {
                c.settings.updateSettings(updateSettingsFrom(changed), environmentId)
                if (!manager.isCurrent(captured)) return@launch
                originalSettings = originalSettings.toMutableMap().apply { putAll(changed) }
                savedMessage = "Settings saved"
                launch { delay(3000); savedMessage = null }
            } catch (e: CancellationException) {
                throw e
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
                title = { Text(category?.title ?: "Unsupported settings") },
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
            if (category == null) {
                FormErrorRow("This settings category is not supported by this app version.")
                return@Column
            }
            Text(
                "Editing $environmentName without changing the app's active environment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            category.sections.forEach { section ->
                SettingsSectionHeader(section.title)
                section.fields.filter { field ->
                    field.visibleWhen?.let { settings[it.key] == it.value } ?: true
                }.forEach { field -> SettingRow(field, settings) }
            }

            if (hasChanges) {
                Button(
                    onClick = { save() },
                    enabled = !saving && canWrite,
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
                    onClick = { fields.forEach { settings[it.key] = originalSettings[it.key] ?: "" } },
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
        is SettingFieldType.Cron -> {
            OutlinedTextField(
                value = value,
                onValueChange = { settings[field.key] = it },
                label = { Text(field.label) },
                supportingText = { Text("Cron expression") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        is SettingFieldType.TextArea -> {
            OutlinedTextField(
                value = value,
                onValueChange = { settings[field.key] = it },
                label = { Text(field.label) },
                supportingText = field.description?.let { description -> ({ Text(description) }) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

internal fun validateSettingChanges(
    fields: List<SettingFieldDef>,
    changed: Map<String, String>,
): List<String> = buildList {
    fields.forEach { field ->
        val value = changed[field.key] ?: return@forEach
        when (val type = field.type) {
            is SettingFieldType.Number -> {
                val number = value.toIntOrNull()
                if (number == null) {
                    add("${field.label} must be a whole number.")
                } else if ((field.minValue != null && number < field.minValue) ||
                    (field.maxValue != null && number > field.maxValue)
                ) {
                    add("${field.label} must be between ${field.minValue ?: "the minimum"} and ${field.maxValue ?: "the maximum"}.")
                }
            }
            is SettingFieldType.Select -> if (value !in type.options) {
                add("${field.label} has an unsupported value.")
            }
            else -> Unit
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
