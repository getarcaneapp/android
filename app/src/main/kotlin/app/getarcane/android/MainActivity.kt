package app.getarcane.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.graphics.toColorInt
import app.getarcane.android.core.AppearancePreferences
import app.getarcane.android.core.AppThemeMode
import app.getarcane.android.core.LocalAppearancePreferences
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.OperationRoute
import app.getarcane.android.core.LocalPinnedStore
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.android.core.Prefs
import app.getarcane.android.ui.ArcaneApp
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneTheme
import app.getarcane.android.nav.AuthenticatedRouteCodec
import app.getarcane.android.nav.LocalAuthenticatedRouteCoordinator
import app.getarcane.android.nav.RouteParseResult
import app.getarcane.android.nav.AuthenticatedRoute
import app.getarcane.android.nav.RouteDestination
import app.getarcane.android.core.sha256

class MainActivity : ComponentActivity() {
    private val arcaneApplication by lazy { application as ArcaneApplication }
    private val arcaneManager by lazy { arcaneApplication.arcaneManager }
    private val operationStore by lazy { arcaneApplication.operationStore }
    private val routeCoordinator by lazy { arcaneApplication.routeCoordinator }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Preserve the app surface behind gesture and three-button navigation in both themes.
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val context = LocalContext.current
            val manager = remember { arcaneManager }
            val pinnedStore = remember { PinnedItemsStore(context) }
            val prefs = remember { Prefs(context) }
            val appearanceScope = rememberCoroutineScope()
            val appearancePreferences = remember(prefs, appearanceScope) {
                AppearancePreferences(prefs, appearanceScope)
            }
            val accentHex by appearancePreferences.accentHex.collectAsState()
            val themeMode by appearancePreferences.themeMode.collectAsState()
            val darkTheme = themeMode.resolvesToDark(isSystemInDarkTheme())
            val accent = accentHex
                ?.let { hex -> runCatching { Color((if (hex.startsWith("#")) hex else "#$hex").toColorInt()) }.getOrNull() }
                ?: ArcaneBlue
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalArcaneManager provides manager,
                LocalOperationStore provides operationStore,
                LocalPinnedStore provides pinnedStore,
                LocalAppearancePreferences provides appearancePreferences,
                LocalAuthenticatedRouteCoordinator provides routeCoordinator,
            ) {
                ArcaneTheme(darkTheme = darkTheme, accent = accent) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ArcaneApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        arcaneManager.handlePasskeyBrowserResume()
        operationStore.refreshNotificationProjection()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (!arcaneManager.handlePasskeyRedirect(uri)) {
            arcaneManager.handleOidcRedirect(uri)
        }
        when (val route = OperationRoute.parse(uri?.toString())) {
            OperationRoute.Center -> routeCoordinator.submit(
                AuthenticatedRoute(null, RouteDestination.OPERATIONS),
            )
            is OperationRoute.Detail -> {
                val serverIdentity = arcaneManager.serverSessionIdentity
                if (serverIdentity.isBlank()) {
                    routeCoordinator.reject("Sign in before opening this older operation notification.")
                } else {
                    routeCoordinator.submit(
                        AuthenticatedRoute(
                            serverBindingHash = sha256(serverIdentity),
                            destination = RouteDestination.OPERATION,
                            resourceId = route.operationId,
                        ),
                    )
                }
            }
            null -> when (val parsed = AuthenticatedRouteCodec.parse(uri?.toString())) {
                is RouteParseResult.Valid -> routeCoordinator.submit(parsed.route)
                is RouteParseResult.Invalid -> routeCoordinator.reject(parsed.message)
                RouteParseResult.NotOwned -> Unit
            }
        }
    }
}
