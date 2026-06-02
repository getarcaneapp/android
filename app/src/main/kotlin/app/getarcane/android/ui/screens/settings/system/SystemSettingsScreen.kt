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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.screens.settings.SettingsSectionFooter
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.sdk.models.user.isAdmin

/** Field types in a settings category. Mirrors iOS `SettingFieldType`. */
sealed interface SettingFieldType {
    data object Text : SettingFieldType
    data object Number : SettingFieldType
    data object Boolean : SettingFieldType
    data object Password : SettingFieldType
    data class Select(val options: List<String>) : SettingFieldType
}

/** A single setting field definition. Mirrors iOS `SettingFieldDef`. */
data class SettingFieldDef(val key: String, val label: String, val type: SettingFieldType)

/** A settings category. Mirrors iOS `SettingsCategoryDef`. */
data class SettingsCategoryDef(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
    val fields: List<SettingFieldDef>,
)

/** Categories + fields, ported 1:1 from iOS `systemSettingsCategories`. */
val systemSettingsCategories: List<SettingsCategoryDef> = listOf(
    SettingsCategoryDef(
        "general", "General", Icons.Filled.Settings, "Server URL, gravatar, default shell",
        listOf(
            SettingFieldDef("baseServerUrl", "Base Server URL", SettingFieldType.Text),
            SettingFieldDef("diskUsagePath", "Disk Usage Path", SettingFieldType.Text),
            SettingFieldDef("enableGravatar", "Enable Gravatar", SettingFieldType.Boolean),
            SettingFieldDef("defaultShell", "Default Shell", SettingFieldType.Text),
            SettingFieldDef("autoInjectEnv", "Auto-Inject .env", SettingFieldType.Boolean),
            SettingFieldDef("defaultDeployPullPolicy", "Default Pull Policy", SettingFieldType.Select(listOf("always", "missing", "never", "build"))),
        ),
    ),
    SettingsCategoryDef(
        "docker", "Docker Daemon", Icons.Filled.Inventory2, "Docker host and project directories",
        listOf(
            SettingFieldDef("dockerHost", "Docker Host", SettingFieldType.Text),
            SettingFieldDef("projectsDirectory", "Projects Directory", SettingFieldType.Text),
            SettingFieldDef("swarmStackSourcesDirectory", "Swarm Stack Sources", SettingFieldType.Text),
            SettingFieldDef("followProjectSymlinks", "Follow Project Symlinks", SettingFieldType.Boolean),
            SettingFieldDef("dockerPruneMode", "Prune Mode", SettingFieldType.Select(listOf("all", "dangling"))),
        ),
    ),
    SettingsCategoryDef(
        "auto-update", "Auto-Update", Icons.Filled.Sync, "Automatic image updates and polling",
        listOf(
            SettingFieldDef("autoUpdate", "Enabled", SettingFieldType.Boolean),
            SettingFieldDef("autoUpdateExcludedContainers", "Excluded Containers", SettingFieldType.Text),
            SettingFieldDef("pollingEnabled", "Polling Enabled", SettingFieldType.Boolean),
        ),
    ),
    SettingsCategoryDef(
        "auto-heal", "Auto-Heal", Icons.Filled.Favorite, "Restart unhealthy containers automatically",
        listOf(
            SettingFieldDef("autoHealEnabled", "Enabled", SettingFieldType.Boolean),
            SettingFieldDef("autoHealMaxRestarts", "Max Restarts", SettingFieldType.Number),
            SettingFieldDef("autoHealRestartWindow", "Restart Window (min)", SettingFieldType.Number),
            SettingFieldDef("autoHealExcludedContainers", "Excluded Containers", SettingFieldType.Text),
        ),
    ),
    SettingsCategoryDef(
        "prune", "Scheduled Pruning", Icons.Filled.Delete, "Automatically clean up unused resources",
        listOf(
            SettingFieldDef("scheduledPruneEnabled", "Enabled", SettingFieldType.Boolean),
            SettingFieldDef("scheduledPruneContainers", "Prune Containers", SettingFieldType.Boolean),
            SettingFieldDef("scheduledPruneImages", "Prune Images", SettingFieldType.Boolean),
            SettingFieldDef("scheduledPruneVolumes", "Prune Volumes", SettingFieldType.Boolean),
            SettingFieldDef("scheduledPruneNetworks", "Prune Networks", SettingFieldType.Boolean),
            SettingFieldDef("scheduledPruneBuildCache", "Prune Build Cache", SettingFieldType.Boolean),
            SettingFieldDef("pruneContainerMode", "Container Mode", SettingFieldType.Select(listOf("none", "stopped", "olderThan"))),
            SettingFieldDef("pruneContainerUntil", "Container Until", SettingFieldType.Text),
            SettingFieldDef("pruneImageMode", "Image Mode", SettingFieldType.Select(listOf("none", "dangling", "all", "olderThan"))),
            SettingFieldDef("pruneImageUntil", "Image Until", SettingFieldType.Text),
            SettingFieldDef("pruneVolumeMode", "Volume Mode", SettingFieldType.Select(listOf("none", "anonymous", "all"))),
            SettingFieldDef("pruneNetworkMode", "Network Mode", SettingFieldType.Select(listOf("none", "unused", "olderThan"))),
            SettingFieldDef("pruneNetworkUntil", "Network Until", SettingFieldType.Text),
            SettingFieldDef("pruneBuildCacheMode", "Build Cache Mode", SettingFieldType.Select(listOf("none", "unused", "all", "olderThan"))),
            SettingFieldDef("pruneBuildCacheUntil", "Build Cache Until", SettingFieldType.Text),
        ),
    ),
    SettingsCategoryDef(
        "vulnerability", "Vulnerability Scanning", Icons.Filled.Security, "Trivy scanner configuration",
        listOf(
            SettingFieldDef("vulnerabilityScanEnabled", "Enabled", SettingFieldType.Boolean),
            SettingFieldDef("trivyImage", "Trivy Image", SettingFieldType.Text),
            SettingFieldDef("trivyNetwork", "Network", SettingFieldType.Text),
            SettingFieldDef("trivySecurityOpts", "Security Options", SettingFieldType.Text),
            SettingFieldDef("trivyPrivileged", "Privileged Mode", SettingFieldType.Boolean),
            SettingFieldDef("trivyResourceLimitsEnabled", "Resource Limits", SettingFieldType.Boolean),
            SettingFieldDef("trivyCpuLimit", "CPU Limit", SettingFieldType.Text),
            SettingFieldDef("trivyMemoryLimitMb", "Memory Limit (MB)", SettingFieldType.Number),
            SettingFieldDef("trivyConcurrentScanContainers", "Concurrent Scans", SettingFieldType.Number),
            SettingFieldDef("trivyPreserveCacheOnVolumePrune", "Preserve Cache", SettingFieldType.Boolean),
        ),
    ),
    SettingsCategoryDef(
        "timeouts", "Timeouts", Icons.Filled.Schedule, "Operation timeouts in seconds",
        listOf(
            SettingFieldDef("dockerApiTimeout", "Docker API (s)", SettingFieldType.Number),
            SettingFieldDef("dockerImagePullTimeout", "Image Pull (s)", SettingFieldType.Number),
            SettingFieldDef("trivyScanTimeout", "Trivy Scan (s)", SettingFieldType.Number),
            SettingFieldDef("gitOperationTimeout", "Git Operation (s)", SettingFieldType.Number),
            SettingFieldDef("httpClientTimeout", "HTTP Client (s)", SettingFieldType.Number),
            SettingFieldDef("registryTimeout", "Registry (s)", SettingFieldType.Number),
            SettingFieldDef("proxyRequestTimeout", "Proxy Request (s)", SettingFieldType.Number),
            SettingFieldDef("buildTimeout", "Build (s)", SettingFieldType.Number),
        ),
    ),
    SettingsCategoryDef(
        "git-sync", "Git Sync Limits", Icons.Filled.Sync, "Repository sync size and file limits",
        listOf(
            SettingFieldDef("gitSyncMaxFiles", "Max Files", SettingFieldType.Number),
            SettingFieldDef("gitSyncMaxTotalSizeMb", "Max Total Size (MB)", SettingFieldType.Number),
            SettingFieldDef("gitSyncMaxBinarySizeMb", "Max Binary Size (MB)", SettingFieldType.Number),
        ),
    ),
    SettingsCategoryDef(
        "misc", "Miscellaneous", Icons.Filled.MoreHoriz, "Additional settings",
        listOf(
            SettingFieldDef("maxImageUploadSize", "Max Image Upload (MB)", SettingFieldType.Number),
        ),
    ),
)

/** System settings hub: category list + Maintenance (Upgrade) for admins. Port of iOS `SystemSettingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(onOpenCategory: (categoryId: String) -> Unit, onUpgrade: () -> Unit) {
    val manager = LocalArcaneManager.current
    val isAdmin = manager.currentUser?.isAdmin ?: false

    Scaffold(topBar = { TopAppBar(title = { Text("System Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(systemSettingsCategories, key = { it.id }) { category ->
                CategoryRow(category, onClick = { onOpenCategory(category.id) })
            }
            item(key = "settings-footer") {
                SettingsSectionFooter("Settings apply to the active environment: ${manager.activeEnvironmentName}")
            }
            if (isAdmin) {
                item(key = "maint-header") { SettingsSectionHeader("Maintenance") }
                item(key = "upgrade") {
                    CategoryRowRaw(
                        icon = Icons.Filled.ArrowCircleUp,
                        title = "Upgrade Arcane",
                        summary = "Update to the latest Arcane release",
                        onClick = onUpgrade,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategoryDef, onClick: () -> Unit) {
    CategoryRowRaw(category.icon, category.title, category.summary, onClick)
}

@Composable
private fun CategoryRowRaw(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
