package app.getarcane.android.ui.screens.volumes

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.PopToRootOnSignal

/**
 * Volumes tab with its own nested back stack (list -> detail -> browser/backups). Mirrors the iOS
 * `VolumesView` NavigationStack: VolumeDetailView links out to VolumeBrowserView and
 * VolumeBackupsView.
 */
@Composable
fun VolumesScreen(popToRootSignal: Int = 0) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            VolumeListScreen(onOpen = { name -> nav.navigate("detail/$name") })
        }
        composable("detail/{name}") { entry ->
            VolumeDetailScreen(
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
                onBrowse = { name -> nav.navigate("browse/$name") },
                onBackups = { name -> nav.navigate("backups/$name") },
            )
        }
        composable("browse/{name}") { entry ->
            VolumeBrowserScreen(
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("backups/{name}") { entry ->
            VolumeBackupsScreen(
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
