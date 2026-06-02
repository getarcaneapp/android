package app.getarcane.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Animated circular progress ring with a centered value + label below. Port of iOS `StatRing`. */
@Composable
fun StatRing(
    value: Float,
    valueText: String,
    label: String,
    tint: Color,
    size: Dp = 62.dp,
    lineWidth: Dp = 7.dp,
) {
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = lineWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = tint.copy(alpha = 0.15f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = tint,
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(valueText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
