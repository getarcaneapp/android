package app.getarcane.android.core

import app.getarcane.sdk.models.image.ImageSummary

/** Human-readable byte size (e.g. 1.5 GB). */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", value, units[i])
}

/** Display name for an image: first real repo:tag, else short id. Mirrors iOS `ImageSummary.displayName`. */
val ImageSummary.displayName: String
    get() = repoTags.firstOrNull { it.isNotBlank() && it != "<none>:<none>" }
        ?: id.removePrefix("sha256:").take(12)
