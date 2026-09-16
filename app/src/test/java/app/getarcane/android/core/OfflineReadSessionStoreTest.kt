package app.getarcane.android.core

import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.hasPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineReadSessionStoreTest {
    private val origin = "https://arcane.invalid:443"
    private val scope = ReadCacheScope(
        serverBindingHash = sha256(origin),
        credentialOriginHash = sha256("credential\u0000$origin"),
        accountBindingHash = "a".repeat(64),
        permissionContextHash = "b".repeat(64),
    )

    @Test
    fun `offline binding is opaque and restores read permissions without mutation permissions`() {
        val directory = Files.createTempDirectory("offline-read-session").toFile()
        val store = OfflineReadSessionStore(directory, now = { 10 })
        assertTrue(
            store.publish(
                scope = scope,
                user = User(
                    id = "account-secret",
                    username = "username-secret",
                    email = "email-secret",
                    roles = listOf("admin"),
                    permissionsByEnv = mapOf("global" to listOf("*")),
                ),
                capabilities = ServerCapabilities(ServerCapabilities.Mode.RBAC),
                supportsPost26MobileFeatures = true,
                supportsProjectWorkspaceContract = true,
                supportsContainerReliabilityActions = true,
            ),
        )

        val bytes = directory.walkTopDown().filter(File::isFile).single().readText()
        listOf(origin, "account-secret", "username-secret", "email-secret", "containers:delete")
            .forEach { forbidden -> assertFalse(forbidden, bytes.contains(forbidden)) }
        val restored = requireNotNull(store.load(origin, origin))
        val user = restored.asReadOnlyUser()
        assertTrue(user.hasPermission("containers:list", "0"))
        assertFalse(user.hasPermission("containers:delete", "0"))
        assertEquals(scope, restored.cacheScope)
    }

    @Test
    fun `wrong credential corrupt old and expired bindings fail closed`() {
        var now = 10L
        val directory = Files.createTempDirectory("offline-read-session-invalid").toFile()
        val store = OfflineReadSessionStore(directory, now = { now })
        store.publish(
            scope,
            User("id", "user", permissionsByEnv = mapOf("global" to listOf("dashboard:read"))),
            ServerCapabilities(ServerCapabilities.Mode.RBAC),
            true,
            true,
            true,
        )
        assertNull(store.load(origin, "https://other.invalid"))

        store.publish(
            scope,
            User("id", "user", permissionsByEnv = mapOf("global" to listOf("dashboard:read"))),
            ServerCapabilities(ServerCapabilities.Mode.RBAC),
            true,
            true,
            true,
        )
        val file = directory.walkTopDown().filter(File::isFile).single()
        file.writeText(file.readText().replace("\"schemaVersion\":1", "\"schemaVersion\":0"))
        assertNull(store.load(origin, origin))

        store.publish(
            scope,
            User("id", "user", permissionsByEnv = mapOf("global" to listOf("dashboard:read"))),
            ServerCapabilities(ServerCapabilities.Mode.RBAC),
            true,
            true,
            true,
        )
        now = 8 * 24 * 60 * 60_000L
        assertNull(store.load(origin, origin))
    }
}
