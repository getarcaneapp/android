package app.getarcane.android.ui.screens.settings

import kotlinx.coroutines.CancellationException
import app.getarcane.sdk.android.passkey.PasskeyCeremonyCancelledException
import app.getarcane.sdk.models.auth.StepUpGrant
import kotlinx.datetime.Instant

internal sealed interface SensitiveMutationResult<out T> {
    data class Success<T>(val value: T) : SensitiveMutationResult<T>
    data object Cancelled : SensitiveMutationResult<Nothing>
    data class Failure(val cause: Throwable) : SensitiveMutationResult<Nothing>
}

/** Keeps cancellation structural while making ordinary failure recovery deterministic. */
internal suspend fun <T> runSensitiveMutation(block: suspend () -> T): SensitiveMutationResult<T> = try {
    SensitiveMutationResult.Success(block())
} catch (_: PasskeyCeremonyCancelledException) {
    SensitiveMutationResult.Cancelled
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    SensitiveMutationResult.Failure(e)
}

internal fun canBeginPasskeyEnrollment(
    passkeyCount: Int,
    canEnrollWithActiveSession: Boolean,
    requiresStepUp: Boolean,
    hasStepUpGrant: Boolean,
): Boolean = hasStepUpGrant ||
    (passkeyCount == 0 && canEnrollWithActiveSession && !requiresStepUp)

internal fun canDeletePasskey(
    passkeyCount: Int,
    canDeleteLastPasskey: Boolean,
    hasStepUpGrant: Boolean,
): Boolean = hasStepUpGrant && (passkeyCount != 1 || canDeleteLastPasskey)

internal fun validStepUpGrant(grant: StepUpGrant?, now: Instant): StepUpGrant? =
    grant?.takeIf { it.expiresAt > now }

internal fun stepUpExpiryDelayMillis(grant: StepUpGrant, now: Instant): Long =
    (grant.expiresAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)

/** Recovery material is valid only while MFA remains enabled and must not survive disable. */
internal fun recoveryCodesAfterMfaState(codes: List<String>, enabled: Boolean): List<String> =
    codes.takeIf { enabled }.orEmpty()

internal fun normalizedRecoveryCode(value: String): String = value.trim()

/** Invalidates replaced or backgrounded operations without retaining any sensitive payload. */
internal class SensitiveOperationGeneration {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun isCurrent(operation: Long): Boolean = operation == generation
}
