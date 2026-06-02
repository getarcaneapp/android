package app.getarcane.android.ui.screens.networks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.base.objectValue
import app.getarcane.sdk.models.base.stringValue
import app.getarcane.sdk.models.network.NetworkContainerEndpoint
import app.getarcane.sdk.models.network.NetworkInspect
import kotlinx.coroutines.launch

private val BUILT_IN = setOf("host", "bridge", "none")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDetailScreen(networkId: String, onBack: () -> Unit, onTopology: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<NetworkInspect>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(networkId, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.networks.inspect(envId = envId, networkId = networkId))
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
        refreshing = false
    }

    val inspect = (state as? Loadable.Success)?.value
    val title = inspect?.name?.ifBlank { inspect.id.take(12) } ?: "Network"
    val isBuiltIn = inspect?.name?.lowercase() in BUILT_IN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (inspect != null && !isBuiltIn) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
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
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) { ErrorBanner(s.message, onRetry = { refreshKey++ }) }
                is Loadable.Success -> {
                    val n = s.value
                    val endpoints = resolveEndpoints(n)
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(
                                    Modifier.size(56.dp).background(ArcaneTeal.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Lan, null, tint = ArcaneTeal, modifier = Modifier.size(28.dp)) }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(n.name.ifBlank { n.id.take(12) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(n.driver, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            DetailSection("Details") {
                                KeyValueRow("ID", n.id.take(12))
                                KeyValueRow("Driver", n.driver)
                                KeyValueRow("Scope", n.scope.replaceFirstChar { it.uppercase() })
                                if (n.`internal`) KeyValueRow("Internal", "Yes")
                                KeyValueRow("Attachable", if (n.attachable) "Yes" else "No")
                                KeyValueRow("IPv4", if (n.enableIPv4) "Enabled" else "Disabled")
                                KeyValueRow("IPv6", if (n.enableIPv6) "Enabled" else "Disabled")
                                HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth().clickable(onClick = onTopology).padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Topology", style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        val ipamConfigs = n.ipam.config.orEmpty().filter {
                            !it.subnet.isNullOrEmpty() || !it.gateway.isNullOrEmpty() || !it.ipRange.isNullOrEmpty()
                        }
                        if (!n.ipam.driver.isNullOrEmpty() || ipamConfigs.isNotEmpty()) {
                            item {
                                DetailSection("IPAM") {
                                    n.ipam.driver?.takeIf { it.isNotEmpty() }?.let { KeyValueRow("Driver", it) }
                                    ipamConfigs.forEach { config ->
                                        config.subnet?.takeIf { it.isNotEmpty() }?.let { MonoRow("Subnet", it) }
                                        config.gateway?.takeIf { it.isNotEmpty() }?.let { MonoRow("Gateway", it) }
                                        config.ipRange?.takeIf { it.isNotEmpty() }?.let { MonoRow("IP Range", it) }
                                    }
                                }
                            }
                        }
                        if (endpoints.isNotEmpty()) {
                            item {
                                DetailSection("Connected Containers (${endpoints.size})") {
                                    endpoints.forEachIndexed { i, ep ->
                                        if (i > 0) HorizontalDivider(Modifier.padding(start = 36.dp))
                                        ContainerEndpointRow(ep)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Network") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (client != null) scope.launch {
                        runCatching { client.networks.delete(envId = envId, networkId = networkId) }
                            .onSuccess { onBack() }
                            .onFailure { errorMessage = friendlyErrorMessage(it) }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
        )
    }
}

/** Endpoint view-model bridging the typed list and the raw legacy `containers` map. */
private data class Endpoint(val id: String, val name: String, val ipv4: String, val ipv6: String)

/** Prefer the typed `containersList`; otherwise decode the legacy `[String: JSONValue]` map. */
private fun resolveEndpoints(n: NetworkInspect): List<Endpoint> {
    if (n.containersList.isNotEmpty()) {
        return n.containersList.map { ep: NetworkContainerEndpoint ->
            Endpoint(ep.id, ep.name, ep.ipv4Address, ep.ipv6Address)
        }
    }
    return n.containers.toSortedMap().mapNotNull { (key, value) ->
        val obj = (value as? JsonValue.Obj)?.value ?: value.objectValue ?: return@mapNotNull null
        Endpoint(
            id = key,
            name = obj["Name"]?.stringValue ?: "",
            ipv4 = obj["IPv4Address"]?.stringValue ?: "",
            ipv6 = obj["IPv6Address"]?.stringValue ?: "",
        )
    }
}

@Composable
private fun ContainerEndpointRow(ep: Endpoint) {
    val displayName = ep.name.trim('/', ' ').ifEmpty { ep.id.take(12) }
    val displayIp = ep.ipv4.ifEmpty { ep.ipv6.ifEmpty { "—" } }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(displayIp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun MonoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
