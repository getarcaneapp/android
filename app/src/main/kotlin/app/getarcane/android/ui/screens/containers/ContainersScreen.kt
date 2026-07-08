package app.getarcane.android.ui.screens.containers

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.popToRootOrReplace
import app.getarcane.android.nav.replaceBackStackWith

/**
 * Containers tab with its own nested back stack (list -> detail -> {logs, terminal, inspect}).
 * Mirrors the iOS NavigationStack + sheets/fullScreenCover.
 */
@Composable
fun ContainersScreen(
    popToRootSignal: Int = 0,
    openContainerId: String? = null,
    openContainerSignal: Int = 0,
    onOpenContainerConsumed: () -> Unit = {},
    onDashboardBack: () -> Unit = {},
) {
    val nav = rememberNavController()
    var dashboardOpenedContainerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(popToRootSignal) {
        if (popToRootSignal > 0) {
            dashboardOpenedContainerId = null
            nav.popToRootOrReplace(rootRoute = "list", fallbackPopUpToRoute = "detail/{id}")
        }
    }
    LaunchedEffect(openContainerSignal) {
        val id = openContainerId ?: return@LaunchedEffect
        dashboardOpenedContainerId = id
        nav.replaceBackStackWith("detail/$id")
        onOpenContainerConsumed()
    }
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ContainerListScreen(onOpen = { id -> nav.navigate("detail/$id") })
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            BackHandler(enabled = dashboardOpenedContainerId == id) {
                dashboardOpenedContainerId = null
                onDashboardBack()
            }
            ContainerDetailScreen(
                id = id,
                onBack = {
                    if (dashboardOpenedContainerId == id) {
                        dashboardOpenedContainerId = null
                        onDashboardBack()
                    } else {
                        nav.popBackStack()
                    }
                },
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
