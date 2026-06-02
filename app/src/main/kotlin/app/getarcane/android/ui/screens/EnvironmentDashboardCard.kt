package app.getarcane.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.getarcane.android.ui.components.StatRing
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.base.intValue
import app.getarcane.sdk.models.system.DockerInfo
import app.getarcane.sdk.models.system.SystemStats
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Per-environment dashboard card with live CPU/Mem/Disk rings. Port of iOS `EnvironmentDashboardCard`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentDashboardCard(
    env: app.getarcane.sdk.models.environment.Environment,
    onSelect: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = EnvironmentId(env.id)
    val isActive = manager.activeEnvironmentId.rawValue == env.id

    var dockerInfo by remember(env.id) { mutableStateOf<DockerInfo?>(null) }
    var stats by remember(env.id) { mutableStateOf<SystemStats?>(null) }

    LaunchedEffect(env.id) {
        dockerInfo = runCatching { client?.system?.dockerInfo(envId) }.getOrNull()
    }
    LaunchedEffect(env.id) {
        delay(150)
        runCatching {
            client?.system?.statsStream(envId)?.collect { stats = it }
        }
    }

    val cpuPct = stats?.cpuUsage
    val memPct = stats?.let { if (it.memoryTotal > 0) it.memoryUsage.toDouble() / it.memoryTotal * 100.0 else null }
    val diskPct = stats?.let {
        val u = it.diskUsage; val t = it.diskTotal
        if (u != null && t != null && t > 0) u.toDouble() / t * 100.0 else null
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

            // Stat rings
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatRing(((cpuPct ?: 0.0) / 100.0).toFloat(), pctShort(cpuPct), "CPU", ArcaneBlue)
                StatRing(((memPct ?: 0.0) / 100.0).toFloat(), pctShort(memPct), "Memory", ArcanePurple)
                StatRing(((diskPct ?: 0.0) / 100.0).toFloat(), pctShort(diskPct), "Disk", ArcaneTeal)
            }

            HorizontalDivider()

            // Mini metrics
            val running = dockerInfo?.info?.get("ContainersRunning")?.intValue
            val stopped = dockerInfo?.info?.get("ContainersStopped")?.intValue
            val images = dockerInfo?.info?.get("Images")?.intValue
            val has = dockerInfo != null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMetric("Running", if (has) "${running ?: 0}" else "--", ArcaneGreen, Modifier.weight(1f))
                MiniMetric("Stopped", if (has) "${stopped ?: 0}" else "--", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                MiniMetric("Images", if (has) "${images ?: 0}" else "--", ArcanePurple, Modifier.weight(1f))
            }
        }
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
