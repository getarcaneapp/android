package app.getarcane.android.core

import app.getarcane.sdk.ServerCapabilities
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.user.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/** Read families approved for persistence. Mutation, stream, log, and credential payloads are absent. */
enum class ReadResource {
    DASHBOARD,
    ENVIRONMENTS,
    CONTAINERS,
    PROJECTS,
    IMAGES,
    VOLUMES,
    NETWORKS,
}

data class ReadCachePolicy(
    val revalidateAfterMillis: Long,
    val maximumStaleMillis: Long,
) {
    companion object {
        val Dashboard = ReadCachePolicy(60_000, 24 * 60 * 60_000L)
        val Environments = ReadCachePolicy(5 * 60_000, 7 * 24 * 60 * 60_000L)
        val Containers = ReadCachePolicy(30_000, 24 * 60 * 60_000L)
        val Projects = ReadCachePolicy(5 * 60_000, 3 * 24 * 60 * 60_000L)
        val Images = ReadCachePolicy(5 * 60_000, 3 * 24 * 60 * 60_000L)
        val Volumes = ReadCachePolicy(5 * 60_000, 3 * 24 * 60 * 60_000L)
        val Networks = ReadCachePolicy(5 * 60_000, 3 * 24 * 60 * 60_000L)
    }
}

/**
 * All identity fields are one-way hashes. The canonical origin and credential binding participate
 * in the hashes but are never copied into cache metadata or a future external snapshot.
 */
@Serializable
data class ReadCacheScope(
    val serverBindingHash: String,
    val credentialOriginHash: String,
    val accountBindingHash: String,
    val permissionContextHash: String,
)

data class ReadCacheRequest(
    val resource: ReadResource,
    val environmentId: String,
    val requestIdentity: String,
    val policy: ReadCachePolicy,
)

sealed interface ResilientRead<out T> {
    data class Stale<T>(
        val value: T,
        val storedAtEpochMs: Long,
        val refreshError: String? = null,
    ) : ResilientRead<T>

    data class Fresh<T>(val value: T, val receivedAtEpochMs: Long) : ResilientRead<T>
    data class Failure(val message: String) : ResilientRead<Nothing>
}

@Serializable
private data class CacheKey(
    val schemaVersion: Int = ResilientReadCache.CACHE_SCHEMA_VERSION,
    val scope: ReadCacheScope,
    val environmentId: String,
    val resource: ReadResource,
    val requestIdentity: String,
)

@Serializable
private data class CacheEnvelope(
    val schemaVersion: Int = ResilientReadCache.CACHE_SCHEMA_VERSION,
    val key: CacheKey,
    val storedAtEpochMs: Long,
    val payload: JsonElement,
)

private data class MemoryEntry(
    val envelope: CacheEnvelope,
    val encodedBytes: Int,
)

private data class InFlightRead(
    val token: Long,
    val generation: Long,
    val deferred: Deferred<JsonElement>,
)

/**
 * Bounded application read cache. It owns bytes and request coalescing, never an authenticated
 * client. Callers supply an SDK loader and serializer for one of the explicitly approved families.
 */
class ResilientReadCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val diskByteLimit: Long = DEFAULT_DISK_BYTES,
    private val diskEntryLimit: Int = DEFAULT_DISK_ENTRIES,
    private val memoryByteLimit: Int = DEFAULT_MEMORY_BYTES,
    private val memoryEntryLimit: Int = DEFAULT_MEMORY_ENTRIES,
    private val maximumEntryBytes: Int = DEFAULT_ENTRY_BYTES,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val diskMutex = Mutex()
    private val memory = LinkedHashMap<CacheKey, MemoryEntry>(16, 0.75f, true)
    private var memoryBytes = 0
    private val inFlight = mutableMapOf<CacheKey, InFlightRead>()
    private val latestFetchToken = mutableMapOf<CacheKey, Long>()
    private var nextFetchToken = 0L
    private val invalidationGeneration = AtomicLong()
    @Volatile private var cacheReadable = true

    init {
        directory.mkdirs()
        ioScope.launch { trimDisk() }
    }

    fun <T> observe(
        scope: ReadCacheScope,
        request: ReadCacheRequest,
        serializer: KSerializer<T>,
        forceRefresh: Boolean = false,
        fetch: suspend () -> T,
    ): Flow<ResilientRead<T>> = flow {
        require(request.environmentId.encodeToByteArray().size <= MAX_KEY_PART_BYTES)
        require(request.requestIdentity.encodeToByteArray().size <= MAX_KEY_PART_BYTES)
        val key = CacheKey(
            scope = scope,
            environmentId = request.environmentId,
            resource = request.resource,
            requestIdentity = request.requestIdentity,
        )
        val generation = invalidationGeneration.get()
        val cached = if (!cacheReadable) null else read(key, request.policy.maximumStaleMillis)
        if (cached != null && !forceRefresh) {
            if (!cacheReadable || invalidationGeneration.get() != generation) return@flow
            val decoded = runCatching { json.decodeFromJsonElement(serializer, cached.payload) }.getOrNull()
            if (decoded != null) {
                emit(ResilientRead.Stale(decoded, cached.storedAtEpochMs))
                if (now() - cached.storedAtEpochMs < request.policy.revalidateAfterMillis) return@flow
            } else {
                delete(key)
            }
        }

        try {
            val result = coalesced(key, generation) {
                json.encodeToJsonElement(serializer, fetch())
            } ?: return@flow
            val (token, payload) = result
            if (!isCurrent(key, token, generation)) return@flow
            val storedAt = now()
            if (!writeIfCurrent(
                    CacheEnvelope(key = key, storedAtEpochMs = storedAt, payload = payload),
                    token,
                    generation,
                )
            ) return@flow
            emit(ResilientRead.Fresh(json.decodeFromJsonElement(serializer, payload), storedAt))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error is ArcaneError.Forbidden || error is ArcaneError.Unauthorized) {
                delete(key)
                emit(ResilientRead.Failure("You no longer have permission to view this data."))
                return@flow
            }
            val fallback = cached?.let {
                runCatching { json.decodeFromJsonElement(serializer, it.payload) }.getOrNull()
            }
            if (fallback != null) {
                emit(
                    ResilientRead.Stale(
                        value = fallback,
                        storedAtEpochMs = cached.storedAtEpochMs,
                        refreshError = friendlyErrorMessage(error),
                    ),
                )
            } else {
                emit(ResilientRead.Failure(friendlyErrorMessage(error)))
            }
        }
    }

    suspend fun invalidate(
        scope: ReadCacheScope? = null,
        resources: Set<ReadResource> = ReadResource.entries.toSet(),
        environmentId: String? = null,
    ) {
        invalidationGeneration.incrementAndGet()
        mutex.withLock {
            val keys = memory.keys.filter { key ->
                key.matches(scope, resources, environmentId)
            }
            keys.forEach(::removeMemoryLocked)
            latestFetchToken.keys.removeAll { it.matches(scope, resources, environmentId) }
            inFlight.keys.removeAll { it.matches(scope, resources, environmentId) }
        }
        diskMutex.withLock {
            withContext(Dispatchers.IO) {
                directory.listFiles().orEmpty().filter { it.extension == ENTRY_EXTENSION }.forEach { file ->
                    val envelope = decodeEnvelope(file)
                    if (envelope == null || envelope.key.matches(scope, resources, environmentId)) file.delete()
                }
            }
        }
    }

    /** Security boundary: make all memory unreachable synchronously, then remove disk entries. */
    fun clearForSessionEnd() {
        cacheReadable = false
        invalidationGeneration.incrementAndGet()
        ioScope.launch { clearStorage(generationAlreadyAdvanced = true) }
    }

    suspend fun clear() {
        cacheReadable = false
        invalidationGeneration.incrementAndGet()
        clearStorage(generationAlreadyAdvanced = true)
    }

    private suspend fun clearStorage(generationAlreadyAdvanced: Boolean) {
        if (!generationAlreadyAdvanced) invalidationGeneration.incrementAndGet()
        mutex.withLock {
            memory.clear()
            memoryBytes = 0
            latestFetchToken.clear()
            inFlight.clear()
        }
        diskMutex.withLock {
            withContext(Dispatchers.IO) {
                directory.listFiles().orEmpty().forEach(File::delete)
            }
        }
        cacheReadable = true
    }

    suspend fun diskBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().sumOf { it.length().coerceAtLeast(0) }
    }

    internal suspend fun entryCount(): Int = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().count { it.extension == ENTRY_EXTENSION }
    }

    private suspend fun read(key: CacheKey, maximumStaleMillis: Long): CacheEnvelope? {
        val hot = mutex.withLock { memory[key]?.envelope }
        val envelope = hot ?: withContext(Dispatchers.IO) {
            decodeEnvelope(fileFor(key))
        } ?: return null
        val age = now() - envelope.storedAtEpochMs
        if (age < 0 || age > maximumStaleMillis || envelope.schemaVersion != CACHE_SCHEMA_VERSION || envelope.key != key) {
            delete(key)
            return null
        }
        if (hot == null) {
            val encodedSize = json.encodeToString(CacheEnvelope.serializer(), envelope).encodeToByteArray().size
            mutex.withLock { putMemoryLocked(key, MemoryEntry(envelope, encodedSize)) }
        }
        withContext(Dispatchers.IO) { fileFor(key).setLastModified(now()) }
        return envelope
    }

    private suspend fun writeIfCurrent(
        envelope: CacheEnvelope,
        token: Long,
        generation: Long,
    ): Boolean = writeAtomically(envelope) { isCurrent(envelope.key, token, generation) }

    private suspend fun writeAtomically(
        envelope: CacheEnvelope,
        stillCurrent: suspend () -> Boolean,
    ): Boolean {
        val bytes = json.encodeToString(CacheEnvelope.serializer(), envelope).encodeToByteArray()
        if (bytes.size > maximumEntryBytes) return false
        val temporary = withContext(Dispatchers.IO) {
            directory.mkdirs()
            File.createTempFile(".write-", ".tmp", directory).also { temporary ->
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
            }
        }
        return try {
            diskMutex.withLock {
                if (!stillCurrent()) return@withLock false
                withContext(Dispatchers.IO) {
                    check(temporary.renameTo(fileFor(envelope.key))) {
                        "Unable to atomically replace cache entry"
                    }
                    trimDiskOnIoThread()
                }
                if (!stillCurrent()) {
                    withContext(Dispatchers.IO) { fileFor(envelope.key).delete() }
                    false
                } else {
                    mutex.withLock {
                        putMemoryLocked(envelope.key, MemoryEntry(envelope, bytes.size))
                    }
                    true
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun delete(key: CacheKey) {
        mutex.withLock { removeMemoryLocked(key) }
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    private suspend fun coalesced(
        key: CacheKey,
        generation: Long,
        fetch: suspend () -> JsonElement,
    ): Pair<Long, JsonElement>? {
        val read = mutex.withLock {
            if (invalidationGeneration.get() != generation) return null
            inFlight[key]?.takeIf { it.generation == generation } ?: run {
                val token = ++nextFetchToken
                InFlightRead(token, generation, ioScope.async { fetch() }).also {
                    inFlight[key] = it
                    latestFetchToken[key] = token
                }
            }
        }
        return try {
            read.token to read.deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight[key] === read && read.deferred.isCompleted) inFlight.remove(key)
            }
        }
    }

    private suspend fun isCurrent(key: CacheKey, token: Long, generation: Long): Boolean =
        mutex.withLock {
            invalidationGeneration.get() == generation && latestFetchToken[key] == token
        }

    private fun CacheKey.matches(
        expectedScope: ReadCacheScope?,
        resources: Set<ReadResource>,
        expectedEnvironmentId: String?,
    ): Boolean =
        (expectedScope == null || scope == expectedScope) &&
            resource in resources &&
            (
                expectedEnvironmentId == null ||
                    environmentId == expectedEnvironmentId ||
                    resource == ReadResource.DASHBOARD ||
                    resource == ReadResource.ENVIRONMENTS
            )

    private fun putMemoryLocked(key: CacheKey, entry: MemoryEntry) {
        memory.remove(key)?.let { memoryBytes -= it.encodedBytes }
        memory[key] = entry
        memoryBytes += entry.encodedBytes
        while (memory.size > memoryEntryLimit || memoryBytes > memoryByteLimit) {
            val eldest = memory.entries.firstOrNull() ?: break
            removeMemoryLocked(eldest.key)
        }
    }

    private fun removeMemoryLocked(key: CacheKey) {
        memory.remove(key)?.let { memoryBytes -= it.encodedBytes }
    }

    private fun fileFor(key: CacheKey): File {
        val stableKey = json.encodeToString(CacheKey.serializer(), key)
        return File(directory, "${sha256(stableKey)}.$ENTRY_EXTENSION")
    }

    private fun decodeEnvelope(file: File): CacheEnvelope? {
        if (!file.isFile || file.length() !in 1..maximumEntryBytes.toLong()) return null
        return runCatching {
            json.decodeFromString(CacheEnvelope.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()?.takeIf { it.isStructurallyValid() }
    }

    private fun CacheEnvelope.isStructurallyValid(): Boolean =
        schemaVersion == CACHE_SCHEMA_VERSION &&
            key.schemaVersion == CACHE_SCHEMA_VERSION &&
            storedAtEpochMs >= 0 &&
            key.environmentId.encodeToByteArray().size <= MAX_KEY_PART_BYTES &&
            key.requestIdentity.encodeToByteArray().size <= MAX_KEY_PART_BYTES &&
            listOf(
                key.scope.serverBindingHash,
                key.scope.credentialOriginHash,
                key.scope.accountBindingHash,
                key.scope.permissionContextHash,
            ).all(::isLowerSha256)

    private suspend fun trimDisk() = withContext(Dispatchers.IO) { trimDiskOnIoThread() }

    private fun trimDiskOnIoThread() {
        val allFiles = directory.listFiles().orEmpty()
        // Same-directory temporary files are active atomic writes. Never let one concurrent writer
        // trim another writer's temp file; only abandoned temps older than one hour are reclaimed.
        allFiles.filter { it.extension != ENTRY_EXTENSION && now() - it.lastModified() > ABANDONED_TEMP_MILLIS }
            .forEach(File::delete)
        val files = allFiles.filter { file ->
            file.extension == ENTRY_EXTENSION && if (decodeEnvelope(file) == null) {
                file.delete()
                false
            } else {
                true
            }
        }
        var total = files.sumOf(File::length)
        val oldestFirst = files.sortedBy(File::lastModified).toMutableList()
        while (oldestFirst.size > diskEntryLimit || total > diskByteLimit) {
            val file = oldestFirst.removeFirstOrNull() ?: break
            total -= file.length()
            file.delete()
        }
    }

    companion object {
        const val CACHE_SCHEMA_VERSION = 1
        const val CACHE_DIRECTORY = "arcane_read_cache_v1"
        private const val ENTRY_EXTENSION = "arc"
        private const val MAX_KEY_PART_BYTES = 1_024
        private const val DEFAULT_DISK_BYTES = 24L * 1024 * 1024
        private const val DEFAULT_DISK_ENTRIES = 256
        private const val DEFAULT_MEMORY_BYTES = 4 * 1024 * 1024
        private const val DEFAULT_MEMORY_ENTRIES = 64
        private const val DEFAULT_ENTRY_BYTES = 2 * 1024 * 1024
        private const val ABANDONED_TEMP_MILLIS = 60 * 60_000L
    }
}

internal fun resilientReadScope(
    serverIdentity: String,
    user: User,
    capabilities: ServerCapabilities,
    supportsPost26MobileFeatures: Boolean,
    supportsProjectWorkspaceContract: Boolean,
    supportsContainerReliabilityActions: Boolean,
): ReadCacheScope {
    val permissionShape = buildString {
        append(capabilities.mode.name)
        append('\u0000').append(supportsPost26MobileFeatures)
        append('\u0000').append(supportsProjectWorkspaceContract)
        append('\u0000').append(supportsContainerReliabilityActions)
        append('\u0000').append(user.roles.sorted().joinToString("\u0001"))
        user.permissionsByEnv.orEmpty().toSortedMap().forEach { (environment, permissions) ->
            append('\u0000').append(environment)
            append('\u0002').append(permissions.sorted().joinToString("\u0001"))
        }
    }
    return ReadCacheScope(
        serverBindingHash = sha256(serverIdentity),
        credentialOriginHash = sha256("credential\u0000$serverIdentity"),
        accountBindingHash = sha256("$serverIdentity\u0000${user.id}"),
        permissionContextHash = sha256(permissionShape),
    )
}

internal fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

private fun isLowerSha256(value: String): Boolean = value.length == 64 && value.all {
    it in '0'..'9' || it in 'a'..'f'
}
