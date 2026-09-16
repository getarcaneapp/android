package app.getarcane.android.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.getarcane.android.R
import app.getarcane.android.ui.theme.ArcaneBlue
import app.getarcane.android.ui.theme.ArcaneCyan
import app.getarcane.android.ui.theme.ArcaneGray
import app.getarcane.android.ui.theme.ArcaneGreen
import app.getarcane.android.ui.theme.ArcaneIndigo
import app.getarcane.android.ui.theme.ArcaneMint
import app.getarcane.android.ui.theme.ArcaneOrange
import app.getarcane.android.ui.theme.ArcanePink
import app.getarcane.android.ui.theme.ArcanePurple
import app.getarcane.android.ui.theme.ArcaneRed
import app.getarcane.android.ui.theme.ArcaneTeal
import app.getarcane.android.ui.theme.ArcaneYellow

enum class TabSection(val title: String, @get:StringRes val titleRes: Int) {
    Management("Management", R.string.nav_management_section),
    Resources("Resources", R.string.nav_resources_section),
    Swarm("Swarm", R.string.nav_swarm_section),
    Administration("Administration", R.string.nav_administration_section),
}

/** The full tab registry. Port of iOS `AppTab` (title/icon/color/section/gating/env-scoping). */
enum class AppTab(
    val id: String,
    val title: String,
    val tabBarTitle: String,
    @get:StringRes val titleRes: Int,
    @get:StringRes val tabBarTitleRes: Int,
    val icon: ImageVector,
    val color: Color,
    val section: TabSection,
    val requiresAdmin: Boolean = false,
    val requiresV2: Boolean = false,
    val isEnvironmentScoped: Boolean = false,
) {
    Dashboard("dashboard", "Dashboard", "Dashboard", R.string.nav_dashboard, R.string.nav_dashboard, Icons.Filled.SpaceDashboard, ArcaneBlue, TabSection.Management, isEnvironmentScoped = true),
    Projects("projects", "Projects", "Projects", R.string.nav_projects, R.string.nav_projects, Icons.Filled.FolderSpecial, ArcaneBlue, TabSection.Management, isEnvironmentScoped = true),
    ContainerRegistries("containerRegistries", "Container Registries", "Registries", R.string.nav_container_registries, R.string.nav_container_registries_short, Icons.Filled.Cloud, ArcanePurple, TabSection.Management, requiresAdmin = true),
    TemplateRegistries("templateRegistries", "Template Registries", "Templates", R.string.nav_template_registries, R.string.nav_template_registries_short, Icons.Filled.Layers, ArcaneIndigo, TabSection.Management, requiresAdmin = true),
    GitRepositories("gitRepositories", "Git Repositories", "Git Repos", R.string.nav_git_repositories, R.string.nav_git_repositories_short, Icons.Filled.Source, ArcaneIndigo, TabSection.Management, requiresAdmin = true),
    GitOps("gitOps", "GitOps", "GitOps", R.string.nav_gitops, R.string.nav_gitops, Icons.Filled.Sync, ArcaneIndigo, TabSection.Management, requiresAdmin = true, isEnvironmentScoped = true),

    Containers("containers", "Containers", "Containers", R.string.nav_containers, R.string.nav_containers, Icons.Filled.Inventory2, ArcaneBlue, TabSection.Resources, isEnvironmentScoped = true),
    Images("images", "Images", "Images", R.string.nav_images, R.string.nav_images, Icons.Filled.Layers, ArcaneBlue, TabSection.Resources, isEnvironmentScoped = true),
    Builds("builds", "Builds", "Builds", R.string.nav_builds, R.string.nav_builds, Icons.Filled.Build, ArcaneOrange, TabSection.Resources, requiresAdmin = true),
    Updates("updates", "Updates", "Updates", R.string.nav_updates, R.string.nav_updates, Icons.Filled.Autorenew, ArcaneGreen, TabSection.Resources),
    Networks("networks", "Networks", "Networks", R.string.nav_networks, R.string.nav_networks, Icons.Filled.Lan, ArcaneTeal, TabSection.Resources, isEnvironmentScoped = true),
    Ports("ports", "Ports", "Ports", R.string.nav_ports, R.string.nav_ports, Icons.Filled.SettingsEthernet, ArcaneCyan, TabSection.Resources, isEnvironmentScoped = true),
    Volumes("volumes", "Volumes", "Volumes", R.string.nav_volumes, R.string.nav_volumes, Icons.Filled.Storage, ArcaneOrange, TabSection.Resources, isEnvironmentScoped = true),
    Jobs("jobs", "Jobs", "Jobs", R.string.nav_jobs, R.string.nav_jobs, Icons.Filled.Schedule, ArcanePink, TabSection.Resources, requiresAdmin = true, isEnvironmentScoped = true),
    Activities("activities", "Activities", "Activity", R.string.nav_activities, R.string.nav_activities_short, Icons.Filled.History, ArcaneOrange, TabSection.Resources, requiresV2 = true),

    Swarm("swarm", "Swarm", "Swarm", R.string.nav_swarm_section, R.string.nav_swarm_section, Icons.Filled.Hub, ArcaneMint, TabSection.Swarm, requiresAdmin = true),

    Events("events", "Events", "Events", R.string.nav_events, R.string.nav_events, Icons.Filled.History, ArcaneRed, TabSection.Administration),
    Variables("variables", "Global Variables", "Variables", R.string.nav_variables, R.string.nav_variables_short, Icons.Filled.Sync, ArcaneTeal, TabSection.Administration, requiresV2 = true),
    Users("users", "Users", "Users", R.string.nav_users, R.string.nav_users, Icons.Filled.Groups, ArcaneBlue, TabSection.Administration, requiresAdmin = true),
    ApiKeys("apiKeys", "API Keys", "API Keys", R.string.nav_api_keys, R.string.nav_api_keys, Icons.Filled.VpnKey, ArcaneYellow, TabSection.Administration, requiresAdmin = true),
    Notifications("notifications", "Notifications", "Notifications", R.string.nav_notifications, R.string.nav_notifications, Icons.Filled.Notifications, ArcaneRed, TabSection.Administration, requiresAdmin = true),
    Webhooks("webhooks", "Webhooks", "Webhooks", R.string.nav_webhooks, R.string.nav_webhooks, Icons.Filled.Webhook, ArcaneGreen, TabSection.Administration, requiresAdmin = true),
    Authentication("authentication", "Authentication", "Auth", R.string.nav_authentication, R.string.nav_authentication_short, Icons.Filled.Lock, ArcaneBlue, TabSection.Administration, requiresAdmin = true),
    Roles("roles", "Roles", "Roles", R.string.nav_roles, R.string.nav_roles, Icons.Filled.AdminPanelSettings, ArcanePurple, TabSection.Administration, requiresAdmin = true, requiresV2 = true),
    OidcRoleMappings("oidcRoleMappings", "OIDC Role Mappings", "OIDC Roles", R.string.nav_oidc_role_mappings, R.string.nav_oidc_role_mappings_short, Icons.Filled.Groups, ArcaneIndigo, TabSection.Administration, requiresAdmin = true, requiresV2 = true),
    SystemSettings("systemSettings", "System Settings", "System", R.string.nav_system_settings, R.string.nav_system_settings_short, Icons.Filled.Dns, ArcaneGray, TabSection.Administration, requiresAdmin = true),
    ;

    val canPinToBottomBar: Boolean
        get() = when (this) {
            Dashboard,
            Containers,
            Images,
            Projects,
            Volumes,
            Networks,
            Ports,
            Updates,
            Activities,
            Events,
            Swarm -> true
            GitRepositories,
            GitOps,
            ContainerRegistries,
            TemplateRegistries,
            Builds,
            Jobs,
            Users,
            ApiKeys,
            Notifications,
            Webhooks,
            SystemSettings,
            Authentication,
            Roles,
            OidcRoleMappings -> false
            Variables -> false
        }

    fun isAvailableForBottomBar(isAdmin: Boolean, supportsV2: Boolean): Boolean =
        canPinToBottomBar &&
            (!requiresAdmin || isAdmin) &&
            (!requiresV2 || supportsV2)

    companion object {
        val defaults: List<AppTab> = listOf(Dashboard, Containers, Images, Projects)
        fun byId(id: String): AppTab? = entries.firstOrNull { it.id == id }
    }
}
