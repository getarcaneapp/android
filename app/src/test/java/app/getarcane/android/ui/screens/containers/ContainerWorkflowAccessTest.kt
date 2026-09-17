package app.getarcane.android.ui.screens.containers

import app.getarcane.sdk.models.user.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerWorkflowAccessTest {
    @Test
    fun accessTracksSelectedEnvironmentAndAuthorizationLoss() {
        val user = User(
            id = "u1",
            username = "operator",
            permissionsByEnv = mapOf(
                "edge-a" to listOf(
                    "containers:create",
                    "containers:edit",
                    "containers:read",
                    "images:commit",
                    "projects:create",
                ),
                "edge-b" to listOf("containers:read"),
            ),
        )
        val allowed = containerWorkflowAccess(user, "edge-a", false, true, true)
        val switched = containerWorkflowAccess(user, "edge-b", false, true, true)
        val refreshed = containerWorkflowAccess(
            user.copy(permissionsByEnv = mapOf("edge-a" to listOf("containers:read"))),
            "edge-a",
            false,
            true,
            true,
        )

        assertTrue(allowed.canCreate && allowed.canEdit && allowed.canCommit && allowed.canCompose)
        assertFalse(switched.canCreate || switched.canEdit || switched.canCommit || switched.canCompose)
        assertFalse(refreshed.canCreate || refreshed.canEdit || refreshed.canCommit || refreshed.canCompose)
    }

    @Test
    fun oldServerOfflineAndHiddenProjectSurfaceFailClosedForMutations() {
        val admin = User(id = "admin", username = "admin", roles = listOf("admin"))
        val oldServer = containerWorkflowAccess(admin, "0", false, false, true)
        assertFalse(oldServer.canCreate || oldServer.canEdit || oldServer.canCommit || oldServer.canCompose)
        assertFalse(containerWorkflowAccess(admin, "0", true, true, true).canCreate)
        assertFalse(containerWorkflowAccess(admin, "0", false, true, false).canCompose)
    }
}
