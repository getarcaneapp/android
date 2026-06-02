package app.getarcane.android.ui.screens.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneYellow
import app.getarcane.sdk.models.vulnerability.CVSSInfo
import app.getarcane.sdk.models.vulnerability.VulnerabilityScanStatus
import app.getarcane.sdk.models.vulnerability.VulnerabilitySeverity
import app.getarcane.sdk.models.vulnerability.VulnerabilitySeveritySummary
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// MARK: - Vulnerability severity presentation

/** Color for a severity, matching the iOS `SeverityBadge`. */
fun severityColor(severity: VulnerabilitySeverity): Color = when (severity) {
    VulnerabilitySeverity.CRITICAL -> ArcaneRed
    VulnerabilitySeverity.HIGH -> ArcaneOrange
    VulnerabilitySeverity.MEDIUM -> ArcaneYellow
    VulnerabilitySeverity.LOW -> ArcaneBlue
    VulnerabilitySeverity.UNKNOWN -> ArcaneGray
}

/** Single-letter label for a severity, matching the iOS `SeverityBadge`. */
fun severityLetter(severity: VulnerabilitySeverity): String = when (severity) {
    VulnerabilitySeverity.CRITICAL -> "C"
    VulnerabilitySeverity.HIGH -> "H"
    VulnerabilitySeverity.MEDIUM -> "M"
    VulnerabilitySeverity.LOW -> "L"
    VulnerabilitySeverity.UNKNOWN -> "?"
}

/** Title-cased display label for a severity (e.g. "Critical"). Mirrors iOS `displayLabel`. */
fun severityDisplayLabel(severity: VulnerabilitySeverity): String =
    severity.wire.lowercase().replaceFirstChar { it.uppercase() }

/** Preferred CVSS score: v3 if present, else v2. Mirrors iOS `CVSSInfo.preferredScore`. */
val CVSSInfo.preferredScore: Double?
    get() = v3Score ?: v2Score

/** Circular severity badge with a single letter (iOS `SeverityBadge`). */
@Composable
fun SeverityBadge(severity: VulnerabilitySeverity) {
    Box(
        Modifier.size(22.dp).background(severityColor(severity), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            severityLetter(severity),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Summary header for a scan: error line, severity pill counts, and status + scan time. Mirrors iOS `SeveritySummaryRow`. */
@Composable
fun SeveritySummaryRow(
    summary: VulnerabilitySeveritySummary?,
    scanTime: String?,
    status: String,
    error: String?,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!error.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, null, tint = ArcaneRed, modifier = Modifier.size(16.dp))
                Text(error, style = MaterialTheme.typography.labelMedium, color = ArcaneRed)
            }
        }
        if (summary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SeverityPill("Critical", summary.critical, ArcaneRed, Modifier.weight(1f))
                SeverityPill("High", summary.high, ArcaneOrange, Modifier.weight(1f))
                SeverityPill("Med", summary.medium, ArcaneYellow, Modifier.weight(1f))
                SeverityPill("Low", summary.low, ArcaneBlue, Modifier.weight(1f))
                SeverityPill("?", summary.unknown, ArcaneGray, Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Status: ${status.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!scanTime.isNullOrEmpty()) {
                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    scanTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SeverityPill(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: - Shared list-section scaffolding

@Composable
fun DetailSection(title: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        content()
    }
}

@Composable
fun LabeledRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
fun MonoRow(value: String, maxLines: Int = 1) {
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

// MARK: - Date formatting (kotlinx.datetime; safe for minSdk 24 without desugaring)

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Abbreviated date + short time (e.g. "May 31, 2026 at 3:04 PM"). Mirrors iOS `.abbreviated`/`.shortened`. */
fun formatImageDate(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = monthNames.getOrElse(dt.monthNumber - 1) { dt.monthNumber.toString() }
    val hour12 = when {
        dt.hour == 0 -> 12
        dt.hour > 12 -> dt.hour - 12
        else -> dt.hour
    }
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    return "$month ${dt.dayOfMonth}, ${dt.year} at $hour12:$minute $amPm"
}

/**
 * Parse an ISO-8601 timestamp string and format it as an abbreviated date/time, falling back to the
 * raw string when it can't be parsed. Mirrors the iOS `String.formattedDate` helper used in
 * `ImageDetailView`.
 */
fun formatIsoDate(raw: String): String =
    runCatching { formatImageDate(Instant.parse(raw)) }.getOrDefault(raw)

/** Lowercase wire status name for a [VulnerabilityScanStatus]. */
fun VulnerabilityScanStatus.label(): String = wire
