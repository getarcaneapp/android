package app.getarcane.android.ui.screens.containers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.formatBytes
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.BannerSeverity
import app.getarcane.android.ui.components.ErrorBanner
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.sdk.models.base.JsonValue
import app.getarcane.sdk.models.base.arrayValue
import app.getarcane.sdk.models.base.int64Value
import app.getarcane.sdk.models.base.objectValue
import app.getarcane.sdk.models.base.stringValue
import app.getarcane.sdk.models.container.ContainerStatsPayload

/** A parsed point in the rolling stats window. Port of iOS `ContainerStatsFrame`. */
private data class StatsFrame(
    val timestampMs: Long,
    val cpuPercent: Double,
    val memoryUsed: Long,
    val memoryLimit: Long,
    val memoryPercent: Double,
    val netRxBytes: Long,
    val netTxBytes: Long,
    val netRxPerSec: Double,
    val netTxPerSec: Double,
    val blockReadBytes: Long,
    val blockWriteBytes: Long,
    val blockReadPerSec: Double,
    val blockWritePerSec: Double,
)

/**
 * Live container stats. Streams `client.containers.stats(...)` into a rolling 60-sample window and
 * renders summary tiles + Canvas line charts (no chart lib). Port of iOS `ContainerStatsView`.
 */
@Composable
fun ContainerStatsScreen(id: String) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    val frames = remember { mutableStateListOf<StatsFrame>() }
    var latest by remember { mutableStateOf<StatsFrame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }

    val windowSize = 60

    LaunchedEffect(id, retryKey) {
        if (client == null) return@LaunchedEffect
        error = null
        streaming = true
        var previous: StatsFrame? = latest ?: frames.lastOrNull()
        try {
            client.containers.stats(envId = envId, id = id).collect { payload ->
                parseFrame(payload, previous)?.let { frame ->
                    previous = frame
                    frames.add(frame)
                    if (frames.size > windowSize) repeat(frames.size - windowSize) { frames.removeAt(0) }
                    latest = frame
                }
            }
        } catch (e: Throwable) {
            error = "Stats stream ended: ${friendlyErrorMessage(e)}"
        } finally {
            streaming = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        error?.let { ErrorBanner(it, severity = BannerSeverity.Warning, onRetry = { retryKey++ }) }

        if (frames.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().height(220.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    if (streaming) "Waiting for stats…" else "Connecting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SummaryTiles(latest)
            ChartCard(
                title = "CPU",
                colors = listOf(ArcaneBlue),
                legend = null,
                series = frames.map { listOf(it.cpuPercent) },
            )
            MemoryCard(frames, latest)
            ChartCard(
                title = "Network I/O",
                colors = listOf(ArcaneGreen, ArcaneOrange),
                legend = listOf("RX", "TX"),
                series = frames.map { listOf(it.netRxPerSec, it.netTxPerSec) },
            )
            ChartCard(
                title = "Block I/O",
                colors = listOf(ArcaneBlue, ArcaneBlue.copy(alpha = 0.5f)),
                legend = listOf("Read", "Write"),
                series = frames.map { listOf(it.blockReadPerSec, it.blockWritePerSec) },
            )
        }
    }
}

@Composable
private fun SummaryTiles(latest: StatsFrame?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                "CPU", percentString(latest?.cpuPercent), null, Icons.Outlined.Memory, ArcaneBlue,
                Modifier.weight(1f),
            )
            Tile(
                "Memory",
                latest?.memoryUsed?.let { formatBytes(it) } ?: "—",
                percentString(latest?.memoryPercent),
                Icons.Filled.Memory, ArcaneBlue,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                "Network",
                "↓ ${rateString(latest?.netRxPerSec)}",
                "↑ ${rateString(latest?.netTxPerSec)}",
                Icons.Outlined.Dns, ArcaneGreen,
                Modifier.weight(1f),
            )
            Tile(
                "Block I/O",
                "R ${rateString(latest?.blockReadPerSec)}",
                "W ${rateString(latest?.blockWritePerSec)}",
                Icons.Outlined.Storage, ArcaneBlue,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tile(
    title: String,
    value: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChartCard(title: String, colors: List<Color>, legend: List<String>?, series: List<List<Double>>) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                legend?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        it.forEachIndexed { idx, name ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(8.dp).background(colors[idx % colors.size], CircleShape))
                                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            LineChart(series = series, colors = colors, modifier = Modifier.fillMaxWidth().height(140.dp))
        }
    }
}

@Composable
private fun MemoryCard(frames: List<StatsFrame>, latest: StatsFrame?) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Memory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (latest != null && latest.memoryLimit > 0) {
                    Text(
                        "${formatBytes(latest.memoryUsed)} / ${formatBytes(latest.memoryLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LineChart(
                series = frames.map { listOf(it.memoryUsed.toDouble() / 1_048_576.0) },
                colors = listOf(ArcaneIndigo),
                fillFirst = true,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
        }
    }
}

/** Minimal multi-series line chart drawn with Canvas. Y auto-scales to the data max. */
@Composable
private fun LineChart(
    series: List<List<Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    fillFirst: Boolean = false,
) {
    val seriesCount = series.firstOrNull()?.size ?: 0
    val maxValue = series.flatten().maxOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 1.0
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(modifier) {
        if (series.size < 2 || seriesCount == 0) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = if (series.size > 1) w / (series.size - 1) else w

        // Baseline.
        drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

        for (s in 0 until seriesCount) {
            val color = colors[s % colors.size]
            val points = series.mapIndexed { i, vals ->
                val v = vals.getOrElse(s) { 0.0 }.coerceAtLeast(0.0)
                val y = h - (v / maxValue * h).toFloat()
                Offset(i * stepX, y.coerceIn(0f, h))
            }

            if (fillFirst && s == 0) {
                val fill = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(fill, color = color.copy(alpha = 0.25f))
            }

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(line, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// MARK: - Parsing (port of ContainerStatsFrame.from)

private fun parseFrame(payload: ContainerStatsPayload, previous: StatsFrame?, nowMs: Long = System.currentTimeMillis()): StatsFrame? {
    val root = payload.raw
    if (root.isEmpty()) return null

    fun Map<String, JsonValue>.obj(key: String): Map<String, JsonValue>? = this[key]?.objectValue
    fun Map<String, JsonValue>.i64(key: String): Long? = this[key]?.int64Value

    val cpu = root.obj("cpu_stats") ?: emptyMap()
    val pre = root.obj("precpu_stats") ?: emptyMap()
    val cpuTotal = cpu.obj("cpu_usage")?.i64("total_usage") ?: 0
    val preTotal = pre.obj("cpu_usage")?.i64("total_usage") ?: 0
    val sysTotal = cpu.i64("system_cpu_usage") ?: 0
    val preSys = pre.i64("system_cpu_usage") ?: 0
    val online = cpu.i64("online_cpus")
        ?: (cpu.obj("cpu_usage")?.get("percpu_usage")?.arrayValue?.size?.toLong() ?: 1L)

    val cpuDelta = (cpuTotal - preTotal).toDouble()
    val sysDelta = (sysTotal - preSys).toDouble()
    val cpuPercent = if (sysDelta > 0 && cpuDelta > 0) (cpuDelta / sysDelta) * maxOf(online, 1L) * 100.0 else 0.0

    val mem = root.obj("memory_stats") ?: emptyMap()
    val usage = mem.i64("usage") ?: 0
    val cache = mem.obj("stats")?.i64("cache")
        ?: mem.obj("stats")?.i64("inactive_file")
        ?: 0
    val memUsed = maxOf(0L, usage - cache)
    val memLimit = mem.i64("limit") ?: 0
    val memPct = if (memLimit > 0) memUsed.toDouble() / memLimit.toDouble() * 100.0 else 0.0

    var rx = 0L
    var tx = 0L
    root.obj("networks")?.values?.forEach { ifc ->
        ifc.objectValue?.let {
            rx += it.i64("rx_bytes") ?: 0
            tx += it.i64("tx_bytes") ?: 0
        }
    }

    var blkR = 0L
    var blkW = 0L
    root.obj("blkio_stats")?.get("io_service_bytes_recursive")?.arrayValue?.forEach { e ->
        val o = e.objectValue ?: return@forEach
        val op = o["op"]?.stringValue ?: ""
        val v = o.i64("value") ?: 0
        when {
            op.equals("Read", ignoreCase = true) -> blkR += v
            op.equals("Write", ignoreCase = true) -> blkW += v
        }
    }

    val dtSec = previous?.let { (nowMs - it.timestampMs) / 1000.0 } ?: 1.0
    val safeDt = maxOf(dtSec, 0.001)
    val netRxPS = previous?.let { maxOf(0.0, (rx - it.netRxBytes) / safeDt) } ?: 0.0
    val netTxPS = previous?.let { maxOf(0.0, (tx - it.netTxBytes) / safeDt) } ?: 0.0
    val blkRPS = previous?.let { maxOf(0.0, (blkR - it.blockReadBytes) / safeDt) } ?: 0.0
    val blkWPS = previous?.let { maxOf(0.0, (blkW - it.blockWriteBytes) / safeDt) } ?: 0.0

    return StatsFrame(
        timestampMs = nowMs,
        cpuPercent = cpuPercent,
        memoryUsed = memUsed,
        memoryLimit = memLimit,
        memoryPercent = memPct,
        netRxBytes = rx,
        netTxBytes = tx,
        netRxPerSec = netRxPS,
        netTxPerSec = netTxPS,
        blockReadBytes = blkR,
        blockWriteBytes = blkW,
        blockReadPerSec = blkRPS,
        blockWritePerSec = blkWPS,
    )
}

private fun percentString(value: Double?): String = value?.let { String.format("%.1f%%", it) } ?: "—"

private fun rateString(bytesPerSec: Double?): String {
    if (bytesPerSec == null || !bytesPerSec.isFinite()) return "—"
    return "${formatBytes(maxOf(0L, bytesPerSec.toLong()))}/s"
}
