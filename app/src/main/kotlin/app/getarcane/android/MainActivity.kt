package app.getarcane.android

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.getarcane.android.core.ArcaneClientManager
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
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = TRANSPARENT,
                darkScrim = TRANSPARENT,
            ),
        )
        setContent {
            val context = LocalContext.current
            val manager = remember { arcaneManager }
            val pinnedStore = remember { PinnedItemsStore(context) }
            val prefs = remember { Prefs(context) }
            val accentHex by prefs.accentHex.collectAsState(initial = null)
            val accent = accentHex
                ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }.getOrNull() }
                ?: ArcaneBlue
            CompositionLocalProvider(
                LocalArcaneManager provides manager,
                LocalPinnedStore provides pinnedStore,
            ) {
                ArcaneTheme(accent = accent) {
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
