package app.getarcane.android.ui.screens.networks

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
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lan
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
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.network.NetworkCreateOptions
import app.getarcane.sdk.models.network.NetworkCreateRequest
import app.getarcane.sdk.models.network.NetworkSummary
import kotlinx.coroutines.launch

/** Docker built-in networks that can't be deleted. Mirrors iOS `systemNetworkNames`. */
private val SYSTEM_NETWORKS = setOf("host", "bridge", "none")

private fun NetworkSummary.isSystem(): Boolean = name.lowercase() in SYSTEM_NETWORKS

private enum class TypeFilter(val label: String) { All("All"), Standard("Standard"), Internal("Internal") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkListScreen(onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<NetworkSummary>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf(TypeFilter.All) }
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
            Loadable.Success(client.networks.list(envId = envId, query = SearchPaginationSort(limit = 200)).data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
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
                title = { Text("Networks") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("Sort", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, trailingIcon = { if (sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, trailingIcon = { if (!sortAsc) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            HorizontalDivider()
                            Text("Type", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            TypeFilter.entries.forEach { f ->
                                DropdownMenuItem(text = { Text(f.label) }, onClick = { typeFilter = f; menuOpen = false }, trailingIcon = { if (typeFilter == f) Icon(Icons.AutoMirrored.Filled.Sort, null) })
                            }
                        }
                    }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "Create network") }
                    IconButton(onClick = { showPrune = true }) { Icon(Icons.Filled.Delete, "Prune unused networks") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search networks") },
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
                    is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Lan, s.message, "Refresh") { refreshKey++ }
                    is Loadable.Success -> {
                        // `NetworkSummary` carries no internal flag (matches iOS, where it's a
                        // stub), so the Standard filter keeps everything and Internal shows none.
                        val filtered = s.value.filter { n ->
                            val matchesSearch = search.isBlank() ||
                                n.name.contains(search, true) ||
                                n.driver.contains(search, true)
                            val matchesType = when (typeFilter) {
                                TypeFilter.All -> true
                                TypeFilter.Standard -> true
                                TypeFilter.Internal -> false
                            }
                            matchesSearch && matchesType
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                            .let { if (sortAsc) it else it.reversed() }

                        val system = filtered.filter { it.isSystem() }
                        val user = filtered.filter { !it.isSystem() }

                        if (s.value.isEmpty()) {
                            ContentUnavailable("No Networks", Icons.Outlined.Lan, "No networks found in this environment.", "Create Network") { showCreate = true }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                                networkSection("Built-in", system, onOpen, deletable = false) {}
                                networkSection(if (system.isEmpty()) "" else "Custom", user, onOpen, deletable = true) { net ->
                                    act { client!!.networks.delete(envId = envId, networkId = net.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateNetworkDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, driver, isInternal ->
                showCreate = false
                act {
                    client!!.networks.create(
                        envId = envId,
                        request = NetworkCreateRequest(name = name, options = NetworkCreateOptions(driver = driver, internal = isInternal)),
                    )
                }
            },
        )
    }

    if (showPrune) {
        AlertDialog(
            onDismissRequest = { showPrune = false },
            title = { Text("Prune Networks") },
            text = { Text("Remove all unused networks.") },
            confirmButton = { TextButton(onClick = { showPrune = false; act { client!!.networks.prune(envId = envId) } }) { Text("Prune") } },
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

private fun LazyListScope.networkSection(
    title: String,
    items: List<NetworkSummary>,
    onOpen: (String) -> Unit,
    deletable: Boolean,
    onDelete: (NetworkSummary) -> Unit,
) {
    if (items.isEmpty()) return
    if (title.isNotEmpty()) {
        item(key = "header-$title") {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
            )
        }
    }
    items(items, key = { it.id }) { network ->
        NetworkRow(
            network = network,
            deletable = deletable,
            onClick = { onOpen(network.id) },
            onDelete = { onDelete(network) },
        )
    }
    if (!deletable) {
        item(key = "footer-$title") {
            Text(
                "Built-in Docker networks can't be removed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NetworkRow(
    network: NetworkSummary,
    deletable: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (deletable) menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(ArcaneTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lan, null, tint = Color.White, modifier = Modifier.size(20.dp)) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(network.name.ifBlank { network.id.take(12) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${network.driver} · ${network.scope}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateNetworkDialog(onDismiss: () -> Unit, onCreate: (name: String, driver: String, isInternal: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("bridge") }
    var isInternal by remember { mutableStateOf(false) }
    var driverMenu by remember { mutableStateOf(false) }
    val drivers = listOf("bridge", "host", "overlay", "macvlan", "none")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Network") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Box {
                    OutlinedTextField(
                        value = driver.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Driver") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { driverMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, null) } },
                    )
                    DropdownMenu(expanded = driverMenu, onDismissRequest = { driverMenu = false }) {
                        drivers.forEach { d ->
                            DropdownMenuItem(text = { Text(d.replaceFirstChar { it.uppercase() }) }, onClick = { driver = d; driverMenu = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Internal")
                    androidx.compose.material3.Switch(checked = isInternal, onCheckedChange = { isInternal = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, driver, isInternal) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
