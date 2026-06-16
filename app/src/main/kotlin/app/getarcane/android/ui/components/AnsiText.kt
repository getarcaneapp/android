package app.getarcane.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.getarcane.android.core.AnsiSanitizer
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneCyan
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneYellow

@Composable
fun buildAnsiAnnotatedString(input: String): AnnotatedString {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    return buildAnnotatedString {
        AnsiSanitizer.parseSgr(input).forEach { segment ->
            val foreground = segment.foreground?.toComposeColor(defaultColor)
            val style = SpanStyle(
                color = foreground ?: Color.Unspecified,
                fontWeight = if (segment.bold) FontWeight.Bold else null,
            )
            if (foreground != null || segment.bold) {
                withStyle(style) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
}

private fun AnsiSanitizer.Foreground.toComposeColor(defaultColor: Color): Color = when (this) {
    AnsiSanitizer.Foreground.Red -> ArcaneRed
    AnsiSanitizer.Foreground.Green -> ArcaneGreen
    AnsiSanitizer.Foreground.Yellow -> ArcaneYellow
    AnsiSanitizer.Foreground.Blue -> ArcaneBlue
    AnsiSanitizer.Foreground.Magenta -> ArcanePurple
    AnsiSanitizer.Foreground.Cyan -> ArcaneCyan
    AnsiSanitizer.Foreground.White -> defaultColor
    AnsiSanitizer.Foreground.Default -> defaultColor
}
