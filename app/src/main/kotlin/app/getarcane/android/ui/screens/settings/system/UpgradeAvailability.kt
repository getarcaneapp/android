package app.getarcane.android.ui.screens.settings.system

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.system.UpgradeCheckResult
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.hasPermission
import app.getarcane.sdk.models.version.VersionInfo
import kotlinx.coroutines.CancellationException

internal sealed interface UpgradeAvailability {
    data object Loading : UpgradeAvailability
    data class Unauthorized(val missingPermissions: Set<String>) : UpgradeAvailability
    data class OlderServer(val currentVersion: String? = null) : UpgradeAvailability
    data class Unsupported(val message: String) : UpgradeAvailability
    data class Unavailable(val message: String) : UpgradeAvailability
    data class Error(val message: String) : UpgradeAvailability
    data class Ready(val result: UpgradeCheckResult, val version: VersionInfo) : UpgradeAvailability
}

internal fun missingUpgradePermissions(user: User, environmentId: String): Set<String> =
    buildSet {
        if (!user.hasPermission(Permission.System.READ, environmentId)) add(Permission.System.READ)
        if (!user.hasPermission(Permission.System.UPGRADE, environmentId)) add(Permission.System.UPGRADE)
    }

/**
 * Resolves eligibility from environment-scoped permissions, typed version capability, and the
 * server's final `checkUpgrade` result. No UI-owned version heuristic participates.
 */
internal suspend fun resolveUpgradeAvailability(
    user: User,
    environmentId: String,
    loadVersion: suspend () -> VersionInfo,
    checkUpgrade: suspend () -> UpgradeCheckResult,
    errorMessage: (Throwable) -> String = { it.message ?: "Unknown error" },
): UpgradeAvailability {
    val missingPermissions = missingUpgradePermissions(user, environmentId)
    if (missingPermissions.isNotEmpty()) return UpgradeAvailability.Unauthorized(missingPermissions)

    val version = try {
        loadVersion()
    } catch (e: CancellationException) {
        throw e
    } catch (_: ArcaneError.NotFound) {
        return UpgradeAvailability.OlderServer()
    } catch (e: ArcaneError.Unauthorized) {
        return UpgradeAvailability.Unauthorized(setOf(Permission.System.READ))
    } catch (e: ArcaneError.Forbidden) {
        return UpgradeAvailability.Unauthorized(setOf(Permission.System.READ))
    } catch (e: Throwable) {
        return UpgradeAvailability.Error(errorMessage(e))
    }

    if (!version.supportsSystemUpgrade) {
        return UpgradeAvailability.OlderServer(version.currentVersion)
    }
    if (!version.updateAvailable) {
        return UpgradeAvailability.Unavailable("${version.displayVersion} is already up to date.")
    }

    val result = try {
        checkUpgrade()
    } catch (e: CancellationException) {
        throw e
    } catch (_: ArcaneError.NotFound) {
        return UpgradeAvailability.Unsupported("This server does not expose self-upgrade for this environment.")
    } catch (e: ArcaneError.Unauthorized) {
        return UpgradeAvailability.Unauthorized(setOf(Permission.System.READ))
    } catch (e: ArcaneError.Forbidden) {
        return UpgradeAvailability.Unauthorized(setOf(Permission.System.READ))
    } catch (e: Throwable) {
        return UpgradeAvailability.Error(errorMessage(e))
    }
    return when {
        result.error -> UpgradeAvailability.Error(result.message)
        result.canUpgrade -> UpgradeAvailability.Ready(result, version)
        else -> UpgradeAvailability.Unsupported(result.message)
    }
}

internal val UpgradeAvailability.canUpgrade: Boolean
    get() = this is UpgradeAvailability.Ready

internal fun UpgradeAvailability.summary(): String = when (this) {
    UpgradeAvailability.Loading -> "Checking this environment…"
    is UpgradeAvailability.Unauthorized -> "Your account is not authorized for this environment"
    is UpgradeAvailability.OlderServer -> currentVersion?.let { "Arcane $it does not support mobile self-upgrade" }
        ?: "This Arcane server is too old for mobile self-upgrade"
    is UpgradeAvailability.Unsupported -> message
    is UpgradeAvailability.Unavailable -> message
    is UpgradeAvailability.Error -> "Couldn't check: $message"
    is UpgradeAvailability.Ready -> result.message
}
