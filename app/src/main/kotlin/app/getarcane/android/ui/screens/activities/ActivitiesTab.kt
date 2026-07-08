package app.getarcane.android.ui.screens.activities

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.nav.PopToRootOnSignal

/**
 * Activities tab entry: a nested back stack (list -> detail). The list is the tab root (no back
 * arrow); the detail is pushed and pops back. Mirrors the iOS Activity Center surface.
 */
@Composable
fun ActivitiesTab(onClose: (() -> Unit)? = null, popToRootSignal: Int = 0) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = "list")
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ActivitiesScreen(
                onBack = onClose,
                onOpenDetail = { id, env ->
                    nav.navigate("detail/${id.encodeArg()}/${env.encodeArg()}")
                },
            )
        }
        composable("detail/{id}/{env}") { entry ->
            ActivityDetailScreen(
                activityId = entry.arguments?.getString("id").orEmpty().decodeArg(),
                envId = entry.arguments?.getString("env").orEmpty().decodeArg(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private fun String.encodeArg(): String = java.net.URLEncoder.encode(this, "UTF-8")

private fun String.decodeArg(): String =
    runCatching { java.net.URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
