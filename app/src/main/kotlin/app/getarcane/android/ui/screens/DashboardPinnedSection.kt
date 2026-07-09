package app.getarcane.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalPinnedStore
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.isRunning
import app.getarcane.android.ui.components.ResourceStatusBadge
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.android.ui.theme.StatusRunning
import app.getarcane.android.ui.theme.StatusUnknown
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.project.ProjectDetails
import app.getarcane.sdk.models.volume.Volume as SdkVolume
import kotlinx.coroutines.launch

@Composable
fun DashboardPinnedSection(
    refreshToken: Int,
    onOpenContainer: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenVolume: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val pinned = LocalPinnedStore.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var containers by remember { mutableStateOf<List<ContainerSummary>>(emptyList()) }
    var projects by remember { mutableStateOf<List<ProjectDetails>>(emptyList()) }
    var volumes by remember { mutableStateOf<List<SdkVolume>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(envId.rawValue, pinned.version, refreshToken, reloadKey) {
        if (client == null) return@LaunchedEffect
        val pinnedContainers = pinned.pinnedIds(PinnedItemsStore.Kind.CONTAINER, envId)
        val pinnedProjects = pinned.pinnedIds(PinnedItemsStore.Kind.PROJECT, envId)
        val pinnedVolumes = pinned.pinnedIds(PinnedItemsStore.Kind.VOLUME, envId)
        if (pinnedContainers.isEmpty() && pinnedProjects.isEmpty() && pinnedVolumes.isEmpty()) {
            containers = emptyList()
            projects = emptyList()
            volumes = emptyList()
            return@LaunchedEffect
        }

        loading = containers.isEmpty() && projects.isEmpty() && volumes.isEmpty()
        containers = if (pinnedContainers.isNotEmpty()) {
            runCatching { client.containers.list(envId = envId).data.filter { it.id in pinnedContainers } }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        projects = if (pinnedProjects.isNotEmpty()) {
            runCatching {
                client.projects.list(
                    envId = envId,
                    query = SearchPaginationSort(start = 0, limit = 500),
                ).data.filter { it.id in pinnedProjects }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        volumes = if (pinnedVolumes.isNotEmpty()) {
            runCatching {
                client.volumes.list(
                    envId = envId,
                    query = SearchPaginationSort(start = 0, limit = 500),
                ).data.filter { it.id in pinnedVolumes }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        loading = false
    }

    val items = containers.map(DashboardPinnedItem::Container) +
        projects.map(DashboardPinnedItem::Project) +
        volumes.map(DashboardPinnedItem::Volume)
    if (items.isEmpty() && !loading) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pinned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }
            items.forEachIndexed { index, item ->
                DashboardPinnedRow(
                    item = item,
                    busy = runningId == item.key,
                    actionsEnabled = runningId == null,
                    onUnpin = {
                        when (item) {
                            is DashboardPinnedItem.Container ->
                                pinned.unpin(item.value.id, PinnedItemsStore.Kind.CONTAINER, envId)
                            is DashboardPinnedItem.Project ->
                                pinned.unpin(item.value.id, PinnedItemsStore.Kind.PROJECT, envId)
                            is DashboardPinnedItem.Volume ->
                                pinned.unpin(item.value.id, PinnedItemsStore.Kind.VOLUME, envId)
                        }
                        onMessage("Unpinned ${item.title}")
                    },
                    onOpen = {
                        when (item) {
                            is DashboardPinnedItem.Container -> onOpenContainer(item.value.id)
                            is DashboardPinnedItem.Project -> onOpenProject(item.value.id)
                            is DashboardPinnedItem.Volume -> onOpenVolume(item.value.name)
                        }
                    },
                    onAction = {
                        val activeClient = manager.client ?: return@DashboardPinnedRow
                        scope.launch {
                            runningId = item.key
                            try {
                                when (item) {
                                    is DashboardPinnedItem.Container -> {
                                        if (item.value.isRunning) {
                                            activeClient.containers.stop(envId = envId, id = item.value.id)
                                            onMessage("Container stopped")
                                        } else {
                                            activeClient.containers.start(envId = envId, id = item.value.id)
                                            onMessage("Container started")
                                        }
                                    }
                                    is DashboardPinnedItem.Project -> {
                                        if (item.value.isDashboardRunning) {
                                            activeClient.projects.down(envId = envId, projectId = item.value.id)
                                            onMessage("Project stopped")
                                        } else {
                                            activeClient.projects.deploy(envId = envId, projectId = item.value.id)
                                            onMessage("Project deployed")
                                        }
                                    }
                                    is DashboardPinnedItem.Volume -> Unit
                                }
                                reloadKey++
                            } catch (e: Throwable) {
                                onMessage(friendlyErrorMessage(e))
                            } finally {
                                runningId = null
                            }
                        }
                    },
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 54.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardPinnedRow(
    item: DashboardPinnedItem,
    busy: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
    onAction: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                        onLongClick = { menu = true },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardPinnedIcon(item.icon, item.tint, item.isRunning)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ResourceStatusBadge(status = item.status)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            val actionTitle = item.actionTitle
            if (actionTitle != null) {
                IconButton(onClick = onAction, enabled = actionsEnabled) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            if (item.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = actionTitle,
                            tint = if (item.isRunning) ArcaneRed else ArcaneGreen,
                        )
                    }
                }
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Pinned item actions")
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    menu = false
                    onOpen()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
            )
            DropdownMenuItem(
                text = { Text("Unpin") },
                onClick = {
                    menu = false
                    onUnpin()
                },
                leadingIcon = { Icon(Icons.Filled.PushPin, null) },
            )
        }
    }
}

@Composable
private fun DashboardPinnedIcon(icon: ImageVector, tint: Color, isRunning: Boolean) {
    Box {
        Box(
            Modifier.size(32.dp).background(tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 1.dp, y = 1.dp)
                .size(9.dp)
                .background(if (isRunning) StatusRunning else StatusUnknown.copy(alpha = 0.5f), CircleShape),
        )
    }
}

private sealed interface DashboardPinnedItem {
    val key: String
    val title: String
    val status: String
    val isRunning: Boolean
    val icon: ImageVector
    val tint: Color
    val actionTitle: String?

    data class Container(val value: ContainerSummary) : DashboardPinnedItem {
        override val key: String = "container-${value.id}"
        override val title: String = value.displayName
        override val status: String = if (value.isRunning) "Running" else "Stopped"
        override val isRunning: Boolean = value.isRunning
        override val icon: ImageVector = Icons.Filled.Inventory2
        override val tint: Color = ArcaneOrange
        override val actionTitle: String = if (isRunning) "Stop Container" else "Start Container"
    }

    data class Project(val value: ProjectDetails) : DashboardPinnedItem {
        override val key: String = "project-${value.id}"
        override val title: String = value.name
        override val status: String = value.status
        override val isRunning: Boolean = value.isDashboardRunning
        override val icon: ImageVector = Icons.Filled.Layers
        override val tint: Color = ArcaneBlue
        override val actionTitle: String = if (isRunning) "Stop Project" else "Deploy Project"
    }

    data class Volume(val value: SdkVolume) : DashboardPinnedItem {
        override val key: String = "volume-${value.id}"
        override val title: String = value.name
        override val status: String = if (value.inUse) "In use" else "Unused"
        override val isRunning: Boolean = value.inUse
        override val icon: ImageVector = Icons.Filled.Storage
        override val tint: Color = ArcaneTeal
        override val actionTitle: String? = null
    }
}

private val ProjectDetails.isDashboardRunning: Boolean
    get() = status.equals("running", ignoreCase = true)
