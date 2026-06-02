package app.getarcane.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
        AuthStatus.AUTHENTICATED -> Column(Modifier.fillMaxSize()) {
            // Demo countdown sits above the tab shell when a demo is active (iOS ContentView VStack).
            DemoBanner()
            Box(Modifier.weight(1f)) { MainTabView() }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
