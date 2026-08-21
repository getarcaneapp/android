package app.getarcane.android.core

import app.getarcane.sdk.auth.TokenPair
import app.getarcane.sdk.auth.TokenStore
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerBoundTokenStoreTest {
    @Test
    fun `matching origin loads only its scoped tokens`() = runBlocking {
        val scoped = RecordingTokenStore(tokens("scoped"))
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding(ORIGIN)
        val store = store(scoped, legacy, binding, allowsLegacyMigration = true)

        assertEquals("scoped", store.loadTokens()?.accessToken)
        assertEquals(1, scoped.loadCount)
        assertEquals(0, legacy.loadCount)
        assertEquals(ORIGIN, binding.value)
    }

    @Test
    fun `different bound origin cannot load scoped or legacy tokens`() = runBlocking {
        val scoped = RecordingTokenStore(tokens("scoped"))
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding("https://other.example.com:443")
        val store = store(scoped, legacy, binding, allowsLegacyMigration = true)

        assertNull(store.loadTokens())
        assertEquals(0, scoped.loadCount)
        assertEquals(0, legacy.loadCount)
    }

    @Test
    fun `unbound store does not activate existing credentials outside migration`() = runBlocking {
        val scoped = RecordingTokenStore(tokens("scoped"))
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding(null)
        val store = store(scoped, legacy, binding, allowsLegacyMigration = false)

        assertNull(store.loadTokens())
        assertEquals(0, scoped.loadCount)
        assertEquals(0, legacy.loadCount)
        assertNull(binding.value)
    }

    @Test
    fun `upgrade migration moves legacy token to saved origin once`() = runBlocking {
        val scoped = RecordingTokenStore(null)
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding(null)
        val store = store(scoped, legacy, binding, allowsLegacyMigration = true)

        assertEquals("legacy", store.loadTokens()?.accessToken)
        assertEquals("legacy", scoped.value?.accessToken)
        assertNull(legacy.value)
        assertEquals(1, legacy.clearCount)
        assertEquals(ORIGIN, binding.value)

        assertEquals("legacy", store.loadTokens()?.accessToken)
        assertEquals(1, legacy.loadCount)
    }

    @Test
    fun `save persists to origin and binds it`() = runBlocking {
        val scoped = RecordingTokenStore(null)
        val binding = CredentialBinding(null)
        val store = store(scoped, RecordingTokenStore(null), binding)

        store.saveTokens(tokens("new"))

        assertEquals("new", scoped.value?.accessToken)
        assertEquals(ORIGIN, binding.value)
    }

    @Test
    fun `clear removes scoped and legacy tokens and matching binding`() = runBlocking {
        val scoped = RecordingTokenStore(tokens("scoped"))
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding(ORIGIN)
        val store = store(scoped, legacy, binding)

        store.clearTokens()

        assertNull(scoped.value)
        assertNull(legacy.value)
        assertEquals(1, scoped.clearCount)
        assertEquals(1, legacy.clearCount)
        assertNull(binding.value)
    }

    @Test
    fun `clear attempts every store and unbinds before surfacing failure`() {
        val failure = IOException("scoped clear failed")
        val scoped = RecordingTokenStore(tokens("scoped"), clearFailure = failure)
        val legacy = RecordingTokenStore(tokens("legacy"))
        val binding = CredentialBinding(ORIGIN)
        val store = store(scoped, legacy, binding)

        val thrown = assertThrows(IOException::class.java) {
            runBlocking { store.clearTokens() }
        }

        assertSame(failure, thrown)
        assertEquals(1, scoped.clearCount)
        assertEquals(1, legacy.clearCount)
        assertNull(legacy.value)
        assertNull(binding.value)
    }

    private fun store(
        scoped: TokenStore,
        legacy: TokenStore,
        binding: CredentialBinding,
        allowsLegacyMigration: Boolean = false,
    ): ServerBoundTokenStore =
        ServerBoundTokenStore(
            origin = ORIGIN,
            originStore = scoped,
            legacyStore = legacy,
            allowsLegacyMigration = allowsLegacyMigration,
            credentialOrigin = { binding.value },
            bindCredentialOrigin = { binding.value = it },
            unbindCredentialOrigin = { if (binding.value == it) binding.value = null },
        )

    private fun tokens(accessToken: String): TokenPair =
        TokenPair(
            accessToken = accessToken,
            refreshToken = "refresh-$accessToken",
            expiresAt = Instant.fromEpochMilliseconds(4_102_444_800_000),
        )

    private class CredentialBinding(var value: String?)

    private class RecordingTokenStore(
        var value: TokenPair?,
        private val clearFailure: Throwable? = null,
    ) : TokenStore {
        var loadCount = 0
        var clearCount = 0

        override suspend fun loadTokens(): TokenPair? {
            loadCount++
            return value
        }

        override suspend fun saveTokens(tokens: TokenPair) {
            value = tokens
        }

        override suspend fun clearTokens() {
            clearCount++
            clearFailure?.let { throw it }
            value = null
        }
    }

    private companion object {
        const val ORIGIN = "https://arcane.example.com:443"
    }
}
