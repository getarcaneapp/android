package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed

enum class BannerSeverity { Error, Warning }

/** Inline error/warning banner with optional retry. Mirrors iOS `ErrorBanner`. */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    severity: BannerSeverity = BannerSeverity.Error,
    onRetry: (() -> Unit)? = null,
) {
    val tint = if (severity == BannerSeverity.Warning) ArcaneOrange else ArcaneRed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = tint)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
