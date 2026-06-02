package app.getarcane.android.ui.screens.updates

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Updates tab with its own nested back stack. Mirrors the iOS `UpdatesView`:
 * image-update results for the active environment, plus actions to run the updater or view its
 * history. iOS picks an environment from a sheet; on Android everything is scoped to the active
 * environment, so the actions push directly.
 */
@Composable
fun UpdatesScreen() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "updates") {
        composable("updates") {
            ImageUpdatesScreen(
                onRunUpdater = { nav.navigate("run") },
                onHistory = { nav.navigate("history") },
            )
        }
        composable("run") {
            UpdaterRunScreen(onBack = { nav.popBackStack() })
        }
        composable("history") {
            UpdaterHistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
