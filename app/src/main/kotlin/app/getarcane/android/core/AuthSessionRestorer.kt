package app.getarcane.android.core

import kotlinx.coroutines.CancellationException

internal data class SavedAuthState(
    val serverUrl: String?,
    val activeEnvironmentId: String?,
    val activeEnvironmentName: String?,
    val credentialOrigin: String? = null,
)

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
        } catch (_: Throwable) {
            updateStatus(AuthStatus.LOGIN)
            try {
                refreshLoginMethods()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Login remains available even when optional OIDC discovery fails.
            }
        }
    } catch (e: CancellationException) {
        updateStatus(AuthStatus.SETUP)
        throw e
    } catch (_: Throwable) {
        updateStatus(AuthStatus.SETUP)
    }
}
