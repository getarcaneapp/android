package app.getarcane.android.core

import app.getarcane.sdk.models.container.ContainerDetails
import app.getarcane.sdk.models.container.ContainerSummary

/** App-level display helpers, mirroring the iOS `Models.swift` extensions. */

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
