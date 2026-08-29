package app.getarcane.android.core

import app.getarcane.sdk.auth.TokenPair
import app.getarcane.sdk.auth.TokenStore
import kotlinx.coroutines.CancellationException

/**
 * Binds SDK token persistence to one canonical server origin.
 *
 * The legacy store is consulted only during an explicitly allowed upgrade migration. A separate
 * credential-origin binding prevents an origin-scoped token from becoming active merely because a
 * user enters another server URL.
 */
internal class ServerBoundTokenStore(
    private val origin: String,
    private val originStore: TokenStore,
    private val legacyStore: TokenStore,
    private val allowsLegacyMigration: Boolean,
    private val credentialOrigin: suspend () -> String?,
    private val bindCredentialOrigin: suspend (String) -> Unit,
    private val unbindCredentialOrigin: suspend (String) -> Unit,
) : TokenStore {
    override suspend fun loadTokens(): TokenPair? {
        val boundOrigin = credentialOrigin()
        val canUseOrigin = boundOrigin == origin || (boundOrigin == null && allowsLegacyMigration)
        if (!canUseOrigin) return null

        originStore.loadTokens()?.let { tokens ->
            if (boundOrigin == null) bindCredentialOrigin(origin)
            return tokens
        }
        if (!allowsLegacyMigration) return null

        val legacyTokens = legacyStore.loadTokens() ?: return null
        originStore.saveTokens(legacyTokens)
        legacyStore.clearTokens()
        bindCredentialOrigin(origin)
        return legacyTokens
    }

    override suspend fun saveTokens(tokens: TokenPair) {
        originStore.saveTokens(tokens)
        bindCredentialOrigin(origin)
    }

    override suspend fun clearTokens() {
        var firstFailure: Throwable? = null
        for (store in listOf(originStore, legacyStore)) {
            try {
                store.clearTokens()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (firstFailure == null) firstFailure = failure
            }
        }
        unbindCredentialOrigin(origin)
        firstFailure?.let { throw it }
    }
}
