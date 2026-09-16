package app.getarcane.android.core

import app.getarcane.sdk.models.system.EnvironmentUpdateJob
import app.getarcane.sdk.models.system.EnvironmentUpdateResultStatus

internal data class FleetUpdateSummary(
    val updated: Int,
    val failed: Int,
    val offline: Int,
    val upToDate: Int,
    val pending: Int,
    val targetVersion: String?,
)

internal fun fleetUpdateSummary(job: EnvironmentUpdateJob): FleetUpdateSummary {
    val results = job.results.orEmpty()
    val updated = results.count {
        it.status == EnvironmentUpdateResultStatus.UPDATED ||
            it.status == EnvironmentUpdateResultStatus.TRIGGERED
    }
    val failed = results.count { it.status == EnvironmentUpdateResultStatus.FAILED }
    val offline = results.count { it.status == EnvironmentUpdateResultStatus.SKIPPED_OFFLINE }
    val upToDate = results.count { it.status == EnvironmentUpdateResultStatus.SKIPPED_UP_TO_DATE }
    return FleetUpdateSummary(
        updated = updated,
        failed = failed,
        offline = offline,
        upToDate = upToDate,
        pending = results.size - updated - failed - offline - upToDate,
        targetVersion = job.managerTargetVersion?.takeIf {
            it.isNotBlank() && ':' !in it && it.length <= 20
        },
    )
}
