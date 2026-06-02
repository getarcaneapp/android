package app.getarcane.android.nav

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

enum class TabSection(val title: String) {
    Management("Management"),
    Resources("Resources"),
    Swarm("Swarm"),
    Administration("Administration"),
}

/** The full tab registry. Port of iOS `AppTab` (title/icon/color/section/gating/env-scoping). */
enum class AppTab(
    val id: String,
    val title: String,
    val tabBarTitle: String,
    val icon: ImageVector,
    val color: Color,
    val section: TabSection,
    val requiresAdmin: Boolean = false,
    val requiresV2: Boolean = false,
    val isEnvironmentScoped: Boolean = false,
) {
    Dashboard("dashboard", "Dashboard", "Dashboard", Icons.Filled.SpaceDashboard, ArcaneBlue, TabSection.Management),
    Projects("projects", "Projects", "Projects", Icons.Filled.FolderSpecial, ArcaneBlue, TabSection.Management, isEnvironmentScoped = true),
    ContainerRegistries("containerRegistries", "Container Registries", "Registries", Icons.Filled.Cloud, ArcanePurple, TabSection.Management),
    TemplateRegistries("templateRegistries", "Template Registries", "Templates", Icons.Filled.Layers, ArcaneIndigo, TabSection.Management),
    GitRepositories("gitRepositories", "Git Repositories", "Git Repos", Icons.Filled.Source, ArcaneIndigo, TabSection.Management),
    GitOps("gitOps", "GitOps", "GitOps", Icons.Filled.Sync, ArcaneIndigo, TabSection.Management, isEnvironmentScoped = true),

    Containers("containers", "Containers", "Containers", Icons.Filled.Inventory2, ArcaneBlue, TabSection.Resources, isEnvironmentScoped = true),
    Images("images", "Images", "Images", Icons.Filled.Layers, ArcanePurple, TabSection.Resources, isEnvironmentScoped = true),
    Builds("builds", "Builds", "Builds", Icons.Filled.Build, ArcaneOrange, TabSection.Resources, isEnvironmentScoped = true),
    Updates("updates", "Updates", "Updates", Icons.Filled.Autorenew, ArcaneGreen, TabSection.Resources, isEnvironmentScoped = true),
    Networks("networks", "Networks", "Networks", Icons.Filled.Lan, ArcaneTeal, TabSection.Resources, isEnvironmentScoped = true),
    Ports("ports", "Ports", "Ports", Icons.Filled.SettingsEthernet, ArcaneCyan, TabSection.Resources, isEnvironmentScoped = true),
    Volumes("volumes", "Volumes", "Volumes", Icons.Filled.Storage, ArcaneOrange, TabSection.Resources, isEnvironmentScoped = true),
    Jobs("jobs", "Jobs", "Jobs", Icons.Filled.Schedule, ArcanePink, TabSection.Resources, isEnvironmentScoped = true),

    Swarm("swarm", "Swarm", "Swarm", Icons.Filled.Hub, ArcaneMint, TabSection.Swarm, isEnvironmentScoped = true),

    Events("events", "Events", "Events", Icons.Filled.History, ArcaneRed, TabSection.Administration),
    Users("users", "Users", "Users", Icons.Filled.Groups, ArcaneBlue, TabSection.Administration, requiresAdmin = true),
    ApiKeys("apiKeys", "API Keys", "API Keys", Icons.Filled.VpnKey, ArcaneYellow, TabSection.Administration, requiresAdmin = true),
    Notifications("notifications", "Notifications", "Notifications", Icons.Filled.Notifications, ArcaneRed, TabSection.Administration, requiresAdmin = true),
    Webhooks("webhooks", "Webhooks", "Webhooks", Icons.Filled.Webhook, ArcaneGreen, TabSection.Administration, requiresAdmin = true),
    Authentication("authentication", "Authentication", "Auth", Icons.Filled.Lock, ArcaneBlue, TabSection.Administration, requiresAdmin = true),
    Roles("roles", "Roles", "Roles", Icons.Filled.AdminPanelSettings, ArcanePurple, TabSection.Administration, requiresAdmin = true, requiresV2 = true),
    OidcRoleMappings("oidcRoleMappings", "OIDC Role Mappings", "OIDC Roles", Icons.Filled.Groups, ArcaneIndigo, TabSection.Administration, requiresAdmin = true, requiresV2 = true),
    SystemSettings("systemSettings", "System Settings", "System", Icons.Filled.Dns, ArcaneGray, TabSection.Administration, requiresAdmin = true),
    ;

    companion object {
        val defaults: List<AppTab> = listOf(Dashboard, Containers, Images, Projects)
        fun byId(id: String): AppTab? = entries.firstOrNull { it.id == id }
    }
}
