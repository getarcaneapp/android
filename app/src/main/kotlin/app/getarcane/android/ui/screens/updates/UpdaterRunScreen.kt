package app.getarcane.android.ui.screens.updates

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.updater.UpdaterResourceResult
import app.getarcane.sdk.models.updater.UpdaterResult
import app.getarcane.sdk.models.updater.UpdaterStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private sealed interface RunPhase {
    data object Starting : RunPhase
    data object Running : RunPhase
    data class Completed(val result: UpdaterResult) : RunPhase
    data class Failed(val message: String) : RunPhase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterRunScreen(onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var phase by remember { mutableStateOf<RunPhase>(RunPhase.Starting) }
    var liveStatus by remember { mutableStateOf<UpdaterStatus?>(null) }

    LaunchedEffect(envId.rawValue) {
        if (client == null) {
            phase = RunPhase.Failed("Not connected")
            return@LaunchedEffect
        }
        phase = RunPhase.Starting
        liveStatus = null

        // Poll loop: flips to Running quickly, then refreshes live status.
        val pollJob = launch {
            delay(400)
            if (phase is RunPhase.Starting) phase = RunPhase.Running
            while (coroutineContext.isActive) {
                runCatching { client.updater.status(envId = envId) }.getOrNull()?.let { status ->
                    liveStatus = status
                    if (phase is RunPhase.Starting) phase = RunPhase.Running
                }
                delay(1500)
            }
        }

        phase = try {
            val result = client.updater.run(envId = envId)
            RunPhase.Completed(result)
        } catch (e: Throwable) {
            RunPhase.Failed(friendlyErrorMessage(e))
        } finally {
            pollJob.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Updater") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val p = phase) {
                    is RunPhase.Starting -> Hero(icon = Icons.Filled.Sync, tint = ArcaneBlue, title = "Starting…", subtitle = "Triggering updater", spinning = false, showSpinner = true)
                    is RunPhase.Running -> {
                        Hero(icon = Icons.Filled.Sync, tint = ArcaneBlue, title = "Running Updater", subtitle = runningSubtitle(liveStatus), spinning = true, showSpinner = false)
                        liveStatus?.let { status ->
                            CountersCard("In Progress", Icons.Filled.BarChart, ArcanePurple) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CounterTile("Containers", status.updatingContainers, Icons.Filled.Inventory2, ArcaneBlue, Modifier.weight(1f))
                                    CounterTile("Projects", status.updatingProjects, Icons.Filled.Folder, ArcanePurple, Modifier.weight(1f))
                                }
                            }
                            if (status.containerIds.isNotEmpty()) IdListCard("Containers Updating", Icons.Filled.Inventory2, ArcaneBlue, status.containerIds)
                            if (status.projectIds.isNotEmpty()) IdListCard("Projects Updating", Icons.Filled.Folder, ArcanePurple, status.projectIds)
                        }
                    }
                    is RunPhase.Completed -> {
                        val result = p.result
                        val success = result.failed == 0
                        Hero(
                            icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            tint = if (success) ArcaneGreen else ArcaneOrange,
                            title = if (success) "Completed" else "Completed with Issues",
                            subtitle = completedSubtitle(result),
                            spinning = false,
                            showSpinner = false,
                        )
                        CountersCard("Summary", Icons.Filled.BarChart, ArcanePurple) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CounterTile("Checked", result.checked, Icons.Filled.Search, ArcaneBlue, Modifier.weight(1f))
                                    CounterTile("Updated", result.updated, Icons.Filled.CheckCircle, ArcaneGreen, Modifier.weight(1f))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CounterTile("Skipped", result.skipped, Icons.Filled.RemoveCircle, ArcaneGray, Modifier.weight(1f))
                                    CounterTile("Failed", result.failed, Icons.Filled.Cancel, ArcaneRed, Modifier.weight(1f))
                                }
                            }
                        }
                        if (result.items.isNotEmpty()) {
                            CountersCard("Resources (${result.items.size})", Icons.AutoMirrored.Filled.ListAlt, ArcaneIndigo) {
                                Column {
                                    result.items.forEachIndexed { index, item ->
                                        UpdaterRunItemRow(item)
                                        if (index < result.items.size - 1) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                    is RunPhase.Failed -> ContentUnavailable("Updater Failed", Icons.Filled.Warning, p.message)
                }
            }
        }
    }
}

private fun runningSubtitle(status: UpdaterStatus?): String {
    if (status == null) return "Working on it"
    val parts = buildList {
        if (status.updatingContainers > 0) add("${status.updatingContainers} container${if (status.updatingContainers == 1) "" else "s"}")
        if (status.updatingProjects > 0) add("${status.updatingProjects} project${if (status.updatingProjects == 1) "" else "s"}")
    }
    return if (parts.isEmpty()) "Checking for updates" else parts.joinToString(" · ") + " in progress"
}

private fun completedSubtitle(result: UpdaterResult): String {
    val parts = buildList {
        add("${result.updated} updated")
        if (result.failed > 0) add("${result.failed} failed")
        if (result.skipped > 0) add("${result.skipped} skipped")
        add("in ${result.duration}")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun Hero(icon: ImageVector, tint: Color, title: String, subtitle: String, spinning: Boolean, showSpinner: Boolean) {
    val rotation = if (spinning) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "spinAngle",
        ).value
    } else {
        0f
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(96.dp).background(tint.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            if (showSpinner) {
                CircularProgressIndicator(color = tint, modifier = Modifier.size(40.dp))
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp).rotate(rotation))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CountersCard(title: String, icon: ImageVector, tint: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun CounterTile(label: String, value: Int, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IdListCard(title: String, icon: ImageVector, tint: Color, ids: List<String>) {
    val rotation = rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle",
    ).value
    CountersCard(title, icon, tint) {
        Column {
            ids.forEachIndexed { index, id ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(22.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Sync, null, tint = tint, modifier = Modifier.size(13.dp).rotate(rotation))
                    }
                    Text(id, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index < ids.size - 1) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun UpdaterRunItemRow(item: UpdaterResourceResult) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(typeTint(item.resourceType).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(typeIcon(item.resourceType), null, tint = typeTint(item.resourceType), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.resourceName ?: item.resourceId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.resourceType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            imageChange(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            item.error?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ArcaneRed, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        StatusPill(text = itemStatusText(item), tint = itemStatusTint(item))
    }
}

@Composable
private fun StatusPill(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier.background(tint.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun typeIcon(type: String): ImageVector = when (type.lowercase()) {
    "container" -> Icons.Filled.Inventory2
    "project", "stack" -> Icons.Filled.Folder
    "image" -> Icons.Filled.Image
    else -> Icons.Filled.Sync
}

private fun typeTint(type: String): Color = when (type.lowercase()) {
    "container" -> ArcaneBlue
    "project", "stack" -> ArcanePurple
    "image" -> ArcanePink
    else -> ArcaneGray
}

private fun itemStatusText(item: UpdaterResourceResult): String {
    if (!item.error.isNullOrEmpty()) return "Failed"
    if (item.updateApplied == true) return "Updated"
    if (item.updateAvailable == true) return "Available"
    return item.status.replaceFirstChar { it.uppercase() }
}

private fun itemStatusTint(item: UpdaterResourceResult): Color {
    if (!item.error.isNullOrEmpty()) return ArcaneRed
    if (item.updateApplied == true) return ArcaneGreen
    if (item.updateAvailable == true) return ArcaneOrange
    return when (item.status.lowercase()) {
        "skipped", "ignored", "up_to_date" -> ArcaneGray
        "failed", "error" -> ArcaneRed
        "updated", "success" -> ArcaneGreen
        else -> ArcaneBlue
    }
}

private fun imageChange(item: UpdaterResourceResult): String? {
    val oldVersions = item.oldImages ?: emptyMap()
    val newVersions = item.newImages ?: emptyMap()
    val key = newVersions.keys.firstOrNull() ?: oldVersions.keys.firstOrNull() ?: return null
    val oldTag = oldVersions[key]
    val newTag = newVersions[key]
    return when {
        oldTag != null && newTag != null && oldTag != newTag -> "$oldTag → $newTag"
        oldTag != null -> oldTag
        newTag != null -> newTag
        else -> null
    }
}
