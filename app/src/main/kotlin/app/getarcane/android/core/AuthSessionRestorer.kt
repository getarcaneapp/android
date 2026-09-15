package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class SavedAuthState(
    val serverUrl: String?,
    val activeEnvironmentId: String?,
    val activeEnvironmentName: String?,
    val credentialOrigin: String? = null,
)

internal suspend fun validateSavedSessionWithin(
    timeoutMs: Long,
    validate: suspend () -> Unit,
) {
    val completed = withTimeoutOrNull(timeoutMs) {
        validate()
        true
    }
    if (completed == null) throw ArcaneError.Transport("Saved-session validation timed out.")
}

/**
 * Restores the persisted server and session without exposing the login screen before validation.
 *
 * Android dependencies stay in [ArcaneClientManager]; keeping the transition coordinator here
 * makes startup failure, invalid-session, and cancellation behavior deterministic and testable.
 */
internal suspend fun restoreAuthenticationSession(
    loadSavedState: suspend () -> SavedAuthState,
    applySavedState: (SavedAuthState) -> Unit,
    openSavedServer: (String) -> Unit,
    validateSavedSession: suspend () -> Unit,
    refreshLoginMethods: suspend () -> Unit,
    updateStatus: (AuthStatus) -> Unit,
    recoverOfflineSession: suspend (Throwable) -> Boolean = { false },
) {
    try {
        val savedState = loadSavedState()
        applySavedState(savedState)

        val savedServer = savedState.serverUrl
        if (savedServer.isNullOrBlank()) {
            updateStatus(AuthStatus.SETUP)
            return
        }

        openSavedServer(savedServer)
        updateStatus(AuthStatus.AUTHENTICATING)

        try {
            validateSavedSession()
            updateStatus(AuthStatus.AUTHENTICATED)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            if (recoverOfflineSession(error)) {
                updateStatus(AuthStatus.AUTHENTICATED)
            } else {
                updateStatus(AuthStatus.LOGIN)
                try {
                    refreshLoginMethods()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Login remains available even when optional OIDC discovery fails.
                }
            }
        }
    } catch (e: CancellationException) {
        updateStatus(AuthStatus.SETUP)
        throw e
    } catch (_: Throwable) {
        updateStatus(AuthStatus.SETUP)
    }
}
