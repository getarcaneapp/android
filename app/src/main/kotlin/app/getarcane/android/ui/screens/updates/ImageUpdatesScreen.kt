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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.image.ImageUpdateInfo
import app.getarcane.sdk.models.imageupdate.ImageUpdateResponse
import app.getarcane.sdk.models.imageupdate.ImageUpdateSummary
import kotlinx.coroutines.launch

private const val IMAGES_PAGE_SIZE = 500

/** True when the server's inline updateInfo carries an actual check result. Mirrors iOS `isDefinitive`. */
private val ImageUpdateInfo.isDefinitive: Boolean
    get() = hasUpdate || error.isNotEmpty() || currentVersion.isNotEmpty() || currentDigest.isNotEmpty() || latestDigest.isNotEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUpdatesScreen(onRunUpdater: () -> Unit, onHistory: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var summary by remember { mutableStateOf<ImageUpdateSummary?>(null) }
    val byRef = remember { mutableStateMapOf<String, ImageUpdateResponse>() }
    var taggedRefs by remember { mutableStateOf<List<String>>(emptyList()) }
    var totalImages by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var checkingRef by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun fetch() {
        if (client == null) return
        // Summary (best-effort).
        summary = runCatching { client.images.updateSummary(envId = envId) }.getOrNull()

        // Image list -> refs + inline update info.
        val refs: List<String>
        try {
            val response = client.images.list(envId = envId, query = SearchPaginationSort(start = 0, limit = IMAGES_PAGE_SIZE))
            val images = response.data
            totalImages = response.pagination.totalItems.toInt()
            refs = images.flatMap { it.repoTags }.filter { it != "<none>:<none>" }
            errorMessage = if (summary == null) "Couldn't load images." else null

            byRef.clear()
            for (image in images) {
                val info = image.updateInfo ?: continue
                if (!info.isDefinitive) continue
                val response2 = info.asResponse()
                for (tag in image.repoTags) if (tag != "<none>:<none>") byRef[tag] = response2
            }
        } catch (e: Throwable) {
            taggedRefs = emptyList()
            totalImages = 0
            if (summary == null) errorMessage = friendlyErrorMessage(e)
            return
        }
        taggedRefs = refs

        // Merge with by-refs cache (its keys take precedence).
        if (refs.isNotEmpty()) {
            runCatching { client.images.updateInfoByRefs(envId = envId, imageRefs = refs) }
                .getOrNull()
                ?.forEach { (k, v) -> if (v != null) byRef[k] = v.asResponse() }
        }
    }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (!hasLoadedOnce) loading = true
        fetch()
        loading = false
        hasLoadedOnce = true
    }

    fun recheckAll() {
        if (client == null) return
        isScanning = true
        scope.launch {
            runCatching { client.images.checkAllUpdates(envId = envId) }
            fetch()
            isScanning = false
        }
    }

    fun recheck(ref: String) {
        if (client == null) return
        checkingRef = ref
        scope.launch {
            runCatching { client.images.checkUpdateByRef(envId = envId, imageRef = ref) }
                .getOrNull()?.let { byRef[ref] = it }
            checkingRef = null
        }
    }

    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Updates") }) },
        bottomBar = {
            UpdatesActionBar(
                onRunUpdater = onRunUpdater,
                onHistory = onHistory,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!hasLoadedOnce && loading) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("  Loading updates…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val taggedSet = taggedRefs.toSet()
                val taggedWithUpdates = taggedRefs.filter { byRef[it]?.hasUpdate == true }
                val untaggedWithUpdates = byRef.entries
                    .filter { it.value.hasUpdate && it.key !in taggedSet }
                    .map { it.key }
                    .sorted()
                val withUpdates = taggedWithUpdates + untaggedWithUpdates

                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                    item(key = "env-header") {
                        Text(
                            manager.activeEnvironmentName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
                        )
                    }
                    item(key = "summary") {
                        ImageUpdateSummaryStrip(summary = summary, isLoading = loading)
                    }
                    errorMessage?.let { err ->
                        item(key = "error") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(16.dp))
                                Text(err, color = ArcaneRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (withUpdates.isNotEmpty()) {
                        items(withUpdates.size, key = { withUpdates[it] }) { i ->
                            val ref = withUpdates[i]
                            UpdateRow(
                                ref = ref,
                                info = byRef[ref],
                                isChecking = checkingRef == ref,
                                onRecheck = { recheck(ref) },
                            )
                        }
                    } else if (summary != null && (summary?.imagesWithUpdates ?: 0) > 0 && !loading) {
                        item(key = "details-unavailable") {
                            val n = summary?.imagesWithUpdates ?: 0
                            Text(
                                "Update details unavailable for $n image${if (n == 1) "" else "s"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    } else if (summary != null && !loading) {
                        item(key = "up-to-date") {
                            Text("All up to date", style = MaterialTheme.typography.bodySmall, color = ArcaneGreen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }

                    if (totalImages > taggedRefs.size && taggedRefs.isNotEmpty()) {
                        item(key = "showing-count") {
                            Text(
                                "Showing ${taggedRefs.size} of $totalImages images",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    item(key = "recheck-all") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isScanning) { recheckAll() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Text("Rechecking…", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                                Text("Recheck all images", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Strip of metric tiles. Mirrors iOS `ImageUpdateSummaryStrip`. */
@Composable
fun ImageUpdateSummaryStrip(summary: ImageUpdateSummary?, isLoading: Boolean) {
    when {
        summary != null -> {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Total", "${summary.totalImages}", MaterialTheme.colorScheme.onSurfaceVariant)
                Metric("With updates", "${summary.imagesWithUpdates}", if (summary.imagesWithUpdates > 0) ArcaneOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                Metric("Digest", "${summary.digestUpdates}", ArcaneBlue)
                Metric("Errors", "${summary.errorsCount}", if (summary.errorsCount > 0) ArcaneRed else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        isLoading -> {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text("Loading summary…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> Text("No summary available", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One image-ref row with its current update state and a recheck button. Mirrors iOS `UpdateRow`. */
@Composable
fun UpdateRow(ref: String, info: ImageUpdateResponse?, isChecking: Boolean, onRecheck: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(ref, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    info == null -> Text("Not yet checked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !info.error.isNullOrEmpty() -> Text(info.error!!, style = MaterialTheme.typography.labelSmall, color = ArcaneRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    info.hasUpdate -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.ArrowCircleUp, null, tint = ArcaneOrange, modifier = Modifier.size(16.dp))
                        Text(versionLine(info), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text("Up to date", style = MaterialTheme.typography.labelMedium, color = ArcaneGreen)
                }
            }
            if (isChecking) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                IconButton(onClick = onRecheck) { Icon(Icons.Filled.Refresh, "Recheck") }
            }
        }
        HorizontalDivider(Modifier.padding(start = 16.dp))
    }
}

private fun versionLine(info: ImageUpdateResponse): String {
    val latest = info.latestVersion
    val current = info.currentVersion
    if (!latest.isNullOrEmpty() && current.isNotEmpty() && latest != current) return "$current → $latest"
    if (info.updateType.isNotEmpty()) return "Update available (${info.updateType})"
    return "Update available"
}

/** Bridge an inline [ImageUpdateInfo] into the response shape used by [UpdateRow]. Mirrors iOS `asResponse`. */
private fun ImageUpdateInfo.asResponse(): ImageUpdateResponse = ImageUpdateResponse(
    hasUpdate = hasUpdate,
    updateType = updateType,
    currentVersion = currentVersion,
    latestVersion = latestVersion.ifEmpty { null },
    currentDigest = currentDigest.ifEmpty { null },
    latestDigest = latestDigest.ifEmpty { null },
    checkTime = checkTime,
    responseTimeMs = responseTimeMs,
    error = error.ifEmpty { null },
    authMethod = authMethod,
    authUsername = authUsername,
    authRegistry = authRegistry,
    usedCredential = usedCredential,
)

/** Bottom action bar with Run Updater / Updater History. Mirrors iOS `actionToolbar` on UpdatesView. */
@Composable
private fun UpdatesActionBar(onRunUpdater: () -> Unit, onHistory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        ActionToolbarButton("Run Updater", Icons.Filled.PlayArrow, ArcaneOrange, onRunUpdater)
        ActionToolbarButton("Updater History", Icons.Filled.History, ArcaneBlue, onHistory)
    }
}

@Composable
private fun ActionToolbarButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, title, tint = tint, modifier = Modifier.size(22.dp)) }
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
