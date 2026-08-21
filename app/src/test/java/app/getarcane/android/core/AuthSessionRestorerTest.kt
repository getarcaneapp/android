package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionRestorerTest {
    @Test
    fun `fresh install routes from authentication gate to setup`() = runBlocking {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)
        var openedServer: String? = null

        restoreAuthenticationSession(
            loadSavedState = { SavedAuthState(null, null, null) },
            applySavedState = {},
            openSavedServer = { openedServer = it },
            validateSavedSession = { error("should not validate") },
            refreshLoginMethods = { error("should not refresh") },
            updateStatus = statuses::add,
        )

        assertEquals(listOf(AuthStatus.AUTHENTICATING, AuthStatus.SETUP), statuses)
        assertNull(openedServer)
    }

    @Test
    fun `process recreation keeps auth gate visible until saved session validates`() = runBlocking {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)
        var appliedState: SavedAuthState? = null
        var openedServer: String? = null
        val state = SavedAuthState("https://arcane.example.com", "edge", "Edge")

        restoreAuthenticationSession(
            loadSavedState = { state },
            applySavedState = { appliedState = it },
            openSavedServer = { openedServer = it },
            validateSavedSession = {},
            refreshLoginMethods = { error("should not refresh") },
            updateStatus = statuses::add,
        )

        assertEquals(state, appliedState)
        assertEquals(state.serverUrl, openedServer)
        assertEquals(
            listOf(AuthStatus.AUTHENTICATING, AuthStatus.AUTHENTICATING, AuthStatus.AUTHENTICATED),
            statuses,
        )
        assertTrue(AuthStatus.LOGIN !in statuses)
    }

    @Test
    fun `invalid saved session routes to login and refreshes login methods`() = runBlocking {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)
        var refreshCount = 0

        restoreAuthenticationSession(
            loadSavedState = { SavedAuthState("https://arcane.example.com", null, null) },
            applySavedState = {},
            openSavedServer = {},
            validateSavedSession = { throw ArcaneError.Unauthorized },
            refreshLoginMethods = { refreshCount++ },
            updateStatus = statuses::add,
        )

        assertEquals(
            listOf(AuthStatus.AUTHENTICATING, AuthStatus.AUTHENTICATING, AuthStatus.LOGIN),
            statuses,
        )
        assertEquals(1, refreshCount)
    }

    @Test
    fun `OIDC discovery failure leaves password login available`() = runBlocking {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)

        restoreAuthenticationSession(
            loadSavedState = { SavedAuthState("https://arcane.example.com", null, null) },
            applySavedState = {},
            openSavedServer = {},
            validateSavedSession = { throw IOException("unauthorized") },
            refreshLoginMethods = { throw IOException("discovery unavailable") },
            updateStatus = statuses::add,
        )

        assertEquals(AuthStatus.LOGIN, statuses.last())
    }

    @Test
    fun `preference failure clears authentication gate to setup`() = runBlocking {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)

        restoreAuthenticationSession(
            loadSavedState = { throw IOException("preferences unavailable") },
            applySavedState = {},
            openSavedServer = {},
            validateSavedSession = {},
            refreshLoginMethods = {},
            updateStatus = statuses::add,
        )

        assertEquals(listOf(AuthStatus.AUTHENTICATING, AuthStatus.SETUP), statuses)
    }

    @Test
    fun `cancellation clears authentication gate and is rethrown`() {
        val statuses = mutableListOf(AuthStatus.AUTHENTICATING)
        val cancellation = CancellationException("manager stopped")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                restoreAuthenticationSession(
                    loadSavedState = { SavedAuthState("https://arcane.example.com", null, null) },
                    applySavedState = {},
                    openSavedServer = {},
                    validateSavedSession = { throw cancellation },
                    refreshLoginMethods = {},
                    updateStatus = statuses::add,
                )
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(AuthStatus.SETUP, statuses.last())
    }
}
