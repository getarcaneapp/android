package app.getarcane.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.nav.AppTab
import app.getarcane.android.ui.screens.activities.ActivitiesTab
import app.getarcane.android.ui.screens.activities.displayTitle
import app.getarcane.android.ui.screens.activities.sortTime
import app.getarcane.android.ui.screens.activities.statusTint
import app.getarcane.android.ui.screens.activities.subtitle
import app.getarcane.android.ui.screens.settings.FormErrorRow
import app.getarcane.android.ui.screens.settings.FormSuccessRow
import app.getarcane.android.ui.screens.settings.LabeledPicker
import app.getarcane.android.ui.screens.settings.LabeledTextField
import app.getarcane.android.ui.screens.settings.SettingsSectionHeader
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.activity.ActivityStatus
import app.getarcane.sdk.models.base.SortOrder
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.system.PruneAllRequest
import app.getarcane.sdk.models.system.PruneAllResult
import app.getarcane.sdk.models.system.PruneBuildCacheMode
import app.getarcane.sdk.models.system.PruneBuildCacheOptions
import app.getarcane.sdk.models.system.PruneContainerMode
import app.getarcane.sdk.models.system.PruneContainersOptions
import app.getarcane.sdk.models.system.PruneImageMode
import app.getarcane.sdk.models.system.PruneImagesOptions
import app.getarcane.sdk.models.system.PruneNetworkMode
import app.getarcane.sdk.models.system.PruneNetworksOptions
import app.getarcane.sdk.models.system.PruneVolumeMode
import app.getarcane.sdk.models.system.PruneVolumesOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cross-environment totals for the overview tiles. */
private data class DashTotals(
    val running: Int,
    val total: Int,
    val images: Int,
    val volumes: Int,
    val updates: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTab: ((String) -> Unit)? = null,
    onOpenContainer: ((String) -> Unit)? = null,
    onOpenProject: ((String) -> Unit)? = null,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val supportsActivities = manager.capabilities.supportsActivities

    var environments by remember { mutableStateOf<List<Environment>>(emptyList()) }
    var totals by remember { mutableStateOf<DashTotals?>(null) }
    var failedActivities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var showActivities by remember { mutableStateOf(false) }
    var showPrune by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        loading = true
        val envs = runCatching { client.environments.list().data }.getOrElse {
            // Fall back to the active environment so the dashboard still shows a card.
            listOf(Environment(id = envId.rawValue, name = manager.activeEnvironmentName, apiUrl = "", status = "active"))
        }
        environments = envs
        // Aggregate the overview tiles across ALL environments (parallel, best-effort per env).
        totals = runCatching {
            coroutineScope {
                envs.map { env ->
                    val e = EnvironmentId(env.id)
                    async {
                        val sc = runCatching { client.containers.statusCounts(envId = e) }.getOrNull()
                        val ic = runCatching { client.images.usageCounts(envId = e) }.getOrNull()
                        val vc = runCatching { client.volumes.counts(envId = e) }.getOrNull()
                        val us = runCatching { client.images.updateSummary(envId = e) }.getOrNull()
                        intArrayOf(
                            sc?.runningContainers ?: 0,
                            sc?.totalContainers ?: 0,
                            ic?.totalImages ?: 0,
                            vc?.total ?: 0,
                            us?.imagesWithUpdates ?: 0,
                        )
                    }
                }.awaitAll()
            }.let { rows ->
                DashTotals(
                    running = rows.sumOf { it[0] },
                    total = rows.sumOf { it[1] },
                    images = rows.sumOf { it[2] },
                    volumes = rows.sumOf { it[3] },
                    updates = rows.sumOf { it[4] },
                )
            }
        }.getOrNull()

        // Surface recent failed background work (RBAC servers only). Best-effort, per environment,
        // limited to the 3 most recent failures across environments. Mirrors iOS `loadFailedWork()`.
        failedActivities = if (supportsActivities) {
            runCatching {
                coroutineScope {
                    envs.map { env ->
                        async {
                            runCatching {
                                client.activities.listPaginated(
                                    envId = EnvironmentId(env.id),
                                    order = SortOrder.DESCENDING,
                                    start = 0,
                                    limit = 20,
                                    status = ActivityStatus.FAILED,
                                ).data
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }.flatten()
                    .filter { it.status == ActivityStatus.FAILED }
                    .sortedByDescending { it.sortTime }
                    .take(3)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    if (supportsActivities) {
                        IconButton(onClick = { showActivities = true }) {
                            Icon(Icons.Filled.History, contentDescription = "Activity Center")
                        }
                    }
                    IconButton(onClick = { showPrune = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "System Prune", tint = ArcaneRed)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { refreshKey++ },
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    val t = totals
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardTile("Updates", t?.let { "${it.updates}" } ?: "—", Icons.Filled.Autorenew, ArcaneGreen, Modifier.weight(1f)) {
                                onOpenTab?.invoke(AppTab.Updates.id)
                            }
                            DashboardTile("Containers", t?.let { "${it.running} / ${it.total}" } ?: "—", Icons.Filled.Inventory2, ArcaneOrange, Modifier.weight(1f)) {
                                onOpenTab?.invoke(AppTab.Containers.id)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardTile("Images", t?.let { "${it.images}" } ?: "—", Icons.Filled.Layers, ArcanePurple, Modifier.weight(1f)) {
                                onOpenTab?.invoke(AppTab.Images.id)
                            }
                            DashboardTile("Volumes", t?.let { "${it.volumes}" } ?: "—", Icons.Filled.Storage, ArcaneTeal, Modifier.weight(1f)) {
                                onOpenTab?.invoke(AppTab.Volumes.id)
                            }
                        }
                    }
                }
                if (supportsActivities && failedActivities.isNotEmpty()) {
                    item {
                        DashboardFailedWorkCard(
                            activities = failedActivities,
                            onOpen = { showActivities = true },
                        )
                    }
                }
                item {
                    DashboardPinnedSection(
                        refreshToken = refreshKey,
                        onOpenContainer = { id -> onOpenContainer?.invoke(id) ?: onOpenTab?.invoke(AppTab.Containers.id) },
                        onOpenProject = { id -> onOpenProject?.invoke(id) ?: onOpenTab?.invoke(AppTab.Projects.id) },
                        onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    )
                }
                item {
                    Text("Environments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(environments, key = { it.id }) { env ->
                    EnvironmentDashboardCard(
                        env = env,
                        onSelect = { manager.setActiveEnvironment(EnvironmentId(env.id), env.name ?: env.id) },
                    )
                }
            }
        }
    }

    // Activity Center is presented modally as a full-screen dialog hosting the activities tab,
    // mirroring the iOS dashboard `.sheet`.
    if (showActivities) {
        Dialog(
            onDismissRequest = { showActivities = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ActivitiesTab(onClose = { showActivities = false })
            }
        }
    }

    if (showPrune) {
        SystemPruneSheet(
            envId = envId,
            onDismiss = { showPrune = false },
            onComplete = { refreshKey++ },
        )
    }
}

/**
 * Card surfacing recent failed background activities. Tapping the card (or a row) opens the
 * Activity Center. Port of iOS `DashboardFailedWorkCard`.
 */
@Composable
private fun DashboardFailedWorkCard(activities: List<Activity>, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).background(ArcaneRed, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Failed Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val n = activities.size
                    Text(
                        "$n failure${if (n == 1) "" else "s"} need attention",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activities.take(3).forEach { activity ->
                    DashboardFailedWorkRow(activity = activity, onClick = onOpen)
                }
            }
        }
    }
}

@Composable
private fun DashboardFailedWorkRow(activity: Activity, onClick: () -> Unit) {
    val tint = activity.statusTint()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                activity.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                activity.latestMessage.ifEmpty { activity.subtitle },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            activity.status.wire.replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DashboardTile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(clickableModifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemPruneSheet(envId: EnvironmentId, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val manager = LocalArcaneManager.current
    val scope = rememberCoroutineScope()

    var containerMode by remember { mutableStateOf(PruneContainerMode.NONE) }
    var containerUntil by remember { mutableStateOf("") }
    var imageMode by remember { mutableStateOf(PruneImageMode.NONE) }
    var imageUntil by remember { mutableStateOf("") }
    var volumeMode by remember { mutableStateOf(PruneVolumeMode.NONE) }
    var networkMode by remember { mutableStateOf(PruneNetworkMode.NONE) }
    var networkUntil by remember { mutableStateOf("") }
    var buildCacheMode by remember { mutableStateOf(PruneBuildCacheMode.NONE) }
    var buildCacheUntil by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedCount = listOf(
        containerMode != PruneContainerMode.NONE,
        imageMode != PruneImageMode.NONE,
        volumeMode != PruneVolumeMode.NONE,
        networkMode != PruneNetworkMode.NONE,
        buildCacheMode != PruneBuildCacheMode.NONE,
    ).count { it }

    fun runPrune() {
        val client = manager.client ?: return
        val request = PruneAllRequest(
            containers = containerMode.takeUnless { it == PruneContainerMode.NONE }?.let {
                PruneContainersOptions(it, containerUntil.takeIf { v -> v.isNotBlank() })
            },
            images = imageMode.takeUnless { it == PruneImageMode.NONE }?.let {
                PruneImagesOptions(it, imageUntil.takeIf { v -> v.isNotBlank() })
            },
            volumes = volumeMode.takeUnless { it == PruneVolumeMode.NONE }?.let { PruneVolumesOptions(it) },
            networks = networkMode.takeUnless { it == PruneNetworkMode.NONE }?.let {
                PruneNetworksOptions(it, networkUntil.takeIf { v -> v.isNotBlank() })
            },
            buildCache = buildCacheMode.takeUnless { it == PruneBuildCacheMode.NONE }?.let {
                PruneBuildCacheOptions(it, buildCacheUntil.takeIf { v -> v.isNotBlank() })
            },
        )
        scope.launch {
            submitting = true
            errorMessage = null
            resultMessage = null
            try {
                resultMessage = formatPruneResult(client.system.prune(request, envId))
                onComplete()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                submitting = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("System Prune") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !submitting) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(onClick = { runPrune() }, enabled = selectedCount > 0 && !submitting) {
                                if (submitting) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(if (selectedCount > 0) "Prune ($selectedCount)" else "Prune")
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item { SettingsSectionHeader("Containers") }
                    item {
                        LabeledPicker(
                            label = "Containers",
                            selected = containerMode,
                            options = PruneContainerMode.entries,
                            optionLabel = { it.label },
                            onSelect = { containerMode = it },
                            enabled = !submitting,
                        )
                    }
                    if (containerMode == PruneContainerMode.OLDER_THAN) {
                        item { LabeledTextField("Older than", containerUntil, { containerUntil = it }, placeholder = "24h") }
                    }

                    item { SettingsSectionHeader("Images") }
                    item {
                        LabeledPicker("Images", imageMode, PruneImageMode.entries, { it.label }, { imageMode = it }, enabled = !submitting)
                    }
                    if (imageMode == PruneImageMode.OLDER_THAN) {
                        item { LabeledTextField("Older than", imageUntil, { imageUntil = it }, placeholder = "24h") }
                    }

                    item { SettingsSectionHeader("Volumes") }
                    item {
                        LabeledPicker("Volumes", volumeMode, PruneVolumeMode.entries, { it.label }, { volumeMode = it }, enabled = !submitting)
                    }

                    item { SettingsSectionHeader("Networks") }
                    item {
                        LabeledPicker("Networks", networkMode, PruneNetworkMode.entries, { it.label }, { networkMode = it }, enabled = !submitting)
                    }
                    if (networkMode == PruneNetworkMode.OLDER_THAN) {
                        item { LabeledTextField("Older than", networkUntil, { networkUntil = it }, placeholder = "24h") }
                    }

                    item { SettingsSectionHeader("Build Cache") }
                    item {
                        LabeledPicker("Build Cache", buildCacheMode, PruneBuildCacheMode.entries, { it.label }, { buildCacheMode = it }, enabled = !submitting)
                    }
                    if (buildCacheMode == PruneBuildCacheMode.OLDER_THAN) {
                        item { LabeledTextField("Older than", buildCacheUntil, { buildCacheUntil = it }, placeholder = "24h") }
                    }

                    resultMessage?.let { msg ->
                        item { FormSuccessRow(msg) }
                    }
                    errorMessage?.let { msg ->
                        item { FormErrorRow(msg) }
                    }
                    item {
                        Button(
                            onClick = { runPrune() },
                            enabled = selectedCount > 0 && !submitting,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Text(if (selectedCount > 0) "Prune ($selectedCount)" else "Prune")
                        }
                    }
                }
            }
        }
    }
}

private val PruneContainerMode.label: String
    get() = when (this) {
        PruneContainerMode.NONE -> "None"
        PruneContainerMode.STOPPED -> "Stopped"
        PruneContainerMode.OLDER_THAN -> "Older than..."
    }

private val PruneImageMode.label: String
    get() = when (this) {
        PruneImageMode.NONE -> "None"
        PruneImageMode.DANGLING -> "Dangling"
        PruneImageMode.ALL -> "All Unused"
        PruneImageMode.OLDER_THAN -> "Older than..."
    }

private val PruneVolumeMode.label: String
    get() = when (this) {
        PruneVolumeMode.NONE -> "None"
        PruneVolumeMode.ANONYMOUS -> "Anonymous"
        PruneVolumeMode.ALL -> "All Unused"
    }

private val PruneNetworkMode.label: String
    get() = when (this) {
        PruneNetworkMode.NONE -> "None"
        PruneNetworkMode.UNUSED -> "Unused"
        PruneNetworkMode.OLDER_THAN -> "Older than..."
    }

private val PruneBuildCacheMode.label: String
    get() = when (this) {
        PruneBuildCacheMode.NONE -> "None"
        PruneBuildCacheMode.UNUSED -> "Unused"
        PruneBuildCacheMode.ALL -> "All"
        PruneBuildCacheMode.OLDER_THAN -> "Older than..."
    }

private fun formatPruneResult(result: PruneAllResult): String {
    val parts = buildList {
        result.containersPruned?.size?.takeIf { it > 0 }?.let { add("$it container${if (it == 1) "" else "s"}") }
        result.imagesDeleted?.size?.takeIf { it > 0 }?.let { add("$it image${if (it == 1) "" else "s"}") }
        result.volumesDeleted?.size?.takeIf { it > 0 }?.let { add("$it volume${if (it == 1) "" else "s"}") }
        result.networksDeleted?.size?.takeIf { it > 0 }?.let { add("$it network${if (it == 1) "" else "s"}") }
    }
    val summary = if (parts.isEmpty()) "No resources pruned." else "Pruned ${parts.joinToString(", ")}."
    val reclaimed = result.spaceReclaimed.takeIf { it > 0 }?.let { " Freed ${formatBytes(it)}." }.orEmpty()
    val errors = result.errors?.takeIf { it.isNotEmpty() }?.joinToString(prefix = " Errors: ")
    return summary + reclaimed + (errors ?: "")
}
