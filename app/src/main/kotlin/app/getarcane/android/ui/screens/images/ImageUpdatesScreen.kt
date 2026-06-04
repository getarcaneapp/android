package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.image.ImageSummary
import app.getarcane.sdk.models.image.ImageUpdateInfo
import app.getarcane.sdk.models.imageupdate.ImageUpdateResponse
import app.getarcane.sdk.models.imageupdate.ImageUpdateSummary
import kotlinx.coroutines.launch

/** Per-image update status (current vs latest). Mirrors iOS `ImageUpdatesView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageUpdatesScreen(images: List<ImageSummary>, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    val taggedRefs = remember(images) {
        images.flatMap { it.repoTags }.filter { it != "<none>:<none>" }.distinct()
    }

    var summary by remember { mutableStateOf<ImageUpdateSummary?>(null) }
    val byRef = remember { mutableStateMapOf<String, ImageUpdateResponse>() }
    var isScanning by remember { mutableStateOf(false) }
    var checkingRef by remember { mutableStateOf<String?>(null) }
    var loadingSummary by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    suspend fun loadSummary() {
        if (client == null) return
        loadingSummary = true
        try {
            summary = client.images.updateSummary(envId = envId)
        } catch (e: Throwable) {
            errorMessage = friendlyErrorMessage(e)
        } finally {
            loadingSummary = false
        }
    }

    suspend fun loadByRefs() {
        if (client == null || taggedRefs.isEmpty()) return
        runCatching {
            val map = client.images.updateInfoByRefs(envId = envId, imageRefs = taggedRefs)
            byRef.clear()
            for ((ref, info) in map) if (info != null) byRef[ref] = info.toResponse()
        }
    }

    LaunchedEffect(reloadTick) {
        loadSummary()
        loadByRefs()
        refreshing = false
    }

    fun recheck(ref: String) {
        if (client == null) return
        checkingRef = ref
        scope.launch {
            try {
                byRef[ref] = client.images.checkUpdateByRef(envId = envId, imageRef = ref)
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                checkingRef = null
            }
        }
    }

    fun scanAll() {
        if (client == null) return
        isScanning = true
        scope.launch {
            try {
                client.images.checkAllUpdates(envId = envId)
                loadSummary()
                loadByRefs()
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isScanning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
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
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadTick++ },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DetailSection("Summary") { SummaryStrip(summary, loadingSummary) } }

                item {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.TextButton(
                                onClick = { scanAll() },
                                enabled = !isScanning,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                                Text("  Scan all images")
                            }
                            Spacer(Modifier.weight(1f))
                            if (isScanning) CircularProgressIndicator(Modifier.size(18.dp))
                        }
                        Text(
                            "Contacts each image's registry. Can take a while for large environments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (taggedRefs.isNotEmpty()) {
                    item {
                        Text(
                            "IMAGES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(taggedRefs.size, key = { taggedRefs[it] }) { i ->
                        val ref = taggedRefs[i]
                        UpdateRow(
                            ref = ref,
                            info = byRef[ref],
                            isChecking = checkingRef == ref,
                            onRecheck = { recheck(ref) },
                        )
                    }
                }

                errorMessage?.let { msg ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                null,
                                tint = ArcaneRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArcaneRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(summary: ImageUpdateSummary?, isLoading: Boolean) {
    when {
        summary != null -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpdateMetric(
                "Total",
                "${summary.totalImages}",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
            UpdateMetric(
                "With updates",
                "${summary.imagesWithUpdates}",
                if (summary.imagesWithUpdates > 0) ArcaneOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
            UpdateMetric(
                "Digest",
                "${summary.digestUpdates}",
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f)
            )
            UpdateMetric(
                "Errors",
                "${summary.errorsCount}",
                if (summary.errorsCount > 0) ArcaneRed else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
        }

        isLoading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(Modifier.size(18.dp))
            Text(
                "Loading summary…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        else -> Text(
            "No summary available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpdateMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpdateRow(
    ref: String,
    info: ImageUpdateResponse?,
    isChecking: Boolean,
    onRecheck: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                ref,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                info == null -> Text(
                    "Not yet checked",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                !info.error.isNullOrEmpty() -> Text(
                    info.error!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaneRed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                info.hasUpdate -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowCircleUp,
                        null,
                        tint = ArcaneOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        versionLine(info),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> Text(
                    "Up to date",
                    style = MaterialTheme.typography.labelMedium,
                    color = ArcaneGreen
                )
            }
        }
        if (isChecking) {
            CircularProgressIndicator(Modifier.size(18.dp))
        } else {
            IconButton(onClick = onRecheck) { Icon(Icons.Filled.Refresh, "Recheck") }
        }
    }
}

private fun versionLine(info: ImageUpdateResponse): String {
    val latest = info.latestVersion
    val current = info.currentVersion
    if (!latest.isNullOrEmpty() && current.isNotEmpty() && latest != current) return "$current → $latest"
    if (info.updateType.isNotEmpty()) return "Update available (${info.updateType})"
    return "Update available"
}

/** The persisted by-refs map returns [ImageUpdateInfo]; adapt it to the richer [ImageUpdateResponse] used by the rows. */
private fun ImageUpdateInfo.toResponse(): ImageUpdateResponse = ImageUpdateResponse(
    hasUpdate = hasUpdate,
    updateType = updateType,
    currentVersion = currentVersion,
    latestVersion = latestVersion,
    currentDigest = currentDigest,
    latestDigest = latestDigest,
    checkTime = checkTime,
    responseTimeMs = responseTimeMs,
    error = error,
    authMethod = authMethod,
    authUsername = authUsername,
    authRegistry = authRegistry,
    usedCredential = usedCredential,
)
