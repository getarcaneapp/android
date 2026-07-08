package app.getarcane.android.ui.screens.projects

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
import app.getarcane.android.ui.screens.settings.registries.TemplateRegistriesScreen

/**
 * Projects tab with its own nested back stack (list -> detail -> streaming action / logs / archived).
 * Mirrors the iOS `NavigationStack` rooted at `ProjectsView`.
 */
@Composable
fun ProjectsScreen(
    popToRootSignal: Int = 0,
    openProjectId: String? = null,
    openProjectSignal: Int = 0,
    onOpenProjectConsumed: () -> Unit = {},
    onDashboardBack: () -> Unit = {},
) {
    val nav = rememberNavController()
    var dashboardOpenedProjectId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(popToRootSignal) {
        if (popToRootSignal > 0) {
            dashboardOpenedProjectId = null
            nav.popToRootOrReplace(rootRoute = "list", fallbackPopUpToRoute = "detail/{id}")
        }
    }
    LaunchedEffect(openProjectSignal) {
        val id = openProjectId ?: return@LaunchedEffect
        dashboardOpenedProjectId = id
        nav.replaceBackStackWith("detail/$id")
        onOpenProjectConsumed()
    }
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ProjectListScreen(
                onOpen = { id -> nav.navigate("detail/$id") },
                onArchived = { nav.navigate("archived") },
                onCreate = { nav.navigate("create") },
                onTemplateRegistries = { nav.navigate("templates") },
            )
        }
        composable("create") {
            CreateProjectScreen(
                onSuccess = { nav.popBackStack() },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("templates") {
            TemplateRegistriesScreen(onBack = { nav.popBackStack() })
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            BackHandler(enabled = dashboardOpenedProjectId == id) {
                dashboardOpenedProjectId = null
                onDashboardBack()
            }
            ProjectDetailScreen(
                projectId = id,
                onBack = {
                    if (dashboardOpenedProjectId == id) {
                        dashboardOpenedProjectId = null
                        onDashboardBack()
                    } else {
                        nav.popBackStack()
                    }
                },
                onStream = { id, action, title ->
                    nav.navigate("stream/$id/$action/${title.encodeArg()}")
                },
                onLogs = { id, title -> nav.navigate("logs/$id/${title.encodeArg()}") },
                onCompose = { id, title -> nav.navigate("compose/$id/${title.encodeArg()}") },
            )
        }
        composable("compose/{id}/{title}") { entry ->
            ComposeFileScreen(
                projectId = entry.arguments?.getString("id").orEmpty(),
                projectName = entry.arguments?.getString("title").orEmpty().decodeArg(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("stream/{id}/{action}/{title}") { entry ->
            StreamingActionScreen(
                projectId = entry.arguments?.getString("id").orEmpty(),
                action = entry.arguments?.getString("action").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty().decodeArg(),
                onDone = { nav.popBackStack() },
            )
        }
        composable("logs/{id}/{title}") { entry ->
            ProjectLogsScreen(
                projectId = entry.arguments?.getString("id").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty().decodeArg(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("archived") {
            ArchivedProjectsScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** URL-encode a nav argument so titles with slashes/spaces survive the route. */
private fun String.encodeArg(): String =
    java.net.URLEncoder.encode(this, "UTF-8")

private fun String.decodeArg(): String =
    runCatching { java.net.URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
