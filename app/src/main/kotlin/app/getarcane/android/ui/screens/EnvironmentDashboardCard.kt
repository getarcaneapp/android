package app.getarcane.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.DashboardActionItem
import app.getarcane.android.core.DashboardActionItemKind
import app.getarcane.android.core.DashboardActionItemSeverity
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.intValue
import app.getarcane.sdk.models.system.DockerInfo
import java.util.Locale
import kotlin.math.roundToInt

enum class EnvironmentCardAction(val label: String) {
    UseEnvironment("Use Environment"),
    ViewSystemDetails("View System Details"),
    Sync("Sync"),
    UpgradeArcane("Upgrade Arcane"),
    SystemPrune("System Prune"),
}

fun environmentCardActions(isAdmin: Boolean, canUpgradeArcane: Boolean = false): List<EnvironmentCardAction> =
    buildList {
        add(EnvironmentCardAction.UseEnvironment)
        add(EnvironmentCardAction.ViewSystemDetails)
        add(EnvironmentCardAction.Sync)
        if (isAdmin) {
            if (canUpgradeArcane) {
                add(EnvironmentCardAction.UpgradeArcane)
            }
            add(EnvironmentCardAction.SystemPrune)
        }
    }

/** Per-environment dashboard card with live CPU/Mem/Disk rings. Port of iOS `EnvironmentDashboardCard`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnvironmentDashboardCard(
    env: app.getarcane.sdk.models.environment.Environment,
    overviewCounts: DashboardEnvironmentCardOverviewCounts? = null,
    actionItems: List<DashboardActionItem> = emptyList(),
    statsSeries: DashboardStatsSeries?,
    refreshToken: Int = 0,
    onSelect: () -> Unit,
    actions: List<EnvironmentCardAction> = environmentCardActions(isAdmin = false),
    onAction: (EnvironmentCardAction) -> Unit = {},
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = EnvironmentId(env.id)
    val isActive = manager.activeEnvironmentId.rawValue == env.id

    var dockerInfo by remember(env.id) { mutableStateOf<DockerInfo?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(env.id, refreshToken) {
        dockerInfo = runCatching { client?.system?.dockerInfo(envId) }.getOrNull()
    }

    val stats = statsSeries?.latest
    val cpuPct = stats?.cpuUsage
    val memPct = stats?.let { if (it.memoryTotal > 0) it.memoryUsage.toDouble() / it.memoryTotal * 100.0 else null }
    val diskPct = diskPercent(stats)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showMenu = true },
            ),
        shape = RoundedCornerShape(20.dp),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (isActive) Icons.Filled.CheckCircle else Icons.Filled.Dns,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(env.name ?: env.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    dockerInfo?.serverVersion?.let {
                        Text("Docker $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Environment actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = { Icon(action.icon, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onAction(action)
                                },
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SparklineMetric(
                    title = "CPU",
                    value = pctShort(cpuPct),
                    series = statsSeries?.cpu.orEmpty(),
                    tint = ArcaneBlue,
                    modifier = Modifier.weight(1f),
                )
                SparklineMetric(
                    title = "Memory",
                    value = pctShort(memPct),
                    series = statsSeries?.memory.orEmpty(),
                    tint = ArcanePurple,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Disk", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pctShort(diskPct), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ArcaneTeal)
                }
                LinearProgressIndicator(
                    progress = { ((diskPct ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = ArcaneTeal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                statsSeries?.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider()

            // Mini metrics
            val running = dockerInfo?.info?.get("ContainersRunning")?.intValue ?: overviewCounts?.running
            val stopped = dockerInfo?.info?.get("ContainersStopped")?.intValue ?: overviewCounts?.stopped
            val images = dockerInfo?.info?.get("Images")?.intValue ?: overviewCounts?.images
            val has = dockerInfo != null || overviewCounts != null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMetric("Running", if (has) "${running ?: 0}" else "--", ArcaneGreen, Modifier.weight(1f))
                MiniMetric("Stopped", if (has) "${stopped ?: 0}" else "--", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                MiniMetric("Images", if (has) "${images ?: 0}" else "--", ArcanePurple, Modifier.weight(1f))
            }

            DashboardCardActionItemsRow(actionItems)
        }
    }
}

private val EnvironmentCardAction.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        EnvironmentCardAction.UseEnvironment -> Icons.Filled.CheckCircle
        EnvironmentCardAction.ViewSystemDetails -> Icons.Filled.Dns
        EnvironmentCardAction.Sync -> Icons.Filled.Sync
        EnvironmentCardAction.UpgradeArcane -> Icons.Filled.ArrowCircleUp
        EnvironmentCardAction.SystemPrune -> Icons.Filled.Delete
    }

@Composable
private fun SparklineMetric(
    title: String,
    value: String,
    series: List<Double>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint)
            }
            Sparkline(series = series, tint = tint, modifier = Modifier.fillMaxWidth().height(36.dp))
        }
    }
}

@Composable
private fun Sparkline(series: List<Double>, tint: Color, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        if (series.size < 2) return@Canvas

        val points = series.mapIndexed { index, raw ->
            val x = index * (w / (series.size - 1).coerceAtLeast(1))
            val y = h - (raw.coerceIn(0.0, 100.0) / 100.0 * h).toFloat()
            Offset(x, y.coerceIn(0f, h))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                lineTo(points[index].x, points[index].y)
            }
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun MiniMetric(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun pctShort(v: Double?): String = v?.let { "${it.coerceIn(0.0, 100.0).roundToInt()}%" } ?: "—"

@Composable
private fun DashboardCardActionItemsRow(items: List<DashboardActionItem>) {
    val summary = dashboardCardActionItemSummary(items) ?: return
    val hasCritical = items.any { it.count > 0 && it.itemSeverity == DashboardActionItemSeverity.Critical }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = if (hasCritical) MaterialTheme.colorScheme.error else ArcaneOrange,
            modifier = Modifier.size(16.dp),
        )
        Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun dashboardCardActionItemSummary(
    items: List<DashboardActionItem>,
    maxItems: Int = 2,
): String? {
    val summaries = items
        .asSequence()
        .filter { it.count > 0 }
        .take(maxItems)
        .map { it.dashboardCardSummaryPart }
        .toList()

    return summaries.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private val DashboardActionItem.dashboardCardSummaryPart: String
    get() = when (itemKind) {
        DashboardActionItemKind.ImageUpdates -> dashboardCardLabel
        else -> "$count $dashboardCardLabel"
    }

private val DashboardActionItem.dashboardCardLabel: String
    get() = when (itemKind) {
        DashboardActionItemKind.StoppedContainers -> "Stopped"
        DashboardActionItemKind.ImageUpdates -> "Image updates"
        DashboardActionItemKind.ActionableVulnerabilities -> "Vulnerabilities"
        DashboardActionItemKind.ExpiringKeys -> "Expiring Keys"
        DashboardActionItemKind.Unknown -> kind
            .split("_", "-")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase(Locale.US) } }
            .ifBlank { kind }
    }
