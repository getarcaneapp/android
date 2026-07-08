package app.getarcane.android.ui.screens.containers

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.popToRootOrReplace

/**
 * Containers tab with its own nested back stack (list -> detail -> {logs, terminal, inspect}).
 * Mirrors the iOS NavigationStack + sheets/fullScreenCover.
 */
@Composable
fun ContainersScreen(
    popToRootSignal: Int = 0,
    dashboardContainerId: String? = null,
    onDashboardBack: () -> Unit = {},
) {
    val nav = rememberNavController()
    LaunchedEffect(popToRootSignal) {
        if (popToRootSignal > 0) {
            nav.popToRootOrReplace(rootRoute = "list", fallbackPopUpToRoute = "detail/{id}")
        }
    }
    NavHost(navController = nav, startDestination = if (dashboardContainerId == null) "list" else "dashboard-detail") {
        composable("list") {
            ContainerListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("dashboard-detail") {
            val id = dashboardContainerId ?: return@composable
            BackHandler(onBack = onDashboardBack)
            ContainerDetailScreen(
                id = id,
                onBack = onDashboardBack,
                onLogs = { id -> nav.navigate("logs/$id") },
                onTerminal = { id -> nav.navigate("terminal/$id") },
                onInspect = { id -> nav.navigate("inspect/$id") },
            )
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ContainerDetailScreen(
                id = id,
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
