package app.getarcane.android.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

enum class SnapshotFreshness {
    FRESH,
    STALE,
    ERROR,
    SIGNED_OUT,
}

enum class SnapshotErrorCode {
    NONE,
    NETWORK_UNAVAILABLE,
    SERVER_ERROR,
    UNKNOWN,
}

@Serializable
data class StatusEnvironmentSnapshot(
    val environmentKey: String,
    val displayName: String,
    val online: Boolean,
    val runningContainers: Int,
    val totalContainers: Int,
    val images: Int,
    val updatesAvailable: Int,
)

/**
 * Credential-free projection for future widgets and system surfaces. It intentionally contains no
 * URL, username, account ID, environment ID, resource ID, mutation capability, or SDK object.
 */
@Serializable
data class StatusSnapshot(
    val schemaVersion: Int = StatusSnapshotStore.SNAPSHOT_SCHEMA_VERSION,
    val sourceVersion: Int,
    val generatedAtEpochMs: Long,
    val sourceUpdatedAtEpochMs: Long?,
    val freshness: SnapshotFreshness,
    val errorCode: SnapshotErrorCode = SnapshotErrorCode.NONE,
    /** One-way canonical server identity used only to bind safe authenticated app routes. */
    val serverBindingHash: String? = null,
    val scopeId: String? = null,
    val activeEnvironmentKey: String? = null,
    val totalRunningContainers: Int = 0,
    val totalContainers: Int = 0,
    val totalImages: Int = 0,
    val totalUpdates: Int = 0,
    val onlineEnvironments: Int = 0,
    val environments: List<StatusEnvironmentSnapshot> = emptyList(),
) {
    companion object {
        fun signedOut(sourceVersion: Int, now: Long): StatusSnapshot = StatusSnapshot(
            sourceVersion = sourceVersion,
            generatedAtEpochMs = now,
            sourceUpdatedAtEpochMs = null,
            freshness = SnapshotFreshness.SIGNED_OUT,
        )
    }
}

sealed interface StatusSnapshotRead {
    data class Available(val snapshot: StatusSnapshot) : StatusSnapshotRead
    data object Missing : StatusSnapshotRead
    data object CorruptOrUnsupported : StatusSnapshotRead
}

/** Atomic, size-bounded storage only; this class never creates or receives an authenticated client. */
class StatusSnapshotStore(
    private val directory: File,
    private val sourceVersion: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val maximumBytes: Int = MAXIMUM_SNAPSHOT_BYTES,
    private val onMaterialChange: () -> Unit = {},
) {
    private val lock = Any()
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }
    private val file = File(directory, SNAPSHOT_FILE)
    private var activeScopeId: String? = null

    init {
        directory.mkdirs()
    }

    fun publish(snapshot: StatusSnapshot): Boolean = synchronized(lock) {
        val bounded = snapshot.bounded()
        if (!bounded.isValid()) return false
        if (bounded.freshness == SnapshotFreshness.SIGNED_OUT) {
            activeScopeId = null
        } else if (bounded.scopeId != activeScopeId) {
            return false
        }
        val current = readFile(deleteInvalid = false)
        // A delayed writer from an older refresh/session must never replace newer state. This also
        // makes a synchronously published signed-out snapshot an effective session fence.
        if (
            current != null &&
            current.freshness != SnapshotFreshness.SIGNED_OUT &&
            bounded.generatedAtEpochMs <= current.generatedAtEpochMs
        ) return false
        writeAtomicAndNotify(bounded, current)
    }

    /** Logout and server/account changes synchronously replace any authenticated projection. */
    fun publishSignedOut(): Boolean = synchronized(lock) {
        activeScopeId = null
        val current = readFile(deleteInvalid = false)
        writeAtomicAndNotify(StatusSnapshot.signedOut(sourceVersion, now()), current)
    }

    /** Authorizes writers for one manager-validated session scope. Readers never activate writes. */
    fun activateScope(scopeId: String): StatusSnapshotRead = synchronized(lock) {
        require(isSha256(scopeId))
        activeScopeId = scopeId
        loadLocked(scopeId)
    }

    fun load(expectedScopeId: String? = null): StatusSnapshotRead = synchronized(lock) {
        loadLocked(expectedScopeId)
    }

    /** Reads only the already-sanitized projection for widgets; it cannot authorize a writer. */
    fun loadForExternalConsumer(): StatusSnapshotRead = synchronized(lock) {
        if (!file.exists()) return@synchronized StatusSnapshotRead.Missing
        val snapshot = readFile(deleteInvalid = true)
            ?: return@synchronized StatusSnapshotRead.CorruptOrUnsupported
        StatusSnapshotRead.Available(snapshot)
    }

    private fun loadLocked(expectedScopeId: String?): StatusSnapshotRead {
        if (!file.exists()) return StatusSnapshotRead.Missing
        val snapshot = readFile(deleteInvalid = true) ?: return StatusSnapshotRead.CorruptOrUnsupported
        if (
            snapshot.freshness != SnapshotFreshness.SIGNED_OUT &&
            (expectedScopeId == null || snapshot.scopeId != expectedScopeId)
        ) {
            val signedOut = StatusSnapshot.signedOut(sourceVersion, now())
            writeAtomicAndNotify(signedOut, snapshot)
            return StatusSnapshotRead.Available(signedOut)
        }
        return StatusSnapshotRead.Available(snapshot)
    }

    /** Removes all prior projection data when no server is configured and refreshes consumers. */
    fun clearForUnconfigured(): Boolean = synchronized(lock) {
        activeScopeId = null
        val deleted = file.delete()
        // A launcher may still hold RemoteViews produced before app data was cleared, even when
        // the projection file is already absent. Re-render every explicit SETUP boundary so that
        // stale signed-in content cannot survive behind that missing file.
        onMaterialChange()
        deleted
    }

    fun fileForDebugInspection(): File = file

    private fun readFile(deleteInvalid: Boolean): StatusSnapshot? {
        val length = file.length()
        if (!file.isFile || length !in 1..maximumBytes.toLong()) {
            if (deleteInvalid) file.delete()
            return null
        }
        val snapshot = runCatching {
            json.decodeFromString(StatusSnapshot.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
        val valid = snapshot?.takeIf { it.isValid() }
        if (valid == null && deleteInvalid) file.delete()
        return valid
    }

    private fun writeAtomic(snapshot: StatusSnapshot): Boolean {
        val bytes = json.encodeToString(StatusSnapshot.serializer(), snapshot).encodeToByteArray()
        if (bytes.size > maximumBytes) {
            file.delete()
            return false
        }
        directory.mkdirs()
        val temporary = File.createTempFile(".snapshot-", ".tmp", directory)
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

    private fun StatusSnapshot.bounded(): StatusSnapshot {
        val generated = generatedAtEpochMs.coerceAtLeast(0)
        if (freshness == SnapshotFreshness.SIGNED_OUT) {
            return StatusSnapshot.signedOut(this@StatusSnapshotStore.sourceVersion, generated)
        }
        return copy(
            schemaVersion = SNAPSHOT_SCHEMA_VERSION,
            sourceVersion = this@StatusSnapshotStore.sourceVersion.coerceAtLeast(0),
            generatedAtEpochMs = generated,
            sourceUpdatedAtEpochMs = sourceUpdatedAtEpochMs?.coerceIn(0, generated),
            serverBindingHash = serverBindingHash?.takeIf(::isSha256),
            scopeId = scopeId?.takeIf(::isSha256),
            activeEnvironmentKey = activeEnvironmentKey?.takeIf(::isSha256),
            totalRunningContainers = boundedCount(totalRunningContainers),
            totalContainers = boundedCount(totalContainers),
            totalImages = boundedCount(totalImages),
            totalUpdates = boundedCount(totalUpdates),
            onlineEnvironments = boundedCount(onlineEnvironments),
            environments = environments.take(MAXIMUM_ENVIRONMENTS).map { environment ->
                environment.copy(
                    environmentKey = environment.environmentKey.takeIf(::isSha256).orEmpty(),
                    displayName = boundedUtf8(environment.displayName, MAXIMUM_NAME_BYTES),
                    runningContainers = boundedCount(environment.runningContainers),
                    totalContainers = boundedCount(environment.totalContainers),
                    images = boundedCount(environment.images),
                    updatesAvailable = boundedCount(environment.updatesAvailable),
                )
            }.filter { it.environmentKey.isNotEmpty() },
        )
    }

    private fun StatusSnapshot.isValid(): Boolean =
        schemaVersion == SNAPSHOT_SCHEMA_VERSION &&
            sourceVersion >= 0 &&
            generatedAtEpochMs >= 0 &&
            (sourceUpdatedAtEpochMs == null || sourceUpdatedAtEpochMs in 0..generatedAtEpochMs) &&
            activeEnvironmentKey?.let(::isSha256) != false &&
            serverBindingHash?.let(::isSha256) != false &&
            listOf(
                totalRunningContainers,
                totalContainers,
                totalImages,
                totalUpdates,
                onlineEnvironments,
            ).all(::isBoundedCount) &&
            environments.size <= MAXIMUM_ENVIRONMENTS &&
            environments.all { environment ->
                isSha256(environment.environmentKey) &&
                    environment.displayName.encodeToByteArray().size <= MAXIMUM_NAME_BYTES &&
                    listOf(
                        environment.runningContainers,
                        environment.totalContainers,
                        environment.images,
                        environment.updatesAvailable,
                    ).all(::isBoundedCount)
            } &&
            when (freshness) {
                SnapshotFreshness.SIGNED_OUT ->
                    serverBindingHash == null &&
                    scopeId == null &&
                        activeEnvironmentKey == null &&
                        sourceUpdatedAtEpochMs == null &&
                        errorCode == SnapshotErrorCode.NONE &&
                        totalRunningContainers == 0 &&
                        totalContainers == 0 &&
                        totalImages == 0 &&
                        totalUpdates == 0 &&
                        onlineEnvironments == 0 &&
                        environments.isEmpty()
                SnapshotFreshness.ERROR ->
                    serverBindingHash?.let(::isSha256) == true &&
                        scopeId?.let(::isSha256) == true &&
                        errorCode != SnapshotErrorCode.NONE
                SnapshotFreshness.FRESH,
                SnapshotFreshness.STALE,
                -> serverBindingHash?.let(::isSha256) == true &&
                    scopeId?.let(::isSha256) == true &&
                    errorCode == SnapshotErrorCode.NONE
            }

    private fun writeAtomicAndNotify(snapshot: StatusSnapshot, previous: StatusSnapshot?): Boolean {
        val written = writeAtomic(snapshot)
        if (written && previous?.materiallyEquals(snapshot) != true) onMaterialChange()
        return written
    }

    private fun StatusSnapshot.materiallyEquals(other: StatusSnapshot): Boolean =
        copy(generatedAtEpochMs = 0, sourceUpdatedAtEpochMs = null) ==
            other.copy(generatedAtEpochMs = 0, sourceUpdatedAtEpochMs = null)

    companion object {
        const val SNAPSHOT_SCHEMA_VERSION = 2
        const val SNAPSHOT_DIRECTORY = "arcane_status_snapshots"
        const val SNAPSHOT_FILE = "status-v1.json"
        const val MAXIMUM_SNAPSHOT_BYTES = 64 * 1024
        const val MAXIMUM_ENVIRONMENTS = 10
        const val MAXIMUM_NAME_BYTES = 128
        const val MAXIMUM_COUNT = 1_000_000_000
    }
}

internal fun statusSnapshotScopeId(scope: ReadCacheScope): String = sha256(
    listOf(
        scope.serverBindingHash,
        scope.credentialOriginHash,
        scope.accountBindingHash,
        scope.permissionContextHash,
    ).joinToString("\u0000"),
)

internal fun opaqueEnvironmentKey(scopeId: String, environmentId: String): String =
    sha256("$scopeId\u0000$environmentId")

private fun isSha256(value: String): Boolean = value.length == 64 && value.all {
    it in '0'..'9' || it in 'a'..'f'
}

private fun boundedCount(value: Int): Int = value.coerceIn(0, StatusSnapshotStore.MAXIMUM_COUNT)

private fun isBoundedCount(value: Int): Boolean = value in 0..StatusSnapshotStore.MAXIMUM_COUNT

private fun boundedUtf8(value: String, maximumBytes: Int): String {
    val bytes = value.encodeToByteArray()
    return if (bytes.size <= maximumBytes) value
    else bytes.copyOf(maximumBytes).decodeToString().trimEnd('\uFFFD')
}
