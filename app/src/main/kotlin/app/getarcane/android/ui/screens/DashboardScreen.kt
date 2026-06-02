package app.getarcane.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.environment.Environment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cross-environment totals for the overview tiles. */
private data class DashTotals(
    val running: Int,
    val total: Int,
    val images: Int,
    val volumes: Int,
    val updates: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId

    var environments by remember { mutableStateOf<List<Environment>>(emptyList()) }
    var totals by remember { mutableStateOf<DashTotals?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        if (client == null) return@LaunchedEffect
        loading = true
        val envs = runCatching { client.environments.list().data }.getOrElse {
            // Fall back to the active environment so the dashboard still shows a card.
            listOf(Environment(id = envId.rawValue, name = manager.activeEnvironmentName, apiUrl = "", status = "active"))
        }
        environments = envs
        // Aggregate the overview tiles across ALL environments (parallel, best-effort per env).
        totals = runCatching {
            coroutineScope {
                envs.map { env ->
                    val e = EnvironmentId(env.id)
                    async {
                        val sc = runCatching { client.containers.statusCounts(envId = e) }.getOrNull()
                        val ic = runCatching { client.images.usageCounts(envId = e) }.getOrNull()
                        val vc = runCatching { client.volumes.counts(envId = e) }.getOrNull()
                        val us = runCatching { client.images.updateSummary(envId = e) }.getOrNull()
                        intArrayOf(
                            sc?.runningContainers ?: 0,
                            sc?.totalContainers ?: 0,
                            ic?.totalImages ?: 0,
                            vc?.total ?: 0,
                            us?.imagesWithUpdates ?: 0,
                        )
                    }
                }.awaitAll()
            }.let { rows ->
                DashTotals(
                    running = rows.sumOf { it[0] },
                    total = rows.sumOf { it[1] },
                    images = rows.sumOf { it[2] },
                    volumes = rows.sumOf { it[3] },
                    updates = rows.sumOf { it[4] },
                )
            }
        }.getOrNull()
        loading = false
    }

    PullToRefreshBox(isRefreshing = loading, onRefresh = { refreshKey++ }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val t = totals
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardTile("Updates", t?.let { "${it.updates}" } ?: "—", Icons.Filled.Autorenew, ArcaneGreen, Modifier.weight(1f))
                        DashboardTile("Containers", t?.let { "${it.running} / ${it.total}" } ?: "—", Icons.Filled.Inventory2, ArcaneOrange, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardTile("Images", t?.let { "${it.images}" } ?: "—", Icons.Filled.Layers, ArcanePurple, Modifier.weight(1f))
                        DashboardTile("Volumes", t?.let { "${it.volumes}" } ?: "—", Icons.Filled.Storage, ArcaneTeal, Modifier.weight(1f))
                    }
                }
            }
            item {
                Text("Environments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(environments, key = { it.id }) { env ->
                EnvironmentDashboardCard(
                    env = env,
                    onSelect = { manager.setActiveEnvironment(EnvironmentId(env.id), env.name ?: env.id) },
                )
            }
        }
    }
}

@Composable
private fun DashboardTile(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
