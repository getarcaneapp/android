package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.image.ImageHistoryItem
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageHistoryScreen(
    identity: ImageInsightIdentity,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    var state by remember(identity.requestKey) {
        mutableStateOf<ImageInsightUiState<List<ImageHistoryItem>>>(ImageInsightUiState.Loading)
    }
    var refreshRevision by remember(identity.requestKey) { mutableIntStateOf(0) }
    var refreshing by remember(identity.requestKey) { mutableStateOf(false) }

    val currentUserId = manager.currentUser?.id.orEmpty()
    val activeEnvironmentId = manager.activeEnvironmentId.rawValue
    val current = identity.isCurrent(
        manager.serverSessionIdentity,
        currentUserId,
        activeEnvironmentId,
    )
    val canRead = manager.currentUser?.hasPermission(
        Permission.Images.READ,
        identity.environmentId,
    ) == true

    LaunchedEffect(
        identity.requestKey,
        manager.client,
        currentUserId,
        manager.serverSessionIdentity,
        activeEnvironmentId,
        canRead,
        refreshRevision,
    ) {
        if (!current) {
            state = ImageInsightUiState.StaleSelection
            refreshing = false
            return@LaunchedEffect
        }
        if (!canRead) {
            state = ImageInsightUiState.Failed(
                ImageInsightFailure(
                    ImageInsightFailureKind.Unauthorized,
                    "Your account does not have image-read access for ${identity.environmentName}.",
                ),
            )
            refreshing = false
            return@LaunchedEffect
        }
        val captured = manager.authenticatedClientScope()
        if (captured == null) {
            state = ImageInsightUiState.StaleSelection
            refreshing = false
            return@LaunchedEffect
        }
        if (!refreshing) state = ImageInsightUiState.Loading
        val result = loadImageInsightResult(
            isCurrent = {
                manager.isCurrent(captured) && identity.isCurrent(
                    manager.serverSessionIdentity,
                    manager.currentUser?.id.orEmpty(),
                    manager.activeEnvironmentId.rawValue,
                )
            },
            load = {
                captured.client.images.history(
                    envId = EnvironmentId(identity.environmentId),
                    imageId = identity.imageId,
                )
            },
        )
        state = when (result) {
            is ImageInsightLoadResult.Success -> ImageInsightUiState.Content(result.value)
            is ImageInsightLoadResult.Failure -> ImageInsightUiState.Failed(result.failure)
            null -> ImageInsightUiState.StaleSelection
        }
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Layer History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ImageInsightScopeHeader(identity, "Docker image layer history · newest first")
            Box(Modifier.fillMaxSize()) {
                when (val currentState = state) {
                    ImageInsightUiState.Loading -> SkeletonListLoadingView(rows = 5)
                    ImageInsightUiState.StaleSelection -> ContentUnavailable(
                        "Image selection changed",
                        Icons.Filled.Warning,
                        "${identity.imageDisplayName} is tied to ${identity.environmentName}. Go back and select the image again.",
                        "Back",
                        onBack,
                    )
                    is ImageInsightUiState.Failed -> ImageInsightFailureContent(
                        currentState.failure,
                        onRetry = {
                            refreshing = true
                            refreshRevision++
                        },
                    )
                    is ImageInsightUiState.Content -> {
                        val history = currentState.value
                        if (history.isEmpty()) {
                            ContentUnavailable(
                                "No Layer History",
                                Icons.Filled.Layers,
                                "Docker returned no layer history for this image. This is separate from Arcane build history.",
                                "Refresh",
                            ) {
                                refreshing = true
                                refreshRevision++
                            }
                        } else {
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    refreshing = true
                                    refreshRevision++
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    itemsIndexed(
                                        history,
                                        key = { index, item -> "$index:${item.id}:${item.created}" },
                                    ) { index, item ->
                                        ImageHistoryRow(item, index, history.size)
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

@Composable
private fun ImageHistoryRow(item: ImageHistoryItem, index: Int, total: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (index == 0) "Layer 1 of $total · newest" else "Layer ${index + 1} of $total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(formatLayerSize(item), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                formatLayerId(item),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatLayerCommand(item),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "${formatLayerCreated(item)} · ${formatLayerTags(item)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.comment.trim().takeIf(String::isNotEmpty)?.let { comment ->
                Text(
                    "Comment: $comment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ImageInsightScopeHeader(identity: ImageInsightIdentity, description: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(identity.imageDisplayName, style = MaterialTheme.typography.titleSmall)
        Text(
            identity.imageId,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${identity.environmentName} · $description",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ImageInsightFailureContent(
    failure: ImageInsightFailure,
    onRetry: () -> Unit,
) {
    val (title, icon) = when (failure.kind) {
        ImageInsightFailureKind.Unauthorized -> "Permission Required" to Icons.Filled.Lock
        ImageInsightFailureKind.Unsupported -> "Not Supported" to Icons.Filled.Warning
        ImageInsightFailureKind.Malformed -> "Unexpected Data" to Icons.Filled.Warning
        ImageInsightFailureKind.Error -> "Couldn't Load Image Insight" to Icons.Filled.Warning
    }
    ContentUnavailable(
        title,
        icon,
        failure.message,
        actionLabel = if (failure.kind == ImageInsightFailureKind.Unauthorized) null else "Try Again",
        onAction = if (failure.kind == ImageInsightFailureKind.Unauthorized) null else onRetry,
    )
}
