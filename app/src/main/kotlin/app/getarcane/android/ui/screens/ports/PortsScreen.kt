package app.getarcane.android.ui.screens.ports

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Ports tab with its own nested back stack (list -> mapping detail). Mirrors the iOS `PortsView`
 * NavigationStack, which pushes a PortMappingDetailView per row.
 */
@Composable
fun PortsScreen() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            PortListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("detail/{id}") { entry ->
            PortDetailScreen(
                portId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
