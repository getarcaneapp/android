package app.getarcane.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Shimmering placeholder list shown while a resource list loads. Mirrors iOS `SkeletonListLoadingView`. */
@Composable
fun SkeletonListLoadingView(rows: Int = 6) {
    val widths = listOf(160, 110, 180, 140, 100, 150)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(rows) { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bar(Modifier.size(36.dp).clip(CircleShape), alpha)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(Modifier.width(widths[i % widths.size].dp).height(14.dp), alpha)
                    Bar(Modifier.width((widths[i % widths.size] - 40).dp).height(10.dp), alpha)
                }
            }
        }
    }
}

@Composable
private fun Bar(modifier: Modifier, alpha: Float) {
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.4f)),
    )
}
