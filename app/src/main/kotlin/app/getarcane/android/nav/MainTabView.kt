package app.getarcane.android.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.core.LocalOperationStore
import app.getarcane.android.core.ActivityOpenRequest
import app.getarcane.android.ui.screens.DashboardScreen
import app.getarcane.android.ui.screens.PlaceholderScreen
import app.getarcane.android.ui.screens.activities.ActivitiesTab
import app.getarcane.android.ui.screens.containers.ContainersScreen
import app.getarcane.android.ui.screens.environments.EnvironmentDetailScreen
import app.getarcane.android.ui.screens.events.EventsScreen
import app.getarcane.android.ui.screens.gitops.GitOpsScreen
import app.getarcane.android.ui.screens.gitops.GitRepositoriesScreen
import app.getarcane.android.ui.screens.updates.AllEnvironmentsImageUpdatesScreen
import app.getarcane.android.ui.screens.images.ImagesScreen
import app.getarcane.android.ui.screens.images.ImagesInitialDestination
import app.getarcane.android.ui.screens.jobs.JobsScreen
import app.getarcane.android.ui.screens.networks.NetworksScreen
import app.getarcane.android.ui.screens.ports.PortsScreen
import app.getarcane.android.ui.screens.projects.ProjectsScreen
import app.getarcane.android.ui.screens.settings.SettingsScreen
import app.getarcane.android.ui.screens.settings.SettingsInitialDestination
import app.getarcane.android.ui.screens.settings.ApiKeysScreen
import app.getarcane.android.ui.screens.settings.UsersScreen
import app.getarcane.android.ui.screens.settings.notifications.NotificationSettingsScreen
import app.getarcane.android.ui.screens.settings.rbac.OidcRoleMappingsScreen
import app.getarcane.android.ui.screens.settings.rbac.RolesScreen
import app.getarcane.android.ui.screens.settings.registries.ContainerRegistriesScreen
import app.getarcane.android.ui.screens.settings.registries.TemplateRegistriesScreen
import app.getarcane.android.ui.screens.settings.system.AuthenticationSettingsScreen
import app.getarcane.android.ui.screens.settings.system.BuildSettingsScreen
import app.getarcane.android.ui.screens.settings.system.SystemSettingsScreen
import app.getarcane.android.ui.screens.settings.webhooks.WebhooksScreen
import app.getarcane.android.ui.screens.swarm.SwarmScreen
import app.getarcane.android.ui.screens.updates.UpdatesScreen
import app.getarcane.android.ui.screens.volumes.VolumesScreen
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.user.hasPermission
import app.getarcane.sdk.models.user.isGlobalAdmin

private const val SETTINGS_ID = MainTabSelection.SETTINGS_ID

private sealed interface DashboardOpenTarget {
    val id: String

    data class Container(override val id: String) : DashboardOpenTarget
    data class Project(override val id: String) : DashboardOpenTarget
    data class Volume(override val id: String) : DashboardOpenTarget
    data class Environment(override val id: String) : DashboardOpenTarget
    data class ImageVulnerabilities(override val id: String, val name: String) : DashboardOpenTarget
    data object ImageUpdates : DashboardOpenTarget {
        override val id: String = "image-updates"
    }
}

/**
 * Bottom-nav shell: 4 swappable tabs + Settings. Tapping selects; long-pressing a tab opens the
 * [TabSwapSheet] to replace it. Port of iOS `MainTabView` + tab-swap gesture.
 */
@Composable
fun MainTabView() {
    val manager = LocalArcaneManager.current
    val operationStore = LocalOperationStore.current
    val routeCoordinator = LocalAuthenticatedRouteCoordinator.current
    val context = LocalContext.current
    val tabsStore = remember { NavTabsStore(context) }
    val selectionStore = remember { MainTabSelectionStore(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    val visible = tabsStore.visibleTabs(isAdmin, supportsV2)
    val canReadVariables = manager.currentUser?.hasPermission(Permission.Variables.READ) == true
    val available = AdaptiveNavigation.availableTabs(isAdmin, supportsV2, canReadVariables)

    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val popToRootSignals = remember { mutableStateMapOf<String, Int>() }
    var swapTarget by remember { mutableStateOf<AppTab?>(null) }
    var dashboardOpenTarget by remember { mutableStateOf<DashboardOpenTarget?>(null) }
    var externalRouteBackTabId by remember { mutableStateOf<String?>(null) }
    var imagesInitialDestination by remember { mutableStateOf(ImagesInitialDestination.List) }
    var settingsInitialDestination by remember {
        mutableStateOf<SettingsInitialDestination>(SettingsInitialDestination.Root)
    }
    var settingsTabRequestId by rememberSaveable { mutableLongStateOf(0) }
    var containerRouteRequestId by rememberSaveable { mutableLongStateOf(0) }
    var containerRouteResourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectRouteRequestId by rememberSaveable { mutableLongStateOf(0) }
    var projectRouteResourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val tabStateHolder = rememberSaveableStateHolder()
    var previousEnvironmentId by remember { mutableStateOf(manager.activeEnvironmentId.rawValue) }
    val tabNavigationOwners = key("tab-navigation@${manager.activeEnvironmentId.rawValue}") {
        mapOf(
            SETTINGS_ID to rememberNavController(),
            AppTab.Containers.id to rememberNavController(),
            AppTab.Images.id to rememberNavController(),
            AppTab.Projects.id to rememberNavController(),
            AppTab.Volumes.id to rememberNavController(),
            AppTab.Networks.id to rememberNavController(),
            AppTab.Ports.id to rememberNavController(),
            AppTab.Events.id to rememberNavController(),
            AppTab.Jobs.id to rememberNavController(),
            AppTab.Activities.id to rememberNavController(),
            AppTab.Updates.id to rememberNavController(),
        )
    }

    LaunchedEffect(manager.activeEnvironmentId.rawValue) {
        val nextEnvironmentId = manager.activeEnvironmentId.rawValue
        if (nextEnvironmentId != previousEnvironmentId) {
            AppTab.entries.filter(AppTab::isEnvironmentScoped).forEach { tab ->
                tabStateHolder.removeState("${tab.id}@$previousEnvironmentId")
            }
            previousEnvironmentId = nextEnvironmentId
        }
    }

    val activityOpenRequest = operationStore.activityOpenRequest
    LaunchedEffect(activityOpenRequest?.requestId) {
        if (activityOpenRequest != null) selected = AppTab.Activities.id
    }

    LaunchedEffect(routeCoordinator.issueMessage) {
        routeCoordinator.issueMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            routeCoordinator.consumeIssue()
        }
    }

    LaunchedEffect(routeCoordinator.pendingRoute) {
        val pending = routeCoordinator.pendingRoute ?: return@LaunchedEffect
        when (val resolution = resolveAuthenticatedRoute(pending, manager)) {
            AuthenticatedRouteResolution.LoginRequired -> Unit
            is AuthenticatedRouteResolution.Rejected -> routeCoordinator.reject(resolution.message)
            is AuthenticatedRouteResolution.Ready -> {
                val route = resolution.route
                route.environmentId?.let { environmentId ->
                    manager.setActiveEnvironment(EnvironmentId(environmentId), environmentId)
                }
                when (route.destination) {
                    RouteDestination.DASHBOARD -> {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        selected = AppTab.Dashboard.id
                    }
                    RouteDestination.CONTAINERS -> {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        selected = AppTab.Containers.id
                        popToRootSignals[AppTab.Containers.id] = (popToRootSignals[AppTab.Containers.id] ?: 0) + 1
                    }
                    RouteDestination.PROJECTS -> {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        selected = AppTab.Projects.id
                        popToRootSignals[AppTab.Projects.id] = (popToRootSignals[AppTab.Projects.id] ?: 0) + 1
                    }
                    RouteDestination.ENVIRONMENT -> {
                        selected = AppTab.Dashboard.id
                        externalRouteBackTabId = AppTab.Dashboard.id
                        dashboardOpenTarget = DashboardOpenTarget.Environment(requireNotNull(route.environmentId))
                    }
                    RouteDestination.CONTAINER -> {
                        selected = AppTab.Dashboard.id
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        containerRouteResourceId = requireNotNull(route.resourceId)
                        containerRouteRequestId += 1
                        selected = AppTab.Containers.id
                    }
                    RouteDestination.PROJECT -> {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        projectRouteResourceId = requireNotNull(route.resourceId)
                        projectRouteRequestId += 1
                        selected = AppTab.Projects.id
                    }
                    RouteDestination.ACTIVITIES -> {
                        dashboardOpenTarget = null
                        selected = AppTab.Activities.id
                    }
                    RouteDestination.ACTIVITY -> operationStore.openExternalActivity(
                        activityId = requireNotNull(route.resourceId),
                        environmentId = requireNotNull(route.environmentId),
                    )
                    RouteDestination.OPERATIONS -> operationStore.openCenter()
                    RouteDestination.OPERATION -> operationStore.openOperation(requireNotNull(route.resourceId))
                }
                routeCoordinator.consume(route)
            }
        }
    }

    if (selected == null && selectionStore.hasLoaded) {
        selected = MainTabSelection.restore(
            storedTabId = selectionStore.selectedTabId,
            visibleTabs = visible,
            isAdmin = isAdmin,
            supportsV2 = supportsV2,
        )
    }

    val currentSelected = selected ?: AppTab.Dashboard.id
    val normalizedSelection = MainTabSelection.normalize(
        selectedTabId = currentSelected,
        visibleTabs = visible,
        isAdmin = isAdmin,
        supportsV2 = supportsV2,
    )
    if (selected != null && currentSelected != normalizedSelection) {
        selected = normalizedSelection
    }
    LaunchedEffect(selectionStore.hasLoaded, normalizedSelection) {
        if (selectionStore.hasLoaded) {
            selectionStore.select(normalizedSelection)
        }
    }
    val hostedResourceTabId = when (dashboardOpenTarget) {
        is DashboardOpenTarget.Container -> AppTab.Containers.id
        is DashboardOpenTarget.Project -> AppTab.Projects.id
        is DashboardOpenTarget.Volume -> AppTab.Volumes.id
        is DashboardOpenTarget.Environment -> null
        is DashboardOpenTarget.ImageVulnerabilities -> null
        DashboardOpenTarget.ImageUpdates -> null
        null -> null
    }
    val bottomBarSelectedTabId = MainTabSelection.bottomBarSelectedTabId(
        selectedTabId = normalizedSelection,
        hostedResourceTabId = hostedResourceTabId,
        visibleTabs = visible,
    )
    val latestNormalizedSelection by rememberUpdatedState(normalizedSelection)

    val rootBackAction = MainBackNavigation.resolve(
        selectedTabId = normalizedSelection,
        hasDashboardOpenTarget = dashboardOpenTarget != null,
    )
    fun returnToDashboard() {
        dashboardOpenTarget = null
        externalRouteBackTabId = null
        selected = AppTab.Dashboard.id
    }
    BackHandler(enabled = rootBackAction == MainBackNavigation.Action.SwitchToDashboard) {
        // Register this before child content so nested NavHosts and transient UI get first chance to
        // consume Back. Dashboard-hosted details clear back to the Dashboard, and non-Dashboard
        // tab roots return home instead of exiting the Activity from a resource/settings tab dead end.
        if (dashboardOpenTarget != null) {
            dashboardOpenTarget = null
            selected = externalRouteBackTabId ?: AppTab.Dashboard.id
            externalRouteBackTabId = null
        } else {
            returnToDashboard()
        }
    }

    fun selectOrPopToRoot(tabId: String) {
        val selectedTab = AppTab.byId(tabId)
        if (tabId == AppTab.Dashboard.id &&
            (latestNormalizedSelection != AppTab.Dashboard.id || dashboardOpenTarget != null)
        ) {
            // The Dashboard item and system Back intentionally use the same state transition.
            returnToDashboard()
        } else if (selectedTab != null && AdaptiveNavigation.usesSettingsHost(selectedTab)) {
            dashboardOpenTarget = null
            externalRouteBackTabId = null
            settingsTabRequestId += 1
            settingsInitialDestination = SettingsInitialDestination.Tab(tabId, settingsTabRequestId)
            selected = SETTINGS_ID
        } else if (dashboardOpenTarget != null && latestNormalizedSelection == AppTab.Dashboard.id && tabId == AppTab.Dashboard.id) {
            dashboardOpenTarget = null
            externalRouteBackTabId = null
        } else if (MainTabSelection.shouldPopToRootOnTap(latestNormalizedSelection, tabId)) {
            dashboardOpenTarget = null
            externalRouteBackTabId = null
            popToRootSignals[tabId] = (popToRootSignals[tabId] ?: 0) + 1
        } else {
            dashboardOpenTarget = null
            externalRouteBackTabId = null
            selected = tabId
        }
    }

    AdaptiveNavigationShell(
        pinnedTabs = visible,
        availableTabs = available,
        selectedTabId = bottomBarSelectedTabId,
        snackbarHostState = snackbarHostState,
        onSelect = ::selectOrPopToRoot,
        onCompactLongClick = { swapTarget = it },
    ) {
        Box(Modifier.fillMaxSize()) {
            val tab = AppTab.byId(normalizedSelection)
            val envKey = if (tab?.isEnvironmentScoped == true) manager.activeEnvironmentId.rawValue else ""
            val popToRootSignal = popToRootSignals[normalizedSelection] ?: 0
            tabStateHolder.SaveableStateProvider("$normalizedSelection@$envKey") {
                TabContent(
                    normalizedSelection,
                    popToRootSignal = popToRootSignal,
                    onSelectTab = { selected = it },
                    dashboardOpenTarget = dashboardOpenTarget,
                    onOpenContainer = { id ->
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.Container(id = id)
                    },
                    onOpenProject = { id ->
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.Project(id = id)
                    },
                    onOpenVolume = { name ->
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.Volume(id = name)
                    },
                    onOpenEnvironment = { id ->
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.Environment(id = id)
                    },
                    onDashboardBack = {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                    },
                    settingsInitialDestination = settingsInitialDestination,
                    onSettingsInitialDestinationHandled = {
                        settingsInitialDestination = SettingsInitialDestination.Root
                    },
                    imagesInitialDestination = imagesInitialDestination,
                    onImagesInitialDestinationHandled = {
                        imagesInitialDestination = ImagesInitialDestination.List
                    },
                    onOpenImageVulnerabilities = { id, name ->
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.ImageVulnerabilities(id = id, name = name)
                    },
                    onOpenImageUpdates = {
                        externalRouteBackTabId = null
                        dashboardOpenTarget = DashboardOpenTarget.ImageUpdates
                    },
                    onOpenApiKeys = {
                        dashboardOpenTarget = null
                        externalRouteBackTabId = null
                        selected = AppTab.ApiKeys.id
                    },
                    activityOpenRequest = activityOpenRequest,
                    onActivityOpenHandled = operationStore::consumeActivityOpenRequest,
                    containerRouteResourceId = containerRouteResourceId,
                    containerRouteRequestId = containerRouteRequestId,
                    onContainerRouteHandled = { requestId ->
                        if (containerRouteRequestId == requestId) containerRouteResourceId = null
                    },
                    projectRouteResourceId = projectRouteResourceId,
                    projectRouteRequestId = projectRouteRequestId,
                    onProjectRouteHandled = { requestId ->
                        if (projectRouteRequestId == requestId) projectRouteResourceId = null
                    },
                    nav = tabNavigationOwners[normalizedSelection],
                )
            }
        }
    }

    val target = swapTarget
    if (target != null) {
        TabSwapSheet(
            current = target,
            tabsStore = tabsStore,
            onPick = { picked ->
                val slot = tabsStore.pinned.indexOfFirst { it.id == target.id }.takeIf { it >= 0 } ?: 0
                tabsStore.swap(slot, picked)
                selected = picked.id
                swapTarget = null
            },
            onReset = {
                tabsStore.resetToDefaults()
                swapTarget = null
            },
            onDismiss = { swapTarget = null },
        )
    }
}

@Composable
private fun TabContent(
    tabId: String,
    popToRootSignal: Int,
    onSelectTab: (String) -> Unit,
    dashboardOpenTarget: DashboardOpenTarget?,
    onOpenContainer: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenVolume: (String) -> Unit,
    onOpenEnvironment: (String) -> Unit,
    onDashboardBack: () -> Unit,
    settingsInitialDestination: SettingsInitialDestination,
    onSettingsInitialDestinationHandled: () -> Unit,
    imagesInitialDestination: ImagesInitialDestination,
    onImagesInitialDestinationHandled: () -> Unit,
    onOpenImageVulnerabilities: (String, String) -> Unit,
    onOpenImageUpdates: () -> Unit,
    onOpenApiKeys: () -> Unit,
    activityOpenRequest: ActivityOpenRequest?,
    onActivityOpenHandled: (Long) -> Unit,
    containerRouteResourceId: String?,
    containerRouteRequestId: Long,
    onContainerRouteHandled: (Long) -> Unit,
    projectRouteResourceId: String?,
    projectRouteRequestId: Long,
    onProjectRouteHandled: (Long) -> Unit,
    nav: NavHostController?,
) {
    when (tabId) {
        SETTINGS_ID -> SettingsScreen(
            popToRootSignal = popToRootSignal,
            initialDestination = settingsInitialDestination,
            onInitialDestinationHandled = onSettingsInitialDestinationHandled,
            nav = requireNotNull(nav),
        )
        AppTab.Dashboard.id -> {
            when (val target = dashboardOpenTarget) {
                is DashboardOpenTarget.Container -> key(target) {
                    ContainersScreen(
                        dashboardContainerId = target.id,
                        onDashboardBack = onDashboardBack,
                    )
                }
                is DashboardOpenTarget.Project -> key(target) {
                    ProjectsScreen(
                        dashboardProjectId = target.id,
                        onDashboardBack = onDashboardBack,
                    )
                }
                is DashboardOpenTarget.Volume -> key(target) {
                    VolumesScreen(
                        dashboardVolumeName = target.id,
                        onDashboardBack = onDashboardBack,
                    )
                }
                is DashboardOpenTarget.Environment -> key(target) {
                    EnvironmentDetailScreen(
                        id = target.id,
                        onBack = onDashboardBack,
                    )
                }
                is DashboardOpenTarget.ImageVulnerabilities -> key(target) {
                    ImagesScreen(
                        initialDestination = ImagesInitialDestination.Vulnerabilities,
                        vulnerabilitiesEnvironmentId = EnvironmentId(target.id),
                        vulnerabilitiesEnvironmentName = target.name,
                        onInitialDestinationHandled = {},
                        onInitialDestinationBack = onDashboardBack,
                    )
                }
                DashboardOpenTarget.ImageUpdates -> key(target) {
                    AllEnvironmentsImageUpdatesScreen(onBack = onDashboardBack)
                }
                null -> DashboardScreen(
                    onOpenTab = onSelectTab,
                    onOpenContainer = onOpenContainer,
                    onOpenProject = onOpenProject,
                    onOpenVolume = onOpenVolume,
                    onOpenEnvironmentDetails = onOpenEnvironment,
                    onOpenImageVulnerabilities = onOpenImageVulnerabilities,
                    onOpenImageUpdates = onOpenImageUpdates,
                    onOpenApiKeys = onOpenApiKeys,
                )
            }
        }
        AppTab.Containers.id -> {
            ContainersScreen(
                popToRootSignal = popToRootSignal,
                initialContainerId = containerRouteResourceId,
                initialRequestId = containerRouteRequestId,
                onInitialDetailHandled = onContainerRouteHandled,
                nav = requireNotNull(nav),
            )
        }
        AppTab.Images.id -> ImagesScreen(
            popToRootSignal = popToRootSignal,
            initialDestination = imagesInitialDestination,
            onInitialDestinationHandled = onImagesInitialDestinationHandled,
            nav = requireNotNull(nav),
        )
        AppTab.Projects.id -> {
            ProjectsScreen(
                popToRootSignal = popToRootSignal,
                initialProjectId = projectRouteResourceId,
                initialRequestId = projectRouteRequestId,
                onInitialDetailHandled = onProjectRouteHandled,
                nav = requireNotNull(nav),
            )
        }
        AppTab.Volumes.id -> VolumesScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Networks.id -> NetworksScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Ports.id -> PortsScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Events.id -> EventsScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Jobs.id -> JobsScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Activities.id -> ActivitiesTab(
            popToRootSignal = popToRootSignal,
            initialDetail = activityOpenRequest,
            onInitialDetailHandled = onActivityOpenHandled,
            nav = requireNotNull(nav),
        )
        AppTab.Updates.id -> UpdatesScreen(popToRootSignal = popToRootSignal, nav = requireNotNull(nav))
        AppTab.Swarm.id -> SwarmScreen()
        AppTab.GitOps.id -> GitOpsScreen()
        AppTab.GitRepositories.id -> GitRepositoriesScreen()
        AppTab.ContainerRegistries.id -> ContainerRegistriesScreen()
        AppTab.TemplateRegistries.id -> TemplateRegistriesScreen()
        AppTab.Users.id -> UsersScreen(onOpenUser = {})
        AppTab.ApiKeys.id -> ApiKeysScreen()
        AppTab.Notifications.id -> NotificationSettingsScreen(onOpenProvider = {})
        AppTab.Webhooks.id -> WebhooksScreen()
        AppTab.SystemSettings.id -> SystemSettingsScreen(onOpenCategory = {}, onUpgrade = {})
        AppTab.Authentication.id -> AuthenticationSettingsScreen()
        AppTab.Builds.id -> BuildSettingsScreen()
        AppTab.Roles.id -> RolesScreen(onOpenRole = {}, onCreateRole = {})
        AppTab.OidcRoleMappings.id -> OidcRoleMappingsScreen()
        else -> PlaceholderScreen(AppTab.byId(tabId)?.title ?: "Unknown")
    }
}
