package app.getarcane.android.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import app.getarcane.android.core.ProjectDeployPreferenceValues
import app.getarcane.android.nav.popToRootOrReplace
import app.getarcane.android.ui.components.AdaptiveListDetailLayout
import app.getarcane.android.ui.components.ListDetailPlaceholder
import app.getarcane.android.ui.screens.settings.registries.TemplateRegistriesScreen

/**
 * Projects tab with its own nested back stack (list -> detail -> streaming action / logs / archived).
 * Mirrors the iOS `NavigationStack` rooted at `ProjectsView`.
 */
@Composable
fun ProjectsScreen(
    popToRootSignal: Int = 0,
    dashboardProjectId: String? = null,
    onDashboardBack: () -> Unit = {},
    initialProjectId: String? = null,
    initialRequestId: Long = 0,
    onInitialDetailHandled: (Long) -> Unit = {},
    nav: NavHostController = rememberNavController(),
) {
    var streamOptions by remember { mutableStateOf<ProjectDeployPreferenceValues?>(null) }
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
    LaunchedEffect(initialRequestId, initialProjectId) {
        if (initialRequestId > 0 && initialProjectId != null) {
            openDetail(initialProjectId)
            onInitialDetailHandled(initialRequestId)
        }
    }

    val listPane: @Composable () -> Unit = {
        ProjectListScreen(
            onOpen = ::openDetail,
            onArchived = { nav.navigate("archived") },
            onCreate = { nav.navigate("create") },
            onTemplateRegistries = { nav.navigate("templates") },
        )
    }
    AdaptiveListDetailLayout(
        listPane = listPane,
    ) { expanded ->
        NavHost(navController = nav, startDestination = if (dashboardProjectId == null) "list" else "dashboard-detail") {
        composable("list") {
            if (expanded) {
                ListDetailPlaceholder("project")
            } else {
                listPane()
            }
        }
        composable("create") {
            CreateProjectScreen(
                onSuccess = { _, _ -> nav.popBackStack() },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("dashboard-detail") {
            val id = dashboardProjectId ?: return@composable
            BackHandler(onBack = onDashboardBack)
            ProjectDetailScreen(
                projectId = id,
                onBack = onDashboardBack,
                onStream = { childId, action, title, options ->
                    streamOptions = options
                    nav.navigate("stream/$childId/$action/${title.encodeArg()}")
                },
                onLogs = { childId, title -> nav.navigate("logs/$childId/${title.encodeArg()}") },
                onCompose = { childId, title -> nav.navigate("compose/$childId/${title.encodeArg()}") },
            )
        }
        composable("templates") {
            TemplateRegistriesScreen(onBack = { nav.popBackStack() })
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ProjectDetailScreen(
                projectId = id,
                onBack = { nav.popBackStack() },
                onStream = { childId, action, title, options ->
                    streamOptions = options
                    nav.navigate("stream/$childId/$action/${title.encodeArg()}")
                },
                onLogs = { childId, title -> nav.navigate("logs/$childId/${title.encodeArg()}") },
                onCompose = { childId, title -> nav.navigate("compose/$childId/${title.encodeArg()}") },
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
                deployOptions = streamOptions,
                onDone = {
                    streamOptions = null
                    nav.popBackStack()
                },
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
            ArchivedProjectsScreen(
                onBack = { nav.popBackStack() },
                onOpenFiles = { id, title -> nav.navigate("compose/$id/${title.encodeArg()}") },
            )
        }
    }
    }
}

/** URL-encode a nav argument so titles with slashes/spaces survive the route. */
private fun String.encodeArg(): String =
    java.net.URLEncoder.encode(this, "UTF-8")

private fun String.decodeArg(): String =
    runCatching { java.net.URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
