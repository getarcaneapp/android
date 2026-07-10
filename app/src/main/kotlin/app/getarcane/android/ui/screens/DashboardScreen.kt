package app.getarcane.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.ArcaneDashboardStreamClient
import app.getarcane.android.core.DashboardActionItemKind
import app.getarcane.android.core.DashboardActionItemSeverity
import app.getarcane.android.core.DashboardEnvironmentStreamState
import app.getarcane.android.core.DashboardStreamStore
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.nav.AppTab
import app.getarcane.android.ui.screens.activities.ActivitiesTab
import app.getarcane.android.ui.screens.activities.sortTime
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
import app.getarcane.sdk.models.user.isGlobalAdmin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cross-environment totals for the overview tiles. */
internal data class DashTotals(
    val running: Int,
    val total: Int,
    val images: Int,
    val volumes: Int,
    val updates: Int,
    val stopped: Int,
)

internal enum class NeedsAttentionSeverity { Critical, Warning }

internal data class NeedsAttentionItem(
    val id: String,
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val severity: NeedsAttentionSeverity,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTab: ((String) -> Unit)? = null,
    onOpenContainer: ((String) -> Unit)? = null,
    onOpenProject: ((String) -> Unit)? = null,
    onOpenVolume: ((String) -> Unit)? = null,
    onOpenEnvironmentDetails: ((String) -> Unit)? = null,
    onOpenImageVulnerabilities: (() -> Unit)? = null,
    onOpenApiKeys: (() -> Unit)? = null,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val supportsActivities = manager.capabilities.supportsActivities
    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false

    var environments by remember { mutableStateOf<List<Environment>>(emptyList()) }
    var totals by remember { mutableStateOf<DashTotals?>(null) }
    var failedActivities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var showActivities by remember { mutableStateOf(false) }
    var showUpdateAll by remember { mutableStateOf(false) }
    var pruneEnvironmentId by remember { mutableStateOf<EnvironmentId?>(null) }
    val statsHistory = remember { mutableStateMapOf<String, DashboardStatsSeries>() }
    val scope = rememberCoroutineScope()
    val streamClient = remember(client) { client?.let(::ArcaneDashboardStreamClient) }
    val streamStore = remember(scope) { DashboardStreamStore(scope) }
    val enabledEnvironmentIds = environments
        .filter { it.enabled }
        .map { it.id }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(client, enabledEnvironmentIds, refreshKey) {
        if (client == null) return@LaunchedEffect

        statsHistory.keys
            .filterNot { it in enabledEnvironmentIds }
            .forEach { statsHistory.remove(it) }

        coroutineScope {
            enabledEnvironmentIds
                .take(DashboardStatsMaxStreams)
                .forEachIndexed { index, id ->
                    launch {
                        delay(150L * (index + 1))
                        val env = EnvironmentId(id)
                        statsHistory[id] = (statsHistory[id] ?: DashboardStatsSeries()).reconnecting()
                        runCatching {
                            client.system.statsStream(env).collect { stats ->
                                statsHistory[id] = (statsHistory[id] ?: DashboardStatsSeries()).append(stats)
                            }
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            statsHistory[id] = (statsHistory[id] ?: DashboardStatsSeries()).copy(
                                error = "Live stats unavailable: ${friendlyErrorMessage(error)}",
                            )
                        }
                    }
                }
        }
    }

    LaunchedEffect(streamClient) {
        streamStore.configure(streamClient)
        if (supportsActivities) streamStore.start()
    }

    LaunchedEffect(supportsActivities) {
        if (supportsActivities) streamStore.start() else streamStore.stop()
    }

    LaunchedEffect(environments) {
        streamStore.reconcile(environments)
    }

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
                            ((sc?.totalContainers ?: 0) - (sc?.runningContainers ?: 0)).coerceAtLeast(0),
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
                    stopped = rows.sumOf { it[5] },
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
                            ActivityCenterToolbarIcon(failedCount = failedActivities.size)
                        }
                    }
                    IconButton(onClick = { pruneEnvironmentId = envId }) {
                        Icon(Icons.Filled.Delete, contentDescription = "System Prune", tint = ArcaneRed)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = {
                streamStore.reconnect()
                refreshKey++
            },
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
                    val t = streamStore.aggregate?.let { aggregate ->
                        DashTotals(
                            running = aggregate.runningContainers,
                            total = aggregate.totalContainers,
                            images = aggregate.totalImages,
                            volumes = totals?.volumes ?: 0,
                            updates = totals?.updates ?: 0,
                            stopped = aggregate.stoppedContainers,
                        )
                    } ?: totals
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
                if (streamStore.streamFailed && !streamStore.streamUnsupported) {
                    item {
                        DashboardStreamFailedBanner(onRetry = { streamStore.retry() })
                    }
                }
                val attentionItems = buildNeedsAttentionItems(
                    environments = environments,
                    streamStates = streamStore.statesByEnvironmentId,
                    totals = totals,
                    failedActivities = failedActivities,
                    onOpenEnvironment = { env ->
                        manager.setActiveEnvironment(EnvironmentId(env.id), env.name ?: env.id)
                    },
                    onOpenContainers = { onOpenTab?.invoke(AppTab.Containers.id) },
                    onOpenUpdates = { onOpenTab?.invoke(AppTab.Updates.id) },
                    onOpenVulnerabilities = { target ->
                        manager.setActiveEnvironment(EnvironmentId(target.id), target.name)
                        onOpenImageVulnerabilities?.invoke() ?: onOpenTab?.invoke(AppTab.Images.id)
                    },
                    onOpenApiKeys = {
                        onOpenApiKeys?.invoke() ?: onOpenTab?.invoke(AppTab.ApiKeys.id)
                    },
                    onOpenActivities = { showActivities = true },
                )
                if (attentionItems.isNotEmpty()) {
                    item {
                        NeedsAttentionSection(items = attentionItems)
                    }
                }
                item {
                    DashboardPinnedSection(
                        refreshToken = refreshKey,
                        onOpenContainer = { id -> onOpenContainer?.invoke(id) ?: onOpenTab?.invoke(AppTab.Containers.id) },
                        onOpenProject = { id -> onOpenProject?.invoke(id) ?: onOpenTab?.invoke(AppTab.Projects.id) },
                        onOpenVolume = { name -> onOpenVolume?.invoke(name) ?: onOpenTab?.invoke(AppTab.Volumes.id) },
                        onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Environments", style = MaterialTheme.typography.titleMedium)
                        if (shouldShowUpdateAllAction(isAdmin)) {
                            Button(onClick = { showUpdateAll = true }) {
                                Icon(Icons.Filled.ArrowCircleUp, contentDescription = null)
                                Text("  Update All")
                            }
                        }
                    }
                }
                items(environments, key = { it.id }) { env ->
                    EnvironmentDashboardCard(
                        env = env,
                        statsSeries = statsHistory[env.id],
                        refreshToken = refreshKey,
                        onSelect = { manager.setActiveEnvironment(EnvironmentId(env.id), env.name ?: env.id) },
                        actions = environmentCardActions(isAdmin = isAdmin),
                        onAction = { action ->
                            when (action) {
                                EnvironmentCardAction.UseEnvironment -> {
                                    manager.setActiveEnvironment(EnvironmentId(env.id), env.name ?: env.id)
                                }
                                EnvironmentCardAction.ViewSystemDetails -> {
                                    onOpenEnvironmentDetails?.invoke(env.id)
                                }
                                EnvironmentCardAction.Sync -> {
                                    refreshKey++
                                    scope.launch { snackbar.showSnackbar("Refreshing ${env.name ?: env.id}") }
                                }
                                EnvironmentCardAction.SystemPrune -> {
                                    pruneEnvironmentId = EnvironmentId(env.id)
                                }
                            }
                        },
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
                ActivitiesTab(
                    onClose = { showActivities = false },
                    onHistoryCleared = { refreshKey++ },
                )
            }
        }
    }

    if (showUpdateAll) {
        Dialog(
            onDismissRequest = { showUpdateAll = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                UpdateAllEnvironmentsDialog(
                    environmentCount = environments.size,
                    onDismiss = { showUpdateAll = false },
                    onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    onComplete = { refreshKey++ },
                )
            }
        }
    }

    pruneEnvironmentId?.let { pruneEnvId ->
        SystemPruneSheet(
            envId = pruneEnvId,
            onDismiss = { pruneEnvironmentId = null },
            onComplete = { refreshKey++ },
        )
    }
}

@Composable
private fun ActivityCenterToolbarIcon(failedCount: Int) {
    BadgedBox(
        badge = {
            if (failedCount > 0) {
                Badge(
                    containerColor = ArcaneRed,
                    modifier = Modifier.offset(x = (-2).dp, y = 2.dp),
                ) {
                    Text(
                        failedActivityBadgeText(failedCount),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Assignment,
            contentDescription = activityCenterButtonContentDescription(failedCount),
            modifier = Modifier.size(24.dp),
        )
    }
}

internal fun failedActivityBadgeText(count: Int): String =
    if (count > 9) "9+" else count.coerceAtLeast(0).toString()

internal fun activityCenterButtonContentDescription(failedCount: Int): String =
    if (failedCount > 0) {
        "Activity Center, $failedCount failed ${if (failedCount == 1) "activity needs" else "activities need"} attention"
    } else {
        "Activity Center"
    }

@Composable
private fun NeedsAttentionSection(items: List<NeedsAttentionItem>) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Needs Attention",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 4.dp),
            )
            items.forEachIndexed { index, item ->
                NeedsAttentionRow(item = item)
                if (index < items.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                }
            }
            Box(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun NeedsAttentionRow(item: NeedsAttentionItem) {
    val tint = when (item.severity) {
        NeedsAttentionSeverity.Critical -> ArcaneRed
        NeedsAttentionSeverity.Warning -> ArcaneOrange
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.action)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${item.count}",
            style = MaterialTheme.typography.bodyMedium,
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
private fun DashboardStreamFailedBanner(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = ArcaneOrange,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Live counts paused",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

internal fun buildNeedsAttentionItems(
    environments: List<Environment>,
    streamStates: Map<String, DashboardEnvironmentStreamState>,
    totals: DashTotals?,
    failedActivities: List<Activity>,
    onOpenEnvironment: (Environment) -> Unit,
    onOpenContainers: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenVulnerabilities: (DashboardActionTargetEnvironment) -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenActivities: () -> Unit,
): List<NeedsAttentionItem> {
    val items = mutableListOf<NeedsAttentionItem>()
    val streamActionItems = aggregateStreamActionItems(streamStates)

    val offline = environments.filter { it.needsAttention }
    offline.firstOrNull()?.let { first ->
        items += NeedsAttentionItem(
            id = "offline-environments",
            title = if (offline.size == 1) "${first.displayName} unreachable" else "Environments unreachable",
            count = offline.size,
            icon = Icons.Filled.Warning,
            severity = NeedsAttentionSeverity.Critical,
            action = { onOpenEnvironment(first) },
        )
    }

    val stopped = totals?.stopped ?: 0
    if (stopped > 0) {
        items += NeedsAttentionItem(
            id = "stopped-containers",
            title = "Stopped containers",
            count = stopped,
            icon = Icons.Filled.StopCircle,
            severity = NeedsAttentionSeverity.Warning,
            action = onOpenContainers,
        )
    }

    val vulnerabilityItems = streamActionItems.vulnerabilities
    for (vulnerabilityItem in vulnerabilityItems) {
        items += NeedsAttentionItem(
            id = "vulnerabilities-${vulnerabilityItem.environment.id}",
            title = if (vulnerabilityItems.size == 1) {
                "Actionable vulnerabilities"
            } else {
                "${vulnerabilityItem.environment.name} vulnerabilities"
            },
            count = vulnerabilityItem.count,
            icon = Icons.Filled.Security,
            severity = if (vulnerabilityItem.isCritical) {
                NeedsAttentionSeverity.Critical
            } else {
                NeedsAttentionSeverity.Warning
            },
            action = { onOpenVulnerabilities(vulnerabilityItem.environment) },
        )
    }

    val updates = totals?.updates ?: 0
    if (updates > 0) {
        items += NeedsAttentionItem(
            id = "image-updates",
            title = "Image updates available",
            count = updates,
            icon = Icons.Filled.Autorenew,
            severity = NeedsAttentionSeverity.Warning,
            action = onOpenUpdates,
        )
    }

    if (streamActionItems.expiringKeys > 0) {
        items += NeedsAttentionItem(
            id = "expiring-keys",
            title = "API keys expiring soon",
            count = streamActionItems.expiringKeys,
            icon = Icons.Filled.VpnKey,
            severity = NeedsAttentionSeverity.Warning,
            action = onOpenApiKeys,
        )
    }

    if (failedActivities.isNotEmpty()) {
        items += NeedsAttentionItem(
            id = "failed-activities",
            title = "Failed activities",
            count = failedActivities.size,
            icon = Icons.Filled.Warning,
            severity = NeedsAttentionSeverity.Critical,
            action = onOpenActivities,
        )
    }

    return items
}

internal data class DashboardActionTargetEnvironment(
    val id: String,
    val name: String,
)

private data class DashboardStreamActionItemSummary(
    val vulnerabilities: List<DashboardVulnerabilityActionItem> = emptyList(),
    val expiringKeys: Int = 0,
)

private data class DashboardVulnerabilityActionItem(
    val environment: DashboardActionTargetEnvironment,
    val count: Int,
    val isCritical: Boolean,
)

private fun aggregateStreamActionItems(
    streamStates: Map<String, DashboardEnvironmentStreamState>,
): DashboardStreamActionItemSummary {
    val vulnerabilities = mutableListOf<DashboardVulnerabilityActionItem>()
    var expiringKeys = 0

    for (state in streamStates.values) {
        if (!state.hasLoaded || state.streamError) continue
        for (item in state.snapshot?.actionItems?.items.orEmpty()) {
            if (item.count <= 0) continue
            when (item.itemKind) {
                DashboardActionItemKind.ActionableVulnerabilities -> {
                    vulnerabilities += DashboardVulnerabilityActionItem(
                        environment = DashboardActionTargetEnvironment(
                            id = state.id,
                            name = state.name,
                        ),
                        count = item.count,
                        isCritical = item.itemSeverity == DashboardActionItemSeverity.Critical,
                    )
                }
                DashboardActionItemKind.ExpiringKeys -> {
                    expiringKeys += item.count
                }
                DashboardActionItemKind.StoppedContainers,
                DashboardActionItemKind.ImageUpdates,
                DashboardActionItemKind.Unknown,
                -> Unit
            }
        }
    }

    return DashboardStreamActionItemSummary(
        vulnerabilities = vulnerabilities,
        expiringKeys = expiringKeys,
    )
}

private val Environment.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: id

private val Environment.needsAttention: Boolean
    get() = status.lowercase(Locale.US) in setOf("offline", "error", "failed", "unhealthy", "unreachable")

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
