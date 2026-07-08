package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.nav.AppTab
import app.getarcane.android.nav.NavTabsStore
import app.getarcane.android.nav.PopToRootOnSignal
import app.getarcane.android.nav.TabSection
import app.getarcane.android.ui.screens.DashboardScreen
import app.getarcane.android.ui.screens.activities.ActivitiesTab
import app.getarcane.android.ui.screens.containers.ContainersScreen
import app.getarcane.android.ui.screens.events.EventsScreen
import app.getarcane.android.ui.screens.gitops.GitOpsScreen
import app.getarcane.android.ui.screens.gitops.GitRepositoriesScreen
import app.getarcane.android.ui.screens.images.ImagesScreen
import app.getarcane.android.ui.screens.jobs.JobsScreen
import app.getarcane.android.ui.screens.networks.NetworksScreen
import app.getarcane.android.ui.screens.ports.PortsScreen
import app.getarcane.android.ui.screens.projects.ProjectsScreen
import app.getarcane.android.ui.screens.settings.notifications.NotificationProviderFormScreen
import app.getarcane.android.ui.screens.settings.notifications.NotificationSettingsScreen
import app.getarcane.android.ui.screens.settings.rbac.OidcRoleMappingsScreen
import app.getarcane.android.ui.screens.settings.rbac.RoleDetailMode
import app.getarcane.android.ui.screens.settings.rbac.RoleDetailScreen
import app.getarcane.android.ui.screens.settings.rbac.RolesScreen
import app.getarcane.android.ui.screens.settings.rbac.UserRoleAssignmentsScreen
import app.getarcane.android.ui.screens.settings.registries.ContainerRegistriesScreen
import app.getarcane.android.ui.screens.settings.registries.TemplateRegistriesScreen
import app.getarcane.android.ui.screens.settings.system.AuthenticationSettingsScreen
import app.getarcane.android.ui.screens.settings.system.BuildSettingsScreen
import app.getarcane.android.ui.screens.settings.system.SettingsCategoryScreen
import app.getarcane.android.ui.screens.settings.system.SystemSettingsScreen
import app.getarcane.android.ui.screens.settings.system.SystemUpgradeScreen
import app.getarcane.android.ui.screens.settings.webhooks.WebhooksScreen
import app.getarcane.android.ui.screens.swarm.SwarmScreen
import app.getarcane.android.ui.screens.updates.UpdatesScreen
import app.getarcane.android.ui.screens.volumes.VolumesScreen
import app.getarcane.android.ui.screens.whatsnew.WhatsNewScreen
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.notification.NotificationProvider
import app.getarcane.sdk.models.user.isAdmin

private object SettingsRoutes {
    const val ROOT = "root"
    const val APP_SETTINGS = "app-settings"
    const val APPEARANCE = "appearance"
    const val WHATS_NEW = "whats-new"
    const val USERS = "users"
    const val USER_DETAIL = "user/{userId}"
    const val USER_ROLE_ASSIGNMENTS = "user/{userId}/roles"
    const val API_KEYS = "api-keys"
    const val ROLES = "roles"
    const val ROLE_CREATE = "role-create"
    const val ROLE_DETAIL = "role/{roleId}"
    const val OIDC_MAPPINGS = "oidc-mappings"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_PROVIDER = "notifications/{provider}"
    const val WEBHOOKS = "webhooks"
    const val AUTHENTICATION = "authentication"
    const val BUILDS = "builds"
    const val SYSTEM = "system"
    const val SYSTEM_CATEGORY = "system/{categoryId}"
    const val CONTAINER_REGISTRIES = "container-registries"
    const val TEMPLATE_REGISTRIES = "template-registries"
    const val UPGRADE = "upgrade"
}

/**
 * The Settings tab. A self-contained nested [NavHost] over the whole settings hierarchy. Port of the
 * iOS `SettingsView` navigation stack; the entry composable is `SettingsScreen()` (no args).
 */
@Composable
fun SettingsScreen(popToRootSignal: Int = 0) {
    val nav = rememberNavController()
    nav.PopToRootOnSignal(popToRootSignal, rootRoute = SettingsRoutes.ROOT)
    NavHost(navController = nav, startDestination = SettingsRoutes.ROOT) {
        composable(SettingsRoutes.ROOT) { SettingsRoot(nav) }
        AppTab.entries.forEach { tab ->
            composable(tab.id) { SettingsTabDestination(tab = tab, nav = nav) }
        }

        composable(SettingsRoutes.APP_SETTINGS) {
            AppSettingsScreen(
                onBack = { nav.popBackStack() },
                onAppearance = { nav.navigate(SettingsRoutes.APPEARANCE) },
                onWhatsNew = { nav.navigate(SettingsRoutes.WHATS_NEW) },
            )
        }
        composable(SettingsRoutes.APPEARANCE) { AppearanceSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(SettingsRoutes.WHATS_NEW) { WhatsNewScreen(onBack = { nav.popBackStack() }) }

        composable(SettingsRoutes.USER_DETAIL) { entry ->
            UserDetailScreen(
                userId = entry.arguments?.getString("userId").orEmpty(),
                onBack = { nav.popBackStack() },
                onEditRoleAssignments = { id -> nav.navigate("user/$id/roles") },
            )
        }
        composable(SettingsRoutes.USER_ROLE_ASSIGNMENTS) { entry ->
            UserRoleAssignmentsScreen(
                userId = entry.arguments?.getString("userId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(SettingsRoutes.ROLE_CREATE) {
            RoleDetailScreen(roleId = null, mode = RoleDetailMode.Create, onClose = { nav.popBackStack() })
        }
        composable(SettingsRoutes.ROLE_DETAIL) { entry ->
            RoleDetailScreen(
                roleId = entry.arguments?.getString("roleId"),
                // Built-in vs custom is resolved on load; the screen shows built-ins read-only via its own check.
                mode = RoleDetailMode.Edit,
                onClose = { nav.popBackStack() },
            )
        }

        composable(SettingsRoutes.NOTIFICATION_PROVIDER) { entry ->
            val wire = entry.arguments?.getString("provider").orEmpty()
            val provider = NotificationProvider.entries.firstOrNull { it.wire == wire } ?: NotificationProvider.DISCORD
            NotificationProviderFormScreen(provider = provider, onBack = { nav.popBackStack() })
        }

        composable(SettingsRoutes.SYSTEM_CATEGORY) { entry ->
            SettingsCategoryScreen(
                categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(SettingsRoutes.UPGRADE) { SystemUpgradeScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRoot(nav: NavHostController) {
    val manager = LocalArcaneManager.current
    val context = LocalContext.current
    val tabsStore = remember { NavTabsStore(context) }
    val isAdmin = manager.currentUser?.isAdmin ?: false
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    val pinnedTabs = tabsStore.pinned.toSet()

    fun visibleTabs(section: TabSection): List<AppTab> =
        AppTab.entries.filter { tab ->
            tab.section == section &&
                tab !in pinnedTabs &&
                (isAdmin || !tab.requiresAdmin) &&
                (supportsV2 || !tab.requiresV2)
        }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    IconButton(onClick = { nav.navigate(SettingsRoutes.APP_SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "App Settings")
                    }
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = ArcaneRed)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            TabSection.entries.forEach { section ->
                val tabs = visibleTabs(section)
                if (tabs.isNotEmpty()) {
                    item(key = "${section.name}-header") {
                        SettingsSectionHeader(section.title)
                    }
                    tabs.forEach { tab ->
                        item(key = tab.id) {
                            SettingsRow(
                                title = tab.title,
                                icon = tab.icon,
                                iconColor = tab.color,
                                onClick = { nav.navigate(tab.id) },
                                trailing = { ChevronTrailing() },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Sign Out",
            message = "You'll be signed out of this server.",
            confirmLabel = "Sign Out",
            onConfirm = { manager.logout() },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

@Composable
private fun SettingsTabDestination(tab: AppTab, nav: NavHostController) {
    when (tab) {
        AppTab.Dashboard -> DashboardScreen()
        AppTab.Containers -> ContainersScreen()
        AppTab.Images -> ImagesScreen()
        AppTab.Projects -> ProjectsScreen()
        AppTab.Volumes -> VolumesScreen()
        AppTab.Networks -> NetworksScreen()
        AppTab.Ports -> PortsScreen()
        AppTab.Updates -> UpdatesScreen()
        AppTab.Activities -> ActivitiesTab()
        AppTab.Events -> EventsScreen()
        AppTab.GitRepositories -> GitRepositoriesScreen()
        AppTab.GitOps -> GitOpsScreen()
        AppTab.Swarm -> SwarmScreen()
        AppTab.Users -> UsersScreen(onOpenUser = { id -> nav.navigate("user/$id") })
        AppTab.ApiKeys -> ApiKeysScreen()
        AppTab.ContainerRegistries -> ContainerRegistriesScreen()
        AppTab.TemplateRegistries -> TemplateRegistriesScreen(onBack = { nav.popBackStack() })
        AppTab.Notifications -> NotificationSettingsScreen(onOpenProvider = { provider -> nav.navigate("notifications/${provider.wire}") })
        AppTab.Webhooks -> WebhooksScreen()
        AppTab.SystemSettings -> SystemSettingsScreen(
            onOpenCategory = { id -> nav.navigate("system/$id") },
            onUpgrade = { nav.navigate(SettingsRoutes.UPGRADE) },
        )
        AppTab.Authentication -> AuthenticationSettingsScreen(onBack = { nav.popBackStack() })
        AppTab.Builds -> BuildSettingsScreen(onBack = { nav.popBackStack() })
        AppTab.Jobs -> JobsScreen()
        AppTab.Roles -> RolesScreen(
            onOpenRole = { id -> nav.navigate("role/$id") },
            onCreateRole = { nav.navigate(SettingsRoutes.ROLE_CREATE) },
        )
        AppTab.OidcRoleMappings -> OidcRoleMappingsScreen()
    }
}
