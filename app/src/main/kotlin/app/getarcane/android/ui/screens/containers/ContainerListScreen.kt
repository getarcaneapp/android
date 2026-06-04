package app.getarcane.android.ui.screens.containers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalPinnedStore
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.android.core.ResourceUpdateFilter
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.hasAvailableUpdate
import app.getarcane.android.core.iconUrl
import app.getarcane.android.core.isRunning
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.StatusRunning
import app.getarcane.android.ui.theme.StatusUnknown
import app.getarcane.sdk.models.container.ContainerSummary
import kotlinx.coroutines.launch

private enum class StateFilter(val label: String) { All("All"), Running("Running"), Stopped("Stopped") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerListScreen(onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val pinned = LocalPinnedStore.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<ContainerSummary>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(StateFilter.All) }
    var updateFilter by remember { mutableStateOf(ResourceUpdateFilter.ALL) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.containers.list(envId = envId).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun act(block: suspend () -> Unit) {
        if (client == null) return
        scope.launch { runCatching { block() }; refreshKey++ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Containers") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("Sort", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, leadingIcon = { if (sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, leadingIcon = { if (!sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            HorizontalDivider()
                            Text("Filter", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            StateFilter.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.label) }, onClick = { filter = f; menuOpen = false }, trailingIcon = { if (filter == f) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            }
                            HorizontalDivider()
                            Text("Updates", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            ResourceUpdateFilter.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.title) }, onClick = { updateFilter = f; menuOpen = false }, trailingIcon = { if (updateFilter == f) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search containers") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is Loadable.Loading -> SkeletonListLoadingView()
                    is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Inventory2, s.message, "Refresh") { refreshKey++ }
                    is Loadable.Success -> {
                        val pinnedIds = pinned.pinnedIds(PinnedItemsStore.Kind.CONTAINER, envId)
                        val filtered = s.value.filter { c ->
                            (filter == StateFilter.All ||
                                (filter == StateFilter.Running && c.isRunning) ||
                                (filter == StateFilter.Stopped && !c.isRunning)) &&
                                updateFilter.matches(c.hasAvailableUpdate) &&
                                (search.isBlank() ||
                                    c.names.any { it.contains(search, true) } ||
                                    c.image.contains(search, true))
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                            .let { if (sortAsc) it else it.reversed() }

                        val pinnedItems = filtered.filter { it.id in pinnedIds }
                        val running = filtered.filter { it.id !in pinnedIds && it.isRunning }
                        val stopped = filtered.filter { it.id !in pinnedIds && !it.isRunning }

                        if (filtered.isEmpty() && search.isBlank() && filter == StateFilter.All && updateFilter == ResourceUpdateFilter.ALL) {
                            ContentUnavailable("No Containers", Icons.Outlined.Inventory2, "No containers found in this environment.", "Refresh") { refreshKey++ }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                                section("Pinned", pinnedItems, pinnedIds, envId, pinned, onOpen)
                                section("Running", running, pinnedIds, envId, pinned, onOpen)
                                section("Stopped", stopped, pinnedIds, envId, pinned, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<ContainerSummary>,
    pinnedIds: Set<String>,
    envId: app.getarcane.sdk.EnvironmentId,
    pinned: PinnedItemsStore,
    onOpen: (String) -> Unit,
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
    items(items, key = { it.id }) { container ->
        ContainerRow(
            container = container,
            isPinned = container.id in pinnedIds,
            onClick = { onOpen(container.id) },
            onTogglePin = { pinned.togglePin(container.id, PinnedItemsStore.Kind.CONTAINER, envId) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ContainerRow(
    container: ContainerSummary,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }

    fun act(block: suspend () -> Unit) {
        val client = manager.client ?: return
        scope.launch { runCatching { block() } }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                CachedAsyncImage(url = container.iconUrl, size = 36.dp, shape = CircleShape) {
                    Box(
                        Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Inventory2, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(10.dp)
                        .background(if (container.isRunning) StatusRunning else StatusUnknown.copy(alpha = 0.5f), CircleShape),
                )
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(container.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isPinned) Icon(Icons.Filled.PushPin, null, tint = androidx.compose.ui.graphics.Color(0xFFFFCC00), modifier = Modifier.size(12.dp))
            }
            Text(
                container.status,
                style = MaterialTheme.typography.bodySmall,
                color = if (container.isRunning) ArcaneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                onClick = { menu = false; onTogglePin() },
                leadingIcon = { Icon(Icons.Filled.PushPin, null) },
            )
            if (container.isRunning) {
                DropdownMenuItem(text = { Text("Stop") }, onClick = { menu = false; act { manager.client!!.containers.stop(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.Stop, null) })
                DropdownMenuItem(text = { Text("Restart") }, onClick = { menu = false; act { manager.client!!.containers.restart(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
            } else {
                DropdownMenuItem(text = { Text("Start") }, onClick = { menu = false; act { manager.client!!.containers.start(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
            }
        }
    }
}
