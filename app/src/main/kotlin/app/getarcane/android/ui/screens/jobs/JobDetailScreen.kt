package app.getarcane.android.ui.screens.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.models.jobschedule.JobStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(jobId: String, onBack: () -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<JobStatus>>(Loadable.Loading) }
    var isRunning by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(jobId, refreshKey) {
        if (client == null) return@LaunchedEffect
        state = try {
            val job = client.jobs.list(envId = envId).jobs.firstOrNull { it.id == jobId }
            if (job != null) Loadable.Success(job) else Loadable.Error("Job not found.")
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    val job = (state as? Loadable.Success)?.value

    fun run() {
        if (client == null || job == null || isRunning) return
        isRunning = true
        scope.launch {
            runCatching { client.jobs.run(jobId = job.id, envId = envId) }
            isRunning = false
            refreshKey++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.name ?: "Job", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (job?.canRunManually == true) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { run() }, enabled = job.enabled) {
                                Icon(Icons.Filled.PlayArrow, "Run Now")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is Loadable.Error -> ContentUnavailable("Error", Icons.Filled.Cancel, s.message)
                is Loadable.Success -> {
                    val j = s.value
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                JobIcon(job = j, isRunning = isRunning, size = 44)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(j.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (j.category.isNotEmpty()) {
                                        Text(
                                            j.category.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (j.enabled) ArcaneIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (j.description.isNotEmpty()) {
                                Text(j.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                        }

                        item {
                            DetailSection("Schedule") {
                                LabeledRow("Cron", j.schedule, mono = true)
                                CronExpression.readable(j.schedule)?.let { LabeledRow("Runs", it) }
                                j.nextRun?.let { LabeledRow("Next Run", absoluteDateTime(it.toEpochMilliseconds())) }
                            }
                        }

                        item {
                            DetailSection("Flags") {
                                LabeledRow("Enabled", if (j.enabled) "Yes" else "No")
                                LabeledRow("Continuous", if (j.isContinuous) "Yes" else "No")
                                LabeledRow("Manager Only", if (j.managerOnly) "Yes" else "No")
                                LabeledRow("Runnable Manually", if (j.canRunManually) "Yes" else "No")
                            }
                        }

                        if (j.prerequisites.isNotEmpty()) {
                            item {
                                DetailSection("Prerequisites") {
                                    j.prerequisites.forEach { prereq ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                if (prereq.isMet) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                                null,
                                                tint = if (prereq.isMet) ArcaneGreen else ArcaneRed,
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(prereq.label, style = MaterialTheme.typography.bodyMedium)
                                                Text(prereq.settingKey, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            DetailSection("Identifier") {
                                LabeledRow("Job ID", j.id, mono = true)
                                j.settingsKey?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Settings Key", it, mono = true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        content()
    }
}

@Composable
private fun LabeledRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
