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

internal fun NavHostController.popToRootOrReplace(rootRoute: String, fallbackPopUpToRoute: String) {
    if (currentDestination?.route == rootRoute) return
    if (popBackStack(rootRoute, inclusive = false)) return
    navigate(rootRoute) {
        popUpTo(fallbackPopUpToRoute) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
