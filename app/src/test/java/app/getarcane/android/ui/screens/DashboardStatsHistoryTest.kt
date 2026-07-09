package app.getarcane.android.ui.screens

import app.getarcane.sdk.models.system.SystemStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardStatsHistoryTest {
    @Test
    fun appendKeepsRollingCpuAndMemoryWindow() {
        val series = (0..DashboardStatsWindowSize).fold(DashboardStatsSeries()) { acc, index ->
            acc.append(stats(cpu = index.toDouble(), memoryUsage = index.toLong(), memoryTotal = 100))
        }

        assertEquals(DashboardStatsWindowSize, series.cpu.size)
        assertEquals(DashboardStatsWindowSize, series.memory.size)
        assertEquals(1.0, series.cpu.first(), 0.0)
        assertEquals(60.0, series.cpu.last(), 0.0)
        assertEquals(1.0, series.memory.first(), 0.0)
        assertEquals(60.0, series.memory.last(), 0.0)
    }

    @Test
    fun appendClampsPercentagesAndClearsError() {
        val series = DashboardStatsSeries(error = "previous")
            .append(stats(cpu = 130.0, memoryUsage = 150, memoryTotal = 100))

        assertEquals(listOf(100.0), series.cpu)
        assertEquals(listOf(100.0), series.memory)
        assertNull(series.error)
    }

    @Test
    fun reconnectingKeepsHistoryAndClearsError() {
        val latest = stats(cpu = 12.0)
        val series = DashboardStatsSeries(
            cpu = listOf(10.0, 12.0),
            memory = listOf(40.0, 42.0),
            latest = latest,
            error = "Live stats unavailable",
        ).reconnecting()

        assertEquals(listOf(10.0, 12.0), series.cpu)
        assertEquals(listOf(40.0, 42.0), series.memory)
        assertEquals(latest, series.latest)
        assertNull(series.error)
    }

    @Test
    fun diskPercentUsesReportedUsageAndTotal() {
        assertEquals(25.0, diskPercent(stats(diskUsage = 25, diskTotal = 100))!!, 0.0)
        assertNull(diskPercent(stats(diskUsage = null, diskTotal = 100)))
        assertNull(diskPercent(stats(diskUsage = 25, diskTotal = null)))
        assertNull(diskPercent(stats(diskUsage = 25, diskTotal = 0)))
    }

    private fun stats(
        cpu: Double = 0.0,
        memoryUsage: Long = 0,
        memoryTotal: Long = 100,
        diskUsage: Long? = null,
        diskTotal: Long? = null,
    ): SystemStats = SystemStats(
        cpuUsage = cpu,
        memoryUsage = memoryUsage,
        memoryTotal = memoryTotal,
        diskUsage = diskUsage,
        diskTotal = diskTotal,
        cpuCount = 4,
        architecture = "x86_64",
        platform = "linux",
        hostname = "test",
        gpuCount = 0,
        gpus = null,
    )
}
