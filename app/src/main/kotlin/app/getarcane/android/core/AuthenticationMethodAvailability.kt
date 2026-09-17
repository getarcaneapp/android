package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.auth.OidcStatusInfo
import kotlinx.coroutines.CancellationException

enum class AuthenticationMethodState { LOADING, AVAILABLE, UNAVAILABLE, ERROR }

internal data class OidcAvailabilityResult(
    val state: AuthenticationMethodState,
    val status: OidcStatusInfo? = null,
)

internal data class PasskeyAvailabilityResult(
    val loginState: AuthenticationMethodState,
    val bridgeState: AuthenticationMethodState,
)

/**
 * Observable login-method state is replaced as one value so a new server can synchronously clear
 * every result. Per-method generations also reject a late result from an earlier refresh of the
 * same client, including a probe that was in flight while logout started.
 */
internal data class AuthenticationMethodAvailability(
    val localProbeGeneration: Long = 0,
    val oidcProbeGeneration: Long = 0,
    val passkeyProbeGeneration: Long = 0,
    val localState: AuthenticationMethodState = AuthenticationMethodState.LOADING,
    val oidcState: AuthenticationMethodState = AuthenticationMethodState.LOADING,
    val oidcStatus: OidcStatusInfo? = null,
    val passkeyLoginState: AuthenticationMethodState = AuthenticationMethodState.LOADING,
    val passkeyBridgeState: AuthenticationMethodState = AuthenticationMethodState.LOADING,
) {
    fun beginAll(): AuthenticationMethodAvailability = copy(
        localProbeGeneration = localProbeGeneration + 1,
        oidcProbeGeneration = oidcProbeGeneration + 1,
        passkeyProbeGeneration = passkeyProbeGeneration + 1,
        localState = AuthenticationMethodState.LOADING,
        oidcState = AuthenticationMethodState.LOADING,
        oidcStatus = null,
        passkeyLoginState = AuthenticationMethodState.LOADING,
        passkeyBridgeState = AuthenticationMethodState.LOADING,
    )

    fun applyLocal(
        probeGeneration: Long,
        state: AuthenticationMethodState,
    ): AuthenticationMethodAvailability = if (probeGeneration == localProbeGeneration) {
        copy(localState = state)
    } else {
        this
    }

    fun beginPasskeyBridgeProbe(): AuthenticationMethodAvailability = copy(
        passkeyProbeGeneration = passkeyProbeGeneration + 1,
        passkeyLoginState = AuthenticationMethodState.LOADING,
        passkeyBridgeState = AuthenticationMethodState.LOADING,
    )

    fun applyOidc(
        probeGeneration: Long,
        result: OidcAvailabilityResult,
    ): AuthenticationMethodAvailability = if (probeGeneration == oidcProbeGeneration) {
        copy(oidcState = result.state, oidcStatus = result.status)
    } else {
        this
    }

    fun applyPasskey(
        probeGeneration: Long,
        result: PasskeyAvailabilityResult,
    ): AuthenticationMethodAvailability = if (probeGeneration == passkeyProbeGeneration) {
        copy(
            passkeyLoginState = result.loginState,
            passkeyBridgeState = result.bridgeState,
        )
    } else {
        this
    }

    fun applyPasskeyBridge(
        probeGeneration: Long,
        bridgeState: AuthenticationMethodState,
    ): AuthenticationMethodAvailability = if (probeGeneration == passkeyProbeGeneration) {
        copy(passkeyBridgeState = bridgeState)
    } else {
        this
    }
}

internal data class LoginActionVisibility(
    val showPasskey: Boolean,
    val showOidc: Boolean,
    val showOidcDisclosure: Boolean,
    val showPassword: Boolean,
    val checking: Boolean,
    val noMethodsAvailable: Boolean,
)

internal fun loginActionVisibility(
    availability: AuthenticationMethodAvailability,
    showPasswordForm: Boolean,
): LoginActionVisibility {
    val checking = availability.localState == AuthenticationMethodState.LOADING ||
        availability.oidcState == AuthenticationMethodState.LOADING ||
        availability.passkeyLoginState == AuthenticationMethodState.LOADING
    val localAvailable = availability.localState == AuthenticationMethodState.AVAILABLE
    val oidcAvailable = availability.oidcState == AuthenticationMethodState.AVAILABLE
    val passkeyAvailable = availability.passkeyLoginState == AuthenticationMethodState.AVAILABLE
    return LoginActionVisibility(
        showPasskey = !checking && passkeyAvailable,
        showOidc = !checking && oidcAvailable && (!localAvailable || !showPasswordForm),
        showOidcDisclosure = !checking && oidcAvailable && localAvailable,
        showPassword = !checking && localAvailable && (!oidcAvailable || showPasswordForm),
        checking = checking,
        noMethodsAvailable = !checking && !localAvailable && !oidcAvailable && !passkeyAvailable,
    )
}

/** Missing means an older server where local authentication remains the compatible default. */
internal suspend fun probeLocalAuthAvailability(
    loadPublicSettings: suspend () -> Map<String, String>,
): AuthenticationMethodState = try {
    val configured = loadPublicSettings()["authLocalEnabled"]?.trim()
    if (configured?.equals("false", ignoreCase = true) == true) {
        AuthenticationMethodState.UNAVAILABLE
    } else {
        AuthenticationMethodState.AVAILABLE
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    AuthenticationMethodState.ERROR
}

/**
 * OIDC is offered only when the public setting explicitly enables it. For environment-managed
 * OIDC, the status endpoint must additionally confirm that the required environment configuration
 * is present. Database-managed OIDC reports its configuration through the enabled public setting;
 * `envConfigured` intentionally describes only the environment-managed variant in Arcane.
 */
internal suspend fun probeOidcAvailability(
    loadPublicSettings: suspend () -> Map<String, String>,
    loadStatus: suspend () -> OidcStatusInfo,
): OidcAvailabilityResult = try {
    val settings = loadPublicSettings()
    val enabled = settings["oidcEnabled"]?.trim()?.equals("true", ignoreCase = true) == true
    if (!enabled) {
        // Status still carries the environment-management flag used by authenticated settings.
        // Its failure cannot turn an explicitly disabled login method into an error or affect the
        // always-available password fallback.
        val status = try {
            loadStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        OidcAvailabilityResult(AuthenticationMethodState.UNAVAILABLE, status)
    } else {
        val status = loadStatus()
        if (status.envForced && !status.envConfigured) {
            OidcAvailabilityResult(AuthenticationMethodState.UNAVAILABLE)
        } else {
            OidcAvailabilityResult(
                state = AuthenticationMethodState.AVAILABLE,
                status = status.copy(
                    providerName = status.providerName?.takeIf(String::isNotBlank)
                        ?: settings["oidcProviderName"],
                    providerLogoUrl = status.providerLogoUrl?.takeIf(String::isNotBlank)
                        ?: settings["oidcProviderLogoUrl"],
                    mergeAccounts = status.mergeAccounts ||
                        settings["oidcMergeAccounts"]?.equals("true", ignoreCase = true) == true,
                ),
            )
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    OidcAvailabilityResult(AuthenticationMethodState.ERROR)
}

private enum class LegacyPasskeyAvailability { AVAILABLE, UNAVAILABLE, ENDPOINT_MISSING, ERROR }

/**
 * Combines the 2.11 availability contract with the versioned browser manifest. Arcane 2.12
 * removed the legacy endpoint, so only a typed 404 falls back to the validated bridge result.
 * Bridge availability remains separate because signed-in users may enroll their first passkey
 * even when the legacy login result is false.
 */
internal suspend fun probePasskeyAvailability(
    loadLegacyAvailability: suspend () -> Boolean,
    loadBridgeAvailability: suspend () -> Boolean,
): PasskeyAvailabilityResult {
    val legacy = try {
        if (loadLegacyAvailability()) {
            LegacyPasskeyAvailability.AVAILABLE
        } else {
            LegacyPasskeyAvailability.UNAVAILABLE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: ArcaneError.NotFound) {
        LegacyPasskeyAvailability.ENDPOINT_MISSING
    } catch (_: Throwable) {
        LegacyPasskeyAvailability.ERROR
    }

    val bridgeState = probePasskeyBridgeAvailability(loadBridgeAvailability)
    val loginState = when (legacy) {
        LegacyPasskeyAvailability.UNAVAILABLE -> AuthenticationMethodState.UNAVAILABLE
        LegacyPasskeyAvailability.ERROR -> AuthenticationMethodState.ERROR
        LegacyPasskeyAvailability.AVAILABLE,
        LegacyPasskeyAvailability.ENDPOINT_MISSING,
        -> bridgeState
    }
    return PasskeyAvailabilityResult(loginState, bridgeState)
}

internal suspend fun probePasskeyBridgeAvailability(
    loadBridgeAvailability: suspend () -> Boolean,
): AuthenticationMethodState = try {
    if (loadBridgeAvailability()) {
        AuthenticationMethodState.AVAILABLE
    } else {
        AuthenticationMethodState.UNAVAILABLE
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    AuthenticationMethodState.ERROR
}
