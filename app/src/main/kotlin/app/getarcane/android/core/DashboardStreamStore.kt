package app.getarcane.android.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.streaming.ndjsonFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

data class DashboardEnvironmentStreamState(
    val id: String,
    val name: String,
    val snapshot: DashboardSnapshot? = null,
    val hasLoaded: Boolean = false,
    val loading: Boolean = true,
    val streamError: Boolean = false,
    val errorMessage: String? = null,
    val errorCode: DashboardStreamErrorCode? = null,
)

data class DashboardStreamAggregateCounts(
    val runningContainers: Int = 0,
    val stoppedContainers: Int = 0,
    val totalContainers: Int = 0,
    val totalImages: Int = 0,
)

interface DashboardStreamClient {
    fun stream(): Flow<DashboardStreamEvent>
    suspend fun snapshot(environmentId: String): DashboardSnapshot
}

class ArcaneDashboardStreamClient(private val client: ArcaneClient) : DashboardStreamClient {
    override fun stream(): Flow<DashboardStreamEvent> =
        client.transport.ndjsonFlow(
            path = "dashboard/stream",
            deserializer = DashboardStreamEvent.serializer(),
            method = "GET",
        )

    override suspend fun snapshot(environmentId: String): DashboardSnapshot {
        val text = client.transport.rawRequestText(
            path = client.rest.environmentPath(EnvironmentId(environmentId), "dashboard"),
        )
        return parseDashboardSnapshotEnvelope(text)
    }
}

class DashboardStreamStore(
    private val scope: CoroutineScope,
    private val maxReconnectAttempts: Int = MAX_RECONNECT_ATTEMPTS,
    private val maxReconnectDelayMillis: Long = MAX_RECONNECT_DELAY_MILLIS,
    private val stableConnectionMillis: Long = STABLE_CONNECTION_MILLIS,
    private val idleRetryMillis: Long = IDLE_RETRY_MILLIS,
) {
    var statesByEnvironmentId by mutableStateOf<Map<String, DashboardEnvironmentStreamState>>(emptyMap())
        private set
    var connected by mutableStateOf(false); private set
    var streamFailed by mutableStateOf(false); private set
    var streamUnsupported by mutableStateOf(false); private set
    var isStreaming by mutableStateOf(false); private set

    private var client: DashboardStreamClient? = null
    private var streamJob: Job? = null
    private var shouldRun = false
    private var generation = 0

    val aggregate: DashboardStreamAggregateCounts?
        get() {
            val states = statesByEnvironmentId.values
            if (states.isEmpty()) return null
            var loadedCount = 0
            var counts = DashboardStreamAggregateCounts()
            for (state in states) {
                if (state.streamError) return null
                val snapshot = state.snapshot
                if (state.hasLoaded && snapshot != null) {
                    loadedCount += 1
                    counts = counts.copy(
                        runningContainers = counts.runningContainers + snapshot.containers.counts.runningContainers,
                        stoppedContainers = counts.stoppedContainers + snapshot.containers.counts.stoppedContainers,
                        totalContainers = counts.totalContainers + snapshot.containers.counts.totalContainers,
                        totalImages = counts.totalImages + snapshot.imageUsageCounts.totalImages,
                    )
                } else if (!state.streamError) {
                    return null
                }
            }
            return counts.takeIf { loadedCount > 0 }
        }

    fun configure(client: DashboardStreamClient?) {
        if (this.client === client) return
        stop()
        this.client = client
        statesByEnvironmentId = if (client == null) {
            emptyMap()
        } else {
            statesByEnvironmentId.mapValues { (_, state) ->
                state.copy(
                    snapshot = null,
                    hasLoaded = false,
                    loading = true,
                    streamError = false,
                    errorMessage = null,
                    errorCode = null,
                )
            }
        }
        streamUnsupported = false
        streamFailed = false
    }

    fun start() {
        shouldRun = true
        val activeClient = client ?: return
        if (streamUnsupported || streamJob != null) return
        generation += 1
        val currentGeneration = generation
        isStreaming = true
        streamJob = scope.launch {
            runStreamLoop(activeClient, currentGeneration)
        }
    }

    fun stop() {
        shouldRun = false
        generation += 1
        streamJob?.cancel()
        streamJob = null
        connected = false
        streamFailed = false
        isStreaming = false
    }

    fun retry() {
        if (!shouldRun) return
        stop()
        clearAllStreamErrors()
        start()
    }

    fun reconnect() = retry()

    fun reconcile(environments: List<Environment>) {
        val enabled = environments.filter { it.enabled }
        val targetIds = enabled.map { it.id }.toSet()
        var next = statesByEnvironmentId.filterKeys { it in targetIds }

        for (environment in enabled) {
            val displayName = environment.name?.trim()?.takeIf { it.isNotEmpty() } ?: environment.id
            val existing = next[environment.id]
            next = if (existing == null) {
                next + (environment.id to DashboardEnvironmentStreamState(id = environment.id, name = displayName))
            } else if (existing.name != displayName) {
                next + (environment.id to existing.copy(name = displayName))
            } else {
                next
            }
        }
        val newIds = next.keys - statesByEnvironmentId.keys
        statesByEnvironmentId = next

        if (streamJob != null) {
            val currentGeneration = generation
            newIds.forEach { id ->
                scope.launch { refreshEnvironment(id, currentGeneration) }
            }
        }
    }

    suspend fun refresh() {
        val ids = statesByEnvironmentId.keys.toList()
        val currentGeneration = generation
        coroutineScope {
            ids.map { id ->
                async { refreshEnvironment(id, currentGeneration) }
            }.awaitAll()
        }
    }

    internal fun applyForTest(event: DashboardStreamEvent) {
        apply(event)
    }

    private suspend fun runStreamLoop(activeClient: DashboardStreamClient, currentGeneration: Int) {
        var attempt = 0
        try {
            while (currentGeneration == generation) {
                val connectedAt = System.currentTimeMillis()
                var receivedFirstEvent = false
                try {
                    activeClient.stream().collect { event ->
                        if (currentGeneration != generation) throw CancellationException()
                        if (!receivedFirstEvent) {
                            receivedFirstEvent = true
                            connected = true
                            streamFailed = false
                            clearAllStreamErrors()
                        }
                        apply(event)
                    }
                } catch (e: ArcaneError.NotFound) {
                    streamUnsupported = true
                    connected = false
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Transport drops and schema mismatch lines reconnect below.
                }

                connected = false
                if (currentGeneration != generation) return
                if (receivedFirstEvent && System.currentTimeMillis() - connectedAt >= stableConnectionMillis) {
                    attempt = 0
                }
                val delayMillis = if (attempt >= maxReconnectAttempts) {
                    streamFailed = true
                    idleRetryMillis
                } else {
                    val backoff = 1_000L * 2.0.pow(attempt.toDouble()).toLong()
                    attempt += 1
                    min(backoff, maxReconnectDelayMillis)
                }
                delay(delayMillis)
            }
        } finally {
            if (currentGeneration == generation) {
                streamJob = null
                connected = false
                isStreaming = false
            }
        }
    }

    private fun apply(event: DashboardStreamEvent) {
        when (event.eventType) {
            DashboardStreamEventType.Snapshot ->
                event.snapshot?.let { applySnapshot(it, event.resolvedEnvironmentId) }
            DashboardStreamEventType.Error ->
                applyError(event.error, event.streamErrorCode, event.resolvedEnvironmentId)
            DashboardStreamEventType.Pending,
            DashboardStreamEventType.Heartbeat,
            DashboardStreamEventType.Unknown,
            -> Unit
        }
    }

    private fun applySnapshot(snapshot: DashboardSnapshot, environmentId: String) {
        val state = statesByEnvironmentId[environmentId] ?: return
        statesByEnvironmentId = statesByEnvironmentId + (environmentId to state.copy(
            snapshot = snapshot,
            hasLoaded = true,
            loading = false,
            streamError = false,
            errorMessage = null,
            errorCode = null,
        ))
    }

    private fun applyError(message: String?, code: DashboardStreamErrorCode?, environmentId: String) {
        val state = statesByEnvironmentId[environmentId] ?: return
        statesByEnvironmentId = statesByEnvironmentId + (environmentId to state.copy(
            loading = false,
            streamError = true,
            errorMessage = message,
            errorCode = code,
        ))
    }

    private fun clearAllStreamErrors() {
        statesByEnvironmentId = statesByEnvironmentId.mapValues { (_, state) ->
            if (state.streamError) state.copy(streamError = false, errorMessage = null, errorCode = null) else state
        }
    }

    private suspend fun refreshEnvironment(environmentId: String, currentGeneration: Int) {
        val activeClient = client ?: return
        try {
            val snapshot = activeClient.snapshot(environmentId)
            if (currentGeneration == generation && statesByEnvironmentId.containsKey(environmentId)) {
                applySnapshot(snapshot, environmentId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (currentGeneration == generation && statesByEnvironmentId.containsKey(environmentId)) {
                applyError(friendlyErrorMessage(e), null, environmentId)
            }
        }
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val MAX_RECONNECT_DELAY_MILLIS = 15_000L
        const val STABLE_CONNECTION_MILLIS = 5_000L
        const val IDLE_RETRY_MILLIS = 30_000L
    }
}
