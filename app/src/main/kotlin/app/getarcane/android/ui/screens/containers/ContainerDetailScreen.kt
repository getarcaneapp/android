package app.getarcane.android.ui.screens.containers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.displayName
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.core.formatBytes
import app.getarcane.android.ui.components.CachedAsyncImage
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.StatusRunning
import app.getarcane.android.ui.theme.StatusPaused
import app.getarcane.android.ui.theme.StatusUnknown
import app.getarcane.sdk.models.container.ContainerConfig
import app.getarcane.sdk.models.container.ContainerDetails
import app.getarcane.sdk.models.container.ContainerHealth
import app.getarcane.sdk.models.container.ContainerHostConfig
import app.getarcane.sdk.models.container.ContainerNetworkEndpoint
import app.getarcane.sdk.models.container.ContainerPort
import app.getarcane.sdk.models.container.ContainerState
import kotlinx.coroutines.launch

private enum class DetailTab(val title: String) { Overview("Overview"), Stats("Stats"), Logs("Logs") }

/** `com.getarcaneapp.arcane.icon` label URL for a detail's labels map. Mirrors iOS `iconUrl`. */
private val ContainerDetails.iconUrl: String?
    get() = labels?.get("com.getarcaneapp.arcane.icon")?.ifBlank { null }

/**
 * Container detail experience. Segmented control (Overview / Stats / Logs) with content switching
 * below. Top bar: Inspect, Terminal (if running), overflow (Rename / Delete). Overview has a bottom
 * action toolbar of circular icon buttons. Port of iOS `ContainerDetailView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(
    id: String,
    onBack: () -> Unit,
    onLogs: (String) -> Unit,
    onTerminal: (String) -> Unit,
    onInspect: (String) -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<ContainerDetails>>(Loadable.Loading) }
    var refreshKey by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var runningActionId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DetailTab.Overview) }

    LaunchedEffect(id, refreshKey) {
        if (client == null) return@LaunchedEffect
        // Don't reset to Loading on refresh — keep showing prior content like iOS.
        try {
            state = Loadable.Success(client.containers.inspect(envId = envId, id = id))
        } catch (e: Throwable) {
            if (state !is Loadable.Success) state = Loadable.Error(friendlyErrorMessage(e))
        }
    }

    val details = (state as? Loadable.Success)?.value
    val title = details?.displayName ?: "Container"
    val isRunning = details?.state?.running ?: false
    val statusString = details?.state?.status ?: ""
    val isPaused = statusString.equals("paused", ignoreCase = true)

    fun perform(actionId: String, block: suspend () -> Unit) {
        if (client == null) return
        scope.launch {
            busy = true
            runningActionId = actionId
            errorMessage = null
            val result = runCatching { block() }
            result.exceptionOrNull()?.let { errorMessage = friendlyErrorMessage(it) }
            busy = false
            runningActionId = null
            refreshKey++
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onInspect(id) }, enabled = !busy) {
                        Icon(Icons.Outlined.Description, "Inspect")
                    }
                    if (isRunning && !isPaused) {
                        IconButton(onClick = { onTerminal(id) }, enabled = !busy) {
                            Icon(Icons.Filled.Terminal, "Terminal")
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }, enabled = !busy) {
                            Icon(Icons.Filled.MoreVert, "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { overflowOpen = false; showRename = true },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = ArcaneRed) },
                                onClick = { overflowOpen = false; confirmDelete = true },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = ArcaneRed) },
                            )
                        }
                    }
                },
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                DetailTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, DetailTab.entries.size),
                        label = { Text(tab.title) },
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "tabContent",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    DetailTab.Overview -> OverviewTab(
                        state = state,
                        details = details,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        statusString = statusString,
                        busy = busy,
                        runningActionId = runningActionId,
                        errorMessage = errorMessage,
                        onRetry = { refreshKey++ },
                        onDismissError = { errorMessage = null },
                        onStart = { perform("start") { client!!.containers.start(envId = envId, id = id) } },
                        onStop = { perform("stop") { client!!.containers.stop(envId = envId, id = id) } },
                        onRestart = { perform("restart") { client!!.containers.restart(envId = envId, id = id) } },
                        onUnpause = { perform("unpause") { client!!.containers.unpause(envId = envId, id = id) } },
                        onRedeploy = { perform("redeploy") { client!!.containers.redeploy(envId = envId, id = id) } },
                    )
                    DetailTab.Stats -> ContainerStatsScreen(id = id)
                    DetailTab.Logs -> EmbeddedLogsView(id = id, title = title)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Container") },
            text = { Text("This will permanently delete the container and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (client != null) scope.launch {
                        busy = true
                        val result = runCatching { client.containers.delete(envId = envId, id = id, force = true) }
                        busy = false
                        if (result.isSuccess) onBack() else errorMessage = friendlyErrorMessage(result.exceptionOrNull()!!)
                    }
                }) { Text("Delete", color = ArcaneRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (showRename) {
        RenameContainerSheet(
            currentName = title,
            onDismiss = { showRename = false },
            onRename = { newName ->
                runCatching {
                    client?.containers?.rename(envId = envId, id = id, newName = newName)
                }.fold(
                    onSuccess = { showRename = false; refreshKey++; null },
                    onFailure = { friendlyErrorMessage(it) },
                )
            },
        )
    }
}

@Composable
private fun OverviewTab(
    state: Loadable<ContainerDetails>,
    details: ContainerDetails?,
    isRunning: Boolean,
    isPaused: Boolean,
    statusString: String,
    busy: Boolean,
    runningActionId: String?,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onUnpause: () -> Unit,
    onRedeploy: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is Loadable.Error -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                ErrorBanner(state.message, onRetry = onRetry)
            }
            is Loadable.Success -> {
                val d = state.value
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    errorMessage?.let {
                        item { ErrorBanner(it, severity = app.getarcane.android.ui.components.BannerSeverity.Warning, onRetry = onDismissError) }
                    }
                    item { StatusHeader(d, isRunning, statusString) }
                    item { ConfigSection(d.config, d.image) }
                    item { StateSection(d.state, isRunning) }
                    item { HostConfigSection(d.hostConfig) }
                    if (d.ports.isNotEmpty()) {
                        item { ContainerPortsSection(d.ports) }
                    }
                    d.state.health?.let { health ->
                        item { ContainerHealthSection(health) }
                    }
                    val networks = d.networkSettings.networks
                    if (networks.isNotEmpty()) {
                        item { NetworkSection(networks) }
                    }
                }
            }
        }

        // Bottom action toolbar: circular icon buttons.
        if (details != null) {
            ActionToolbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                isRunning = isRunning,
                isPaused = isPaused,
                busy = busy,
                runningActionId = runningActionId,
                onStart = onStart,
                onStop = onStop,
                onRestart = onRestart,
                onUnpause = onUnpause,
                onRedeploy = onRedeploy,
            )
        }
    }
}

@Composable
private fun StatusHeader(d: ContainerDetails, isRunning: Boolean, statusString: String) {
    val indicatorColor = when (statusString.lowercase()) {
        "running" -> StatusRunning
        "paused" -> StatusPaused
        else -> StatusUnknown.copy(alpha = 0.5f)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box {
            CachedAsyncImage(url = d.iconUrl, size = 56.dp, shape = CircleShape) {
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Inventory2,
                        null,
                        tint = if (isRunning) ArcaneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            PulsingStatusDot(
                color = indicatorColor,
                animate = isRunning,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(d.image, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusBadge(statusString, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Pulsing 14dp status dot. Mirrors iOS `symbolEffect(.pulse)`. */
@Composable
private fun PulsingStatusDot(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = if (animate) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statusPulseAlpha",
    )
    Box(
        modifier
            .size(14.dp)
            .alpha(if (animate) pulse else 1f)
            .background(color, CircleShape),
    )
}

/** Bottom toolbar with circular icon action buttons. Mirrors iOS `actionToolbar`. */
@Composable
private fun ActionToolbar(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    isPaused: Boolean,
    busy: Boolean,
    runningActionId: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onUnpause: () -> Unit,
    onRedeploy: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isPaused -> CircleActionButton("Unpause", Icons.Filled.PlayArrow, ArcaneGreen, busy, runningActionId == "unpause", onUnpause)
                isRunning -> {
                    CircleActionButton("Stop", Icons.Filled.Stop, ArcaneRed, busy, runningActionId == "stop", onStop)
                    CircleActionButton("Restart", Icons.Filled.Refresh, ArcaneOrange, busy, runningActionId == "restart", onRestart)
                }
                else -> CircleActionButton("Start", Icons.Filled.PlayArrow, ArcaneGreen, busy, runningActionId == "start", onStart)
            }
            CircleActionButton("Redeploy", Icons.Outlined.Autorenew, ArcaneBlue, busy, runningActionId == "redeploy", onRedeploy)
        }
    }
}

@Composable
private fun CircleActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    busy: Boolean,
    isRunningThis: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledIconButton(
            onClick = onClick,
            enabled = !busy,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = tint.copy(alpha = 0.15f),
                contentColor = tint,
            ),
        ) {
            if (isRunningThis) {
                CircularProgressIndicator(Modifier.size(22.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(icon, label, Modifier.size(24.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfigSection(config: ContainerConfig, image: String) {
    DetailSection("Configuration") {
        LabeledRow("Image", image)
        config.cmd?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Command", it.joinToString(" ")) }
        config.workingDir?.takeIf { it.isNotBlank() }?.let { LabeledRow("Working Dir", it) }
        config.user?.takeIf { it.isNotBlank() }?.let { LabeledRow("User", it) }
        config.env?.takeIf { it.isNotEmpty() }?.let { env ->
            DisclosureRow("Environment (${env.size})") {
                env.forEach { v ->
                    val parts = v.split("=", limit = 2)
                    if (parts.size == 2) {
                        KeyValueBlock(parts[0], parts[1])
                    } else {
                        Text(v, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        config.entrypoint?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Entrypoint", it.joinToString(" ")) }
    }
}

@Composable
private fun StateSection(state: ContainerState, isRunning: Boolean) {
    DetailSection("State") {
        LabeledRow("Status", state.status.replaceFirstChar { it.uppercase() })
        state.startedAt?.let { LabeledRow("Started", formatDetailDate(it)) }
        if (!isRunning) {
            state.finishedAt?.let { LabeledRow("Finished", formatDetailDate(it)) }
            state.exitCode?.let { LabeledRow("Exit Code", it.toString()) }
        }
    }
}

@Composable
private fun HostConfigSection(hostConfig: ContainerHostConfig) {
    DetailSection("Host Config") {
        hostConfig.networkMode?.let { LabeledRow("Network Mode", it) }
        hostConfig.restartPolicy?.let { LabeledRow("Restart Policy", it) }
        hostConfig.memory?.takeIf { it > 0 }?.let { LabeledRow("Memory Limit", formatBytes(it)) }
        if (hostConfig.privileged == true) LabeledRow("Privileged", "Yes")
        if (hostConfig.autoRemove == true) LabeledRow("Auto Remove", "Yes")
    }
}

@Composable
private fun NetworkSection(networks: Map<String, ContainerNetworkEndpoint>) {
    DetailSection("Networks") {
        networks.keys.sorted().forEach { netName ->
            val endpoint = networks[netName] ?: return@forEach
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(netName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                endpoint.ipAddress?.takeIf { it.isNotBlank() }?.let {
                    Text("IP: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                endpoint.macAddress?.takeIf { it.isNotBlank() }?.let {
                    Text("MAC: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// MARK: - Shared section primitives

@Composable
internal fun DetailSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

@Composable
internal fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** A row that expands inline to reveal [content] (replaces iOS NavigationLink-into-sublist). */
@Composable
private fun DisclosureRow(label: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { content() }
        }
    }
}

@Composable
private fun KeyValueBlock(key: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

/** Ports section: host:public → private/proto rows, monospaced. Port of iOS `ContainerPortsSection`. */
@Composable
internal fun ContainerPortsSection(ports: List<ContainerPort>) {
    val sorted = ports.sortedWith(compareBy({ it.privatePort }, { it.type }))
    DetailSection("Ports") {
        sorted.forEach { port ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (port.publicPort != null) {
                    val host = port.ip?.ifBlank { null } ?: "0.0.0.0"
                    Text("$host:${port.publicPort}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
                Text("${port.privatePort}/${port.type}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (port.publicPort == null) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Text(
                            "internal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Active port mappings reported by the container.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Health section: status dot + failing streak + history. Port of iOS `ContainerHealthSection`. */
@Composable
internal fun ContainerHealthSection(health: ContainerHealth) {
    val statusColor = when (health.status.lowercase()) {
        "healthy" -> ArcaneGreen
        "unhealthy" -> ArcaneRed
        "starting" -> ArcaneOrange
        else -> StatusUnknown
    }
    DetailSection("Health") {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
            Text(health.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        }
        LabeledRow("Failing streak", health.failingStreak.toString())
        val log = health.log
        if (!log.isNullOrEmpty()) {
            DisclosureRow("History (${log.size})") {
                log.forEach { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.start?.let { formatHealthDate(it) } ?: "—", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            entry.end?.let {
                                Text("→ ${formatHealthDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            val ok = entry.exitCode == 0
                            Surface(shape = CircleShape, color = (if (ok) ArcaneGreen else ArcaneRed).copy(alpha = 0.2f)) {
                                Text(
                                    "exit ${entry.exitCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (ok) ArcaneGreen else ArcaneRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        entry.output?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Date helpers

// ISO8601 timestamp like 2024-01-31T12:34:56.789Z (optionally with offset). We only need the
// wall-clock fields for display, so parse them directly rather than depend on a datetime lib.
private val isoRegex = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})""")

/** ISO8601 -> "MMM d, yyyy, h:mm AM/PM", best-effort (falls back to raw). */
internal fun formatDetailDate(iso: String): String = isoRegex.find(iso)?.destructured?.let { (y, mo, d, h, mi, _) ->
    "${monthAbbrev(mo.toInt())} ${d.toInt()}, $y, ${hour12(h.toInt())}:$mi ${meridiem(h.toInt())}"
} ?: iso

/** ISO8601 -> "MMM d, yyyy, h:mm:ss AM/PM", best-effort (falls back to raw). */
internal fun formatHealthDate(iso: String): String = isoRegex.find(iso)?.destructured?.let { (y, mo, d, h, mi, s) ->
    "${monthAbbrev(mo.toInt())} ${d.toInt()}, $y, ${hour12(h.toInt())}:$mi:$s ${meridiem(h.toInt())}"
} ?: iso

private fun hour12(h: Int) = if (h % 12 == 0) 12 else h % 12
private fun meridiem(h: Int) = if (h < 12) "AM" else "PM"
private fun monthAbbrev(m: Int) = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").getOrElse(m - 1) { m.toString() }
