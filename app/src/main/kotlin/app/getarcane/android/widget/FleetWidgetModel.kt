package app.getarcane.android.widget

import app.getarcane.android.core.SnapshotFreshness
import app.getarcane.android.core.StatusEnvironmentSnapshot
import app.getarcane.android.core.StatusSnapshot
import app.getarcane.android.core.StatusSnapshotRead
import app.getarcane.android.nav.AuthenticatedRoute
import app.getarcane.android.nav.AuthenticatedRouteCodec
import app.getarcane.android.nav.RouteDestination

internal enum class FleetWidgetState {
    FRESH,
    STALE,
    UNAVAILABLE,
    SIGNED_OUT,
    UNCONFIGURED,
}

internal data class FleetWidgetModel(
    val state: FleetWidgetState,
    val statusLabel: String,
    val freshnessLabel: String?,
    val runningContainers: Int = 0,
    val totalContainers: Int = 0,
    val images: Int = 0,
    val updates: Int = 0,
    val onlineEnvironments: Int = 0,
    val environments: List<StatusEnvironmentSnapshot> = emptyList(),
    val routeUri: String? = null,
) {
    companion object {
        fun from(read: StatusSnapshotRead, nowEpochMs: Long): FleetWidgetModel = when (read) {
            StatusSnapshotRead.Missing -> FleetWidgetModel(
                state = FleetWidgetState.UNCONFIGURED,
                statusLabel = "Open Arcane to set up",
                freshnessLabel = null,
            )
            StatusSnapshotRead.CorruptOrUnsupported -> FleetWidgetModel(
                state = FleetWidgetState.UNAVAILABLE,
                statusLabel = "Status unavailable",
                freshnessLabel = null,
            )
            is StatusSnapshotRead.Available -> fromSnapshot(read.snapshot, nowEpochMs)
        }

        private fun fromSnapshot(snapshot: StatusSnapshot, nowEpochMs: Long): FleetWidgetModel {
            if (snapshot.freshness == SnapshotFreshness.SIGNED_OUT) {
                return FleetWidgetModel(
                    state = FleetWidgetState.SIGNED_OUT,
                    statusLabel = "Signed out",
                    freshnessLabel = null,
                )
            }
            val sourceTime = snapshot.sourceUpdatedAtEpochMs ?: snapshot.generatedAtEpochMs
            val ageMillis = (nowEpochMs - sourceTime).coerceAtLeast(0)
            val state = when (snapshot.freshness) {
                SnapshotFreshness.FRESH -> when {
                    ageMillis > MAXIMUM_STALE_AGE_MILLIS -> FleetWidgetState.UNAVAILABLE
                    ageMillis > FRESH_REVALIDATION_MILLIS -> FleetWidgetState.STALE
                    else -> FleetWidgetState.FRESH
                }
                SnapshotFreshness.STALE -> FleetWidgetState.STALE
                SnapshotFreshness.ERROR -> FleetWidgetState.UNAVAILABLE
                SnapshotFreshness.SIGNED_OUT -> error("Handled above")
            }
            val status = when (state) {
                FleetWidgetState.FRESH -> "Fresh"
                FleetWidgetState.STALE -> "Stale snapshot"
                FleetWidgetState.UNAVAILABLE -> "Live status unavailable"
                FleetWidgetState.SIGNED_OUT,
                FleetWidgetState.UNCONFIGURED,
                -> error("Handled above")
            }
            return FleetWidgetModel(
                state = state,
                statusLabel = status,
                freshnessLabel = ageLabel(nowEpochMs, sourceTime),
                runningContainers = snapshot.totalRunningContainers,
                totalContainers = snapshot.totalContainers,
                images = snapshot.totalImages,
                updates = snapshot.totalUpdates,
                onlineEnvironments = snapshot.onlineEnvironments,
                environments = snapshot.environments.take(4),
                routeUri = snapshot.serverBindingHash?.let { serverHash ->
                    AuthenticatedRouteCodec.encode(
                        AuthenticatedRoute(serverHash, RouteDestination.DASHBOARD),
                    )
                },
            )
        }

        private fun ageLabel(nowEpochMs: Long, sourceEpochMs: Long): String {
            val elapsedMinutes = ((nowEpochMs - sourceEpochMs).coerceAtLeast(0) / 60_000)
            return when {
                elapsedMinutes < 1 -> "Updated just now"
                elapsedMinutes < 60 -> "Updated ${elapsedMinutes}m ago"
                elapsedMinutes < 1_440 -> "Updated ${elapsedMinutes / 60}h ago"
                else -> "Updated ${elapsedMinutes / 1_440}d ago"
            }
        }

        // Mirrors the approved Dashboard cache policy. No timer or network work is scheduled;
        // whenever the launcher asks Glance to render, old data cannot continue to claim Fresh.
        private const val FRESH_REVALIDATION_MILLIS = 60_000L
        private const val MAXIMUM_STALE_AGE_MILLIS = 24 * 60 * 60_000L
    }
}
