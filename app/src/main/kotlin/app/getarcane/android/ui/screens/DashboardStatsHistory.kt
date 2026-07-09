package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.system.SystemStats

const val DashboardStatsWindowSize = 60
const val DashboardStatsMaxStreams = 6

data class DashboardStatsSeries(
    val cpu: List<Double> = emptyList(),
    val memory: List<Double> = emptyList(),
    val latest: SystemStats? = null,
    val error: String? = null,
)

fun DashboardStatsSeries.append(stats: SystemStats): DashboardStatsSeries {
    val memoryPercent =
        if (stats.memoryTotal > 0) stats.memoryUsage.toDouble() / stats.memoryTotal.toDouble() * 100.0 else null

    return copy(
        cpu = (cpu + stats.cpuUsage.coerceIn(0.0, 100.0)).takeLast(DashboardStatsWindowSize),
        memory = memoryPercent
            ?.let { (memory + it.coerceIn(0.0, 100.0)).takeLast(DashboardStatsWindowSize) }
            ?: memory,
        latest = stats,
        error = null,
    )
}

fun DashboardStatsSeries.reconnecting(): DashboardStatsSeries =
    copy(error = null)

fun diskPercent(stats: SystemStats?): Double? {
    val usage = stats?.diskUsage ?: return null
    val total = stats.diskTotal ?: return null
    return if (total > 0) usage.toDouble() / total.toDouble() * 100.0 else null
}
