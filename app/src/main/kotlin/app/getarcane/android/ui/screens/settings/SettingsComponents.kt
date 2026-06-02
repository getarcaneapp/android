package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneCyan
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneMint
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.android.ui.theme.ArcaneYellow
import app.getarcane.sdk.models.user.User

/**
 * A tappable settings row: tinted leading icon, title + optional subtitle, optional trailing content.
 * Mirrors iOS `SettingsRow` / `SettingsExternalRow`.
 */
@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.width(28.dp))
        },
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = subtitle?.takeIf { it.isNotEmpty() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = trailing,
    )
}

/** Trailing chevron, matching the iOS `chevron.right` affordance on tappable rows. */
@Composable
fun ChevronTrailing() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

/** Trailing outbound-arrow, matching the iOS `arrow.up.right` on external-link rows. */
@Composable
fun ExternalLinkTrailing() {
    Icon(
        Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(18.dp),
    )
}

/** Uppercase section header, matching the iOS grouped-list `Section` header. */
@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Footer caption under a section, matching the iOS grouped-list `Section` footer. */
@Composable
fun SettingsSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    )
}

/** A round, tinted leading icon used in list rows (iOS glass-circle parity). */
@Composable
fun CircleIcon(icon: ImageVector, tint: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Capsule badge (e.g. "Disabled", "Built-in", "Admin"). */
@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (filled) Color.White else color,
        modifier = Modifier
            .background(
                if (filled) color else color.copy(alpha = 0.15f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Prefer the human-friendly display name, falling back to username. Mirrors iOS `User.displayUsername`. */
val User.displayUsername: String
    get() = displayName?.takeIf { it.isNotEmpty() } ?: username

// MARK: - Accent color options (mirrors iOS AppearanceSettingsView.AccentColorOption)

/**
 * The selectable accent-color swatches, in the same order and with the same hex values as the iOS
 * app's `AccentColorOption`. The picker stores the chosen hex locally (driving the theme is the
 * lead's job).
 */
enum class AccentColorOption(val displayName: String, val color: Color, val hex: String) {
    Blue("Blue", ArcaneBlue, "#007AFF"),
    Indigo("Indigo", ArcaneIndigo, "#5856D6"),
    Purple("Purple", ArcanePurple, "#AF52DE"),
    Pink("Pink", ArcanePink, "#FF2D55"),
    Red("Red", ArcaneRed, "#FF3B30"),
    Orange("Orange", ArcaneOrange, "#FF9500"),
    Yellow("Yellow", ArcaneYellow, "#FFCC00"),
    Green("Green", ArcaneGreen, "#34C759"),
    Teal("Teal", ArcaneTeal, "#5AC8FA"),
    Mint("Mint", ArcaneMint, "#00C7BE"),
    Cyan("Cyan", ArcaneCyan, "#32D2F0"),
    ;

    companion object {
        /** Resolve a stored hex back to its swatch, defaulting to [Blue] for empty/unknown values. */
        fun fromHex(hex: String?): AccentColorOption {
            if (hex.isNullOrEmpty()) return Blue
            val normalized = hex.lowercase()
            return entries.firstOrNull { it.hex.lowercase() == normalized } ?: Blue
        }
    }
}
