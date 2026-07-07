package app.getarcane.android.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.getarcane.android.core.LocalArcaneManager
import app.getarcane.android.ui.screens.DashboardScreen
import app.getarcane.android.ui.screens.PlaceholderScreen
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
import app.getarcane.android.ui.screens.settings.SettingsScreen
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
import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.user.isGlobalAdmin

private const val SETTINGS_ID = MainTabSelection.SETTINGS_ID

/**
 * Bottom-nav shell: 4 swappable tabs + Settings. Tapping selects; long-pressing a tab opens the
 * [TabSwapSheet] to replace it. Port of iOS `MainTabView` + tab-swap gesture.
 */
@Composable
fun MainTabView() {
    val manager = LocalArcaneManager.current
    val context = LocalContext.current
    val tabsStore = remember { NavTabsStore(context) }

    val isAdmin = manager.currentUser?.isGlobalAdmin ?: false
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC
    val visible = tabsStore.visibleTabs(isAdmin, supportsV2)

    var selected by rememberSaveable { mutableStateOf(AppTab.Dashboard.id) }
    var swapTarget by remember { mutableStateOf<AppTab?>(null) }
    val normalizedSelection = MainTabSelection.normalize(
        selectedTabId = selected,
        visibleTabs = visible,
        isAdmin = isAdmin,
        supportsV2 = supportsV2,
    )
    if (selected != normalizedSelection) {
        selected = normalizedSelection
    }

    val rootBackAction = MainBackNavigation.resolve(selected)
    BackHandler(enabled = rootBackAction == MainBackNavigation.Action.SwitchToDashboard) {
        // Register this before child content so nested NavHosts and transient UI get first chance to
        // consume Back. Once a non-Dashboard tab is at its root, system Back returns home instead of
        // exiting the Activity from a resource/settings tab dead end.
        selected = AppTab.Dashboard.id
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                visible.forEach { tab ->
                    NavBarItem(
                        icon = tab.icon,
                        label = tab.tabBarTitle,
                        selected = selected == tab.id,
                        onClick = { selected = tab.id },
                        onLongClick = { swapTarget = tab },
                    )
                }
                NavBarItem(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    selected = selected == SETTINGS_ID,
                    onClick = { selected = SETTINGS_ID },
                    onLongClick = null,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val tab = AppTab.byId(selected)
            val envKey = if (tab?.isEnvironmentScoped == true) manager.activeEnvironmentId.rawValue else ""
            key(selected, envKey) {
                TabContent(selected, onSelectTab = { selected = it })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.NavBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = tint)
    }
}

@Composable
private fun TabContent(tabId: String, onSelectTab: (String) -> Unit) {
    when (tabId) {
        SETTINGS_ID -> SettingsScreen()
        AppTab.Dashboard.id -> DashboardScreen(onOpenTab = onSelectTab)
        AppTab.Containers.id -> ContainersScreen()
        AppTab.Images.id -> ImagesScreen()
        AppTab.Projects.id -> ProjectsScreen()
        AppTab.Volumes.id -> VolumesScreen()
        AppTab.Networks.id -> NetworksScreen()
        AppTab.Ports.id -> PortsScreen()
        AppTab.Events.id -> EventsScreen()
        AppTab.Jobs.id -> JobsScreen()
        AppTab.Activities.id -> ActivitiesTab()
        AppTab.Updates.id -> UpdatesScreen()
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
