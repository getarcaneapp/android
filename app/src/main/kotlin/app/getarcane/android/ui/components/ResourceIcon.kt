package app.getarcane.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A tinted circular icon used as a list row leading element (iOS glass-circle parity). */
@Composable
fun ResourceIcon(icon: ImageVector, tint: Color, size: Int = 38) {
    Box(
        Modifier.size(size.dp).background(tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.52f).dp))
    }
}
