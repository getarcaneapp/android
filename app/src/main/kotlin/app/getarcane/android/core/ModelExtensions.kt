package app.getarcane.android.core

import app.getarcane.sdk.models.container.ContainerDetails
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.project.ProjectDetails

/** App-level display helpers, mirroring the iOS `Models.swift` extensions. */

/**
 * Filter resources by image-update availability. Mirrors the iOS `ResourceUpdateFilter`.
 */
enum class ResourceUpdateFilter(val title: String) {
    ALL("All"),
    HAS_UPDATES("Has Updates"),
    ;

    fun matches(hasUpdate: Boolean): Boolean = when (this) {
        ALL -> true
        HAS_UPDATES -> hasUpdate
    }
}

/** True when an image update is available for this container. Mirrors iOS `hasAvailableUpdate`. */
val ContainerSummary.hasAvailableUpdate: Boolean
    get() = updateInfo?.hasUpdate == true

/** True when an image update is available for this project. Mirrors iOS `hasAvailableUpdate`. */
val ProjectDetails.hasAvailableUpdate: Boolean
    get() = updateInfo?.hasUpdate == true

val ContainerSummary.displayName: String
    get() = names.firstOrNull()?.removePrefix("/")?.ifBlank { null } ?: id.take(12)

val ContainerSummary.isRunning: Boolean
    get() = state.equals("running", ignoreCase = true)

/** Icon URL from the `com.getarcaneapp.arcane.icon` label, if present. Mirrors iOS `iconUrl`. */
val ContainerSummary.iconUrl: String?
    get() = (labels["com.getarcaneapp.arcane.icon"] as? app.getarcane.sdk.models.base.JsonValue.Str)?.value

val ContainerDetails.displayName: String
    get() = name.removePrefix("/").ifBlank { id.take(12) }

val ContainerDetails.isRunning: Boolean
    get() = state.running
