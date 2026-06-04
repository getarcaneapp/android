package app.getarcane.android.ui.screens.volumes

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalPinnedStore
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.android.ui.theme.ArcaneYellow
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.volume.Volume
import kotlinx.coroutines.launch

private enum class ScopeFilter(val label: String) { All("All"), Local("Local"), Global("Global") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeListScreen(onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val pinned = LocalPinnedStore.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<Volume>>>(Loadable.Loading) }
    var sizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var search by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var scopeFilter by remember { mutableStateOf(ScopeFilter.All) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showPrune by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.volumes.list(envId = envId, query = SearchPaginationSort(limit = 200)).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        // Sizes are slow / optional — load them silently and ignore failures.
        sizes = runCatching {
            client.volumes.sizes(envId = envId).associate { info -> info.name to info.size }
        }.getOrDefault(emptyMap())
        refreshing = false
    }

    fun act(block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            runCatching { block() }.onFailure { actionError = friendlyErrorMessage(it) }
            refreshKey++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volumes") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("Sort", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, trailingIcon = { if (sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, trailingIcon = { if (!sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            HorizontalDivider()
                            Text("Scope", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            ScopeFilter.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.label) }, onClick = { scopeFilter = f; menuOpen = false }, trailingIcon = { if (scopeFilter == f) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            }
                        }
                    }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "Create volume") }
                    IconButton(onClick = { showPrune = true }) { Icon(Icons.Filled.Delete, "Prune unused volumes") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search volumes") },
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
                    is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Storage, s.message, "Refresh") { refreshKey++ }
                    is Loadable.Success -> {
                        val pinnedIds = pinned.pinnedIds(PinnedItemsStore.Kind.VOLUME, envId)
                        val filtered = s.value.filter { v ->
                            val matchesSearch = search.isBlank() ||
                                v.name.contains(search, true) ||
                                v.driver.contains(search, true)
                            val matchesScope = when (scopeFilter) {
                                ScopeFilter.All -> true
                                ScopeFilter.Local -> v.scope.equals("local", true)
                                ScopeFilter.Global -> !v.scope.equals("local", true)
                            }
                            matchesSearch && matchesScope
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                            .let { if (sortAsc) it else it.reversed() }

                        val pinnedItems = filtered.filter { it.id in pinnedIds }
                        val used = filtered.filter { it.id !in pinnedIds && it.inUse }
                        val unused = filtered.filter { it.id !in pinnedIds && !it.inUse }

                        if (s.value.isEmpty()) {
                            ContentUnavailable("No Volumes", Icons.Outlined.Storage, "No volumes found in this environment.", "Create Volume") { showCreate = true }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                                volumeSection("Pinned", pinnedItems, sizes, pinnedIds, envId, pinned, onOpen) { act { client!!.volumes.remove(envId = envId, name = it.name) } }
                                volumeSection("Used", used, sizes, pinnedIds, envId, pinned, onOpen) { act { client!!.volumes.remove(envId = envId, name = it.name) } }
                                volumeSection("Unused", unused, sizes, pinnedIds, envId, pinned, onOpen) { act { client!!.volumes.remove(envId = envId, name = it.name) } }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateVolumeDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, driver ->
                showCreate = false
                act { client!!.volumes.create(envId = envId, request = app.getarcane.sdk.models.volume.CreateVolume(name = name, driver = driver.ifBlank { null })) }
            },
        )
    }

    if (showPrune) {
        AlertDialog(
            onDismissRequest = { showPrune = false },
            title = { Text("Prune Unused Volumes") },
            text = { Text("All unused volumes will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { showPrune = false; act { client!!.volumes.prune(envId = envId) } }) { Text("Prune") } },
            dismissButton = { TextButton(onClick = { showPrune = false }) { Text("Cancel") } },
        )
    }

    actionError?.let { msg ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Action Failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } },
        )
    }
}

private fun LazyListScope.volumeSection(
    title: String,
    items: List<Volume>,
    sizes: Map<String, Long>,
    pinnedIds: Set<String>,
    envId: EnvironmentId,
    pinned: PinnedItemsStore,
    onOpen: (String) -> Unit,
    onDelete: (Volume) -> Unit,
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
    items(items, key = { it.id }) { volume ->
        VolumeRow(
            volume = volume,
            size = sizes[volume.name],
            isPinned = volume.id in pinnedIds,
            onClick = { onOpen(volume.name) },
            onTogglePin = { pinned.togglePin(volume.id, PinnedItemsStore.Kind.VOLUME, envId) },
            onDelete = { onDelete(volume) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VolumeRow(
    volume: Volume,
    size: Long?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    // iOS subtitle: size (if known) then driver (when not "local").
    val subtitle = buildList {
        if (size != null && size > 0) add(formatBytes(size))
        if (!volume.driver.equals("local", true)) add(volume.driver)
    }.joinToString(" · ")

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(ArcaneTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Storage, null, tint = Color.White, modifier = Modifier.size(20.dp)) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(volume.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isPinned) Icon(Icons.Filled.PushPin, null, tint = ArcaneYellow, modifier = Modifier.size(12.dp))
                    UsageBadge(inUse = volume.inUse)
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                onClick = { menu = false; onTogglePin() },
                leadingIcon = { Icon(Icons.Filled.PushPin, null) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menu = false; onDelete() },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
            )
        }
    }
}

@Composable
private fun UsageBadge(inUse: Boolean) {
    val color = if (inUse) ArcaneGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (inUse) "In use" else "Unused",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateVolumeDialog(onDismiss: () -> Unit, onCreate: (name: String, driver: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("local") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Volume") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = driver, onValueChange = { driver = it }, label = { Text("Driver") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, driver) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
