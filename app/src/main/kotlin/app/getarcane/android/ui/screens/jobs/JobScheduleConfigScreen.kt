package app.getarcane.android.ui.screens.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.jobschedule.JobScheduleConfig
import app.getarcane.sdk.models.jobschedule.UpdateJobScheduleConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Schedule field metadata. Mirrors iOS `jobScheduleFields` (same keys/labels/icons/tints, same order). */
private data class JobScheduleField(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val get: (JobScheduleConfig) -> String,
)

private val jobScheduleFields = listOf(
    JobScheduleField("autoHealInterval", "Auto Heal", Icons.Filled.Favorite, ArcanePink) { it.autoHealInterval },
    JobScheduleField("autoUpdateInterval", "Auto Update", Icons.Filled.Sync, ArcaneBlue) { it.autoUpdateInterval },
    JobScheduleField("dockerClientRefreshInterval", "Docker Client Refresh", Icons.Filled.Inventory2, ArcaneBlue) { it.dockerClientRefreshInterval },
    JobScheduleField("environmentHealthInterval", "Environment Health", Icons.Filled.MonitorHeart, ArcaneGreen) { it.environmentHealthInterval },
    JobScheduleField("eventCleanupInterval", "Event Cleanup", Icons.Filled.Delete, ArcaneRed) { it.eventCleanupInterval },
    JobScheduleField("gitopsSyncInterval", "GitOps Sync", Icons.AutoMirrored.Filled.CallMerge, ArcaneIndigo) { it.gitopsSyncInterval },
    JobScheduleField("pollingInterval", "Polling", Icons.Filled.Wifi, ArcaneTeal) { it.pollingInterval },
    JobScheduleField("scheduledPruneInterval", "Scheduled Prune", Icons.Filled.ContentCut, ArcaneOrange) { it.scheduledPruneInterval },
    JobScheduleField("vulnerabilityScanInterval", "Vulnerability Scan", Icons.Filled.Shield, ArcanePurple) { it.vulnerabilityScanInterval },
)

private fun buildUpdate(changed: Map<String, String>): UpdateJobScheduleConfig = UpdateJobScheduleConfig(
    environmentHealthInterval = changed["environmentHealthInterval"],
    eventCleanupInterval = changed["eventCleanupInterval"],
    autoUpdateInterval = changed["autoUpdateInterval"],
    dockerClientRefreshInterval = changed["dockerClientRefreshInterval"],
    pollingInterval = changed["pollingInterval"],
    scheduledPruneInterval = changed["scheduledPruneInterval"],
    gitopsSyncInterval = changed["gitopsSyncInterval"],
    vulnerabilityScanInterval = changed["vulnerabilityScanInterval"],
    autoHealInterval = changed["autoHealInterval"],
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobScheduleConfigScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    val values = remember { mutableStateMapOf<String, String>() }
    val original = remember { mutableStateMapOf<String, String>() }
    var loaded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val hasChanges = jobScheduleFields.any { values[it.key] != original[it.key] }

    fun applyConfig(config: JobScheduleConfig) {
        values.clear(); original.clear()
        jobScheduleFields.forEach {
            val v = it.get(config)
            values[it.key] = v
            original[it.key] = v
        }
    }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (!loaded) isLoading = true
        try {
            applyConfig(client.jobs.getSchedules(envId = envId))
            errorMessage = null
            loaded = true
        } catch (e: Throwable) {
            errorMessage = friendlyErrorMessage(e)
        }
        isLoading = false
    }

    fun save() {
        if (client == null) return
        isSaving = true
        errorMessage = null
        savedMessage = null
        val changed = jobScheduleFields
            .filter { values[it.key] != original[it.key] }
            .associate { it.key to (values[it.key] ?: "") }
        if (changed.isEmpty()) { isSaving = false; return }
        scope.launch {
            try {
                val updated = client.jobs.updateSchedules(buildUpdate(changed), envId = envId)
                applyConfig(updated)
                savedMessage = "Schedules saved"
                delay(3000)
                savedMessage = null
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { refreshKey++ }, enabled = !isLoading) { Icon(Icons.Filled.Refresh, "Refresh") }
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { save() }, enabled = hasChanges) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && !loaded) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("  Loading schedules…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jobScheduleFields.forEach { field ->
                        item(key = field.key) {
                            ScheduleRow(
                                field = field,
                                value = values[field.key] ?: "",
                                onChange = { values[field.key] = it },
                            )
                        }
                    }
                    item(key = "footer") {
                        Text(
                            "Cron expressions accept 5- or 6-field syntax. Changes save to the active environment's settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    if (hasChanges) {
                        item(key = "discard") {
                            TextButton(
                                onClick = { jobScheduleFields.forEach { values[it.key] = original[it.key] ?: "" } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Discard Changes", color = ArcaneRed) }
                        }
                    }
                    errorMessage?.let { msg ->
                        item(key = "error") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(18.dp))
                                Text(msg, color = ArcaneRed, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    savedMessage?.let { msg ->
                        item(key = "saved") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, null, tint = ArcaneGreen, modifier = Modifier.size(18.dp))
                                Text(msg, color = ArcaneGreen, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(field: JobScheduleField, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(28.dp).background(field.tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(field.icon, null, tint = field.tint, modifier = Modifier.size(16.dp)) }
            Text(field.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("* * * * *", fontFamily = FontFamily.Monospace) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        CronExpression.readable(value)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
