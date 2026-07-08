package app.getarcane.android.ui.screens.containers

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.PopToRootOnSignal

/**
 * Containers tab with its own nested back stack (list -> detail -> {logs, terminal, inspect}).
 * Mirrors the iOS NavigationStack + sheets/fullScreenCover.
 */
@Composable
fun ContainersScreen(popToRootSignal: Int = 0) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ContainerListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("detail/{id}") { entry ->
            ContainerDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onLogs = { id -> nav.navigate("logs/$id") },
                onTerminal = { id -> nav.navigate("terminal/$id") },
                onInspect = { id -> nav.navigate("inspect/$id") },
            )
        }
        composable("logs/{id}") { entry ->
            LogsScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("terminal/{id}") { entry ->
            ContainerTerminalScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                title = entry.arguments?.getString("id")?.take(12).orEmpty(),
                onClose = { nav.popBackStack() },
            )
        }
        composable("inspect/{id}") { entry ->
            ContainerInspectScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onClose = { nav.popBackStack() },
            )
        }
    }
}
