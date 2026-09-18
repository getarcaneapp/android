package app.getarcane.android.core

import app.getarcane.sdk.models.version.VersionInfo

/** Container pause/unpause/kill were added by Arcane 2.2.0. */
internal fun VersionInfo.supportsContainerReliabilityActions(): Boolean {
    return supportsAtLeast(2, 2)
}

/** Standalone edit/commit/Compose conversion shipped with Arcane 2.10. */
internal fun VersionInfo.supportsContainerManagementWorkflows(): Boolean = supportsAtLeast(2, 10)

private fun VersionInfo.supportsAtLeast(requiredMajor: Int, requiredMinor: Int): Boolean {
    if (!isSemverVersion) return false
    val components = currentVersion
        .trim()
        .removePrefix("v")
        .substringBefore('+')
        .substringBefore('-')
        .split('.')
    if (components.size != 3) return false
    val version = components.map { it.toIntOrNull()?.takeIf { value -> value >= 0 } ?: return false }
    return version[0] > requiredMajor ||
        (version[0] == requiredMajor && version[1] >= requiredMinor)
}
