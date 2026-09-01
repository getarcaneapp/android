package app.getarcane.android.ui.screens.settings.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.screens.settings.FormSectionFooter
import app.getarcane.android.ui.screens.settings.FormSectionHeader
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.LabeledToggle
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.notification.NotificationProvider
import app.getarcane.sdk.models.notification.UpdateNotificationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Dynamic notification-provider form (config + events + save + test). Port of iOS `NotificationProviderFormView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationProviderFormScreen(provider: NotificationProvider, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    val supportsPost26Features = manager.supportsPost26MobileFeatures
    val fields = remember(provider, supportsPost26Features) {
        fieldsForProvider(provider, supportsPost26Features)
    }

    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    val formValues = remember { mutableStateMapOf<String, String>() }
    var enabled by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(EventSubscriptions()) }
    var isEditing by remember { mutableStateOf(false) }

    // Snapshots for dirty-state comparison.
    var originalValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var originalEnabled by remember { mutableStateOf(false) }
    var originalEvents by remember { mutableStateOf(EventSubscriptions()) }
    var originalConfig by remember { mutableStateOf<Map<String, JsonValue>>(emptyMap()) }
    var preservedCredentials by remember { mutableStateOf<Set<String>>(emptySet()) }

    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider, envId.rawValue) {
        // Seed defaults.
        formValues.clear()
        enabled = false
        events = EventSubscriptions()
        isEditing = false
        originalConfig = emptyMap()
        preservedCredentials = emptySet()
        loadFailed = false
        error = null
        fields.forEach { if (formValues[it.key] == null) formValues[it.key] = it.defaultValue }
        if (client != null) {
            val existing = try {
                client.notifications.listSettings(envId).firstOrNull { it.provider == provider }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
                loadFailed = true
                null
            }
            if (existing != null) {
                isEditing = true
                enabled = existing.enabled
                originalConfig = existing.config
                preservedCredentials = preservedCredentialKeys(existing.config, provider)
                val extracted = extractConfigValues(existing.config)
                for ((key, value) in extracted) {
                    formValues[key] = value
                }
                events = EventSubscriptions.from(existing.config)
            }
        }
        originalValues = formValues.toMap()
        originalEnabled = enabled
        originalEvents = events
        loading = false
    }

    val isValid: Boolean = !loadFailed && isProviderFormValid(
        values = formValues,
        provider = provider,
        enabled = enabled,
        preservedCredentials = preservedCredentials,
        supportsPost26Features = supportsPost26Features,
    )
    val hasChanges = hasNotificationProviderChanges(
        values = formValues,
        enabled = enabled,
        events = events,
        originalValues = originalValues,
        originalEnabled = originalEnabled,
        originalEvents = originalEvents,
    )

    fun save() {
        val c = client ?: return
        scope.launch {
            saving = true; error = null
            try {
                val config = buildConfigPayload(
                    values = formValues.toMap(),
                    provider = provider,
                    events = events,
                    baseConfig = originalConfig,
                    supportsPost26Features = supportsPost26Features,
                )
                c.notifications.upsertSettings(UpdateNotificationSettings(provider = provider, enabled = enabled, config = config), envId)
                onBack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = friendlyErrorMessage(e)
            } finally {
                saving = false
            }
        }
    }

    fun test() {
        val c = client ?: return
        scope.launch {
            testing = true; testResult = null
            try {
                c.notifications.test(provider = provider, envId = envId)
                testResult = "Success — test notification sent"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                testResult = friendlyErrorMessage(e)
            } finally {
                testing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider.displayName) },
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
            FormSectionHeader("Status")
            LabeledToggle("Enabled", enabled, { enabled = it })

            FormSectionHeader("Configuration")
            fields.forEach { field -> DynamicField(field, formValues) }

            FormSectionHeader("Event Subscriptions")
            EventSubscriptions.keys.forEach { item ->
                LabeledToggle(item.label, events.get(item.key), { events = events.set(item.key, it) })
            }
            FormSectionFooter("Choose which events trigger notifications for this provider.")

            error?.let { FormErrorRow(it) }

            Button(
                onClick = { save() },
                enabled = !saving && isValid && hasChanges,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text(if (isEditing) "Update" else "Save")
            }

            OutlinedButton(
                onClick = { test() },
                enabled = !testing && enabled && isValid && !hasChanges,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Text("  Send Test Notification")
                }
            }

            testResult?.let { result ->
                val success = result.contains("Success")
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(if (success) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (success) ArcaneGreen else ArcaneRed)
                    Text(result, color = if (success) ArcaneGreen else ArcaneRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DynamicField(field: ProviderFieldDescriptor, values: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>) {
    val value = values[field.key] ?: ""
    when (field.kind) {
        ProviderFieldKind.Toggle -> LabeledToggle(field.label, value == "true", { values[field.key] = it.toString() })
        ProviderFieldKind.Picker -> LabeledPicker(
            label = field.label,
            selected = value.ifEmpty { field.defaultValue },
            options = field.pickerOptions.map { it.value },
            optionLabel = { v -> field.pickerOptions.firstOrNull { it.value == v }?.label ?: v },
            onSelect = { values[field.key] = it },
        )
        ProviderFieldKind.Email -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null }, keyboardType = KeyboardType.Email)
        ProviderFieldKind.Password -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null }, isPassword = true)
        ProviderFieldKind.Number -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null }, keyboardType = KeyboardType.Number)
        ProviderFieldKind.Url -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null }, keyboardType = KeyboardType.Uri)
        ProviderFieldKind.Textarea -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null }, singleLine = false, minLines = 2)
        ProviderFieldKind.Text -> LabeledTextField(field.label, value, { values[field.key] = it }, placeholder = field.placeholder.ifEmpty { null })
    }
}
