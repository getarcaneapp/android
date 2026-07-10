package app.getarcane.android.ui.screens.updates

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.image.ImageUpdateInfo
import app.getarcane.sdk.models.imageupdate.ImageUpdateResponse
import app.getarcane.sdk.models.imageupdate.ImageUpdateSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ALL_ENV_IMAGES_PAGE_SIZE = 500

private val ImageUpdateInfo.hasDefinitiveUpdateInfo: Boolean
    get() = hasUpdate ||
        error.isNotEmpty() ||
        currentVersion.isNotEmpty() ||
        currentDigest.isNotEmpty() ||
        latestDigest.isNotEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllEnvironmentsImageUpdatesScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()

    var buckets by remember { mutableStateOf<List<ImageUpdateEnvironmentBucket>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasLoadedOnce by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var checkingKey by remember { mutableStateOf<String?>(null) }
    var rescanningEnvironmentId by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        if (client == null) return
        if (!hasLoadedOnce) loading = true
        val environments = runCatching { client.environments.list().data }
            .getOrDefault(emptyList())
            .filter { it.enabled }
        buckets = coroutineScope {
            environments.map { environment ->
                async {
                    loadImageUpdateBucket(client, environment)
                }
            }.awaitAll()
        }
        loading = false
        hasLoadedOnce = true
    }

    LaunchedEffect(client, refreshKey) {
        load()
    }

    fun recheckAll(bucket: ImageUpdateEnvironmentBucket) {
        if (client == null) return
        rescanningEnvironmentId = bucket.id
        scope.launch {
            val envId = EnvironmentId(bucket.id)
            runCatching { client.images.checkAllUpdates(envId = envId) }
            buckets = buckets.map {
                if (it.id == bucket.id) {
                    loadImageUpdateBucket(client, bucket.environment)
                } else {
                    it
                }
            }
            rescanningEnvironmentId = null
        }
    }

    fun recheck(bucket: ImageUpdateEnvironmentBucket, ref: String) {
        if (client == null) return
        checkingKey = "${bucket.id}::$ref"
        scope.launch {
            val envId = EnvironmentId(bucket.id)
            val updated = runCatching { client.images.checkUpdateByRef(envId = envId, imageRef = ref) }.getOrNull()
            if (updated != null) {
                buckets = buckets.map {
                    if (it.id == bucket.id) {
                        it.copy(updateInfoByRef = it.updateInfoByRef + (ref to updated))
                    } else {
                        it
                    }
                }
            }
            checkingKey = null
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasLoadedOnce && loading -> {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("  Loading updates...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                hasLoadedOnce && buckets.isEmpty() -> {
                    Text(
                        "No environments found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    val totalUpdates = buckets.sumOf { it.updateRefs.size }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "overview") {
                            UpdatesOverviewCard(totalUpdates = totalUpdates, environmentCount = buckets.size)
                        }
                        items(buckets, key = { it.id }) { bucket ->
                            EnvironmentUpdatesCard(
                                bucket = bucket,
                                rescanning = rescanningEnvironmentId == bucket.id,
                                checkingKey = checkingKey,
                                onRecheckAll = { recheckAll(bucket) },
                                onRecheck = { ref -> recheck(bucket, ref) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesOverviewCard(totalUpdates: Int, environmentCount: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (totalUpdates > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (totalUpdates > 0) ArcaneOrange else ArcaneGreen,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (totalUpdates == 1) "1 update available" else "$totalUpdates updates available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$environmentCount ${if (environmentCount == 1) "environment" else "environments"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EnvironmentUpdatesCard(
    bucket: ImageUpdateEnvironmentBucket,
    rescanning: Boolean,
    checkingKey: String?,
    onRecheckAll: () -> Unit,
    onRecheck: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        bucket.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        bucket.summary?.let {
                            "${it.totalImages} images checked"
                        } ?: "Summary unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRecheckAll, enabled = !rescanning && !bucket.loading) {
                    if (rescanning) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Recheck")
                    }
                }
            }

            ImageUpdateSummaryStrip(summary = bucket.summary, isLoading = bucket.loading)

            bucket.errorMessage?.let { error ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = ArcaneRed, modifier = Modifier.size(16.dp))
                    Text(error, color = ArcaneRed, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (bucket.updateRefs.isEmpty()) {
                Text(
                    bucket.summary?.let { "All ${it.totalImages} images up to date" } ?: "No updates found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                bucket.updateRefs.forEach { ref ->
                    UpdateRow(
                        ref = ref,
                        info = bucket.updateInfoByRef[ref],
                        isChecking = checkingKey == "${bucket.id}::$ref",
                        onRecheck = { onRecheck(ref) },
                    )
                }
            }
        }
    }
}

private data class ImageUpdateEnvironmentBucket(
    val environment: Environment,
    val summary: ImageUpdateSummary?,
    val totalImages: Int,
    val taggedRefs: List<String>,
    val updateInfoByRef: Map<String, ImageUpdateResponse>,
    val errorMessage: String?,
    val loading: Boolean = false,
) {
    val id: String = environment.id
    val displayName: String = environment.name?.takeIf { it.isNotBlank() } ?: environment.id

    val updateRefs: List<String>
        get() {
            val tagged = taggedRefs.filter { updateInfoByRef[it]?.hasUpdate == true }
            val untagged = updateInfoByRef.entries
                .filter { it.value.hasUpdate && it.key !in taggedRefs.toSet() }
                .map { it.key }
                .sorted()
            return tagged + untagged
        }
}

private suspend fun loadImageUpdateBucket(
    client: ArcaneClient,
    environment: Environment,
): ImageUpdateEnvironmentBucket {
    val envId = EnvironmentId(environment.id)

    val summary = runCatching { client.images.updateSummary(envId = envId) }.getOrNull()
    val updateInfoByRef = mutableMapOf<String, ImageUpdateResponse>()
    var taggedRefs = emptyList<String>()
    var totalImages = 0
    var errorMessage: String? = null

    try {
        val response = client.images.list(
            envId = envId,
            query = SearchPaginationSort(start = 0, limit = ALL_ENV_IMAGES_PAGE_SIZE),
        )
        totalImages = response.pagination.totalItems.toInt()
        taggedRefs = response.data
            .flatMap { it.repoTags }
            .filter { it != "<none>:<none>" }
        for (image in response.data) {
            val info = image.updateInfo ?: continue
            if (!info.hasDefinitiveUpdateInfo) continue
            val updateResponse = info.toUpdateResponse()
            for (tag in image.repoTags) {
                if (tag != "<none>:<none>") updateInfoByRef[tag] = updateResponse
            }
        }
    } catch (e: Throwable) {
        errorMessage = friendlyErrorMessage(e)
    }

    if (taggedRefs.isNotEmpty()) {
        runCatching { client.images.updateInfoByRefs(envId = envId, imageRefs = taggedRefs) }
            .getOrNull()
            ?.forEach { (ref, info) ->
                if (info != null) updateInfoByRef[ref] = info.toUpdateResponse()
            }
    }

    return ImageUpdateEnvironmentBucket(
        environment = environment,
        summary = summary,
        totalImages = totalImages,
        taggedRefs = taggedRefs,
        updateInfoByRef = updateInfoByRef,
        errorMessage = errorMessage,
    )
}

private fun ImageUpdateInfo.toUpdateResponse(): ImageUpdateResponse = ImageUpdateResponse(
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
