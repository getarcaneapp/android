package app.getarcane.android.ui.screens.projects

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.hasAvailableUpdate
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneYellow
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.project.DestroyProject
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.user.isAdmin
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

private enum class ProjectStatusFilter(val label: String) {
    All("All"), Running("Running"), Stopped("Stopped"), Partial("Partial")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpen: (String) -> Unit,
    onArchived: () -> Unit,
    onCreate: () -> Unit,
    onTemplateRegistries: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val pinned = LocalPinnedStore.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val isAdmin = manager.currentUser?.isAdmin ?: false
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<Unit>>(Loadable.Loading) }
    val projects = remember { mutableStateListOf<ProjectDetails>() }
    var search by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(ProjectStatusFilter.All) }
    var updateFilter by remember { mutableStateOf(ResourceUpdateFilter.ALL) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectDetails?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Pagination state.
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadPage(page: Int, reset: Boolean) {
        if (client == null) return
        val start = ((page - 1) * PAGE_SIZE).coerceAtLeast(0)
        val response = client.projects.list(
            envId = envId,
            query = SearchPaginationSort(start = start, limit = PAGE_SIZE),
        )
        if (reset) {
            projects.clear()
            projects.addAll(response.data)
        } else {
            val existing = projects.mapTo(HashSet()) { it.id }
            projects.addAll(response.data.filter { it.id !in existing })
        }
        currentPage = response.pagination.currentPage.coerceAtLeast(1)
        hasMore = response.pagination.currentPage < response.pagination.totalPages
    }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (projects.isEmpty()) state = Loadable.Loading
        state = try {
            loadPage(1, reset = true)
            Loadable.Success(Unit)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    fun loadMore() {
        if (!hasMore || loadingMore || client == null) return
        scope.launch {
            loadingMore = true
            runCatching { loadPage(currentPage + 1, reset = false) }
            loadingMore = false
        }
    }

    fun delete(project: ProjectDetails, removeFiles: Boolean) {
        if (client == null) return
        pendingDelete = null
        scope.launch {
            try {
                client.projects.destroy(
                    envId = envId,
                    projectId = project.id,
                    options = DestroyProject(removeFiles = removeFiles, removeVolumes = false),
                )
                projects.removeAll { it.id == project.id }
            } catch (e: Throwable) {
                actionError = friendlyErrorMessage(e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onTemplateRegistries) {
                            Icon(Icons.Filled.Description, "Template registries")
                        }
                    }
                    IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, "Create project") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text("Sort", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, leadingIcon = { if (sortAsc) Icon(Icons.Filled.Sort, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, leadingIcon = { if (!sortAsc) Icon(Icons.Filled.Sort, null) })
                            HorizontalDivider()
                            Text("Filter", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            ProjectStatusFilter.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.label) },
                                    onClick = { filter = f; menuOpen = false },
                                    leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                                    trailingIcon = { if (filter == f) Icon(Icons.Filled.Sort, null) },
                                )
                            }
                            HorizontalDivider()
                            Text("Updates", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            ResourceUpdateFilter.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.title) },
                                    onClick = { updateFilter = f; menuOpen = false },
                                    leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                                    trailingIcon = { if (updateFilter == f) Icon(Icons.Filled.Sort, null) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Archived Projects") },
                                onClick = { menuOpen = false; onArchived() },
                                leadingIcon = { Icon(Icons.Filled.Archive, null) },
                            )
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
                placeholder = { Text("Search projects") },
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
                    is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Layers, s.message, "Refresh") { refreshKey++ }
                    is Loadable.Success -> {
                        val pinnedIds = pinned.pinnedIds(PinnedItemsStore.Kind.PROJECT, envId)
                        val filtered = projects.filter { p ->
                            val status = p.status.lowercase()
                            val matchesStatus = when (filter) {
                                ProjectStatusFilter.All -> true
                                ProjectStatusFilter.Running -> status == "running"
                                ProjectStatusFilter.Stopped -> status == "stopped" || status == "exited"
                                ProjectStatusFilter.Partial -> status == "partial" || status == "partially running"
                            }
                            matchesStatus && updateFilter.matches(p.hasAvailableUpdate) &&
                                (search.isBlank() || p.displayName.contains(search, true))
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                            .let { if (sortAsc) it else it.reversed() }

                        val pinnedItems = filtered.filter { it.id in pinnedIds }
                        val active = filtered.filter { it.id !in pinnedIds && !it.isStopped }
                        val stopped = filtered.filter { it.id !in pinnedIds && it.isStopped }

                        if (projects.isEmpty()) {
                            ContentUnavailable(
                                "No Projects",
                                Icons.Outlined.Layers,
                                "No Compose projects found in this environment.",
                                "Create Project",
                            ) { onCreate() }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                                projectSection("Pinned", pinnedItems, pinnedIds, envId, pinned, onOpen) { pendingDelete = it }
                                projectSection("Active", active, pinnedIds, envId, pinned, onOpen) { pendingDelete = it }
                                projectSection("Stopped", stopped, pinnedIds, envId, pinned, onOpen) { pendingDelete = it }
                                if (hasMore) {
                                    item(key = "load-more") {
                                        LaunchedEffect(currentPage) { loadMore() }
                                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                            CircularProgressIndicator(Modifier.size(24.dp))
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

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Remove the project from Arcane, or also remove its files from disk.") },
            confirmButton = {
                Column {
                    TextButton(onClick = { delete(project, removeFiles = false) }) { Text("Delete") }
                    TextButton(onClick = { delete(project, removeFiles = true) }) { Text("Delete and Remove Files") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Couldn't Delete Project") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } },
        )
    }
}

private fun LazyListScope.projectSection(
    title: String,
    items: List<ProjectDetails>,
    pinnedIds: Set<String>,
    envId: EnvironmentId,
    pinned: PinnedItemsStore,
    onOpen: (String) -> Unit,
    onDelete: (ProjectDetails) -> Unit,
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
    items(items, key = { it.id }) { project ->
        ProjectRow(
            project = project,
            isPinned = project.id in pinnedIds,
            onClick = { onOpen(project.id) },
            onTogglePin = { pinned.togglePin(project.id, PinnedItemsStore.Kind.PROJECT, envId) },
            onDelete = { onDelete(project) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: ProjectDetails,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CachedAsyncImage(url = project.iconUrl, size = 36.dp, shape = CircleShape) {
                Box(
                    Modifier.size(36.dp).background(ArcaneOrange, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Layers, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        project.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isPinned) Icon(Icons.Filled.PushPin, null, tint = ArcaneYellow, modifier = Modifier.size(12.dp))
                }
            }
            StatusBadge(status = project.status)
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
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            )
        }
    }
}
