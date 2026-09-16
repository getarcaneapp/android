package app.getarcane.android.core

import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.user.isGlobalAdmin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal credential-free binding that lets a process restarted offline locate only the cache for
 * the still-bound credential origin. It deliberately retains read permissions, never mutation
 * permissions or user/server display identity.
 */
@Serializable
internal data class OfflineReadSession(
    val schemaVersion: Int = OfflineReadSessionStore.SCHEMA_VERSION,
    val generatedAtEpochMs: Long,
    val serverBindingHash: String,
    val credentialOriginHash: String,
    val cacheScope: ReadCacheScope,
    val capabilityMode: String,
    val supportsPost26MobileFeatures: Boolean,
    val supportsProjectWorkspaceContract: Boolean,
    val supportsContainerReliabilityActions: Boolean,
    val readPermissionsByEnvironment: Map<String, List<String>>,
)

internal class OfflineReadSessionStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    private val file = File(directory, FILE_NAME)
    private val lock = Any()

    init {
        directory.mkdirs()
    }

    fun publish(
        scope: ReadCacheScope,
        user: User,
        capabilities: ServerCapabilities,
        supportsPost26MobileFeatures: Boolean,
        supportsProjectWorkspaceContract: Boolean,
        supportsContainerReliabilityActions: Boolean,
    ): Boolean = synchronized(lock) {
        val effectivePermissions = user.permissionsByEnv
            ?: if (user.isGlobalAdmin) mapOf(User.GLOBAL_PERMISSIONS_KEY to READ_ONLY_PERMISSIONS) else emptyMap()
        val readPermissions = effectivePermissions.entries
            .sortedBy { it.key }
            .take(MAXIMUM_ENVIRONMENTS)
            .associate { (environment, permissions) ->
                boundedUtf8(environment, MAXIMUM_STRING_BYTES) to permissions
                    .flatMap { permission ->
                        if (permission == "*") READ_ONLY_PERMISSIONS else listOf(permission)
                    }
                    .filter(::isReadPermission)
                    .distinct()
                    .sorted()
                    .take(MAXIMUM_PERMISSIONS)
                    .map { boundedUtf8(it, MAXIMUM_STRING_BYTES) }
            }
            .filterKeys(String::isNotEmpty)
        writeAtomic(
            OfflineReadSession(
                generatedAtEpochMs = now(),
                serverBindingHash = scope.serverBindingHash,
                credentialOriginHash = scope.credentialOriginHash,
                cacheScope = scope,
                capabilityMode = capabilities.mode.name,
                supportsPost26MobileFeatures = supportsPost26MobileFeatures,
                supportsProjectWorkspaceContract = supportsProjectWorkspaceContract,
                supportsContainerReliabilityActions = supportsContainerReliabilityActions,
                readPermissionsByEnvironment = readPermissions,
            ),
        )
    }

    fun load(serverIdentity: String, credentialOrigin: String?): OfflineReadSession? = synchronized(lock) {
        if (credentialOrigin != serverIdentity) {
            file.delete()
            return null
        }
        if (!file.isFile || file.length() !in 1..MAXIMUM_BYTES.toLong()) {
            return null
        }
        val session = runCatching {
            json.decodeFromString(OfflineReadSession.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
        val expectedServer = sha256(serverIdentity)
        val expectedCredential = sha256("credential\u0000$credentialOrigin")
        session?.takeIf {
            it.isValid() &&
                it.serverBindingHash == expectedServer &&
                it.credentialOriginHash == expectedCredential &&
                now() - it.generatedAtEpochMs in 0..MAXIMUM_AGE_MILLIS
        } ?: run {
            file.delete()
            null
        }
    }

    fun clear() = synchronized(lock) {
        file.delete()
        directory.listFiles().orEmpty().forEach(File::delete)
    }

    private fun writeAtomic(session: OfflineReadSession): Boolean {
        val bytes = json.encodeToString(OfflineReadSession.serializer(), session).encodeToByteArray()
        if (bytes.size > MAXIMUM_BYTES) return false
        directory.mkdirs()
        val temporary = File.createTempFile(".offline-session-", ".tmp", directory)
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            temporary.renameTo(file)
        } finally {
            temporary.delete()
        }
    }

    private fun OfflineReadSession.isValid(): Boolean =
        schemaVersion == SCHEMA_VERSION &&
            generatedAtEpochMs >= 0 &&
            capabilityMode in ServerCapabilities.Mode.entries.map { it.name } &&
            listOf(
                serverBindingHash,
                credentialOriginHash,
                cacheScope.serverBindingHash,
                cacheScope.credentialOriginHash,
                cacheScope.accountBindingHash,
                cacheScope.permissionContextHash,
            ).all(::isLowerSha256) &&
            serverBindingHash == cacheScope.serverBindingHash &&
            credentialOriginHash == cacheScope.credentialOriginHash &&
            readPermissionsByEnvironment.size <= MAXIMUM_ENVIRONMENTS &&
            readPermissionsByEnvironment.all { (environment, permissions) ->
                environment.encodeToByteArray().size <= MAXIMUM_STRING_BYTES &&
                    permissions.size <= MAXIMUM_PERMISSIONS &&
                    permissions.all { permission ->
                        permission.encodeToByteArray().size <= MAXIMUM_STRING_BYTES && isReadPermission(permission)
                    }
            }

    companion object {
        const val DIRECTORY = "arcane_offline_read_session_v1"
        const val SCHEMA_VERSION = 1
        private const val FILE_NAME = "session-v1.json"
        private const val MAXIMUM_BYTES = 64 * 1024
        private const val MAXIMUM_ENVIRONMENTS = 64
        private const val MAXIMUM_PERMISSIONS = 64
        private const val MAXIMUM_STRING_BYTES = 256
        private const val MAXIMUM_AGE_MILLIS = 7 * 24 * 60 * 60_000L
    }
}

internal fun OfflineReadSession.asReadOnlyUser(): User = User(
    id = cacheScope.accountBindingHash,
    username = "Offline",
    roles = emptyList(),
    permissionsByEnv = readPermissionsByEnvironment,
    serverIsGlobalAdmin = false,
)

private fun isReadPermission(permission: String): Boolean =
    permission.endsWith(":read") || permission.endsWith(":list")

private val READ_ONLY_PERMISSIONS = listOf(
    "dashboard:read",
    "environments:list",
    "environments:read",
    "containers:list",
    "containers:read",
    "projects:list",
    "projects:read",
    "images:list",
    "images:read",
    "volumes:list",
    "volumes:read",
    "networks:list",
    "networks:read",
    "activities:list",
    "activities:read",
)

private fun isLowerSha256(value: String): Boolean = value.length == 64 && value.all {
    it in '0'..'9' || it in 'a'..'f'
}

private fun boundedUtf8(value: String, maximumBytes: Int): String {
    val bytes = value.encodeToByteArray()
    return if (bytes.size <= maximumBytes) value
    else bytes.copyOf(maximumBytes).decodeToString().trimEnd('\uFFFD')
}
