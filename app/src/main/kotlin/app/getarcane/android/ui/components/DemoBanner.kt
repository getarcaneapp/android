package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.theme.ArcaneOrange
import kotlinx.coroutines.delay

/**
 * Slim banner shown above the main shell while a hosted demo is active: "Demo Mode" with a live
 * countdown to expiry (turns orange under a minute) and an "End" button. Port of iOS `DemoBanner`.
 */
@Composable
fun DemoBanner() {
    val manager = LocalArcaneManager.current
    val endsAt = manager.demoEndsAt
    if (!manager.isDemoActive || endsAt == null) return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val totalSeconds = ((endsAt - now).coerceAtLeast(0) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val isLowTime = totalSeconds < 60
    val brand = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(28.dp).background(brand.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = brand, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Demo Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    String.format("%d:%02d remaining", minutes, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (isLowTime) ArcaneOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { manager.endDemo(expired = false) }) { Text("End") }
        }
    }
}
