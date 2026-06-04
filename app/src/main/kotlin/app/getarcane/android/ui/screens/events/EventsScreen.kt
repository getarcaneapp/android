package app.getarcane.android.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.base.stringValue
import app.getarcane.sdk.models.event.Event
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Events tab with its own nested back stack (list -> detail). */
@Composable
fun EventsScreen() {
    val nav = rememberNavController()
    // Hold the loaded events here so the detail screen can resolve by ID without re-fetching
    // (the list endpoint is the only source — there is no per-event GET).
    var loaded by remember { mutableStateOf<List<Event>>(emptyList()) }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            EventListScreen(
                onLoaded = { loaded = it },
                onOpen = { id -> nav.navigate("detail/$id") },
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val event = loaded.firstOrNull { it.id == id }
            EventDetailScreen(event = event, onBack = { nav.popBackStack() })
        }
    }
}

private const val PAGE_SIZE = 50

private enum class EventSeverityFilter(
    val title: String,
    val wire: String?,
    val icon: ImageVector
) {
    All("All", null, Icons.Filled.History),
    Info("Info", "info", Icons.Filled.Info),
    Warning("Warning", "warning", Icons.Filled.Warning),
    Error("Error", "error", Icons.Filled.ErrorOutline),
    Critical("Critical", "critical", Icons.Filled.LocalFireDepartment),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventListScreen(onLoaded: (List<Event>) -> Unit, onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<Event>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(EventSeverityFilter.All) }
    var limit by remember { mutableStateOf(PAGE_SIZE) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            val response = client.events.listPaginated(start = 0, limit = limit)
            val sorted = response.data.sortedByDescending { it.timestamp }
            hasMore = response.data.size >= limit
            onLoaded(sorted)
            Loadable.Success(sorted)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun loadMore() {
        if (client == null || isLoadingMore || !hasMore) return
        isLoadingMore = true
        val newLimit = limit + PAGE_SIZE
        scope.launch {
            try {
                val response = client.events.listPaginated(start = 0, limit = newLimit)
                val sorted = response.data.sortedByDescending { it.timestamp }
                limit = newLimit
                hasMore = response.data.size >= newLimit
                onLoaded(sorted)
                state = Loadable.Success(sorted)
            } catch (_: Throwable) {
                // Keep existing page on a failed "load more".
            } finally {
                isLoadingMore = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.FilterList,
                                "Filter",
                                tint = if (severity == EventSeverityFilter.All) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                "Severity",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    top = 8.dp,
                                    bottom = 4.dp
                                ),
                            )
                            EventSeverityFilter.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.title) },
                                    onClick = { severity = f; menuOpen = false },
                                    leadingIcon = { Icon(f.icon, null) },
                                    trailingIcon = {
                                        if (severity == f) Icon(
                                            Icons.Filled.Check,
                                            null
                                        )
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { refreshing = true; refreshKey++ },
                        enabled = state !is Loadable.Loading
                    ) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search events") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is Loadable.Loading -> SkeletonListLoadingView()
                    is Loadable.Error -> ContentUnavailable(
                        "Couldn't Load Events",
                        Icons.Filled.Warning,
                        s.message,
                        "Retry",
                    ) { refreshKey++ }

                    is Loadable.Success -> {
                        val trimmed = search.trim()
                        val filtered = s.value.filter { e ->
                            val matchesSeverity =
                                severity.wire == null || e.severity.lowercase() == severity.wire
                            val matchesSearch = trimmed.isEmpty() ||
                                    e.title.contains(trimmed, true) ||
                                    (e.description ?: "").contains(trimmed, true) ||
                                    (e.resourceName ?: "").contains(trimmed, true) ||
                                    e.type.contains(trimmed, true)
                            matchesSeverity && matchesSearch
                        }
                        if (s.value.isEmpty()) {
                            ContentUnavailable("No Events", Icons.Filled.History)
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(filtered, key = { it.id }) { event ->
                                    EventRow(event) { onOpen(event.id) }
                                }
                                if (hasMore && trimmed.isEmpty() && severity == EventSeverityFilter.All) {
                                    item(key = "load-more") {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isLoadingMore) {
                                                CircularProgressIndicator(Modifier.size(22.dp))
                                            } else {
                                                TextButton(onClick = { loadMore() }) {
                                                    Icon(
                                                        Icons.Filled.ArrowDownward,
                                                        null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.size(6.dp))
                                                    Text(
                                                        "Show More",
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun severityIcon(severity: String): ImageVector = when (severity.lowercase()) {
    "critical", "fatal" -> Icons.Filled.LocalFireDepartment
    "error" -> Icons.Filled.ErrorOutline
    "warning", "warn" -> Icons.Filled.Warning
    "success" -> Icons.Filled.Check
    else -> Icons.Filled.Info
}

private fun severityTint(severity: String): Color = when (severity.lowercase()) {
    "critical", "fatal" -> ArcanePink
    "error" -> ArcaneRed
    "warning", "warn" -> ArcaneOrange
    "success" -> ArcaneGreen
    "debug" -> ArcaneGray
    else -> ArcaneBlue
}

@Composable
private fun EventRow(event: Event, onClick: () -> Unit) {
    val tint = severityTint(event.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(severityIcon(event.severity), null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            event.description?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    relativeTime(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                if (event.type.isNotEmpty()) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        event.type,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailScreen(event: Event?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back"
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (event == null) {
            Box(Modifier
                .fillMaxSize()
                .padding(padding)) {
                ErrorBanner("Event not found.", modifier = Modifier.padding(16.dp))
            }
            return@Scaffold
        }
        val tint = severityTint(event.severity)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailSection {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(tint.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                severityIcon(event.severity),
                                null,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                event.severity.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                            )
                        }
                    }
                    event.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                DetailSection("Event") {
                    LabeledRow("Type", event.type)
                    LabeledRow("Occurred", formatTimestamp(event.timestamp))
                    if (event.createdAt != event.timestamp) {
                        LabeledRow("Recorded", formatTimestamp(event.createdAt))
                    }
                }
            }

            if (event.resourceType != null || event.resourceName != null || event.resourceId != null) {
                item {
                    DetailSection("Resource") {
                        event.resourceType?.takeIf { it.isNotEmpty() }
                            ?.let { LabeledRow("Type", it.replaceFirstChar { c -> c.uppercase() }) }
                        event.resourceName?.takeIf { it.isNotEmpty() }
                            ?.let { LabeledRow("Name", it) }
                        event.resourceId?.takeIf { it.isNotEmpty() }
                            ?.let { LabeledRow("ID", it, mono = true) }
                    }
                }
            }

            if (event.username != null || event.userId != null || event.environmentId != null) {
                item {
                    DetailSection("Context") {
                        event.username?.takeIf { it.isNotEmpty() }?.let { LabeledRow("User", it) }
                        event.userId?.takeIf { it.isNotEmpty() }
                            ?.let { LabeledRow("User ID", it, mono = true) }
                        event.environmentId?.takeIf { it.isNotEmpty() }
                            ?.let { LabeledRow("Environment", it, mono = true) }
                    }
                }
            }

            val metadata = event.metadata.orEmpty()
            if (metadata.isNotEmpty()) {
                item {
                    DetailSection("Metadata") {
                        metadata.keys.sorted().forEach { key ->
                            LabeledRow(key, jsonValueString(metadata[key]))
                        }
                    }
                }
            }
        }
    }
}

private fun jsonValueString(value: JsonValue?): String = when (value) {
    null, is JsonValue.Null -> ""
    is JsonValue.Str -> value.value
    is JsonValue.Number -> {
        val d = value.value
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }

    is JsonValue.Bool -> value.value.toString()
    else -> value.stringValue ?: value.toString()
}

@Composable
private fun DetailSection(title: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        content()
    }
}

@Composable
private fun LabeledRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private val monthNames =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Named relative time (e.g. "2 hours ago", "just now"). Mirrors iOS `.relative(presentation: .named)`. */
private fun relativeTime(instant: Instant): String {
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

/** Abbreviated date + standard time (e.g. "May 31, 2026 at 3:04:21 PM"). Mirrors iOS `.abbreviated`/`.standard`. */
private fun formatTimestamp(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = monthNames.getOrElse(dt.monthNumber - 1) { dt.monthNumber.toString() }
    val hour12 = when {
        dt.hour == 0 -> 12
        dt.hour > 12 -> dt.hour - 12
        else -> dt.hour
    }
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    val second = dt.second.toString().padStart(2, '0')
    return "$month ${dt.dayOfMonth}, ${dt.year} at $hour12:$minute:$second $amPm"
}
