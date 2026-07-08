package app.getarcane.android.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController

@Composable
internal fun NavHostController.PopToRootOnSignal(signal: Int, rootRoute: String) {
    LaunchedEffect(signal) {
        if (signal > 0) {
            popBackStack(rootRoute, inclusive = false)
        }
    }
}
