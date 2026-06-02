package app.getarcane.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.getarcane.android.core.AuthStatus
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.nav.MainTabView
import app.getarcane.android.ui.auth.LoginScreen
import app.getarcane.android.ui.components.DemoBanner

/** Root composable: routes on auth state. Port of iOS `ContentView`. */
@Composable
fun ArcaneApp() {
    val manager = LocalArcaneManager.current
    when (manager.authStatus) {
        AuthStatus.AUTHENTICATING -> LoadingScreen()
        AuthStatus.SETUP, AuthStatus.LOGIN -> LoginScreen()
        AuthStatus.AUTHENTICATED -> {
            // When a demo is active, the banner sits above the tab shell (iOS ContentView VStack).
            // Consume the status-bar inset at this level so the banner drops below the notch/clock
            // and the tab shell below doesn't double-inset. With no demo, leave the inset for
            // MainTabView's own scaffolds (edge-to-edge top bars), so the normal layout is unchanged.
            val demoActive = manager.isDemoActive
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (demoActive) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier),
            ) {
                DemoBanner()
                Box(Modifier.weight(1f)) { MainTabView() }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
