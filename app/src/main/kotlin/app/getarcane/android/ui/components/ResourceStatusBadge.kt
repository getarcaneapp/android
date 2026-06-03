package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneYellow

/**
 * A capsule status pill with a leading status icon and tinted background/border.
 * Port of the iOS `ResourceStatusBadge`. Supports an explicit [isLive] override
 * (e.g. for resources that are streaming/online regardless of their text status).
 */
@Composable
fun ResourceStatusBadge(
    status: String?,
    modifier: Modifier = Modifier,
    isLive: Boolean? = null,
) {
    val normalized = status?.trim()?.lowercase().orEmpty()
    val displayText = status?.trim().takeUnless { it.isNullOrEmpty() }
        ?.replaceFirstChar { it.uppercaseChar() } ?: "Unknown"
    val live = isLive ?: (normalized == "running" || normalized == "online")
    val unknownTint = MaterialTheme.colorScheme.onSurfaceVariant

    val tint: Color = when {
        live -> ArcaneGreen
        normalized in setOf("running", "online", "success", "completed", "done") -> ArcaneGreen
        normalized in setOf("partial", "partially running") -> ArcaneOrange
        normalized in setOf("stopped", "exited", "offline") -> ArcaneRed
        normalized in setOf("error", "failed", "unhealthy") -> ArcaneRed
        normalized == "paused" -> ArcaneYellow
        else -> unknownTint
    }

    val icon: ImageVector = when {
        live -> Icons.Filled.CheckCircle
        normalized in setOf("running", "online", "success", "completed", "done") -> Icons.Filled.CheckCircle
        normalized in setOf("stopped", "exited", "offline") -> Icons.Filled.StopCircle
        normalized in setOf("error", "failed", "unhealthy") -> Icons.Filled.Error
        normalized == "paused" -> Icons.Filled.PauseCircle
        else -> Icons.Filled.Circle
    }

    Row(
        modifier = modifier
            .background(tint.copy(alpha = if (live) 0.16f else 0.12f), CircleShape)
            .border(0.75.dp, tint.copy(alpha = if (live) 0.28f else 0.18f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
            displayText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
        )
    }
}
