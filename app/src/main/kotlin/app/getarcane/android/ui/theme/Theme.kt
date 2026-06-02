package app.getarcane.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App theme. The accent color is configurable (iOS AppearanceSettings parity); it drives the
 * Material3 `primary`. All real styling is done in Compose (the XML theme is window-chrome only).
 */
@Composable
fun ArcaneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = ArcaneBlue,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = ArcaneTeal,
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = ArcaneTeal,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArcaneTypography,
        content = content,
    )
}
