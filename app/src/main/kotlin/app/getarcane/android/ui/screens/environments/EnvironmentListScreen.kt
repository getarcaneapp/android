package app.getarcane.android.ui.screens.environments

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.environment.Environment

/** Online when the status reads "online"/"up". Mirrors iOS `Environment.isOnline`. */
internal val Environment.isOnline: Boolean
    get() = status.equals("online", true) || status.equals("up", true)

/** Display label: the name, else the id. Mirrors iOS `environment.name ?? environment.id`. */
internal val Environment.label: String
    get() = name?.takeIf { it.isNotBlank() } ?: id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentListScreen(onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client

    var state by remember { mutableStateOf<Loadable<List<Environment>>>(Loadable.Loading) }
    var sortAsc by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.environments.list().data)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Environments") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.SwapVert, "Sort") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("A–Z") }, onClick = { sortAsc = true; menuOpen = false }, trailingIcon = { if (sortAsc) Icon(Icons.Filled.CheckCircle, null) })
                            DropdownMenuItem(text = { Text("Z–A") }, onClick = { sortAsc = false; menuOpen = false }, trailingIcon = { if (!sortAsc) Icon(Icons.Filled.CheckCircle, null) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> SkeletonListLoadingView()
                is Loadable.Error -> ContentUnavailable("Unable to load environments", Icons.Outlined.Dns, s.message, "Retry") { refreshKey++ }
                is Loadable.Success -> {
                    val sorted = s.value.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                        .let { if (sortAsc) it else it.reversed() }
                    if (sorted.isEmpty()) {
                        ContentUnavailable("No Environments", Icons.Outlined.Dns, "Add an environment to get started.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(sorted, key = { it.id }) { env ->
                                val isActive = env.id == manager.activeEnvironmentId.rawValue
                                EnvironmentRow(
                                    env = env,
                                    isActive = isActive,
                                    onClick = { onOpen(env.id) },
                                    onSetActive = { manager.setActiveEnvironment(EnvironmentId(env.id), env.label) },
                                )
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
private fun EnvironmentRow(
    env: Environment,
    isActive: Boolean,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val statusColor = if (env.isOnline) ArcaneGreen else ArcaneGray
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (!isActive) menu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(statusColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Dns, null, tint = statusColor, modifier = Modifier.size(20.dp)) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(env.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isActive) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.background(ArcaneBlue, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (env.apiUrl.isNotEmpty()) {
                    Text(env.apiUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StatusBadge(env.status)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Set Active") }, onClick = { menu = false; onSetActive() }, leadingIcon = { Icon(Icons.Filled.CheckCircle, null) })
        }
    }
}
