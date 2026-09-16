package app.getarcane.android.ui.screens.containers

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.popToRootOrReplace
import app.getarcane.android.ui.components.AdaptiveListDetailLayout
import app.getarcane.android.ui.components.ListDetailPlaceholder

/**
 * Containers tab with its own nested back stack (list -> detail -> {logs, terminal, inspect}).
 * Mirrors the iOS NavigationStack + sheets/fullScreenCover.
 */
@Composable
fun ContainersScreen(
    popToRootSignal: Int = 0,
    dashboardContainerId: String? = null,
    onDashboardBack: () -> Unit = {},
    initialContainerId: String? = null,
    initialRequestId: Long = 0,
    onInitialDetailHandled: (Long) -> Unit = {},
    nav: NavHostController = rememberNavController(),
) {
    fun openDetail(id: String) {
        nav.navigate("detail/$id") {
            popUpTo(nav.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }
    LaunchedEffect(popToRootSignal) {
        if (popToRootSignal > 0) {
            nav.popToRootOrReplace(rootRoute = "list", fallbackPopUpToRoute = "detail/{id}")
        }
    }
    LaunchedEffect(initialRequestId, initialContainerId) {
        if (initialRequestId > 0 && initialContainerId != null) {
            openDetail(initialContainerId)
            onInitialDetailHandled(initialRequestId)
        }
    }

    val listPane: @Composable () -> Unit = {
        ContainerListScreen(onOpen = ::openDetail)
    }
    AdaptiveListDetailLayout(
        listPane = listPane,
    ) { expanded ->
        NavHost(navController = nav, startDestination = if (dashboardContainerId == null) "list" else "dashboard-detail") {
            composable("list") {
                if (expanded) ListDetailPlaceholder("container") else listPane()
            }
            composable("dashboard-detail") {
                val id = dashboardContainerId ?: return@composable
                BackHandler(onBack = onDashboardBack)
                ContainerDetailScreen(
                    id = id,
                    onBack = onDashboardBack,
                    onLogs = { childId -> nav.navigate("logs/$childId") },
                    onTerminal = { childId -> nav.navigate("terminal/$childId") },
                    onInspect = { childId -> nav.navigate("inspect/$childId") },
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ContainerDetailScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onLogs = { childId -> nav.navigate("logs/$childId") },
                    onTerminal = { childId -> nav.navigate("terminal/$childId") },
                    onInspect = { childId -> nav.navigate("inspect/$childId") },
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
}
