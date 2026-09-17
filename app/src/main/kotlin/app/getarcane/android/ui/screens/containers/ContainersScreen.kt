package app.getarcane.android.ui.screens.containers

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.supportsContainerManagementWorkflows
import app.getarcane.android.nav.popToRootOrReplace
import app.getarcane.android.ui.components.AdaptiveListDetailLayout
import app.getarcane.android.ui.components.ListDetailPlaceholder
import app.getarcane.sdk.EnvironmentId
import kotlinx.coroutines.CancellationException

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
    onOpenProject: (String) -> Unit = {},
    nav: NavHostController = rememberNavController(),
) {
    val manager = LocalArcaneManager.current
    val environmentId = manager.activeEnvironmentId.rawValue
    val user = manager.currentUser
    var supportsManagementForEnvironment by remember(environmentId) {
        mutableStateOf(manager.activeEnvironmentId == EnvironmentId.LOCAL_DOCKER && manager.supportsContainerManagementWorkflows)
    }
    LaunchedEffect(manager.client, environmentId, manager.supportsContainerManagementWorkflows) {
        supportsManagementForEnvironment = when {
            manager.activeEnvironmentId == EnvironmentId.LOCAL_DOCKER -> manager.supportsContainerManagementWorkflows
            manager.client == null -> false
            else -> try {
                manager.client!!.version.environmentVersion(manager.activeEnvironmentId).supportsContainerManagementWorkflows()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                false
            }
        }
    }
    val workflowAccess = containerWorkflowAccess(
        user = user,
        environmentId = environmentId,
        offline = manager.offlineReadSessionActive,
        supportsManagement = supportsManagementForEnvironment,
        canCreateProjectSurface = manager.canAccessSurface("route.projects.new", environmentId),
    )
    val canCreate = workflowAccess.canCreate
    val canEdit = workflowAccess.canEdit
    val canCommit = workflowAccess.canCommit
    val canCompose = workflowAccess.canCompose
    val currentEntry by nav.currentBackStackEntryAsState()

    LaunchedEffect(currentEntry?.destination?.route, canCreate, canEdit, canCommit, canCompose) {
        val route = currentEntry?.destination?.route.orEmpty()
        val allowed = when {
            route == "create" -> canCreate
            route.startsWith("edit/") -> canEdit
            route.startsWith("commit/") -> canCommit
            route.startsWith("compose/") -> canCompose
            else -> true
        }
        if (!allowed) nav.popBackStack()
    }
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
        ContainerListScreen(onOpen = ::openDetail, onCreate = if (canCreate) ({ nav.navigate("create") }) else null)
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
                    onEdit = if (canEdit) ({ childId -> nav.navigate("edit/$childId") }) else null,
                    onCommit = if (canCommit) ({ childId -> nav.navigate("commit/$childId") }) else null,
                    onCompose = if (canCompose) ({ childId -> nav.navigate("compose/$childId") }) else null,
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
                    onEdit = if (canEdit) ({ childId -> nav.navigate("edit/$childId") }) else null,
                    onCommit = if (canCommit) ({ childId -> nav.navigate("commit/$childId") }) else null,
                    onCompose = if (canCompose) ({ childId -> nav.navigate("compose/$childId") }) else null,
                )
            }
            composable("create") {
                ContainerConfigurationScreen(
                    mode = ContainerConfigurationMode.CREATE,
                    onBack = { nav.popBackStack() },
                    onSaved = ::openDetail,
                )
            }
            composable("edit/{id}") { entry ->
                ContainerConfigurationScreen(
                    mode = ContainerConfigurationMode.EDIT,
                    containerId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onSaved = ::openDetail,
                )
            }
            composable("commit/{id}") { entry ->
                ContainerCommitScreen(
                    containerId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("compose/{id}") { entry ->
                ContainerComposeScreen(
                    containerId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onProjectCreated = onOpenProject,
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
