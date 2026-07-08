package app.getarcane.android.ui.screens.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.PopToRootOnSignal

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
) {
    val nav = rememberNavController()
    var pendingExternalOpen by remember(openContainerSignal) {
        mutableStateOf(openContainerId != null)
    }
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    LaunchedEffect(openContainerSignal) {
        val id = openContainerId ?: return@LaunchedEffect
        nav.navigate("detail/$id") {
            popUpTo("list")
        }
        pendingExternalOpen = false
        onOpenContainerConsumed()
    }
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            if (pendingExternalOpen) {
                Box(Modifier.fillMaxSize())
            } else {
                ContainerListScreen(onOpen = { id -> nav.navigate("detail/$id") })
            }
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
