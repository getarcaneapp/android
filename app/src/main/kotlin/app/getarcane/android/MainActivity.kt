package app.getarcane.android

import android.content.Intent
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
import app.getarcane.android.core.AppearancePreferences
import app.getarcane.android.core.ArcaneClientManager
import app.getarcane.android.core.AppThemeMode
import app.getarcane.android.core.LocalAppearancePreferences
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalPinnedStore
import app.getarcane.android.core.PinnedItemsStore
import app.getarcane.android.core.Prefs
import app.getarcane.android.ui.ArcaneApp
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneTheme

class MainActivity : ComponentActivity() {
    private val arcaneManager by lazy { ArcaneClientManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOidcRedirectIntent(intent)
        enableEdgeToEdge()
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
                ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }.getOrNull() }
                ?: ArcaneBlue
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalArcaneManager provides manager,
                LocalPinnedStore provides pinnedStore,
                LocalAppearancePreferences provides appearancePreferences,
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
        handleOidcRedirectIntent(intent)
    }

    private fun handleOidcRedirectIntent(intent: Intent?) {
        arcaneManager.handleOidcRedirect(intent?.data)
    }
}
