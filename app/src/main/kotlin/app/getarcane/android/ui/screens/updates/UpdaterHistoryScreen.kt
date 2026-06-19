package app.getarcane.android.ui.screens.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.screens.jobs.absoluteDateTime
import app.getarcane.android.ui.screens.jobs.relativeTime
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.serialization.ArcaneInstantSerializer
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

private const val PAGE_SIZE = 50

@Serializable
internal data class UpdaterHistoryEnvelope(
    val success: Boolean = true,
    val data: List<UpdaterHistoryRecord> = emptyList(),
)

@Serializable
internal data class UpdaterHistoryRecord(
    val id: String,
    val resourceId: String,
    val resourceType: String,
    val resourceName: String,
    val status: String,
    @Serializable(with = ArcaneInstantSerializer::class)
    val startTime: Instant,
    @Serializable(with = ArcaneInstantSerializer::class)
    val endTime: Instant? = null,
    val updateAvailable: Boolean,
    val updateApplied: Boolean,
    val oldImageVersions: Map<String, JsonValue>? = null,
    val newImageVersions: Map<String, JsonValue>? = null,
    val error: String? = null,
    val details: Map<String, JsonValue>? = null,
    @Serializable(with = ArcaneInstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = ArcaneInstantSerializer::class)
    val updatedAt: Instant? = null,
)

internal fun parseUpdaterHistory(text: String): List<UpdaterHistoryRecord> =
    app.getarcane.sdk.serialization.ArcaneJson.default.decodeFromString<UpdaterHistoryEnvelope>(text).data

internal suspend fun loadUpdaterHistory(client: ArcaneClient, envId: EnvironmentId, limit: Int): List<UpdaterHistoryRecord> {
    val text = client.transport.rawRequestText(
        path = client.rest.environmentPath(envId, "updater/history"),
        query = listOf("limit" to limit.toString()),
    )
    return client.configuration.json.decodeFromString<UpdaterHistoryEnvelope>(text).data
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterHistoryScreen(onBack: () -> Unit, environmentId: EnvironmentId? = null, environmentName: String? = null) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = environmentId ?: manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<UpdaterHistoryRecord>>>(Loadable.Loading) }
    var limit by remember { mutableStateOf(PAGE_SIZE) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<UpdaterHistoryRecord?>(null) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        limit = PAGE_SIZE
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            val fetched = loadUpdaterHistory(client, envId, limit)
            hasMore = fetched.size >= limit
            Loadable.Success(fetched)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    fun loadMore() {
        if (client == null || isLoadingMore) return
        isLoadingMore = true
        val newLimit = limit + PAGE_SIZE
        scope.launch {
            runCatching { loadUpdaterHistory(client, envId, newLimit) }
                .onSuccess { fetched ->
                    state = Loadable.Success(fetched)
                    limit = newLimit
                    hasMore = fetched.size >= newLimit
                }
            isLoadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updater History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, "Refresh") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search ${environmentName ?: manager.activeEnvironmentName} history") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Couldn't Load History", Icons.Filled.History, s.message, "Refresh") { refreshKey++ }
                is Loadable.Success -> {
                    val q = search.trim()
                    val filtered = if (q.isEmpty()) s.value else s.value.filter {
                        it.resourceName.contains(q, true) || it.resourceType.contains(q, true) || it.status.contains(q, true)
                    }
                    if (s.value.isEmpty()) {
                        ContentUnavailable("No Update History", Icons.Filled.History)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { record ->
                                UpdaterHistoryRow(record = record, onClick = { selected = record })
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                            if (hasMore && q.isEmpty()) {
                                item(key = "show-more") {
                                    Row(
                                        Modifier.fillMaxWidth().clickable(enabled = !isLoadingMore) { loadMore() }.padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isLoadingMore) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        } else {
                                            Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(18.dp))
                                            Text("  Show More", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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

    selected?.let { record ->
        UpdaterHistoryDetailDialog(record = record, onDismiss = { selected = null })
    }
}

@Composable
private fun UpdaterHistoryRow(record: UpdaterHistoryRecord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(typeTint(record.resourceType).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(typeIcon(record.resourceType), null, tint = typeTint(record.resourceType), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(record.resourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.resourceType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("•", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Text(relativeTime(record.startTime.toEpochMilliseconds()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            recordImageChange(record)?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        StatusPill(text = recordStatusText(record), tint = recordStatusTint(record))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdaterHistoryDetailDialog(record: UpdaterHistoryRecord, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(record.resourceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LabeledRow("Resource", record.resourceName)
            LabeledRow("Type", record.resourceType.replaceFirstChar { it.uppercase() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(text = recordStatusText(record), tint = recordStatusTint(record))
            }
            LabeledRow("Update Applied", if (record.updateApplied) "Yes" else "No")
            LabeledRow("Update Available", if (record.updateAvailable) "Yes" else "No")

            HorizontalDivider()
            Text("Timing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LabeledRow("Started", absoluteDateTime(record.startTime.toEpochMilliseconds()))
            record.endTime?.let { end ->
                LabeledRow("Ended", absoluteDateTime(end.toEpochMilliseconds()))
                val durationMs = end.toEpochMilliseconds() - record.startTime.toEpochMilliseconds()
                if (durationMs > 0) LabeledRow("Duration", formatDuration(durationMs))
            }

            record.error?.takeIf { it.isNotEmpty() }?.let { err ->
                HorizontalDivider()
                Text("Error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(err, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
            }

            val oldVersions = versionMap(record.oldImageVersions)
            val newVersions = versionMap(record.newImageVersions)
            if (oldVersions.isNotEmpty() || newVersions.isNotEmpty()) {
                HorizontalDivider()
                Text("Image Versions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                (oldVersions.keys + newVersions.keys).toSortedSet().forEach { key ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(oldVersions[key] ?: "—", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Text(newVersions[key] ?: "—", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Identifiers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LabeledRow("Resource ID", record.resourceId, mono = true)
            LabeledRow("Record ID", record.id, mono = true)
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun StatusPill(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier.background(tint.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun typeIcon(type: String): ImageVector = when (type.lowercase()) {
    "container" -> Icons.Filled.Inventory2
    "project", "stack" -> Icons.Filled.Folder
    "image" -> Icons.Filled.Image
    else -> Icons.Filled.Sync
}

private fun typeTint(type: String): Color = when (type.lowercase()) {
    "container" -> ArcaneBlue
    "project", "stack" -> ArcanePurple
    "image" -> ArcanePink
    else -> ArcaneGray
}

private fun recordStatusText(record: UpdaterHistoryRecord): String {
    if (!record.error.isNullOrEmpty()) return "Failed"
    if (record.updateApplied) return "Updated"
    if (record.updateAvailable) return "Available"
    return record.status.replaceFirstChar { it.uppercase() }
}

private fun recordStatusTint(record: UpdaterHistoryRecord): Color {
    if (!record.error.isNullOrEmpty()) return ArcaneRed
    if (record.updateApplied) return ArcaneGreen
    if (record.updateAvailable) return ArcaneOrange
    return when (record.status.lowercase()) {
        "skipped", "ignored" -> ArcaneGray
        "failed", "error" -> ArcaneRed
        "updated", "success" -> ArcaneGreen
        else -> ArcaneBlue
    }
}

/** Decode a string value out of a JsonValue, used for old/new image-version maps. */
private fun versionMap(raw: Map<String, JsonValue>?): Map<String, String> {
    if (raw == null) return emptyMap()
    return raw.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.value?.let { k to it } }.toMap()
}

private fun recordImageChange(record: UpdaterHistoryRecord): String? {
    val oldVersions = versionMap(record.oldImageVersions)
    val newVersions = versionMap(record.newImageVersions)
    val key = newVersions.keys.firstOrNull() ?: oldVersions.keys.firstOrNull() ?: return null
    val oldTag = oldVersions[key]
    val newTag = newVersions[key]
    return when {
        oldTag != null && newTag != null && oldTag != newTag -> "$oldTag → $newTag"
        oldTag != null -> oldTag
        newTag != null -> newTag
        else -> null
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return buildList {
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (s > 0 || isEmpty()) add("${s}s")
    }.joinToString(" ")
}
