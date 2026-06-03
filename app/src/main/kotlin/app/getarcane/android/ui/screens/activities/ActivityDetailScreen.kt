package app.getarcane.android.ui.screens.activities

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.friendlyErrorMessage
import app.getarcane.android.ui.components.StatusBadge
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.activity.ActivityMessage
import app.getarcane.sdk.models.user.hasPermission
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Detail for a single activity: header (title/status/progress/error), metadata, and output messages.
 *
 * @param activityId the activity to fetch.
 * @param envId the environment id it lives in (the row's `sourceEnvironmentKey`).
 * @param onBack pops back to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: String,
    envId: String,
    onBack: () -> Unit,
) {
    val manager = LocalArcaneManager.current
    val client = manager.client
    val scope = rememberCoroutineScope()
    val environmentId = remember(envId) { EnvironmentId(envId) }

    var activity by remember { mutableStateOf<Activity?>(null) }
    var messages by remember { mutableStateOf<List<ActivityMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    suspend fun loadDetail() {
        if (client == null) return
        isLoading = true
        errorMessage = null
        try {
            val detail = client.activities.detail(
                envId = environmentId,
                activityId = activityId,
                limit = 500,
            )
            activity = detail.activity
            messages = detail.messages.sortedBy { it.createdAt }
        } catch (e: Throwable) {
            errorMessage = friendlyErrorMessage(e)
        } finally {
            isLoading = false
        }
    }

    fun cancelActivity() {
        if (client == null) return
        scope.launch {
            isCancelling = true
            errorMessage = null
            try {
                val requestedBy = manager.currentUser?.displayName ?: manager.currentUser?.username
                activity = client.activities.cancel(
                    envId = environmentId,
                    activityId = activityId,
                    requestedBy = requestedBy,
                )
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isCancelling = false
            }
        }
    }

    LaunchedEffect(activityId, envId) { loadDetail() }

    val canCancel = activity?.let {
        it.isCancellable &&
            (manager.currentUser?.hasPermission("activities:cancel", environmentId.rawValue) ?: false)
    } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (canCancel) {
                        IconButton(onClick = { showCancelConfirm = true }, enabled = !isCancelling) {
                            if (isCancelling) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.Cancel, "Cancel activity", tint = ArcaneRed)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = activity
            if (current == null) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (errorMessage != null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Text(errorMessage!!, color = ArcaneRed, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "header") { ActivityHeader(current) }
                    item(key = "details") { ActivityDetailsSection(current) }
                    if (messages.isNotEmpty()) {
                        item(key = "messages-header") {
                            Text(
                                "Messages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(messages, key = { it.id }) { ActivityMessageRow(it) }
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Activity?") },
            text = { Text("Arcane will request cancellation. Work that already finished cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; cancelActivity() }) {
                    Text("Cancel Activity")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep Running") }
            },
        )
    }

    errorMessage?.let { message ->
        if (activity != null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
            )
        }
    }
}

@Composable
private fun ActivityHeader(activity: Activity) {
    val tint = activity.statusTint()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(activity.typeIcon(), null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    activity.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(status = activity.status.wire)
        }

        val progress = activity.progress
        if (progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress", style = MaterialTheme.typography.bodySmall)
                    Text("$progress%", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    color = tint,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val error = activity.error
        if (!error.isNullOrEmpty()) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = ArcaneRed)
        }
    }
}

@Composable
private fun ActivityDetailsSection(activity: Activity) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        LabeledRow("Type", activity.type.displayName)
        LabeledRow("Status", activity.status.wire.replaceFirstChar { it.uppercaseChar() })
        LabeledRow("Started", absoluteActivityTime(activity.startedAt))
        activity.endedAt?.let { LabeledRow("Ended", absoluteActivityTime(it)) }
        activity.durationMs?.let { LabeledRow("Duration", formatDuration(it)) }
        activity.startedBy?.let { LabeledRow("Started By", it.displayLabel) }
        activity.sourceEnvironmentName?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Source", it) }
        activity.resourceId?.takeIf { it.isNotEmpty() }?.let { LabeledRow("Resource ID", it) }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun ActivityMessageRow(message: ActivityMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            message.level.icon(),
            null,
            tint = message.level.tint(),
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(message.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                timeOfDay(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainder = seconds % 60
    if (minutes < 60) return "${minutes}m ${remainder}s"
    val hours = minutes / 60
    return "${hours}h ${minutes % 60}m"
}

/** Abbreviated date + standard time (e.g. "May 31, 2026 at 3:04:21 PM"). */
private fun absoluteActivityTime(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = MONTH_NAMES.getOrElse(dt.monthNumber - 1) { dt.monthNumber.toString() }
    val hour12 = when {
        dt.hour == 0 -> 12
        dt.hour > 12 -> dt.hour - 12
        else -> dt.hour
    }
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    val second = dt.second.toString().padStart(2, '0')
    return "$month ${dt.dayOfMonth}, ${dt.year} at $hour12:$minute:$second $amPm"
}

/** Hour:minute:second (e.g. "3:04:21 PM"). */
private fun timeOfDay(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour12 = when {
        dt.hour == 0 -> 12
        dt.hour > 12 -> dt.hour - 12
        else -> dt.hour
    }
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    val second = dt.second.toString().padStart(2, '0')
    return "$hour12:$minute:$second $amPm"
}

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
