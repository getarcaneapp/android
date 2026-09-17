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
import androidx.compose.material.icons.filled.Add
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
import app.getarcane.android.core.ReadCachePolicy
import app.getarcane.android.core.ReadCacheRequest
import app.getarcane.android.core.ReadResource
import app.getarcane.android.core.ResilientRead
import app.getarcane.android.core.completeListQuery
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.hasAvailableUpdate
import app.getarcane.android.core.iconUrl
import app.getarcane.android.core.isRunning
import app.getarcane.android.core.sanitizedForReadCache
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.components.StaleDataBanner
import app.getarcane.android.ui.components.StaleDataInfo
import app.getarcane.android.ui.components.staleDataInfoAfterRefreshFailure
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.StatusRunning
import app.getarcane.android.ui.theme.StatusUnknown
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private val ContainerStateFilter.label: String
    get() = when (this) {
        ContainerStateFilter.All -> "All"
        ContainerStateFilter.Running -> "Running"
        ContainerStateFilter.Stopped -> "Stopped"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerListScreen(onOpen: (String) -> Unit, onCreate: (() -> Unit)? = null) {
    val manager = LocalArcaneManager.current
    val pinned = LocalPinnedStore.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()
    val cacheScope = manager.currentReadCacheScope()

    var loadState by remember(cacheScope, envId.rawValue) {
        mutableStateOf(ContainerListLoadState<List<ContainerSummary>>())
    }
    var search by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(ContainerStateFilter.All) }
    var updateFilter by remember { mutableStateOf(ResourceUpdateFilter.ALL) }
    var refreshKey by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var staleInfo by remember(cacheScope, envId.rawValue) { mutableStateOf<StaleDataInfo?>(null) }

    LaunchedEffect(client, cacheScope, manager.offlineReadSessionActive, envId.rawValue, refreshKey) {
        if (client == null || cacheScope == null) return@LaunchedEffect
        loadState = beginContainerReload(loadState)
        manager.readCache.observe(
            scope = cacheScope,
            request = ReadCacheRequest(
                resource = ReadResource.CONTAINERS,
                environmentId = envId.rawValue,
                requestIdentity = "containers?start=0&limit=-1",
                policy = ReadCachePolicy.Containers,
            ),
            serializer = ListSerializer(ContainerSummary.serializer()),
            forceRefresh = refreshKey > 0,
        ) {
                manager.awaitAuthoritativeReadScope()
                loadCompleteContainerCollection(
                    idOf = { it.id },
                    loadAll = {
                        val response = client.containers.list(
                            envId = envId,
                            query = completeListQuery(),
                        )
                        CompleteContainerResponse(
                            items = response.data,
                            totalItems = response.pagination.totalItems,
                            success = response.success,
                        )
                    },
                ).map(ContainerSummary::sanitizedForReadCache)
        }.collect { read ->
            when (read) {
                is ResilientRead.Stale -> {
                    loadState = completeContainerLoad(read.value)
                    staleInfo = staleDataInfoAfterRefreshFailure(read.storedAtEpochMs, read.refreshError)
                }
                is ResilientRead.Fresh -> {
                    loadState = completeContainerLoad(read.value)
                    staleInfo = null
                }
                is ResilientRead.Failure -> loadState = failContainerLoad(read.message)
            }
        }
    }

    fun act(block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            runCatching { block() }.onSuccess {
                cacheScope?.let {
                    manager.readCache.invalidate(
                        scope = it,
                        resources = setOf(ReadResource.CONTAINERS, ReadResource.DASHBOARD),
                        environmentId = envId.rawValue,
                    )
                }
            }
            refreshKey++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Containers") },
                actions = {
                    if (onCreate != null) {
                        IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, "Create container") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("Sort", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, leadingIcon = { if (sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, leadingIcon = { if (!sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            HorizontalDivider()
                            Text("Filter", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            ContainerStateFilter.entries.forEach { f ->
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
            staleInfo?.let { StaleDataBanner(it) }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search containers") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = loadState.refreshing,
                onRefresh = {
                    loadState = beginContainerRefresh(loadState)
                    refreshKey++
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = loadState.content) {
                    is Loadable.Loading -> SkeletonListLoadingView()
                    is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Inventory2, s.message, "Refresh") { refreshKey++ }
                    is Loadable.Success -> {
                        val pinnedIds = pinned.pinnedIds(PinnedItemsStore.Kind.CONTAINER, envId)
                        val filtered = filterAndSortContainers(
                            containers = s.value,
                            search = search,
                            stateFilter = filter,
                            updateFilter = updateFilter,
                            sortAscending = sortAsc,
                        )

                        val pinnedItems = filtered.filter { it.id in pinnedIds }
                        val running = filtered.filter { it.id !in pinnedIds && it.isRunning }
                        val stopped = filtered.filter { it.id !in pinnedIds && !it.isRunning }

                        if (filtered.isEmpty() && search.isBlank() && filter == ContainerStateFilter.All && updateFilter == ResourceUpdateFilter.ALL) {
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
    val user = manager.currentUser
    val canStart = !manager.offlineReadSessionActive && user?.hasPermission(Permission.Containers.START, envId.rawValue) == true
    val canStop = !manager.offlineReadSessionActive && user?.hasPermission(Permission.Containers.STOP, envId.rawValue) == true
    val canRestart = !manager.offlineReadSessionActive && user?.hasPermission(Permission.Containers.RESTART, envId.rawValue) == true
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
            if (container.isRunning && canStop) {
                DropdownMenuItem(text = { Text("Stop") }, onClick = { menu = false; act { manager.client!!.containers.stop(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.Stop, null) })
            }
            if (container.isRunning && canRestart) {
                DropdownMenuItem(text = { Text("Restart") }, onClick = { menu = false; act { manager.client!!.containers.restart(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
            }
            if (!container.isRunning && canStart) {
                DropdownMenuItem(text = { Text("Start") }, onClick = { menu = false; act { manager.client!!.containers.start(envId = envId, id = container.id) } }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
            }
        }
    }
}
