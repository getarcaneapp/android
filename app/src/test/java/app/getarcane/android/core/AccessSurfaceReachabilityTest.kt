package app.getarcane.android.core

import app.getarcane.android.nav.AppTab
import app.getarcane.sdk.models.role.AccessSurface
import app.getarcane.sdk.models.role.AccessSurfaceAccessMode
import app.getarcane.sdk.models.role.AccessSurfaceKind
import app.getarcane.sdk.models.role.AccessSurfaceMatchMode
import app.getarcane.sdk.models.role.AccessSurfaceScopeMode
import app.getarcane.sdk.models.role.PermissionsManifest
import app.getarcane.sdk.models.user.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessSurfaceReachabilityTest {
    private val manifest = PermissionsManifest(
        accessSurfaces = listOf(
            AccessSurface(
                id = "route.containers",
                kind = AccessSurfaceKind.ROUTE,
                accessMode = AccessSurfaceAccessMode.PERMISSIONS,
                matchMode = AccessSurfaceMatchMode.ANY_OF,
                scopeMode = AccessSurfaceScopeMode.SELECTED_ENVIRONMENT_PLUS_GLOBAL,
                permissions = listOf("containers:list"),
            ),
            AccessSurface(
                id = "settings.category.users",
                kind = AccessSurfaceKind.SETTINGS_CATEGORY,
                accessMode = AccessSurfaceAccessMode.PERMISSIONS,
                matchMode = AccessSurfaceMatchMode.ANY_OF,
                scopeMode = AccessSurfaceScopeMode.GLOBAL_ONLY,
                permissions = listOf("users:list"),
            ),
        ),
    )

    @Test
    fun `manifest replaces broad admin reachability and follows selected environment`() {
        val user = User(
            id = "u1",
            username = "restricted",
            permissionsByEnv = mapOf("edge-a" to listOf("containers:list")),
        )

        assertTrue(canAccessTab(AppTab.Containers, user, true, manifest, "edge-a"))
        assertFalse(canAccessTab(AppTab.Containers, user, true, manifest, "edge-b"))
        assertFalse(canAccessTab(AppTab.Users, user, true, manifest, "edge-a"))
    }

    @Test
    fun `older servers retain admin fallback`() {
        val legacyAdmin = User(id = "admin", username = "admin", roles = listOf("admin"))
        val legacyUser = User(id = "viewer", username = "viewer")

        assertTrue(canAccessTab(AppTab.Users, legacyAdmin, false, null, "0"))
        assertFalse(canAccessTab(AppTab.Users, legacyUser, false, null, "0"))
        assertTrue(canAccessTab(AppTab.Containers, legacyUser, false, null, "0"))
    }

    @Test
    fun `unknown future surfaces fail closed without breaking unrelated fallback`() {
        val user = User(id = "u1", username = "user", permissionsByEnv = emptyMap())
        val unknownOnly = PermissionsManifest(
            accessSurfaces = listOf(
                AccessSurface(
                    id = "route.containers",
                    kind = AccessSurfaceKind("future"),
                    accessMode = AccessSurfaceAccessMode("future"),
                    matchMode = AccessSurfaceMatchMode("future"),
                    scopeMode = AccessSurfaceScopeMode("future"),
                ),
            ),
        )
        assertFalse(canAccessTab(AppTab.Containers, user, true, unknownOnly, "0"))
        assertTrue(canAccessTab(AppTab.Events, user, true, PermissionsManifest(), "0"))
    }

    @Test
    fun `manifest load failures fail closed instead of using legacy fallback`() {
        val admin = User(id = "admin", username = "admin", roles = listOf("admin"))

        assertFalse(
            canAccessTab(
                tab = AppTab.Containers,
                user = admin,
                supportsV2 = true,
                manifest = null,
                environmentId = "0",
                allowLegacyFallback = false,
            ),
        )
    }
}
