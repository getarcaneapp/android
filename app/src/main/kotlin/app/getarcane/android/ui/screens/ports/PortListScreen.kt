package app.getarcane.android.ui.screens.ports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.components.SkeletonListLoadingView
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.port.PortMapping

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortListScreen(onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var state by remember { mutableStateOf<Loadable<List<PortMapping>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            val ports = client.ports.list(
                envId = envId,
                query = SearchPaginationSort(start = 0, limit = 500)
            ).data
            PortStore.put(ports)
            Loadable.Success(ports)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Ports") }) }) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search ports") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshKey++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is Loadable.Loading -> SkeletonListLoadingView()
                    is Loadable.Error -> ContentUnavailable(
                        "Couldn't Load Ports",
                        Icons.Outlined.SettingsEthernet,
                        s.message,
                        "Retry"
                    ) { refreshKey++ }

                    is Loadable.Success -> {
                        val query = search.trim()
                        val filtered = if (query.isEmpty()) s.value else s.value.filter { p ->
                            p.containerName.contains(query, true) ||
                                    p.protocolName.contains(query, true) ||
                                    p.containerPort.toString().contains(query) ||
                                    (p.hostPort?.toString()?.contains(query) ?: false) ||
                                    (p.hostIp?.contains(query, true) ?: false)
                        }
                        // Group by container, sort ports by host port then container port.
                        val grouped = filtered.groupBy { it.containerName }
                            .map { (name, ports) ->
                                name to ports.sortedWith(
                                    compareBy<PortMapping> {
                                        it.hostPort ?: Int.MAX_VALUE
                                    }.thenBy { it.containerPort },
                                )
                            }
                            .sortedBy { it.first.lowercase() }

                        if (s.value.isEmpty()) {
                            ContentUnavailable(
                                "No Ports",
                                Icons.Outlined.SettingsEthernet,
                                "No published ports in this environment."
                            )
                        } else if (grouped.isEmpty()) {
                            ContentUnavailable(
                                "No Matches",
                                Icons.Outlined.SettingsEthernet,
                                "No ports match \"$query\"."
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                grouped.forEach { (container, ports) ->
                                    portGroup(container, ports, onOpen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.portGroup(
    container: String,
    ports: List<PortMapping>,
    onOpen: (String) -> Unit
) {
    item(key = "header-$container") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Inventory2, null, tint = ArcaneBlue, modifier = Modifier.size(14.dp))
            Text(
                container,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                "(${ports.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    items(ports, key = { it.id }) { port ->
        PortRow(port) { onOpen(port.id) }
    }
}

@Composable
private fun PortRow(port: PortMapping, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconColor = if (port.isPublished) ArcaneGreen else ArcaneGray
        Box(
            Modifier
                .size(28.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (port.isPublished) Icons.Filled.SwapHoriz else Icons.Filled.Lock,
                null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (port.hostPort != null) {
                    Text(
                        hostString(port),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        port.containerPort.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        port.containerPort.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "(internal)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                port.protocolName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = protocolTint(port.protocolName)
            )
        }
        if (port.isPublished) {
            Text(
                "PUB",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ArcaneGreen,
                modifier = Modifier
                    .background(ArcaneGreen.copy(alpha = 0.15f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

internal fun hostString(port: PortMapping): String {
    val ip = port.hostIp?.takeIf { it.isNotEmpty() } ?: "0.0.0.0"
    val hp = port.hostPort ?: return ip
    return "$ip:$hp"
}

internal fun protocolTint(protocol: String): Color = when (protocol.lowercase()) {
    "tcp" -> ArcaneBlue
    "udp" -> ArcanePurple
    "sctp" -> ArcanePink
    else -> ArcaneGray
}

/**
 * Process-scoped cache of the most recently loaded port mappings, keyed by id. Ports have no
 * inspect endpoint, so the detail screen reads the row the list already fetched. Survives the
 * list -> detail navigation within the Ports tab.
 */
internal object PortStore {
    private var byId: Map<String, PortMapping> = emptyMap()
    fun put(ports: List<PortMapping>) {
        byId = ports.associateBy { it.id }
    }

    fun get(id: String): PortMapping? = byId[id]
}
