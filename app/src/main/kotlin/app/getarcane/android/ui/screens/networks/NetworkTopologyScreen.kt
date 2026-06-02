package app.getarcane.android.ui.screens.networks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.network.NetworkTopology
import app.getarcane.sdk.models.network.TopologyEdge
import app.getarcane.sdk.models.network.TopologyNode
import app.getarcane.sdk.models.network.TopologyNodeType

/**
 * Renders the network topology graph (`client.networks.topology`) as a node/edge list: each network
 * node is a section header, and the containers it connects to (resolved from edges) are listed
 * beneath with their attachment IP. Mirrors iOS `NetworkTopologyView`, which surfaces the same graph
 * in a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTopologyScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var state by remember { mutableStateOf<Loadable<NetworkTopology>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.networks.topology(envId = envId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Topology") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshKey++ },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Error", Icons.Outlined.Hub, s.message, "Retry") { refreshKey++ }
                is Loadable.Success -> {
                    val topo = s.value
                    if (topo.nodes.isEmpty()) {
                        ContentUnavailable("No Topology", Icons.Outlined.Hub, "No network connections to display.")
                    } else {
                        TopologyList(topo)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopologyList(topo: NetworkTopology) {
    val nodesById = topo.nodes.associateBy { it.id }
    val networks = topo.nodes.filter { it.type == TopologyNodeType.NETWORK }.sortedBy { it.name.lowercase() }
    // Edges go network -> container; group container targets by source network.
    val edgesBySource: Map<String, List<TopologyEdge>> = topo.edges.groupBy { it.source }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        networks.forEach { network ->
            networkBlock(network, edgesBySource[network.id].orEmpty(), nodesById)
        }
        // Any container nodes not attached to a network (rare) — list them as orphans.
        val attachedTargets = topo.edges.map { it.target }.toSet()
        val orphanContainers = topo.nodes.filter { it.type == TopologyNodeType.CONTAINER && it.id !in attachedTargets }
        if (orphanContainers.isNotEmpty()) {
            item(key = "orphans-header") {
                Text(
                    "UNATTACHED CONTAINERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            orphanContainers.forEach { node ->
                item(key = "orphan-${node.id}") { ContainerNodeRow(node.name.ifBlank { node.id.take(12) }, null, node.metadata.image) }
            }
        }
    }
}

private fun LazyListScope.networkBlock(
    network: TopologyNode,
    edges: List<TopologyEdge>,
    nodesById: Map<String, TopologyNode>,
) {
    item(key = "net-${network.id}") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(ArcaneTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lan, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(network.name.ifBlank { network.id.take(12) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val driver = network.metadata.driver
                val subtitle = listOfNotNull(driver, "${edges.size} connected").joinToString(" · ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Hub, null, tint = ArcaneTeal, modifier = Modifier.size(18.dp))
        }
    }
    if (edges.isEmpty()) {
        item(key = "net-${network.id}-empty") {
            Text(
                "No connected containers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 64.dp, bottom = 8.dp),
            )
        }
    } else {
        edges.sortedBy { (nodesById[it.target]?.name ?: it.target).lowercase() }.forEach { edge ->
            item(key = "edge-${edge.id}") {
                val container = nodesById[edge.target]
                val label = container?.name?.ifBlank { container.id.take(12) } ?: edge.target.take(12)
                val ip = edge.ipv4Address?.takeIf { it.isNotEmpty() } ?: edge.ipv6Address?.takeIf { it.isNotEmpty() }
                ContainerNodeRow(label, ip, container?.metadata?.image)
            }
        }
    }
}

@Composable
private fun ContainerNodeRow(name: String, ip: String?, image: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = ip ?: image
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = if (ip != null) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
