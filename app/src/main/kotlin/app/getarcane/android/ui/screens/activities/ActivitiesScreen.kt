package app.getarcane.android.ui.screens.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.ActivityCenterStore
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * Activity Center list. Live-streams background activities from every environment, with search,
 * status filtering, pull-to-refresh, and clear-history.
 *
 * @param onBack pops back to the previous screen.
 * @param onOpenDetail invoked when a row is tapped; receives the activity id and the environment id
 *   it should be fetched from (the activity's [sourceEnvironmentKey]). Wire this to a route that
 *   shows [ActivityDetailScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    onBack: (() -> Unit)? = null,
    onOpenDetail: (activityId: String, envId: String) -> Unit = { _, _ -> },
) {
    val manager = LocalArcaneManager.current
    val supportsActivities = manager.capabilities.supportsActivities
    val scope = rememberCoroutineScope()
    val store = remember { ActivityCenterStore(scope) }

    var refreshing by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearMessage by remember { mutableStateOf<String?>(null) }

    val clearableEnvironmentIds: Set<String> = remember(
        manager.currentUser,
        store.environmentIds,
    ) {
        val user = manager.currentUser ?: return@remember emptySet()
        store.environmentIds.filter { user.hasPermission("activities:delete", it) }.toSet()
    }
    val canClearHistory = clearableEnvironmentIds.isNotEmpty()

    // Load + start the live stream on appear; stop streams on dispose.
    LaunchedEffect(supportsActivities) {
        if (!supportsActivities) return@LaunchedEffect
        store.configure(manager.client)
        store.load(refresh = true)
        store.startStream()
    }
    DisposableEffect(Unit) {
        onDispose { store.stopStream() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activities") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (supportsActivities) {
                        IconButton(
                            onClick = { scope.launch { store.load(refresh = true) } },
                            enabled = !store.isLoading,
                        ) { Icon(Icons.Filled.Refresh, "Refresh") }
                        if (canClearHistory) {
                            IconButton(
                                onClick = { showClearConfirm = true },
                                enabled = store.activities.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    "Clear history",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !supportsActivities -> ContentUnavailable(
                    "Requires Arcane v2",
                    Icons.Filled.History,
                    "Activity Center is available when connected to an Arcane 2.0 server.",
                )

                store.isLoading && store.activities.isEmpty() -> SkeletonListLoadingView()

                store.errorMessage != null && store.activities.isEmpty() -> ContentUnavailable(
                    "Couldn't Load Activities",
                    Icons.Filled.Warning,
                    store.errorMessage,
                    "Retry",
                ) { scope.launch { store.load(refresh = true); store.startStream() } }

                store.activities.isEmpty() -> ContentUnavailable(
                    "No Activities",
                    Icons.Filled.History,
                    "Background work from your environments will appear here.",
                )

                else -> ActivityListContent(
                    store = store,
                    refreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch {
                            store.load(refresh = true)
                            store.startStream()
                            refreshing = false
                        }
                    },
                    onLoadMore = { scope.launch { store.loadMore() } },
                    onOpenDetail = { onOpenDetail(it.id, it.sourceEnvironmentKey) },
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Activity History?") },
            text = { Text("Running and queued activities are preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        val result = store.clearHistory(clearableEnvironmentIds)
                        if (result != null) {
                            val noun = if (result.deleted == 1L) "activity" else "activities"
                            var msg = "Cleared ${result.deleted} completed $noun."
                            if (result.failed > 0) {
                                val envNoun = if (result.failed == 1) "environment" else "environments"
                                msg += " ${result.failed} $envNoun could not be cleared."
                            }
                            clearMessage = msg
                        }
                    }
                }) { Text("Clear History") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }

    clearMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { clearMessage = null },
            title = { Text("Activity History") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { clearMessage = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityListContent(
    store: ActivityCenterStore,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (Activity) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = store.searchText,
            onValueChange = { store.searchText = it },
            placeholder = { Text("Search activities") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = store.statusFilter == filter,
                    onClick = { store.statusFilter = filter },
                    label = { Text(filter.title) },
                    leadingIcon = { Icon(filter.icon, null, Modifier.size(18.dp)) },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val filtered = store.filteredActivities
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                store.streamErrorMessage?.let { streamError ->
                    item(key = "stream-error") {
                        ErrorBanner(
                            message = streamError,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            severity = app.getarcane.android.ui.components.BannerSeverity.Warning,
                            onRetry = onRefresh,
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "no-matches") {
                        ContentUnavailable(
                            "No Matching Activities",
                            Icons.Filled.FilterListOff,
                            "Adjust the filters or search text.",
                        )
                    }
                } else {
                    items(filtered, key = { "${it.sourceEnvironmentKey}:${it.id}" }) { activity ->
                        ActivityRow(activity = activity, onClick = { onOpenDetail(activity) })
                    }

                    if (store.hasMore && store.searchText.isBlank()) {
                        item(key = "load-more") {
                            LaunchedEffect(filtered.size) { onLoadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, onClick: () -> Unit) {
    val tint = activity.statusTint()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(activity.typeIcon(), null, tint = tint, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    activity.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                StatusBadge(status = activity.status.wire)
            }

            Text(
                activity.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (activity.latestMessage.isNotEmpty()) {
                Text(
                    activity.latestMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val progress = activity.progress
            if (progress != null && activity.isCancellable) {
                LinearProgressIndicator(
                    progress = { (progress.coerceIn(0, 100)) / 100f },
                    color = tint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    relativeActivityTime(activity.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                val source = activity.sourceEnvironmentName
                if (!source.isNullOrEmpty()) {
                    Text(
                        "- $source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** Named relative time (e.g. "2 hours ago", "just now"). Mirrors iOS `.relative(presentation: .named)`. */
internal fun relativeActivityTime(instant: Instant): String {
    val now = kotlinx.datetime.Clock.System.now()
    val deltaSeconds = (now - instant).inWholeSeconds
    val past = deltaSeconds >= 0
    val secs = kotlin.math.abs(deltaSeconds)
    fun phrase(value: Long, unit: String): String {
        val plural = if (value == 1L) unit else "${unit}s"
        return if (past) "$value $plural ago" else "in $value $plural"
    }
    return when {
        secs < 5 -> "just now"
        secs < 60 -> phrase(secs, "second")
        secs < 3600 -> phrase(secs / 60, "minute")
        secs < 86_400 -> phrase(secs / 3600, "hour")
        secs < 604_800 -> phrase(secs / 86_400, "day")
        secs < 2_592_000 -> phrase(secs / 604_800, "week")
        secs < 31_536_000 -> phrase(secs / 2_592_000, "month")
        else -> phrase(secs / 31_536_000, "year")
    }
}
