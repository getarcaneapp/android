package app.getarcane.android.ui.screens.jobs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.Loadable
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.ContentUnavailable
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.sdk.models.jobschedule.JobStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val envId = manager.activeEnvironmentId
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Loadable<List<JobStatus>>>(Loadable.Loading) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val runningJobs = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(envId.rawValue, refreshKey) {
        if (client == null) return@LaunchedEffect
        if (state !is Loadable.Success) state = Loadable.Loading
        state = try {
            Loadable.Success(client.jobs.list(envId = envId).jobs)
        } catch (e: Throwable) {
            Loadable.Error(friendlyErrorMessage(e))
        }
    }

    fun run(job: JobStatus) {
        if (client == null || runningJobs[job.id] == true) return
        runningJobs[job.id] = true
        scope.launch {
            actionMessage = try {
                val result = client.jobs.run(jobId = job.id, envId = envId)
                if (result.message.isEmpty()) "${job.name} started" else result.message
            } catch (e: Throwable) {
                friendlyErrorMessage(e)
            }
            runningJobs.remove(job.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search jobs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (val s = state) {
                is Loadable.Loading -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                is Loadable.Error -> ContentUnavailable(
                    "Couldn't Load Jobs",
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    s.message,
                    "Refresh"
                ) { refreshKey++ }

                is Loadable.Success -> {
                    val q = search.trim()
                    val filtered = if (q.isEmpty()) s.value else s.value.filter {
                        it.name.contains(q, true) || it.description.contains(
                            q,
                            true
                        ) || it.category.contains(q, true)
                    }
                    if (s.value.isEmpty()) {
                        ContentUnavailable(
                            "No Jobs",
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            "Scheduled jobs for this environment will appear here when the server reports them.",
                            "Refresh",
                        ) { refreshKey++ }
                    } else if (filtered.isEmpty()) {
                        ContentUnavailable(
                            "No Matching Jobs",
                            Icons.Filled.Search,
                            "No jobs match “$q”. Clear the search or try another job name, category, or description.",
                        )
                    } else {
                        val grouped = filtered
                            .groupBy { it.category.ifEmpty { "Other" } }
                            .toSortedMap()
                            .mapValues { (_, v) -> v.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                        ) {
                            actionMessage?.let { msg ->
                                item(key = "action-msg") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            null,
                                            tint = ArcaneGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            msg,
                                            color = ArcaneGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            grouped.forEach { (category, jobs) ->
                                item(key = "header-$category") {
                                    Text(
                                        category.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            top = 14.dp,
                                            bottom = 4.dp
                                        ),
                                    )
                                }
                                items(jobs, key = { it.id }) { job ->
                                    JobRow(
                                        job = job,
                                        isRunning = runningJobs[job.id] == true,
                                        onClick = { onOpen(job.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: JobStatus, isRunning: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JobIcon(job = job, isRunning = isRunning, size = 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                job.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (job.description.isNotEmpty()) {
                Text(
                    job.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    job.schedule,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                job.nextRun?.let { next ->
                    Text(
                        "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        relativeTime(next.toEpochMilliseconds()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        when {
            !job.enabled -> JobBadge("OFF", ArcaneGray)
            job.isContinuous -> JobBadge("CONT", ArcanePurple)
        }
    }
}

/** Job icon (circular, tinted), rotating while running. Mirrors iOS `JobRow.icon`/`tint`. */
@Composable
fun JobIcon(job: JobStatus, isRunning: Boolean, size: Int) {
    val icon: ImageVector = when {
        !job.enabled -> Icons.Filled.PauseCircle
        isRunning -> Icons.Filled.Sync
        job.isContinuous -> Icons.Filled.AllInclusive
        else -> Icons.Filled.Schedule
    }
    val tint = when {
        !job.enabled -> ArcaneGray
        isRunning -> ArcaneBlue
        else -> ArcaneIndigo
    }
    val rotation = if (isRunning) {
        val transition = rememberInfiniteTransition(label = "spin")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "spinAngle",
        ).value
    } else {
        0f
    }
    Box(
        Modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier
            .size((size * 0.55f).dp)
            .rotate(rotation))
    }
}

@Composable
fun JobBadge(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier
            .background(
                tint.copy(alpha = 0.15f),
                androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Relative time string (e.g. "in 5 min", "2 hr ago"). Mirrors iOS `.relative(presentation: .named)`. */
internal fun relativeTime(epochMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/** Absolute date+time string (abbreviated date, standard time). Mirrors iOS `.formatted(date:.abbreviated,time:.standard)`. */
internal fun absoluteDateTime(epochMillis: Long): String {
    val df = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.MEDIUM
    )
    return df.format(java.util.Date(epochMillis))
}
