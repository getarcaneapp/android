package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.auth.OidcStatusInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationMethodAvailabilityTest {
    @Test
    fun `local auth follows explicit setting and preserves older-server default`() = runBlocking {
        assertEquals(
            AuthenticationMethodState.UNAVAILABLE,
            probeLocalAuthAvailability { mapOf("authLocalEnabled" to "false") },
        )
        assertEquals(
            AuthenticationMethodState.AVAILABLE,
            probeLocalAuthAvailability { mapOf("oidcEnabled" to "false") },
        )
        assertEquals(
            AuthenticationMethodState.ERROR,
            probeLocalAuthAvailability { throw ArcaneError.Transport("offline") },
        )
    }

    @Test
    fun `bridge available and legacy availability true shows passkey`() = runBlocking {
        val result = probePasskeyAvailability(
            loadLegacyAvailability = { true },
            loadBridgeAvailability = { true },
        )

        assertEquals(AuthenticationMethodState.AVAILABLE, result.loginState)
        assertEquals(AuthenticationMethodState.AVAILABLE, result.bridgeState)
    }

    @Test
    fun `bridge available and legacy availability false hides login without disabling enrollment`() = runBlocking {
        val result = probePasskeyAvailability(
            loadLegacyAvailability = { false },
            loadBridgeAvailability = { true },
        )

        assertEquals(AuthenticationMethodState.UNAVAILABLE, result.loginState)
        assertEquals(AuthenticationMethodState.AVAILABLE, result.bridgeState)
    }

    @Test
    fun `bridge unavailable hides passkey`() = runBlocking {
        val result = probePasskeyAvailability(
            loadLegacyAvailability = { true },
            loadBridgeAvailability = { false },
        )

        assertEquals(AuthenticationMethodState.UNAVAILABLE, result.loginState)
        assertEquals(AuthenticationMethodState.UNAVAILABLE, result.bridgeState)
    }

    @Test
    fun `legacy endpoint 404 falls back to available bridge`() = runBlocking {
        val result = probePasskeyAvailability(
            loadLegacyAvailability = { throw ArcaneError.NotFound },
            loadBridgeAvailability = { true },
        )

        assertEquals(AuthenticationMethodState.AVAILABLE, result.loginState)
        assertEquals(AuthenticationMethodState.AVAILABLE, result.bridgeState)
    }

    @Test
    fun `non-404 legacy failure hides passkey with error while preserving bridge support`() = runBlocking {
        val result = probePasskeyAvailability(
            loadLegacyAvailability = { throw ArcaneError.Transport("offline") },
            loadBridgeAvailability = { true },
        )

        assertEquals(AuthenticationMethodState.ERROR, result.loginState)
        assertEquals(AuthenticationMethodState.AVAILABLE, result.bridgeState)
    }

    @Test
    fun `passkey availability cancellation is rethrown`() {
        val cancellation = CancellationException("server changed")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                probePasskeyAvailability(
                    loadLegacyAvailability = { throw cancellation },
                    loadBridgeAvailability = { true },
                )
            }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `OIDC requires explicit enabled setting and remains independent from passkey`() = runBlocking {
        var statusRequested = false
        val disabled = probeOidcAvailability(
            loadPublicSettings = {
                mapOf("oidcEnabled" to "false", "oidcProviderName" to "Configured Provider")
            },
            loadStatus = {
                statusRequested = true
                oidcStatus()
            },
        )
        val enabled = probeOidcAvailability(
            loadPublicSettings = { mapOf("oidcEnabled" to "true") },
            loadStatus = { oidcStatus(providerName = "Configured Provider") },
        )

        var availability = AuthenticationMethodAvailability().beginAll()
        availability = availability.applyLocal(
            availability.localProbeGeneration,
            AuthenticationMethodState.AVAILABLE,
        )
        availability = availability.applyPasskey(
            availability.passkeyProbeGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.AVAILABLE,
                AuthenticationMethodState.AVAILABLE,
            ),
        )
        availability = availability.applyOidc(availability.oidcProbeGeneration, disabled)

        assertTrue(statusRequested)
        assertEquals(AuthenticationMethodState.UNAVAILABLE, disabled.state)
        assertEquals(AuthenticationMethodState.AVAILABLE, enabled.state)
        assertEquals(AuthenticationMethodState.AVAILABLE, availability.passkeyLoginState)
        assertEquals(AuthenticationMethodState.UNAVAILABLE, availability.oidcState)
    }

    @Test
    fun `environment-managed OIDC must also report complete configuration`() = runBlocking {
        val result = probeOidcAvailability(
            loadPublicSettings = { mapOf("oidcEnabled" to "true") },
            loadStatus = { oidcStatus(envForced = true, envConfigured = false) },
        )

        assertEquals(AuthenticationMethodState.UNAVAILABLE, result.state)
        assertNull(result.status)
    }

    @Test
    fun `OIDC failure leaves passkey and password available`() = runBlocking {
        val oidcResult = probeOidcAvailability(
            loadPublicSettings = { throw ArcaneError.Transport("offline") },
            loadStatus = { error("must not run") },
        )
        var availability = AuthenticationMethodAvailability().beginAll()
        availability = availability.applyLocal(
            availability.localProbeGeneration,
            AuthenticationMethodState.AVAILABLE,
        )
        availability = availability.applyPasskey(
            availability.passkeyProbeGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.AVAILABLE,
                AuthenticationMethodState.AVAILABLE,
            ),
        )
        availability = availability.applyOidc(availability.oidcProbeGeneration, oidcResult)
        val visibility = loginActionVisibility(availability, showPasswordForm = false)

        assertEquals(AuthenticationMethodState.ERROR, availability.oidcState)
        assertTrue(visibility.showPasskey)
        assertTrue(visibility.showPassword)
        assertFalse(visibility.showOidc)
        assertFalse(visibility.showOidcDisclosure)
    }

    @Test
    fun `server switch clears methods and rejects results from prior server`() {
        var availability = AuthenticationMethodAvailability().beginAll()
        val oldOidcGeneration = availability.oidcProbeGeneration
        val oldPasskeyGeneration = availability.passkeyProbeGeneration
        availability = availability.applyLocal(
            availability.localProbeGeneration,
            AuthenticationMethodState.AVAILABLE,
        )
        availability = availability.applyOidc(
            oldOidcGeneration,
            OidcAvailabilityResult(AuthenticationMethodState.AVAILABLE, oidcStatus()),
        )
        availability = availability.applyPasskey(
            oldPasskeyGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.AVAILABLE,
                AuthenticationMethodState.AVAILABLE,
            ),
        )

        availability = availability.beginAll()
        availability = availability.applyOidc(
            oldOidcGeneration,
            OidcAvailabilityResult(AuthenticationMethodState.AVAILABLE, oidcStatus()),
        )
        availability = availability.applyPasskey(
            oldPasskeyGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.AVAILABLE,
                AuthenticationMethodState.AVAILABLE,
            ),
        )

        assertEquals(AuthenticationMethodState.LOADING, availability.oidcState)
        assertEquals(AuthenticationMethodState.LOADING, availability.localState)
        assertEquals(AuthenticationMethodState.LOADING, availability.passkeyLoginState)
        assertEquals(AuthenticationMethodState.LOADING, availability.passkeyBridgeState)
        assertNull(availability.oidcStatus)
    }

    @Test
    fun `loading never exposes optional authentication actions`() {
        val visibility = loginActionVisibility(
            AuthenticationMethodAvailability(),
            showPasswordForm = false,
        )

        assertFalse(visibility.showPasskey)
        assertFalse(visibility.showOidc)
        assertFalse(visibility.showOidcDisclosure)
        assertFalse(visibility.showPassword)
        assertTrue(visibility.checking)
    }

    @Test
    fun `OIDC availability preserves password fallback disclosure`() {
        var availability = AuthenticationMethodAvailability().beginAll()
        availability = availability.applyLocal(
            availability.localProbeGeneration,
            AuthenticationMethodState.AVAILABLE,
        )
        availability = availability.applyOidc(
            availability.oidcProbeGeneration,
            OidcAvailabilityResult(AuthenticationMethodState.AVAILABLE, oidcStatus()),
        )
        availability = availability.applyPasskey(
            availability.passkeyProbeGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.UNAVAILABLE,
                AuthenticationMethodState.UNAVAILABLE,
            ),
        )

        val providerPrimary = loginActionVisibility(availability, showPasswordForm = false)
        val passwordFallback = loginActionVisibility(availability, showPasswordForm = true)

        assertTrue(providerPrimary.showOidc)
        assertFalse(providerPrimary.showPassword)
        assertTrue(providerPrimary.showOidcDisclosure)
        assertFalse(passwordFallback.showOidc)
        assertTrue(passwordFallback.showPassword)
        assertTrue(passwordFallback.showOidcDisclosure)
    }

    @Test
    fun `disabled local auth never exposes password or OIDC fallback disclosure`() {
        var availability = AuthenticationMethodAvailability().beginAll()
        availability = availability.applyLocal(
            availability.localProbeGeneration,
            AuthenticationMethodState.UNAVAILABLE,
        )
        availability = availability.applyOidc(
            availability.oidcProbeGeneration,
            OidcAvailabilityResult(AuthenticationMethodState.AVAILABLE, oidcStatus()),
        )
        availability = availability.applyPasskey(
            availability.passkeyProbeGeneration,
            PasskeyAvailabilityResult(
                AuthenticationMethodState.UNAVAILABLE,
                AuthenticationMethodState.AVAILABLE,
            ),
        )

        val visibility = loginActionVisibility(availability, showPasswordForm = true)
        assertTrue(visibility.showOidc)
        assertFalse(visibility.showOidcDisclosure)
        assertFalse(visibility.showPassword)
    }

    @Test
    fun `terminal unavailable states show configuration message`() {
        val availability = AuthenticationMethodAvailability(
            localState = AuthenticationMethodState.UNAVAILABLE,
            oidcState = AuthenticationMethodState.UNAVAILABLE,
            passkeyLoginState = AuthenticationMethodState.ERROR,
            passkeyBridgeState = AuthenticationMethodState.ERROR,
        )
        val visibility = loginActionVisibility(availability, showPasswordForm = false)
        assertTrue(visibility.noMethodsAvailable)
        assertFalse(visibility.checking)
    }

    private fun oidcStatus(
        envForced: Boolean = false,
        envConfigured: Boolean = false,
        providerName: String? = null,
    ) = OidcStatusInfo(
        envForced = envForced,
        envConfigured = envConfigured,
        mergeAccounts = false,
        providerName = providerName,
        providerLogoUrl = null,
    )
}
