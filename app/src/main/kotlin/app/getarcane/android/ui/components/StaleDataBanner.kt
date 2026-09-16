package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

data class StaleDataInfo(
    val storedAtEpochMs: Long,
    val refreshError: String? = null,
)

@Composable
fun StaleDataBanner(info: StaleDataInfo, nowEpochMs: Long = System.currentTimeMillis()) {
    val age = staleAgeLabel(nowEpochMs - info.storedAtEpochMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        androidx.compose.foundation.layout.Row {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                "  Cached data · updated $age",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        info.refreshError?.let {
            Text(
                "Live refresh unavailable. Pull to retry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

internal fun staleAgeLabel(ageMillis: Long): String {
    val safeAge = ageMillis.coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeAge)
    val hours = TimeUnit.MILLISECONDS.toHours(safeAge)
    val days = TimeUnit.MILLISECONDS.toDays(safeAge)
    return when {
        days > 0 -> "$days day${if (days == 1L) "" else "s"} ago"
        hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        minutes > 0 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        else -> "just now"
    }
}
