package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.image.ImageSummary
import kotlinx.coroutines.launch

internal enum class TagsFilter(val label: String) { All("All"), Tagged("Tagged"), Untagged("Untagged") }

/** Update-check decoration state for an image row. Mirrors iOS `ImageUpdateState`. */
sealed interface ImageUpdateState {
    data object Unknown : ImageUpdateState
    data object UpToDate : ImageUpdateState
    data object HasUpdate : ImageUpdateState
    data class Error(val message: String) : ImageUpdateState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageListScreen(
    onLoaded: (List<ImageSummary>) -> Unit,
    onOpen: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenVulnerabilities: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var state by remember { mutableStateOf<Loadable<List<ImageSummary>>>(Loadable.Loading) }
    var updateInfo by remember { mutableStateOf<Map<String, ImageUpdateState>>(emptyMap()) }
    var search by remember { mutableStateOf("") }
    var tagsFilter by remember { mutableStateOf(TagsFilter.All) }
    var sortAsc by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    var optionsMenu by remember { mutableStateOf(false) }
    var pruneMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showPullSheet by remember { mutableStateOf(false) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var showPruneSheet by remember { mutableStateOf(false) }
    var confirmDanglingPrune by remember { mutableStateOf(false) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            val images = client.images.list(envId = envId).data
            onLoaded(images)
            // Best-effort update decoration via the persisted by-refs map.
            val refs = images.flatMap { it.repoTags }.filter { it != "<none>:<none>" }
            if (refs.isNotEmpty()) {
                runCatching {
                    val map = client.images.updateInfoByRefs(envId = envId, imageRefs = refs)
                    updateInfo = buildMap {
                        for ((ref, info) in map) {
                            if (info == null) continue
                            put(
                                ref,
                                when {
                                    !info.error.isNullOrEmpty() -> ImageUpdateState.Error(info.error!!)
                                    info.hasUpdate -> ImageUpdateState.HasUpdate
                                    else -> ImageUpdateState.UpToDate
                                },
                            )
                        }
                    }
                }
            }
            Loadable.Success(images)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun updateStateFor(image: ImageSummary): ImageUpdateState {
        for (tag in image.repoTags) {
            if (tag == "<none>:<none>") continue
            updateInfo[tag]?.let { return it }
        }
        return ImageUpdateState.Unknown
    }

    fun reportError(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun removeImage(image: ImageSummary) {
        if (client == null) return
        scope.launch {
            try {
                client.images.remove(envId = envId, id = image.id)
                refreshKey++
            } catch (e: Throwable) {
                reportError(friendlyErrorMessage(e))
            }
        }
    }

    fun quickPrune() {
        if (client == null) return
        scope.launch {
            try {
                client.images.prune(envId = envId, mode = "dangling")
                refreshKey++
            } catch (e: Throwable) {
                reportError(friendlyErrorMessage(e))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Images") },
                actions = {
                    // Options menu (sort / filter / updates / vulnerabilities / upload).
                    Box {
                        IconButton(onClick = { optionsMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = optionsMenu,
                            onDismissRequest = { optionsMenu = false }) {
                            Text(
                                "Sort",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    top = 8.dp,
                                    bottom = 4.dp
                                )
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A–Z)") },
                                onClick = { sortAsc = true; optionsMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                                trailingIcon = { if (sortAsc) Icon(Icons.Filled.Check, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z–A)") },
                                onClick = { sortAsc = false; optionsMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                                trailingIcon = { if (!sortAsc) Icon(Icons.Filled.Check, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (tagsFilter != TagsFilter.All) "Filter (1)" else "Filter…") },
                                onClick = { optionsMenu = false; showFilterSheet = true },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Updates") },
                                onClick = { optionsMenu = false; onOpenUpdates() },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdateAlt, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Vulnerabilities") },
                                onClick = { optionsMenu = false; onOpenVulnerabilities() },
                                leadingIcon = { Icon(Icons.Filled.Security, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Upload tarball…") },
                                onClick = { optionsMenu = false; showUploadSheet = true },
                                leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            )
                        }
                    }
                    // Pull image.
                    IconButton(onClick = { showPullSheet = true }) {
                        Icon(
                            Icons.Filled.Download,
                            "Pull image"
                        )
                    }
                    // Prune menu.
                    Box {
                        IconButton(onClick = { pruneMenu = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                "Prune images"
                            )
                        }
                        DropdownMenu(
                            expanded = pruneMenu,
                            onDismissRequest = { pruneMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Quick Prune (Dangling)") },
                                onClick = { pruneMenu = false; confirmDanglingPrune = true },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Prune Options…") },
                                onClick = { pruneMenu = false; showPruneSheet = true },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) },
                            )
                        }
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
                placeholder = { Text("Search images") },
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
                        "Error",
                        Icons.Filled.Warning,
                        s.message,
                        "Retry"
                    ) { refreshKey++ }

                    is Loadable.Success -> {
                        if (s.value.isEmpty()) {
                            ContentUnavailable(
                                "No Images",
                                Icons.Outlined.Layers,
                                "No images pulled to this environment yet.",
                                "Pull Image",
                            ) { showPullSheet = true }
                        } else {
                            val query = search.trim()
                            val filtered = s.value.filter { image ->
                                val matchesSearch = query.isEmpty() ||
                                        image.displayName.contains(query, true) ||
                                        image.id.contains(query, true)
                                val isTagged = image.repoTags.any { it != "<none>:<none>" }
                                val matchesTags = when (tagsFilter) {
                                    TagsFilter.All -> true
                                    TagsFilter.Tagged -> isTagged
                                    TagsFilter.Untagged -> !isTagged
                                }
                                matchesSearch && matchesTags
                            }
                                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                                .let { if (sortAsc) it else it.reversed() }

                            val used = filtered.filter { it.inUse }
                            val unused = filtered.filterNot { it.inUse }

                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                imageSection("Used", used, ::updateStateFor, onOpen, ::removeImage)
                                imageSection(
                                    "Unused",
                                    unused,
                                    ::updateStateFor,
                                    onOpen,
                                    ::removeImage
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDanglingPrune) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDanglingPrune = false },
            title = { Text("Prune Dangling Images") },
            text = { Text("Remove all dangling images. This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDanglingPrune = false; quickPrune()
                }) { Text("Prune") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDanglingPrune = false
                }) { Text("Cancel") }
            },
        )
    }

    if (showFilterSheet) {
        ImageFilterSheet(
            selected = tagsFilter,
            onSelect = { tagsFilter = it },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showPullSheet) {
        PullImageSheet(onDismiss = { showPullSheet = false }, onComplete = { refreshKey++ })
    }

    if (showUploadSheet) {
        UploadImageSheet(onDismiss = { showUploadSheet = false }, onComplete = { refreshKey++ })
    }

    if (showPruneSheet) {
        ImagePruneSheet(onDismiss = { showPruneSheet = false }, onComplete = { refreshKey++ })
    }
}

private fun LazyListScope.imageSection(
    title: String,
    items: List<ImageSummary>,
    updateStateFor: (ImageSummary) -> ImageUpdateState,
    onOpen: (String) -> Unit,
    onDelete: (ImageSummary) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { image ->
        ImageRow(
            image = image,
            updateState = updateStateFor(image),
            onClick = { onOpen(image.id) },
            onDelete = { onDelete(image) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageRow(
    image: ImageSummary,
    updateState: ImageUpdateState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(ArcanePurple, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Layers,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    image.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                UpdateStateBadge(updateState)
            }

            if (image.inUse) {
                Text("IN USE", style = MaterialTheme.typography.labelSmall, color = ArcaneGreen)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menu = false; onDelete() },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = ArcaneRed) },
            )
        }
    }
}

@Composable
private fun UpdateStateBadge(state: ImageUpdateState) {
    when (state) {
        is ImageUpdateState.Unknown -> Unit
        is ImageUpdateState.UpToDate ->
            Icon(
                Icons.Filled.CheckCircle,
                "Up to date",
                tint = ArcaneGreen,
                modifier = Modifier.size(16.dp)
            )

        is ImageUpdateState.HasUpdate ->
            Icon(
                Icons.Filled.ArrowCircleUp,
                "Update available",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

        is ImageUpdateState.Error ->
            Icon(
                Icons.Filled.Warning,
                "Update check failed",
                tint = ArcaneRed,
                modifier = Modifier.size(16.dp)
            )
    }
}
