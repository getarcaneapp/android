package app.getarcane.android.ui.screens.networks

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.PopToRootOnSignal

/**
 * Networks tab with its own nested back stack (list -> detail -> topology). Mirrors the iOS
 * `NetworksView` NavigationStack: NetworkDetailView links out to NetworkTopologyView.
 */
@Composable
fun NetworksScreen(popToRootSignal: Int = 0) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            NetworkListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("detail/{id}") { entry ->
            NetworkDetailScreen(
                networkId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onTopology = { nav.navigate("topology") },
            )
        }
        composable("topology") {
            NetworkTopologyScreen(onBack = { nav.popBackStack() })
        }
    }
}
