package app.getarcane.android.ui.screens.environments

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Environments tab with its own nested back stack (list -> detail). Mirrors the iOS
 * `EnvironmentsView` NavigationStack, which pushes an EnvironmentDetailView per row.
 */
@Composable
fun EnvironmentsScreen() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            EnvironmentListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("detail/{id}") { entry ->
            EnvironmentDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
