package app.getarcane.android.core

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.ArcaneConfiguration
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.android.AndroidSecureTokenStore
import app.getarcane.sdk.android.oidc.OidcAuthenticator
import app.getarcane.sdk.models.auth.OidcStatusInfo
import app.getarcane.sdk.models.user.User
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

enum class AuthStatus { SETUP, AUTHENTICATING, LOGIN, AUTHENTICATED }

/**
 * Central app state: server config, the [ArcaneClient], auth state, current user, server
 * capabilities, and the active environment. Compose-observable (mutableState-backed). Port of the
 * iOS `ArcaneClientManager`.
 */
class ArcaneClientManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cookieJar = ArcaneCookieJar()

    var authStatus by mutableStateOf(AuthStatus.SETUP); private set
    var serverUrl by mutableStateOf(""); private set
    var currentUser by mutableStateOf<User?>(null); private set
    var capabilities by mutableStateOf(ServerCapabilities.UNKNOWN); private set
    var isLoading by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var oidc by mutableStateOf<OidcStatusInfo?>(null); private set

    // Demo state (parity with iOS): a temporary hosted instance with an expiry.
    var isStartingDemo by mutableStateOf(false); private set
    var demoEndsAt by mutableStateOf<Long?>(null); private set
    var demoExpiredMessage by mutableStateOf<String?>(null); private set
    private var demoExpiryJob: Job? = null

    var activeEnvironmentId by mutableStateOf(EnvironmentId.LOCAL_DOCKER); private set
    var activeEnvironmentName by mutableStateOf("Local Docker"); private set

    var client: ArcaneClient? = null; private set

    private val oidcRedirectUri = OIDC_REDIRECT_URI

    companion object {
        const val OIDC_REDIRECT_URI = "arcane-mobile://oidc-callback"
        const val OIDC_REDIRECT_SCHEME = "arcane-mobile"
        const val OIDC_REDIRECT_HOST = "oidc-callback"

        const val OIDC_REDIRECT_SCHEME_LEGACY = "app.getarcane.android"
        const val OIDC_REDIRECT_HOST_LEGACY = "oidc"
        const val OIDC_REDIRECT_PATH_LEGACY = "callback"
    }

    val isOidcAvailable: Boolean get() =
        oidc?.let { it.envConfigured || it.envForced || it.providerName?.isNotBlank() == true } ?: false
    val isDemoActive: Boolean get() = demoEndsAt != null

    init {
        scope.launch {
            val saved = prefs.serverUrl.first()
            prefs.activeEnvId.first()?.let { id ->
                activeEnvironmentId = EnvironmentId(id)
                activeEnvironmentName = prefs.activeEnvName.first() ?: "Local Docker"
            }
            if (!saved.isNullOrBlank()) {
                serverUrl = saved
                client = makeClient(saved)
                authStatus = AuthStatus.AUTHENTICATING
                checkExistingAuth()
            } else {
                authStatus = AuthStatus.SETUP
            }
        }
    }

    private fun makeClient(url: String, defaultHeaders: Map<String, String> = emptyMap()): ArcaneClient =
        ArcaneClient(
            ArcaneConfiguration(
                baseUrl = url,
                tokenStore = AndroidSecureTokenStore(appContext),
                defaultEnvironmentId = activeEnvironmentId,
                defaultHeaders = defaultHeaders,
                engine = makeHttpEngine(),
            ),
        )

    private fun makeHttpEngine(): HttpClientEngine =
        OkHttp.create {
            config {
                cookieJar(this@ArcaneClientManager.cookieJar)
            }
        }

    /** Setup mode: validate + persist the server URL and create the client, then go to login. */
    fun configure(rawUrl: String) {
        scope.launch {
            errorMessage = null
            val normalized = ServerUrl.normalize(rawUrl)
            if (normalized == null) {
                errorMessage = "Enter a valid server URL (e.g. https://arcane.example.com)."
                return@launch
            }
            serverUrl = normalized
            prefs.setServerUrl(normalized)
            oidc = null
            cookieJar.clear()
            client?.close()
            client = makeClient(normalized)
            authStatus = AuthStatus.LOGIN
            refreshOidc()
        }
    }

    fun login(username: String, password: String) {
        val c = client ?: return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = c.auth.login(username, password)
                currentUser = response.user
                capabilities = c.serverCapabilities()
                authStatus = AuthStatus.AUTHENTICATED
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        val c = client ?: return
        scope.launch {
            runCatching { c.auth.logout() }
            cookieJar.clear()
            currentUser = null
            capabilities = ServerCapabilities.UNKNOWN
            authStatus = AuthStatus.LOGIN
            oidc = null
            refreshOidc()
        }
    }

    fun startOidcSignIn(context: Context) {
        val c = client ?: return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                OidcAuthenticator(c).startSignIn(context = context, redirectUri = oidcRedirectUri)
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun handleOidcRedirect(uri: Uri?) {
        val callback = uri?.takeIf(::isExpectedOidcCallback) ?: return
        val c = client ?: return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val (code, state) = extractOidcCallbackParams(callback)
                    ?: throw IllegalArgumentException("Missing OAuth callback parameters")
                val response = if (
                    callback.getQueryParameter("code") != null &&
                    callback.getQueryParameter("state") != null
                ) {
                    OidcAuthenticator(c).completeSignIn(
                        callbackUri = callback,
                        redirectUri = oidcRedirectUri,
                    )
                } else {
                    c.auth.oidcCallback(code = code, state = state, mobileRedirectUri = oidcRedirectUri)
                }
                currentUser = response.user
                capabilities = c.serverCapabilities()
                authStatus = AuthStatus.AUTHENTICATED
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun changeServer() {
        errorMessage = null
        authStatus = AuthStatus.SETUP
    }

    /** Dismiss the "your demo ended" notice shown on the login screen. */
    fun dismissDemoExpiredMessage() {
        demoExpiredMessage = null
    }

    /**
     * Provision a temporary hosted demo instance, point the client at it, and sign in with the
     * generated credentials. Port of iOS `startDemo()`.
     */
    fun startDemo() {
        scope.launch {
            isLoading = true
            isStartingDemo = true
            errorMessage = null
            demoExpiredMessage = null
            try {
                val session = DemoService.startInstance()
                serverUrl = DemoService.DEMO_BASE_URL
                prefs.setServerUrl(DemoService.DEMO_BASE_URL)
                client?.close()
                // The demo router uses the session-id cookie to route API calls to the provisioned
                // instance; iOS gets this via shared cookie storage, so inject it on every request.
                client = makeClient(
                    DemoService.DEMO_BASE_URL,
                    defaultHeaders = mapOf("Cookie" to "session-id=${session.sessionId}"),
                )
                val c = client!!
                try {
                    val response = c.auth.login(session.username, session.password)
                    currentUser = response.user
                    capabilities = c.serverCapabilities()
                    demoEndsAt = session.endsAtMillis
                    authStatus = AuthStatus.AUTHENTICATED
                    DemoService.startHeartbeat(scope)
                    scheduleDemoExpiry(session.endsAtMillis)
                } catch (e: Throwable) {
                    errorMessage = friendlyErrorMessage(e)
                    DemoService.endSession()
                }
            } catch (e: DemoService.DemoException) {
                errorMessage = e.message
            } catch (e: Throwable) {
                errorMessage = friendlyErrorMessage(e)
            } finally {
                isLoading = false
                isStartingDemo = false
            }
        }
    }

    /** Tear down the active demo and return to setup. [expired] surfaces the "demo ended" notice. */
    fun endDemo(expired: Boolean) {
        demoExpiryJob?.cancel()
        demoExpiryJob = null
        // Flip UI state immediately so the user is returned to setup without waiting on cleanup.
        val ending = client
        currentUser = null
        capabilities = ServerCapabilities.UNKNOWN
        demoEndsAt = null
        serverUrl = ""
        client = null
        authStatus = AuthStatus.SETUP
        if (expired) {
            demoExpiredMessage = "Your demo ended. Start a new one or connect to your own server."
        }
        scope.launch {
            DemoService.endSession()
            runCatching { ending?.auth?.logout() }
            runCatching { ending?.close() }
            prefs.setServerUrl("")
        }
    }

    private fun scheduleDemoExpiry(endsAtMillis: Long) {
        demoExpiryJob?.cancel()
        val interval = endsAtMillis - System.currentTimeMillis()
        if (interval <= 0) {
            endDemo(expired = true)
            return
        }
        demoExpiryJob = scope.launch {
            delay(interval)
            endDemo(expired = true)
        }
    }

    fun setActiveEnvironment(id: EnvironmentId, name: String) {
        activeEnvironmentId = id
        activeEnvironmentName = name
        scope.launch { prefs.setActiveEnv(id.rawValue, name) }
    }

    private suspend fun checkExistingAuth() {
        val c = client ?: run { authStatus = AuthStatus.LOGIN; return }
        try {
            currentUser = c.auth.me()
            capabilities = c.serverCapabilities()
            authStatus = AuthStatus.AUTHENTICATED
        } catch (e: Throwable) {
            authStatus = AuthStatus.LOGIN
            refreshOidc()
        }
    }

    private suspend fun refreshOidc() {
        val c = client ?: return
        val settings = runCatching { c.settings.getPublicSettings() }.getOrNull()
        val status = runCatching { c.auth.oidcStatus() }.getOrNull()
        if (settings == null) {
            oidc = status
            return
        }

        val public = settings.associate { it.key to it.value }
        val oidcEnabled = public["oidcEnabled"]?.equals("true", ignoreCase = true) == true
        val providerName = public["oidcProviderName"]
        val providerLogoUrl = public["oidcProviderLogoUrl"]
        val mergeAccounts = public["oidcMergeAccounts"]?.equals("true", ignoreCase = true) == true

        oidc = OidcStatusInfo(
            envConfigured = status?.envConfigured ?: oidcEnabled,
            envForced = status?.envForced ?: false,
            mergeAccounts = status?.mergeAccounts ?: mergeAccounts,
            providerName = status?.providerName ?: providerName,
            providerLogoUrl = status?.providerLogoUrl ?: providerLogoUrl,
        )
    }

    private fun isExpectedOidcCallback(uri: Uri): Boolean {
        if (uri.scheme.equals(OIDC_REDIRECT_SCHEME, ignoreCase = true)) {
            return uri.host == OIDC_REDIRECT_HOST ||
                uri.path == "/$OIDC_REDIRECT_HOST" ||
                uri.path == "/$OIDC_REDIRECT_HOST/"
        }
        return if (uri.scheme.equals(OIDC_REDIRECT_SCHEME_LEGACY, ignoreCase = true)) {
            uri.host == OIDC_REDIRECT_HOST_LEGACY &&
                (uri.path == "/$OIDC_REDIRECT_PATH_LEGACY" || uri.path == "/$OIDC_REDIRECT_PATH_LEGACY/")
        } else {
            false
        }
    }

    private fun extractOidcCallbackParams(uri: Uri): Pair<String, String>? {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code != null && state != null) {
            return code to state
        }

        val fragment = uri.fragment ?: return null
        var fragmentCode: String? = null
        var fragmentState: String? = null
        for (pair in fragment.split("&")) {
            if (!pair.contains("=")) continue
            val keyAndValue = pair.split("=", limit = 2)
            if (keyAndValue.size != 2) continue
            val key = keyAndValue[0]
            val value = Uri.decode(keyAndValue[1])
            when (key) {
                "code" -> fragmentCode = value
                "state" -> fragmentState = value
            }
        }

        return if (fragmentCode != null && fragmentState != null) {
            fragmentCode to fragmentState
        } else {
            null
        }
        }

    /**
     * Public re-fetch of the OIDC provider status, used by the login screen so the provider button
     * shows correctly whenever login is (re-)entered or the server changes. No-op while in setup
     * mode or before a client/server URL exists. Port of iOS `refreshOIDCStatus()`.
     */
    fun refreshOidcStatus() {
        if (authStatus == AuthStatus.SETUP || serverUrl.isBlank() || client == null) return
        scope.launch { refreshOidc() }
    }

}

private class ArcaneCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { existing ->
            existing.expiresAt <= now || cookies.any { incoming ->
                incoming.name == existing.name &&
                    incoming.domain == existing.domain &&
                    incoming.path == existing.path
            }
        }
        this.cookies.addAll(cookies.filter { it.expiresAt > now })
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }
}

/** Provides the [ArcaneClientManager] to the composition. */
val LocalArcaneManager = staticCompositionLocalOf<ArcaneClientManager> {
    error("ArcaneClientManager not provided")
}
