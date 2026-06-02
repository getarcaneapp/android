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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.project.ProjectDetails
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

private const val PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedProjectsScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<Unit>>(Loadable.Loading) }
    val projects = remember { mutableStateListOf<ProjectDetails>() }
    var refreshKey by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadPage(page: Int, reset: Boolean) {
        if (client == null) return
        val start = ((page - 1) * PAGE_SIZE).coerceAtLeast(0)
        val response = client.projects.list(
            envId = envId,
            query = SearchPaginationSort(start = start, limit = PAGE_SIZE),
            archived = "true",
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
    }

    fun loadMore() {
        if (!hasMore || loadingMore || client == null) return
        scope.launch {
            loadingMore = true
            runCatching { loadPage(currentPage + 1, reset = false) }
            loadingMore = false
        }
    }

    fun unarchive(project: ProjectDetails) {
        if (client == null) return
        scope.launch {
            runCatching { client.projects.unarchive(envId = envId, projectId = project.id) }
                .onSuccess { projects.removeAll { it.id == project.id } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived Projects") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Archive, s.message, "Refresh") { refreshKey++ }
                is Loadable.Success -> {
                    if (projects.isEmpty()) {
                        ContentUnavailable("No Archived Projects", Icons.Outlined.Archive, "Archived projects will appear here.")
                    } else {
                        val sorted = projects.sortedByDescending { it.archivedAt ?: Instant.DISTANT_PAST }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(sorted, key = { it.id }) { project ->
                                ArchivedProjectRow(project = project, onUnarchive = { unarchive(project) })
                            }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArchivedProjectRow(project: ProjectDetails, onUnarchive: () -> Unit) {
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CachedAsyncImage(url = project.iconUrl, size = 36.dp, shape = CircleShape) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    project.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    project.archivedAt?.let { "Archived ${formatArchivedDate(it)}" } ?: "Archived",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Unarchive") },
                onClick = { menu = false; onUnarchive() },
                leadingIcon = { Icon(Icons.Filled.Unarchive, null) },
            )
        }
    }
}

/** Medium date + short time, mirroring the iOS `DateFormatter` (.medium / .short). */
private fun formatArchivedDate(instant: Instant): String {
    val millis = instant.toEpochMilliseconds()
    val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    return format.format(java.util.Date(millis))
}
