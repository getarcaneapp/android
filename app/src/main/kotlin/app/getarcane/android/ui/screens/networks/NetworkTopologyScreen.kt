package app.getarcane.android.ui.screens.networks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneTeal
import kotlinx.coroutines.CancellationException

private enum class TopologyViewMode { DIAGRAM, LIST }

private sealed interface TopologyLoadState {
    data object Loading : TopologyLoadState
    data class Error(val message: String) : TopologyLoadState
    data class Content(
        val topology: PresentedTopology,
        val loadedAtEpochMs: Long,
        val refreshError: String? = null,
    ) : TopologyLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTopologyScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val environmentId = manager.activeEnvironmentId
    var state by remember(client, environmentId.rawValue) {
        mutableStateOf<TopologyLoadState>(TopologyLoadState.Loading)
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var mode by rememberSaveable(environmentId.rawValue) { mutableStateOf(TopologyViewMode.DIAGRAM) }
    var selectedGraphIdentity by rememberSaveable(environmentId.rawValue) { mutableStateOf<String?>(null) }
    var selectedNodeId by rememberSaveable(environmentId.rawValue) { mutableStateOf<String?>(null) }

    LaunchedEffect(client, environmentId.rawValue, refreshKey) {
        val session = manager.authenticatedClientScope()
        if (client == null || session == null) {
            state = TopologyLoadState.Error("The Arcane connection is unavailable.")
            refreshing = false
            return@LaunchedEffect
        }
        val previous = state as? TopologyLoadState.Content
        if (previous == null) state = TopologyLoadState.Loading
        try {
            val response = client.networks.topology(envId = environmentId)
            if (!manager.isCurrent(session) || manager.activeEnvironmentId != environmentId) return@LaunchedEffect
            val topology = TopologyNormalizer.normalize(response)
            selectedNodeId = retainedTopologySelection(selectedGraphIdentity, topology, selectedNodeId)
            selectedGraphIdentity = topology.identity
            if (!topology.interactiveEligible) mode = TopologyViewMode.LIST
            state = TopologyLoadState.Content(topology, System.currentTimeMillis())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!manager.isCurrent(session) || manager.activeEnvironmentId != environmentId) return@LaunchedEffect
            val message = friendlyErrorMessage(error)
            state = previous?.copy(refreshError = message) ?: TopologyLoadState.Error(message)
        } finally {
            if (manager.isCurrent(session) && manager.activeEnvironmentId == environmentId) refreshing = false
        }
    }

    val content = state as? TopologyLoadState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Topology") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (content != null) {
                        if (mode == TopologyViewMode.DIAGRAM) {
                            IconButton(onClick = { mode = TopologyViewMode.LIST }) {
                                Icon(Icons.AutoMirrored.Filled.List, "Show grouped list")
                            }
                        } else if (content.topology.interactiveEligible) {
                            IconButton(onClick = { mode = TopologyViewMode.DIAGRAM }) {
                                Icon(Icons.Filled.Hub, "Show interactive diagram")
                            }
                        }
                    }
                    IconButton(
                        onClick = { refreshing = true; refreshKey++ },
                        enabled = !refreshing,
                    ) {
                        Icon(Icons.Filled.Refresh, "Refresh topology")
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
            when (val current = state) {
                TopologyLoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is TopologyLoadState.Error -> ContentUnavailable(
                    "Couldn't Load Topology",
                    Icons.Outlined.Hub,
                    current.message,
                    "Retry",
                ) { refreshKey++ }
                is TopologyLoadState.Content -> TopologyContent(
                    state = current,
                    mode = mode,
                    selectedNodeId = selectedNodeId,
                    onSelectNode = { selectedNodeId = it },
                )
            }
        }
    }
}

@Composable
private fun TopologyContent(
    state: TopologyLoadState.Content,
    mode: TopologyViewMode,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
) {
    val topology = state.topology
    if (topology.nodes.isEmpty()) {
        ContentUnavailable(
            "No Network Topology",
            Icons.Outlined.Hub,
            "No network or container topology data is available for this environment.",
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        state.refreshError?.let { error ->
            TopologyNotice(
                "Showing the last in-memory topology. Refresh failed: $error",
                MaterialTheme.colorScheme.errorContainer,
            )
        }
        if (!topology.interactiveEligible) {
            TopologyNotice(
                topology.nonInteractiveReason(),
                MaterialTheme.colorScheme.secondaryContainer,
            )
        } else if (topology.issues.total > 0) {
            TopologyNotice(
                "${topology.issues.total} malformed, duplicate, unknown, or incomplete topology items were safely omitted.",
                MaterialTheme.colorScheme.secondaryContainer,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode == TopologyViewMode.DIAGRAM && topology.interactiveEligible) {
                InteractiveTopologyDiagram(
                    topology = topology,
                    selectedNodeId = selectedNodeId,
                    onSelectNode = onSelectNode,
                )
            } else {
                TopologyList(topology, selectedNodeId, onSelectNode)
            }
        }
    }

    val selected = selectedNodeId?.let(topology.nodesById::get)
    if (selected != null) {
        TopologyNodeDetails(
            node = selected,
            topology = topology,
            onDismiss = { onSelectNode(null) },
        )
    }
}

@Composable
private fun TopologyNotice(message: String, color: Color) {
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun InteractiveTopologyDiagram(
    topology: PresentedTopology,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
) {
    val graph = remember(topology.identity) { TopologyGraphLayout.build(topology) }
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by rememberSaveable(topology.identity) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(topology.identity) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(topology.identity) { mutableFloatStateOf(0f) }
    var fittedSize by remember(topology.identity) { mutableStateOf(IntSize.Zero) }

    fun contentPixels(): TopologySize = with(density) {
        TopologySize(graph.contentSize.width.dp.toPx(), graph.contentSize.height.dp.toPx())
    }

    fun fit(size: IntSize) {
        val fitted = TopologyViewport.fit(
            TopologySize(size.width.toFloat(), size.height.toFloat()),
            contentPixels(),
        )
        scale = fitted.scale
        offsetX = fitted.offsetX
        offsetY = fitted.offsetY
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .semantics {
                contentDescription = "Interactive network-to-container diagram. Pinch to zoom and drag to pan."
            }
            .onSizeChanged { size ->
                viewportSize = size
                if (size != IntSize.Zero && fittedSize != size) {
                    fit(size)
                    fittedSize = size
                }
            }
            .pointerInput(topology.identity, viewportSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = TopologyViewport.clamp(
                        viewport = TopologySize(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
                        content = contentPixels(),
                        proposed = TopologyViewport(scale * zoom, offsetX + pan.x, offsetY + pan.y),
                    )
                    scale = next.scale
                    offsetX = next.offsetX
                    offsetY = next.offsetY
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(graph.contentSize.width.dp, graph.contentSize.height.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        ) {
            TopologyEdges(graph)
            graph.nodes.forEach { node ->
                val position = graph.positions.getValue(node.id)
                val size = graph.nodeSize(node)
                TopologyNodeCard(
                    node = node,
                    selected = node.id == selectedNodeId,
                    connectionCount = graph.edges.count { it.source == node.id || it.target == node.id },
                    onClick = { onSelectNode(node.id) },
                    modifier = Modifier
                        .offset(
                            x = (position.x - size.width / 2).dp,
                            y = (position.y - size.height / 2).dp,
                        )
                        .requiredSize(size.width.dp, size.height.dp),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = { fit(viewportSize); fittedSize = viewportSize }) {
                Icon(Icons.Filled.ZoomOutMap, "Fit topology to screen")
            }
            FilledTonalIconButton(onClick = {
                val reset = TopologyViewport.clamp(
                    TopologySize(viewportSize.width.toFloat(), viewportSize.height.toFloat()),
                    contentPixels(),
                    TopologyViewport(1f, 0f, 0f),
                )
                scale = reset.scale
                offsetX = reset.offsetX
                offsetY = reset.offsetY
            }) {
                Icon(Icons.Filled.RestartAlt, "Reset topology zoom")
            }
        }
    }
}

@Composable
private fun TopologyEdges(graph: TopologyGraphLayout) {
    val density = LocalDensity.current
    val nodesById = remember(graph.nodes) { graph.nodes.associateBy(PresentedTopologyNode::id) }
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        graph.edges.forEach { edge ->
            val source = nodesById[edge.source] ?: return@forEach
            val target = nodesById[edge.target] ?: return@forEach
            val sourcePosition = graph.positions.getValue(source.id)
            val targetPosition = graph.positions.getValue(target.id)
            val sourceSize = graph.nodeSize(source)
            val targetSize = graph.nodeSize(target)
            val startX = with(density) { (sourcePosition.x + sourceSize.width / 2).dp.toPx() }
            val startY = with(density) { sourcePosition.y.dp.toPx() }
            val endX = with(density) { (targetPosition.x - targetSize.width / 2).dp.toPx() }
            val endY = with(density) { targetPosition.y.dp.toPx() }
            val control = ((endX - startX) * 0.45f).coerceAtLeast(with(density) { 40.dp.toPx() })
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(startX + control, startY, endX - control, endY, endX, endY)
            }
            drawPath(path, color = Color.Gray.copy(alpha = 0.65f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            val arrow = Path().apply {
                moveTo(endX, endY)
                lineTo(endX - 10.dp.toPx(), endY - 6.dp.toPx())
                lineTo(endX - 10.dp.toPx(), endY + 6.dp.toPx())
                close()
            }
            drawPath(arrow, Color.Gray)
        }
    }
}

private fun PresentedTopology.nonInteractiveReason(): String = when {
    issues.inputNodesTruncated > 0 || issues.inputEdgesTruncated > 0 ->
        "The server topology exceeds the safe input bound of ${TopologyNormalizer.MAXIMUM_INPUT_NODES} nodes " +
            "or ${TopologyNormalizer.MAXIMUM_INPUT_EDGES} edges. Showing the bounded grouped list."
    nodes.none { it.kind != PresentedTopologyNodeKind.UNKNOWN } ->
        "No supported network-to-container relationships are available. Showing the grouped list."
    else ->
        "This graph exceeds the interactive limit of ${TopologyNormalizer.MAXIMUM_INTERACTIVE_NODES} nodes " +
            "or ${TopologyNormalizer.MAXIMUM_INTERACTIVE_EDGES} edges. Showing the bounded grouped list."
}

@Composable
private fun TopologyNodeCard(
    node: PresentedTopologyNode,
    selected: Boolean,
    connectionCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNetwork = node.kind == PresentedTopologyNodeKind.NETWORK
    val accent = if (isNetwork) ArcaneTeal else when (node.status?.lowercase()) {
        "running" -> Color(0xFF2E7D32)
        "paused" -> Color(0xFFED6C02)
        "exited", "dead" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier
            .border(if (selected) 3.dp else 1.dp, accent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .focusable()
            .clearAndSetSemantics {
                role = Role.Button
                this.selected = selected
                contentDescription = buildString {
                    append(if (isNetwork) "Network" else "Container")
                    append(", ").append(node.label)
                    node.status?.let { append(", status ").append(it) }
                    append(", ").append(connectionCount).append(" connections")
                }
                onClick(label = "Show node details") {
                    onClick()
                    true
                }
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.14f), CircleShape), Alignment.Center) {
                    Icon(
                        if (isNetwork) Icons.Filled.Lan else Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        node.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isNetwork) listOfNotNull(node.driver, node.scope).joinToString(" · ").ifBlank { "Network" }
                        else node.status?.replaceFirstChar(Char::uppercase) ?: "Container",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            Text(
                "$connectionCount connection${if (connectionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isNetwork && node.image != null) {
                Text(
                    node.image,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TopologyList(
    topology: PresentedTopology,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
) {
    val networks = topology.nodes.filter { it.kind == PresentedTopologyNodeKind.NETWORK }
    val edgesBySource = topology.edges.groupBy { it.source }
    val attachedContainers = topology.edges.mapTo(mutableSetOf()) { it.target }
    val orphans = topology.nodes.filter {
        it.kind != PresentedTopologyNodeKind.NETWORK && it.id !in attachedContainers
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        networks.forEach { network ->
            item(key = "network-${network.id}") {
                TopologyListNode(
                    node = network,
                    subtitle = listOfNotNull(network.driver, "${edgesBySource[network.id].orEmpty().size} connected")
                        .joinToString(" · "),
                    selected = selectedNodeId == network.id,
                    onClick = { onSelectNode(network.id) },
                )
            }
            val networkEdges = edgesBySource[network.id].orEmpty()
            if (networkEdges.isEmpty()) {
                item(key = "empty-${network.id}") {
                    Text(
                        "No connected containers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 68.dp, bottom = 8.dp),
                    )
                }
            } else {
                items(networkEdges, key = { "edge-${it.key}" }) { edge ->
                    val container = topology.nodesById.getValue(edge.target)
                    TopologyListNode(
                        node = container,
                        subtitle = edge.ipv4Address ?: edge.ipv6Address ?: container.image ?: "No assigned address",
                        selected = selectedNodeId == container.id,
                        indent = true,
                        onClick = { onSelectNode(container.id) },
                    )
                }
            }
        }
        if (orphans.isNotEmpty()) {
            item(key = "orphans-title") {
                Text(
                    "UNATTACHED OR UNSUPPORTED NODES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(orphans, key = { "orphan-${it.id}" }) { node ->
                TopologyListNode(
                    node = node,
                    subtitle = node.status ?: node.image ?: "No valid topology relationship",
                    selected = selectedNodeId == node.id,
                    onClick = { onSelectNode(node.id) },
                )
            }
        }
    }
}

@Composable
private fun TopologyListNode(
    node: PresentedTopologyNode,
    subtitle: String,
    selected: Boolean,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .focusable()
            .clearAndSetSemantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "${node.kind.name.lowercase()}, ${node.label}, $subtitle"
                onClick(label = "Show node details") {
                    onClick()
                    true
                }
            }
            .padding(start = if (indent) 48.dp else 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (node.kind == PresentedTopologyNodeKind.NETWORK) Icons.Filled.Lan else Icons.Filled.Inventory2,
            contentDescription = null,
            tint = if (node.kind == PresentedTopologyNodeKind.NETWORK) ArcaneTeal else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(node.label, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (subtitle.contains('/') || subtitle.contains(':')) FontFamily.Monospace else FontFamily.Default,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopologyNodeDetails(
    node: PresentedTopologyNode,
    topology: PresentedTopology,
    onDismiss: () -> Unit,
) {
    val connections = topology.edges.filter { it.source == node.id || it.target == node.id }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(node.label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    node.kind.name.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { HorizontalDivider() }
            listOfNotNull(
                node.driver?.let { "Driver" to it },
                node.scope?.let { "Scope" to it },
                node.status?.let { "Status" to it },
                node.image?.let { "Image" to it },
                if (node.kind == PresentedTopologyNodeKind.NETWORK) "Default" to if (node.isDefault) "Yes" else "No" else null,
            ).forEach { (label, value) ->
                item(key = "metadata-$label") { DetailRow(label, value) }
            }
            item {
                Text("Connections (${connections.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (connections.isEmpty()) {
                item { Text("No connected nodes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(connections, key = { "details-${it.key}" }) { edge ->
                    val otherId = if (edge.source == node.id) edge.target else edge.source
                    val other = topology.nodesById[otherId]
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(other?.label ?: otherId, fontWeight = FontWeight.SemiBold)
                        edge.ipv4Address?.let { DetailRow("IPv4", it) }
                        edge.ipv6Address?.let { DetailRow("IPv6", it) }
                        if (edge.ipv4Address == null && edge.ipv6Address == null) {
                            Text("No assigned IP address", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
