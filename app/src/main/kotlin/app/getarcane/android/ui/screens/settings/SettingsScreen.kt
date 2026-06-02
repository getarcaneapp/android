package app.getarcane.android.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Webhook
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.getarcane.android.core.LocalArcaneManager
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
import app.getarcane.android.ui.screens.whatsnew.WhatsNewScreen
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneYellow
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
fun SettingsScreen() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = SettingsRoutes.ROOT) {
        composable(SettingsRoutes.ROOT) { SettingsRoot(nav) }

        composable(SettingsRoutes.APP_SETTINGS) {
            AppSettingsScreen(
                onBack = { nav.popBackStack() },
                onAppearance = { nav.navigate(SettingsRoutes.APPEARANCE) },
                onWhatsNew = { nav.navigate(SettingsRoutes.WHATS_NEW) },
            )
        }
        composable(SettingsRoutes.APPEARANCE) { AppearanceSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(SettingsRoutes.WHATS_NEW) { WhatsNewScreen(onBack = { nav.popBackStack() }) }

        composable(SettingsRoutes.USERS) {
            UsersScreen(onOpenUser = { id -> nav.navigate("user/$id") })
        }
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

        composable(SettingsRoutes.API_KEYS) { ApiKeysScreen() }

        composable(SettingsRoutes.ROLES) {
            RolesScreen(
                onOpenRole = { id -> nav.navigate("role/$id") },
                onCreateRole = { nav.navigate(SettingsRoutes.ROLE_CREATE) },
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

        composable(SettingsRoutes.OIDC_MAPPINGS) { OidcRoleMappingsScreen() }

        composable(SettingsRoutes.NOTIFICATIONS) {
            NotificationSettingsScreen(onOpenProvider = { provider -> nav.navigate("notifications/${provider.wire}") })
        }
        composable(SettingsRoutes.NOTIFICATION_PROVIDER) { entry ->
            val wire = entry.arguments?.getString("provider").orEmpty()
            val provider = NotificationProvider.entries.firstOrNull { it.wire == wire } ?: NotificationProvider.DISCORD
            NotificationProviderFormScreen(provider = provider, onBack = { nav.popBackStack() })
        }

        composable(SettingsRoutes.WEBHOOKS) { WebhooksScreen() }
        composable(SettingsRoutes.AUTHENTICATION) { AuthenticationSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(SettingsRoutes.BUILDS) { BuildSettingsScreen(onBack = { nav.popBackStack() }) }

        composable(SettingsRoutes.SYSTEM) {
            SystemSettingsScreen(
                onOpenCategory = { id -> nav.navigate("system/$id") },
                onUpgrade = { nav.navigate(SettingsRoutes.UPGRADE) },
            )
        }
        composable(SettingsRoutes.SYSTEM_CATEGORY) { entry ->
            SettingsCategoryScreen(
                categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(SettingsRoutes.UPGRADE) { SystemUpgradeScreen(onBack = { nav.popBackStack() }) }

        composable(SettingsRoutes.CONTAINER_REGISTRIES) { ContainerRegistriesScreen() }
        composable(SettingsRoutes.TEMPLATE_REGISTRIES) { TemplateRegistriesScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRoot(nav: NavHostController) {
    val manager = LocalArcaneManager.current
    val isAdmin = manager.currentUser?.isAdmin ?: false
    val supportsV2 = manager.capabilities.mode == ServerCapabilities.Mode.RBAC

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showChangeServerConfirm by remember { mutableStateOf(false) }

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
            // Server / Account
            item(key = "server-header") { SettingsSectionHeader("Server") }
            item(key = "active-env") {
                SettingsRow(
                    title = "Active Environment",
                    subtitle = manager.activeEnvironmentName,
                    icon = Icons.Filled.Storage,
                    iconColor = ArcaneBlue,
                )
            }
            item(key = "server-url") {
                SettingsRow(
                    title = "Server",
                    subtitle = manager.serverUrl.ifEmpty { "Not configured" },
                    icon = Icons.Filled.Link,
                    iconColor = ArcaneBlue,
                    onClick = { showChangeServerConfirm = true },
                    trailing = { ChevronTrailing() },
                )
            }
            item(key = "server-footer") {
                val user = manager.currentUser
                SettingsSectionFooter(
                    if (user != null) "Signed in as ${user.displayUsername}. Tap the server row to switch."
                    else "Tap the server row to switch.",
                )
            }

            // Management
            if (isAdmin) {
                item(key = "mgmt-header") { SettingsSectionHeader("Management") }
                item(key = "container-registries") {
                    SettingsRow("Container Registries", Icons.Filled.Cloud, ArcanePurple, onClick = { nav.navigate(SettingsRoutes.CONTAINER_REGISTRIES) }, trailing = { ChevronTrailing() })
                }
                item(key = "template-registries") {
                    SettingsRow("Template Registries", Icons.Filled.Description, ArcaneIndigo, onClick = { nav.navigate(SettingsRoutes.TEMPLATE_REGISTRIES) }, trailing = { ChevronTrailing() })
                }
            }

            // Administration
            item(key = "admin-header") { SettingsSectionHeader("Administration") }
            item(key = "notifications") {
                SettingsRow("Notifications", Icons.Filled.Notifications, ArcaneRed, onClick = { nav.navigate(SettingsRoutes.NOTIFICATIONS) }, trailing = { ChevronTrailing() })
            }
            if (isAdmin) {
                item(key = "users") {
                    SettingsRow("Users", Icons.Filled.Group, ArcaneBlue, onClick = { nav.navigate(SettingsRoutes.USERS) }, trailing = { ChevronTrailing() })
                }
                item(key = "api-keys") {
                    SettingsRow("API Keys", Icons.Filled.VpnKey, ArcaneYellow, onClick = { nav.navigate(SettingsRoutes.API_KEYS) }, trailing = { ChevronTrailing() })
                }
                item(key = "webhooks") {
                    SettingsRow("Webhooks", Icons.Filled.Webhook, ArcaneGreen, onClick = { nav.navigate(SettingsRoutes.WEBHOOKS) }, trailing = { ChevronTrailing() })
                }
                item(key = "authentication") {
                    SettingsRow("Authentication", Icons.Filled.Lock, ArcaneBlue, onClick = { nav.navigate(SettingsRoutes.AUTHENTICATION) }, trailing = { ChevronTrailing() })
                }
                item(key = "builds") {
                    SettingsRow("Builds", Icons.Filled.Settings, ArcaneGray, onClick = { nav.navigate(SettingsRoutes.BUILDS) }, trailing = { ChevronTrailing() })
                }
                item(key = "system") {
                    SettingsRow("System Settings", Icons.Filled.Dns, ArcaneGray, onClick = { nav.navigate(SettingsRoutes.SYSTEM) }, trailing = { ChevronTrailing() })
                }
                if (supportsV2) {
                    item(key = "roles") {
                        SettingsRow("Roles", Icons.Filled.AdminPanelSettings, ArcanePurple, onClick = { nav.navigate(SettingsRoutes.ROLES) }, trailing = { ChevronTrailing() })
                    }
                    item(key = "oidc-mappings") {
                        SettingsRow("OIDC Role Mappings", Icons.Filled.Group, ArcaneIndigo, onClick = { nav.navigate(SettingsRoutes.OIDC_MAPPINGS) }, trailing = { ChevronTrailing() })
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
    if (showChangeServerConfirm) {
        ConfirmDialog(
            title = "Change Server?",
            message = "You'll be signed out and asked for a new server URL.",
            confirmLabel = "Change Server",
            onConfirm = { manager.logout() },
            onDismiss = { showChangeServerConfirm = false },
        )
    }
}
