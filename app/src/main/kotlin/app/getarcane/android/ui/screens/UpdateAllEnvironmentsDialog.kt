package app.getarcane.android.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.screens.jobs.absoluteDateTime
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.user.isGlobalAdmin
import app.getarcane.sdk.serialization.ArcaneInstantSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val ManagerEnvironmentId = EnvironmentId("0")
private const val UpdateAllPollDelayMillis = 3_000L
private const val UpdateAllMaxReconnectFailures = 60

@Serializable
internal data class EnvironmentUpdateJob(
    val id: String = "",
    val status: EnvironmentUpdateJobStatus,
    val userId: String = "",
    val username: String = "",
    val managerVersionAtStart: String = "",
    val managerDigestAtStart: String = "",
    val managerTargetVersion: String = "",
    val results: List<EnvironmentUpdateResult> = emptyList(),
    val error: String? = null,
    @Serializable(with = ArcaneInstantSerializer::class)
    val completedAt: Instant? = null,
)

@Serializable
internal enum class EnvironmentUpdateJobStatus {
    @SerialName("pending_restart")
    PendingRestart,
    @SerialName("running")
    Running,
    @SerialName("completed")
    Completed,
    @SerialName("failed")
    Failed,
}

@Serializable
internal data class EnvironmentUpdateResult(
    val environmentId: String,
    val environmentName: String,
    val status: EnvironmentUpdateResultStatus,
    val fromVersion: String = "",
    val toVersion: String = "",
    val error: String = "",
)

@Serializable
internal enum class EnvironmentUpdateResultStatus {
    @SerialName("pending")
    Pending,
    @SerialName("updating")
    Updating,
    @SerialName("updated")
    Updated,
    @SerialName("triggered")
    Triggered,
    @SerialName("skipped_up_to_date")
    SkippedUpToDate,
    @SerialName("skipped_offline")
    SkippedOffline,
    @SerialName("failed")
    Failed,
}

private sealed interface UpdateAllPhase {
    data object Loading : UpdateAllPhase
    data class Ready(val lastJob: EnvironmentUpdateJob?) : UpdateAllPhase
    data object Triggering : UpdateAllPhase
    data class Polling(val job: EnvironmentUpdateJob) : UpdateAllPhase
    data class Reconnecting(val job: EnvironmentUpdateJob) : UpdateAllPhase
    data class Finished(val job: EnvironmentUpdateJob, val note: String?) : UpdateAllPhase
    data class Unsupported(val message: String) : UpdateAllPhase
    data class Failed(val message: String) : UpdateAllPhase
}

internal val EnvironmentUpdateJob.isTerminal: Boolean
    get() = status == EnvironmentUpdateJobStatus.Completed || status == EnvironmentUpdateJobStatus.Failed

internal fun shouldShowUpdateAllAction(isAdmin: Boolean): Boolean = isAdmin

internal fun updateAllLastRunSummary(job: EnvironmentUpdateJob): String {
    if (job.status == EnvironmentUpdateJobStatus.Failed) return job.error ?: "Failed"
    val updated = job.results.count { it.status == EnvironmentUpdateResultStatus.Updated || it.status == EnvironmentUpdateResultStatus.Triggered }
    val failed = job.results.count { it.status == EnvironmentUpdateResultStatus.Failed }
    val skipped = job.results.count { it.status == EnvironmentUpdateResultStatus.SkippedOffline }
    val parts = buildList {
        add("$updated updated")
        if (failed > 0) add("$failed failed")
        if (skipped > 0) add("$skipped skipped")
        displayUpdateAllVersion(job.managerTargetVersion)?.let(::add)
    }
    return parts.joinToString(" · ")
}

internal fun updateAllFinishedMessage(job: EnvironmentUpdateJob, note: String?): String =
    note ?: when {
        job.status == EnvironmentUpdateJobStatus.Completed && job.results.none { it.status == EnvironmentUpdateResultStatus.Failed } ->
            "All environments updated"
        job.status == EnvironmentUpdateJobStatus.Failed ->
            job.error ?: "Fleet update failed"
        else ->
            updateAllLastRunSummary(job)
    }

private fun displayUpdateAllVersion(raw: String): String? =
    raw.takeIf { it.isNotBlank() && ":" !in it && it.length <= 20 }

private suspend fun ArcaneClient.updateAllStatus(): EnvironmentUpdateJob =
    rest.get(rest.environmentPath(ManagerEnvironmentId, "system/upgrade/all/status"))

private suspend fun ArcaneClient.triggerUpdateAll(): EnvironmentUpdateJob =
    rest.post(rest.environmentPath(ManagerEnvironmentId, "system/upgrade/all"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAllEnvironmentsDialog(
    environmentCount: Int,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false

    var phase by remember { mutableStateOf<UpdateAllPhase>(UpdateAllPhase.Loading) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun finish(job: EnvironmentUpdateJob, note: String?) {
        phase = UpdateAllPhase.Finished(job, note)
        onMessage(updateAllFinishedMessage(job, note))
        onComplete()
    }

    fun startPolling(c: ArcaneClient, initialJob: EnvironmentUpdateJob) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastKnown = initialJob
            var failures = 0
            while (true) {
                delay(UpdateAllPollDelayMillis)
                try {
                    val job = c.updateAllStatus()
                    failures = 0
                    lastKnown = job
                    if (job.isTerminal) {
                        finish(job, note = null)
                        return@launch
                    }
                    phase = UpdateAllPhase.Polling(job)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    failures += 1
                    if (failures == 2) phase = UpdateAllPhase.Reconnecting(lastKnown)
                    if (failures >= UpdateAllMaxReconnectFailures) {
                        val managerRestarting = lastKnown.status == EnvironmentUpdateJobStatus.PendingRestart &&
                            lastKnown.results.firstOrNull { it.environmentId == ManagerEnvironmentId.rawValue }?.status in setOf(
                                EnvironmentUpdateResultStatus.Updated,
                                EnvironmentUpdateResultStatus.Triggered,
                                EnvironmentUpdateResultStatus.Updating,
                            )
                        finish(
                            lastKnown,
                            if (managerRestarting) {
                                "The Arcane manager is restarting. Check back in a minute."
                            } else {
                                "Lost connection before the update finished. Check the server once it's reachable again."
                            },
                        )
                        return@launch
                    }
                }
            }
        }
    }

    fun preflight() {
        val c = client ?: run {
            phase = UpdateAllPhase.Failed("Not connected")
            return
        }
        scope.launch {
            phase = UpdateAllPhase.Loading
            try {
                val job = c.updateAllStatus()
                if (job.isTerminal) {
                    phase = UpdateAllPhase.Ready(job)
                } else {
                    phase = UpdateAllPhase.Polling(job)
                    startPolling(c, job)
                }
            } catch (e: ArcaneError.NotFound) {
                phase = UpdateAllPhase.Ready(null)
            } catch (e: ArcaneError.Decoding) {
                phase = UpdateAllPhase.Ready(null)
            } catch (e: Throwable) {
                phase = UpdateAllPhase.Failed(friendlyErrorMessage(e))
            }
        }
    }

    fun trigger() {
        val c = client ?: run {
            phase = UpdateAllPhase.Failed("Not connected")
            return
        }
        scope.launch {
            phase = UpdateAllPhase.Triggering
            try {
                val job = c.triggerUpdateAll()
                phase = UpdateAllPhase.Polling(job)
                startPolling(c, job)
            } catch (e: ArcaneError.Conflict) {
                preflight()
            } catch (e: ArcaneError.NotFound) {
                phase = UpdateAllPhase.Unsupported("This Arcane server doesn't support fleet updates. Update the server first.")
            } catch (e: ArcaneError.Server) {
                phase = UpdateAllPhase.Unsupported(e.serverMessage.ifBlank { friendlyErrorMessage(e) })
            } catch (e: Throwable) {
                phase = UpdateAllPhase.Failed(friendlyErrorMessage(e))
            }
        }
    }

    LaunchedEffect(isAdmin, refreshKey) {
        if (isAdmin) preflight()
    }

    DisposableEffect(Unit) {
        onDispose { pollJob?.cancel() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Update All") },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!isAdmin) {
                    ContentUnavailable("Admins Only", Icons.Filled.Lock, "Updating all environments requires an administrator account.")
                    return@Column
                }
                when (val p = phase) {
                    UpdateAllPhase.Loading -> LoadingUpdateAllCard()
                    is UpdateAllPhase.Ready -> ReadyUpdateAllContent(
                        environmentCount = environmentCount,
                        lastJob = p.lastJob,
                        onTrigger = ::trigger,
                    )
                    UpdateAllPhase.Triggering -> ProgressUpdateAllCard("Starting…", "Contacting the manager")
                    is UpdateAllPhase.Polling -> RunningUpdateAllContent(p.job, reconnecting = false)
                    is UpdateAllPhase.Reconnecting -> RunningUpdateAllContent(p.job, reconnecting = true)
                    is UpdateAllPhase.Finished -> FinishedUpdateAllContent(p.job, p.note)
                    is UpdateAllPhase.Unsupported -> ContentUnavailable("Not Available", Icons.Filled.Lock, p.message)
                    is UpdateAllPhase.Failed -> {
                        ContentUnavailable("Update All Failed", Icons.Filled.Warning, p.message)
                        OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, null)
                            Text("  Try Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyUpdateAllContent(
    environmentCount: Int,
    lastJob: EnvironmentUpdateJob?,
    onTrigger: () -> Unit,
) {
    HeroUpdateAllCard(
        tint = ArcaneBlue,
        icon = Icons.Filled.ArrowCircleUp,
        title = "Update All Environments",
        subtitle = if (environmentCount == 1) "1 environment · latest release" else "$environmentCount environments · latest release",
    )
    StepsCard()
    lastJob?.let { LastRunCard(it) }
    Button(
        onClick = onTrigger,
        colors = ButtonDefaults.buttonColors(containerColor = ArcaneRed),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.ArrowCircleUp, null)
        Text("  Update All")
    }
}

@Composable
private fun RunningUpdateAllContent(job: EnvironmentUpdateJob, reconnecting: Boolean) {
    when {
        reconnecting -> ProgressUpdateAllCard("Reconnecting…", "The manager may be restarting")
        job.status == EnvironmentUpdateJobStatus.PendingRestart -> ProgressUpdateAllCard("Restarting Manager", "Arcane will reconnect when the manager is back")
        else -> ProgressUpdateAllCard("Updating Environments", updateAllLastRunSummary(job))
    }
    if (job.results.isNotEmpty()) ResultsCard(job.results)
}

@Composable
private fun FinishedUpdateAllContent(job: EnvironmentUpdateJob, note: String?) {
    val failed = job.results.count { it.status == EnvironmentUpdateResultStatus.Failed }
    HeroUpdateAllCard(
        tint = if (job.status == EnvironmentUpdateJobStatus.Completed && failed == 0) ArcaneGreen else ArcaneOrange,
        icon = if (job.status == EnvironmentUpdateJobStatus.Completed && failed == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
        title = if (job.status == EnvironmentUpdateJobStatus.Completed && failed == 0) "Update Complete" else "Update Finished",
        subtitle = updateAllFinishedMessage(job, note),
    )
    if (job.results.isNotEmpty()) ResultsCard(job.results)
}

@Composable
private fun LoadingUpdateAllCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Checking update status…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressUpdateAllCard(title: String, subtitle: String) {
    HeroUpdateAllCard(
        tint = ArcaneBlue,
        icon = Icons.Filled.Refresh,
        title = title,
        subtitle = subtitle,
        spinner = true,
    )
}

@Composable
private fun HeroUpdateAllCard(
    tint: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    spinner: Boolean = false,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(72.dp).background(tint.copy(alpha = 0.16f), CircleShape), Alignment.Center) {
                if (spinner) {
                    CircularProgressIndicator(modifier = Modifier.size(34.dp), color = tint)
                } else {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            StepRow(Icons.Filled.Dns, ArcaneBlue, "Agents update first")
            HorizontalDivider(Modifier.padding(start = 44.dp))
            StepRow(Icons.Filled.RestartAlt, ArcaneGray, "Manager restarts last")
            HorizontalDivider(Modifier.padding(start = 44.dp))
            StepRow(Icons.Filled.CloudOff, ArcaneOrange, "Brief disconnect at the end")
        }
    }
}

@Composable
private fun StepRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.14f), CircleShape), Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LastRunCard(job: EnvironmentUpdateJob) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val success = job.status == EnvironmentUpdateJobStatus.Completed
            Icon(
                if (success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                null,
                tint = if (success) ArcaneGreen else ArcaneOrange,
            )
            Column(Modifier.weight(1f)) {
                Text("Last run", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    updateAllLastRunSummary(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            job.completedAt?.let {
                Text(
                    absoluteDateTime(it.toEpochMilliseconds()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultsCard(results: List<EnvironmentUpdateResult>) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            results.forEachIndexed { index, result ->
                ResultRow(result)
                if (index < results.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp))
            }
        }
    }
}

@Composable
private fun ResultRow(result: EnvironmentUpdateResult) {
    val tint = result.status.tint
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(tint.copy(alpha = 0.16f), CircleShape), Alignment.Center) {
            Icon(result.status.icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                result.environmentName.ifBlank { result.environmentId },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = result.error.ifBlank {
                listOf(result.fromVersion, result.toVersion)
                    .filter { it.isNotBlank() }
                    .joinToString(" → ")
                    .ifBlank { result.status.label }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(result.status.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

private val EnvironmentUpdateResultStatus.label: String
    get() = when (this) {
        EnvironmentUpdateResultStatus.Pending -> "Pending"
        EnvironmentUpdateResultStatus.Updating -> "Updating"
        EnvironmentUpdateResultStatus.Updated -> "Updated"
        EnvironmentUpdateResultStatus.Triggered -> "Triggered"
        EnvironmentUpdateResultStatus.SkippedUpToDate -> "Current"
        EnvironmentUpdateResultStatus.SkippedOffline -> "Offline"
        EnvironmentUpdateResultStatus.Failed -> "Failed"
    }

private val EnvironmentUpdateResultStatus.icon: ImageVector
    get() = when (this) {
        EnvironmentUpdateResultStatus.Pending -> Icons.Filled.Refresh
        EnvironmentUpdateResultStatus.Updating -> Icons.Filled.Refresh
        EnvironmentUpdateResultStatus.Updated -> Icons.Filled.CheckCircle
        EnvironmentUpdateResultStatus.Triggered -> Icons.Filled.ArrowCircleUp
        EnvironmentUpdateResultStatus.SkippedUpToDate -> Icons.Filled.CheckCircle
        EnvironmentUpdateResultStatus.SkippedOffline -> Icons.Filled.CloudOff
        EnvironmentUpdateResultStatus.Failed -> Icons.Filled.Warning
    }

private val EnvironmentUpdateResultStatus.tint: Color
    get() = when (this) {
        EnvironmentUpdateResultStatus.Pending -> ArcaneGray
        EnvironmentUpdateResultStatus.Updating -> ArcaneBlue
        EnvironmentUpdateResultStatus.Updated -> ArcaneGreen
        EnvironmentUpdateResultStatus.Triggered -> ArcaneBlue
        EnvironmentUpdateResultStatus.SkippedUpToDate -> ArcaneGray
        EnvironmentUpdateResultStatus.SkippedOffline -> ArcaneGray
        EnvironmentUpdateResultStatus.Failed -> ArcaneRed
    }
