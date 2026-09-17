package app.getarcane.android.core

import app.getarcane.android.nav.AppTab
import app.getarcane.sdk.models.role.Permission
import app.getarcane.sdk.models.role.PermissionsManifest
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.hasPermission
import app.getarcane.sdk.models.user.isGlobalAdmin

internal fun canAccessTab(
    tab: AppTab,
    user: User,
    supportsV2: Boolean,
    manifest: PermissionsManifest?,
    environmentId: String,
    allowLegacyFallback: Boolean = true,
): Boolean {
    if (tab.requiresV2 && !supportsV2) return false
    if (supportsV2 && manifest?.accessSurfaces?.isNotEmpty() == true && tab.accessSurfaceIds.isNotEmpty()) {
        return tab.accessSurfaceIds.any { manifest.canAccessSurface(it, user, environmentId) }
    }
    if (supportsV2 && tab.accessSurfaceIds.isNotEmpty() && !allowLegacyFallback) return false
    if (tab == AppTab.Variables && user.permissionsByEnv != null) {
        return user.hasPermission(Permission.Variables.READ)
    }
    return !tab.requiresAdmin || user.isGlobalAdmin
}
