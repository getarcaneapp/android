package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.theme.StatusPaused
import app.getarcane.android.ui.theme.StatusRunning
import app.getarcane.android.ui.theme.StatusStopped
import app.getarcane.android.ui.theme.StatusUnknown

/** Maps a Docker-ish status string to its semantic color (iOS StatusBadge parity). */
fun statusColor(status: String?): Color = when (status?.lowercase()?.trim()) {
    "running", "online", "up", "healthy" -> StatusRunning
    "paused" -> StatusPaused
    "stopped", "offline", "exited", "dead", "unhealthy" -> StatusStopped
    else -> StatusUnknown
}

/** A color-coded status pill. */
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Text(
        text = status.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
