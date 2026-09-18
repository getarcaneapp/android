package app.getarcane.android.ui.screens.settings.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.loadCompleteEnvironments
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.SettingsSectionFooter
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import kotlinx.coroutines.CancellationException
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.environment.Environment

sealed interface SettingFieldType {
    data object Text : SettingFieldType
    data object Number : SettingFieldType
    data object Boolean : SettingFieldType
    data object Password : SettingFieldType
    data object Cron : SettingFieldType
    data object TextArea : SettingFieldType
    data class Select(val options: List<String>) : SettingFieldType
}

data class SettingFieldCondition(val key: String, val value: String)

data class SettingFieldDef(
    val key: String,
    val label: String,
    val type: SettingFieldType,
    val description: String? = null,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val visibleWhen: SettingFieldCondition? = null,
)

data class SettingsSectionDef(val id: String, val title: String, val fields: List<SettingFieldDef>)

data class SettingsCategoryDef(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
    val sections: List<SettingsSectionDef>,
) {
    val fields: List<SettingFieldDef> get() = sections.flatMap(SettingsSectionDef::fields)
}

private fun field(key: String, label: String, type: SettingFieldType, min: Int? = null, max: Int? = null) =
    SettingFieldDef(key, label, type, minValue = min, maxValue = max)

val systemSettingsCategories: List<SettingsCategoryDef> = listOf(
    SettingsCategoryDef(
        "storage-limits", "Storage & Limits", Icons.Filled.Storage,
        "Directories, storage paths, sync, and upload limits",
        listOf(
            SettingsSectionDef("directories", "Directories & Storage Paths", listOf(
                field("projectsDirectory", "Projects Directory", SettingFieldType.Text),
                field("templatesDirectory", "Templates Directory", SettingFieldType.Text),
                field("swarmStackSourcesDirectory", "Swarm Stack Sources", SettingFieldType.Text),
                field("diskUsagePath", "Disk Usage Path", SettingFieldType.Text),
                field("followProjectSymlinks", "Follow Project Symlinks", SettingFieldType.Boolean),
            )),
            SettingsSectionDef("limits", "Sync & Upload Limits", listOf(
                field("maxImageUploadSize", "Max Image Upload (MB)", SettingFieldType.Number),
                field("gitSyncMaxFiles", "Git Sync Max Files", SettingFieldType.Number),
                field("gitSyncMaxTotalSizeMb", "Git Sync Total Size (MB)", SettingFieldType.Number),
                field("gitSyncMaxBinarySizeMb", "Git Sync Binary Size (MB)", SettingFieldType.Number),
            )),
        ),
    ),
    SettingsCategoryDef(
        "docker", "Docker Settings", Icons.Filled.Inventory2,
        "Shell, deployment defaults, and resource pruning",
        listOf(
            SettingsSectionDef("configuration", "Configuration", listOf(
                field("baseServerUrl", "Base Server URL", SettingFieldType.Text),
                field("defaultShell", "Default Shell", SettingFieldType.Text),
                field("defaultDeployPullPolicy", "Default Pull Policy", SettingFieldType.Select(listOf("missing", "always", "never"))),
                field("autoInjectEnv", "Auto-Inject .env", SettingFieldType.Boolean),
            )),
            SettingsSectionDef("prune", "Prune Options", listOf(
                field("pruneContainerMode", "Prune Containers", SettingFieldType.Select(listOf("none", "stopped", "olderThan"))),
                SettingFieldDef("pruneContainerUntil", "Container Age Filter", SettingFieldType.Text, visibleWhen = SettingFieldCondition("pruneContainerMode", "olderThan")),
                field("pruneImageMode", "Prune Images", SettingFieldType.Select(listOf("none", "dangling", "all", "olderThan"))),
                SettingFieldDef("pruneImageUntil", "Image Age Filter", SettingFieldType.Text, visibleWhen = SettingFieldCondition("pruneImageMode", "olderThan")),
                field("pruneVolumeMode", "Prune Volumes", SettingFieldType.Select(listOf("none", "anonymous", "all"))),
                field("pruneNetworkMode", "Prune Networks", SettingFieldType.Select(listOf("none", "unused", "olderThan"))),
                SettingFieldDef("pruneNetworkUntil", "Network Age Filter", SettingFieldType.Text, visibleWhen = SettingFieldCondition("pruneNetworkMode", "olderThan")),
                field("pruneBuildCacheMode", "Prune Build Cache", SettingFieldType.Select(listOf("none", "unused", "all", "olderThan"))),
                SettingFieldDef("pruneBuildCacheUntil", "Build Cache Age Filter", SettingFieldType.Text, visibleWhen = SettingFieldCondition("pruneBuildCacheMode", "olderThan")),
            )),
        ),
    ),
    SettingsCategoryDef(
        "security", "Security", Icons.Filled.Security, "Trivy vulnerability scanner configuration",
        listOf(SettingsSectionDef("vulnerability", "Vulnerability Scanning", listOf(
            field("trivyImage", "Trivy Image", SettingFieldType.Text),
            SettingFieldDef("trivyNetwork", "Trivy Network", SettingFieldType.Text, description = "Empty inherits Arcane's network; built-in and custom Docker networks are accepted."),
            field("trivySecurityOpts", "Security Options", SettingFieldType.TextArea),
            field("trivyPrivileged", "Privileged Mode", SettingFieldType.Boolean),
            field("trivyResourceLimitsEnabled", "Resource Limits", SettingFieldType.Boolean),
            field("trivyCpuLimit", "CPU Limit", SettingFieldType.Text),
            field("trivyMemoryLimitMb", "Memory Limit (MB)", SettingFieldType.Number),
            field("trivyConcurrentScanContainers", "Concurrent Scans", SettingFieldType.Number, min = 1),
            field("trivyPreserveCacheOnVolumePrune", "Preserve Cache", SettingFieldType.Boolean),
            field("trivyConfig", "Trivy Config (YAML)", SettingFieldType.TextArea),
            field("trivyIgnore", ".trivyignore", SettingFieldType.TextArea),
        ))),
    ),
    SettingsCategoryDef(
        "automations", "Automations", Icons.Filled.Schedule,
        "Updates, monitoring, maintenance, and scheduled scans",
        listOf(
            SettingsSectionDef("updates", "Updates", listOf(
                field("pollingEnabled", "Image Polling", SettingFieldType.Boolean),
                field("pollingInterval", "Polling Interval", SettingFieldType.Cron),
                field("autoUpdate", "Auto-Update", SettingFieldType.Boolean),
                field("autoUpdateInterval", "Update Interval", SettingFieldType.Cron),
                field("autoUpdateExcludedContainers", "Excluded Containers", SettingFieldType.TextArea),
            )),
            SettingsSectionDef("monitoring", "Monitoring", listOf(
                field("autoHealEnabled", "Auto-Heal", SettingFieldType.Boolean),
                field("autoHealInterval", "Check Interval", SettingFieldType.Cron),
                field("autoHealMaxRestarts", "Max Restarts", SettingFieldType.Number),
                field("autoHealRestartWindow", "Restart Window (min)", SettingFieldType.Number),
                field("autoHealExcludedContainers", "Excluded Containers", SettingFieldType.TextArea),
            )),
            SettingsSectionDef("maintenance", "Maintenance", listOf(
                field("scheduledPruneEnabled", "Scheduled Pruning", SettingFieldType.Boolean),
                field("scheduledPruneInterval", "Prune Interval", SettingFieldType.Cron),
                field("environmentHealthInterval", "Environment Health Check", SettingFieldType.Cron),
                field("dockerClientRefreshInterval", "Docker Client Refresh", SettingFieldType.Cron),
                field("eventCleanupInterval", "Event Cleanup", SettingFieldType.Cron),
                field("expiredSessionsCleanupInterval", "Expired Sessions Cleanup", SettingFieldType.Cron),
            )),
            SettingsSectionDef("security", "Security", listOf(
                field("vulnerabilityScanEnabled", "Vulnerability Scanning", SettingFieldType.Boolean),
                field("vulnerabilityScanInterval", "Scan Interval", SettingFieldType.Cron),
            )),
        ),
    ),
    SettingsCategoryDef(
        "activity", "Activity", Icons.Filled.History, "Activity Center history retention",
        listOf(SettingsSectionDef("history", "Activity History", listOf(
            field("activityHistoryRetentionDays", "Retention (days)", SettingFieldType.Number, 0, 3650),
            field("activityHistoryMaxEntries", "Max Entries", SettingFieldType.Number, 0, 100000),
        ))),
    ),
    SettingsCategoryDef(
        "timeouts", "Timeouts", Icons.Filled.Schedule, "Docker, Git, and network operation timeouts",
        listOf(
            SettingsSectionDef("docker", "Docker Operations", listOf(
                field("dockerApiTimeout", "Docker API (s)", SettingFieldType.Number, 1, 3600),
                field("dockerImagePullTimeout", "Image Pull (s)", SettingFieldType.Number, 30, 7200),
                field("trivyScanTimeout", "Trivy Scan (s)", SettingFieldType.Number, 60, 14400),
            )),
            SettingsSectionDef("git", "Git Operations", listOf(
                field("gitOperationTimeout", "Git Operation (s)", SettingFieldType.Number, 30, 3600),
            )),
            SettingsSectionDef("network", "Network Operations", listOf(
                field("httpClientTimeout", "HTTP Client (s)", SettingFieldType.Number, 5, 300),
                field("registryTimeout", "Registry (s)", SettingFieldType.Number, 5, 300),
                field("proxyRequestTimeout", "Proxy Request (s)", SettingFieldType.Number, 10, 600),
            )),
        ),
    ),
)

private data class SettingsTarget(val id: EnvironmentId, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    onOpenCategory: (categoryId: String, environmentId: EnvironmentId, environmentName: String) -> Unit,
    onUpgrade: (environmentId: EnvironmentId, environmentName: String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val session = manager.authenticatedClientScope()
    var target by remember(manager.serverSessionIdentity, manager.currentUser?.id) {
        mutableStateOf(SettingsTarget(manager.activeEnvironmentId, manager.activeEnvironmentName))
    }
    var targets by remember { mutableStateOf(listOf(target)) }
    var targetsLoading by remember { mutableStateOf(false) }
    var upgradeAvailability by remember(session, target.id.rawValue) {
        mutableStateOf<UpgradeAvailability>(UpgradeAvailability.Loading)
    }

    LaunchedEffect(session) {
        val captured = session ?: return@LaunchedEffect
        targetsLoading = true
        try {
            val environments = loadCompleteEnvironments { captured.client.environments.list(it) }
            if (manager.isCurrent(captured)) {
                targets = environments.map(Environment::toSettingsTarget).ifEmpty { listOf(target) }
                targets.firstOrNull { it.id == target.id }?.let { target = it }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            if (targets.none { it.id == target.id }) targets = listOf(target) + targets
        } finally {
            targetsLoading = false
        }
    }

    LaunchedEffect(session, target.id.rawValue) {
        val captured = session ?: return@LaunchedEffect
        val user = manager.currentUser ?: return@LaunchedEffect
        upgradeAvailability = resolveUpgradeAvailability(
            user = user,
            environmentId = target.id.rawValue,
            loadVersion = { captured.client.version.environmentVersion(target.id) },
            checkUpgrade = { captured.client.system.checkUpgrade(target.id) },
            errorMessage = ::friendlyErrorMessage,
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Environment Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "target") {
                LabeledPicker(
                    label = "Settings Environment",
                    selected = target,
                    options = targets,
                    optionLabel = SettingsTarget::name,
                    onSelect = { target = it },
                    enabled = !targetsLoading && targets.size > 1,
                )
                SettingsSectionFooter("This selects which environment to edit without changing the environment used elsewhere in the app.")
            }
            item(key = "configuration-header") { SettingsSectionHeader("Configuration") }
            items(systemSettingsCategories.take(4), key = { it.id }) { category ->
                CategoryRow(category) { onOpenCategory(category.id, target.id, target.name) }
            }
            item(key = "services-header") { SettingsSectionHeader("Services") }
            items(systemSettingsCategories.drop(4), key = { it.id }) { category ->
                CategoryRow(category) { onOpenCategory(category.id, target.id, target.name) }
            }
            item(key = "maintenance-header") { SettingsSectionHeader("Maintenance") }
            item(key = "upgrade") {
                CategoryRowRaw(
                    icon = Icons.Filled.ArrowCircleUp,
                    title = if (upgradeAvailability.canUpgrade) "Upgrade Arcane" else "Upgrade status",
                    summary = if (upgradeAvailability.canUpgrade) "Update ${target.name} to the latest Arcane release" else upgradeAvailability.summary(),
                    onClick = if (upgradeAvailability.canUpgrade) ({ onUpgrade(target.id, target.name) }) else null,
                )
            }
            if (targetsLoading) item(key = "target-loading") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private fun Environment.toSettingsTarget(): SettingsTarget = SettingsTarget(EnvironmentId(id), name ?: id)

@Composable
private fun CategoryRow(category: SettingsCategoryDef, onClick: () -> Unit) =
    CategoryRowRaw(category.icon, category.title, category.summary, onClick)

@Composable
private fun CategoryRowRaw(icon: ImageVector, title: String, summary: String, onClick: (() -> Unit)?) {
    Row(
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
