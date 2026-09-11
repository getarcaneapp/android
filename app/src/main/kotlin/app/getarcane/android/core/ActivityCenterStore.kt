package app.getarcane.android.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.getarcane.android.ui.screens.activities.ActivityStatusFilter
import app.getarcane.android.ui.screens.activities.displayTitle
import app.getarcane.android.ui.screens.activities.isCancellable
import app.getarcane.android.ui.screens.activities.sortTime
import app.getarcane.android.ui.screens.activities.sourceEnvironmentKey
import app.getarcane.android.ui.screens.activities.subtitle
import app.getarcane.sdk.ArcaneClient
import app.getarcane.sdk.EnvironmentId
import app.getarcane.sdk.models.activity.Activity
import app.getarcane.sdk.models.activity.ActivityMessage
import app.getarcane.sdk.models.activity.ActivityStreamEvent
import app.getarcane.sdk.models.activity.ActivityStreamEventType
import app.getarcane.sdk.models.base.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compose-observable state holder for the Activity Center. Port of the iOS `ActivityCenterStore`.
 *
 * Fans out initial listing across every environment, then consumes Arcane's one multiplexed activity
 * stream. Buckets are capped at [PAGE_SIZE] and merged without letting a failed source erase healthy data.
 *
 * @param scope a [CoroutineScope] used to run the live streams. Pass the composition scope
 *   (`rememberCoroutineScope()`); cancelling it stops all streams.
 */
class ActivityCenterStore(private val scope: CoroutineScope) {

    var activities by mutableStateOf<List<Activity>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    var isLoadingMore by mutableStateOf(false); private set
    var isStreaming by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var streamErrorMessage by mutableStateOf<String?>(null); private set
    var environmentIds: List<String> by mutableStateOf(emptyList()); private set
    internal var sourceFailures by mutableStateOf<List<ActivitySourceFailure>>(emptyList()); private set
    internal var retryingSourceIds by mutableStateOf<Set<String>>(emptySet()); private set

    // Filter inputs (observable; mutated by the UI).
    var searchText by mutableStateOf("")
    var statusFilter by mutableStateOf(ActivityStatusFilter.ALL)
    var typeFilter by mutableStateOf("")
    var resourceFilter by mutableStateOf("")

    private var client: ArcaneClient? = null
    private var limit = PAGE_SIZE
    private val activityBuckets = LinkedHashMap<String, List<Activity>>()
    private val environmentNames = HashMap<String, String>()
    private var streamJob: Job? = null
    private val sourceFailureRegistry = ActivitySourceFailureRegistry()
    private var loadJob: Job? = null
    private var clientGeneration = 0L
    private var loadGeneration = 0L
    private var streamGeneration = 0L
    private var streamingRequested = false

    val filteredActivities: List<Activity>
        get() {
            val trimmed = searchText.trim()
            return activities.filter { activity ->
                statusFilter.matches(activity) &&
                    (typeFilter.isEmpty() || activity.type.wire == typeFilter) &&
                    (resourceFilter.isEmpty() || activity.resourceType == resourceFilter) &&
                    (trimmed.isEmpty() || matchesSearch(activity, trimmed))
            }
        }

    val availableTypes: List<String>
        get() = sortedUnique(activities.map { it.type.wire })

    val availableResourceTypes: List<String>
        get() = sortedUnique(activities.mapNotNull { it.resourceType })

    fun configure(client: ArcaneClient?) {
        if (this.client === client) return
        clientGeneration++
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
        stopStream()
        this.client = client
        activities = emptyList()
        activityBuckets.clear()
        environmentNames.clear()
        environmentIds = emptyList()
        limit = PAGE_SIZE
        hasMore = false
        isLoading = false
        isLoadingMore = false
        errorMessage = null
        streamErrorMessage = null
        sourceFailures = sourceFailureRegistry.clear()
        retryingSourceIds = emptySet()
    }

    /** Fan out `listPaginated` across all environments, bucket per env, merge + sort. */
    suspend fun load(reset: Boolean = true, refresh: Boolean = false) {
        val client = client ?: return
        val owningJob = currentCoroutineContext()[Job]
        loadJob?.takeIf { it !== owningJob }?.cancel()
        loadJob = owningJob
        val expectedClientGeneration = clientGeneration
        val operationGeneration = ++loadGeneration
        val pageLimit = if (reset) PAGE_SIZE else limit
        if (activities.isEmpty() || refresh) isLoading = true
        errorMessage = null
        try {
            val environments = resolveEnvironments(client)
            val results: List<SourceLoadResult> = coroutineScope {
                environments.map { environment ->
                    async {
                        runSuspendCatching {
                            client.activities.listPaginated(
                                envId = environment.id,
                                order = SortOrder.DESCENDING,
                                start = 0,
                                limit = pageLimit,
                            ).data
                        }.fold(
                            onSuccess = { SourceLoadResult(environment, it, null) },
                            onFailure = { SourceLoadResult(environment, null, friendlyErrorMessage(it)) },
                        )
                    }
                }.awaitAll()
            }

            val currentEnvironmentIds = environments.mapTo(HashSet()) { it.id.rawValue }
            if (!isCurrentLoad(client, expectedClientGeneration, operationGeneration)) return
            val buckets = LinkedHashMap(activityBuckets.filterKeys { it in currentEnvironmentIds })
            var anyHasMore = false
            var failures = 0
            val nextFailures = ArrayList<ActivitySourceFailure>()
            for ((environment, data, failureMessage) in results) {
                if (data == null) {
                    failures += 1
                    nextFailures +=
                        ActivitySourceFailure(
                            sourceId = environment.id.rawValue,
                            sourceName = environment.name,
                            message = failureMessage ?: "Activity source unavailable.",
                            kind = ActivityFailureKind.InitialLoad,
                        )
                    continue
                }
                val normalized = data.map { normalize(it, environment) }
                buckets[environment.id.rawValue] = sortActivities(normalized)
                if (data.size >= pageLimit) anyHasMore = true
            }

            limit = pageLimit
            environmentIds = environments.map { it.id.rawValue }
            environmentNames.clear()
            environments.forEach { environmentNames[it.id.rawValue] = it.name }
            sourceFailures = sourceFailureRegistry.retain(currentEnvironmentIds)
            results.filter { it.data != null }.forEach { sourceFailures = sourceFailureRegistry.recover(it.environment.id.rawValue) }
            nextFailures.forEach { sourceFailures = sourceFailureRegistry.record(it) }
            activityBuckets.clear()
            activityBuckets.putAll(buckets)
            hasMore = anyHasMore
            rebuildActivities()
            if (failures > 0) {
                streamErrorMessage = "Some activity sources could not load. Healthy environments are preserved."
                if (sourceFailureRegistry.allEnvironmentSourcesFailed(environmentIds) && activities.isEmpty()) {
                    errorMessage = "No activity source could be loaded. Retry an environment below."
                }
            } else if (sourceFailureRegistry.isEmpty()) {
                streamErrorMessage = null
            }
            if (streamingRequested) restartStreams()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isCurrentLoad(client, expectedClientGeneration, operationGeneration) && activities.isEmpty()) {
                errorMessage = friendlyErrorMessage(e)
            }
        } finally {
            if (isCurrentLoad(client, expectedClientGeneration, operationGeneration)) isLoading = false
            if (loadJob === owningJob) loadJob = null
        }
    }

    suspend fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        val previousLimit = limit
        val requestedLimit = previousLimit + PAGE_SIZE
        try {
            limit = requestedLimit
            load(reset = false)
        } catch (e: CancellationException) {
            if (limit == requestedLimit) limit = previousLimit
            throw e
        } finally {
            isLoadingMore = false
        }
    }

    /** One owned coroutine collecting Arcane's multiplexed activity stream. */
    fun startStream() {
        streamingRequested = true
        restartStreams()
    }

    private fun restartStreams() {
        val client = client ?: return
        cancelStreamJobs()
        streamErrorMessage = null
        if (environmentIds.isEmpty()) return

        val expectedClientGeneration = clientGeneration
        val expectedStreamGeneration = streamGeneration
        isStreaming = true
        streamJob = scope.launch {
            consumeStream(client, expectedClientGeneration, expectedStreamGeneration)
        }
    }

    fun stopStream() {
        streamingRequested = false
        cancelStreamJobs()
    }

    private fun cancelStreamJobs() {
        streamGeneration++
        streamJob?.cancel()
        streamJob = null
        isStreaming = false
    }

    /** Request cancellation of a running/queued activity. Returns true on success. */
    suspend fun cancel(activity: Activity, requestedBy: String?): Boolean {
        val client = client ?: return false
        val envId = EnvironmentId(activity.sourceEnvironmentKey)
        return try {
            val updated = client.activities.cancel(
                envId = envId,
                activityId = activity.id,
                requestedBy = requestedBy,
            )
            upsert(normalize(updated, environmentFor(envId)))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errorMessage = friendlyErrorMessage(e)
            false
        }
    }

    /** Clear completed history across the [allowedEnvironmentIds] subset. Returns deleted/failed counts. */
    suspend fun clearHistory(allowedEnvironmentIds: Set<String>): ClearHistoryResult? {
        val client = client ?: return null
        val targets = environmentIds.filter { it in allowedEnvironmentIds }
        if (targets.isEmpty()) return null

        val results: List<Pair<Long?, Boolean>> = coroutineScope {
            targets.map { id ->
                async {
                    runSuspendCatching {
                        client.activities.clearHistory(envId = EnvironmentId(id)).deleted
                    }.fold(
                        onSuccess = { it to true },
                        onFailure = { null to false },
                    )
                }
            }.awaitAll()
        }

        var deleted = 0L
        var failed = 0
        for ((count, ok) in results) {
            if (ok && count != null) deleted += count else failed += 1
        }

        load(refresh = true)
        return ClearHistoryResult(deleted = deleted, failed = failed)
    }

    /** Retry just one failed source; healthy environment buckets and the aggregate stream stay intact. */
    internal fun retrySource(sourceId: String) {
        if (sourceId in retryingSourceIds) return
        if (sourceId == ACTIVITY_STREAM_SOURCE_ID) {
            clearSourceFailure(sourceId)
            restartStreams()
            return
        }
        val client = client ?: return
        val environment = environmentFor(EnvironmentId(sourceId))
        val expectedClientGeneration = clientGeneration
        retryingSourceIds = retryingSourceIds + sourceId
        scope.launch {
            try {
                val data = client.activities.listPaginated(
                    envId = environment.id,
                    order = SortOrder.DESCENDING,
                    start = 0,
                    limit = limit,
                ).data
                if (this@ActivityCenterStore.client === client && clientGeneration == expectedClientGeneration) {
                    activityBuckets[sourceId] = sortActivities(data.map { normalize(it, environment) })
                    clearSourceFailure(sourceId)
                    errorMessage = null
                    rebuildActivities()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (this@ActivityCenterStore.client === client && clientGeneration == expectedClientGeneration) {
                    setSourceFailure(
                        ActivitySourceFailure(sourceId, environment.name, friendlyErrorMessage(e), ActivityFailureKind.InitialLoad),
                    )
                }
            } finally {
                if (this@ActivityCenterStore.client === client && clientGeneration == expectedClientGeneration) {
                    retryingSourceIds = retryingSourceIds - sourceId
                }
            }
        }
    }

    private suspend fun consumeStream(
        client: ArcaneClient,
        expectedClientGeneration: Long,
        expectedStreamGeneration: Long,
    ) {
        val owningJob = currentCoroutineContext()[Job]
        var failedAttempt = 0
        try {
            while (isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration)) {
                var stableHeartbeatCount = 0
                try {
                    client.activities.stream(limit = PAGE_SIZE)
                        .withActivityHeartbeatTimeout(ACTIVITY_HEARTBEAT_TIMEOUT_MILLIS)
                        .collect { event ->
                            if (isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration)) {
                                clearSourceFailure(ACTIVITY_STREAM_SOURCE_ID)
                                apply(event)
                                if (event.type == ActivityStreamEventType.HEARTBEAT) stableHeartbeatCount++
                            }
                        }
                    throw IllegalStateException("Activity stream ended.")
                } catch (e: TimeoutCancellationException) {
                    if (!isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration)) return
                    setSourceFailure(
                        ActivitySourceFailure(
                            ACTIVITY_STREAM_SOURCE_ID,
                            "Activity stream",
                            "No heartbeat was received for 45 seconds.",
                            ActivityFailureKind.Connection,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (!isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration)) return
                    setSourceFailure(
                        ActivitySourceFailure(
                            ACTIVITY_STREAM_SOURCE_ID,
                            "Activity stream",
                            friendlyErrorMessage(e),
                            ActivityFailureKind.Connection,
                        ),
                    )
                }
                if (stableHeartbeatCount >= 3) failedAttempt = 0
                val retryDelay = activityReconnectDelayMillis(failedAttempt++) ?: break
                delay(retryDelay)
            }
            if (isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration)) {
                streamErrorMessage = "Live activity updates stopped after three reconnect attempts."
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            if (isCurrentStream(client, expectedClientGeneration, expectedStreamGeneration) && streamJob === owningJob) {
                streamJob = null
                isStreaming = false
            }
        }
    }

    private fun setSourceFailure(failure: ActivitySourceFailure) {
        sourceFailures = sourceFailureRegistry.record(failure)
    }

    private fun clearSourceFailure(sourceId: String) {
        sourceFailures = sourceFailureRegistry.recover(sourceId)
        if (sourceFailureRegistry.isEmpty()) streamErrorMessage = null
    }

    private fun apply(event: ActivityStreamEvent) {
        val eventEnvironmentId = event.environmentId
        val environment = eventEnvironmentId?.let { environmentFor(EnvironmentId(it)) }
        when (event.type) {
            ActivityStreamEventType.SNAPSHOT -> if (environment != null) {
                replaceSnapshot(event.activities, environment)
                clearSourceFailure(environment.id.rawValue)
            }
            ActivityStreamEventType.ACTIVITY -> event.activity?.let { activity ->
                val source = environment ?: environmentFor(EnvironmentId(activity.sourceEnvironmentKey))
                upsert(normalize(activity, source))
                clearSourceFailure(source.id.rawValue)
            }
            ActivityStreamEventType.MESSAGE -> event.message?.let { applyMessage(it) }
            ActivityStreamEventType.MISSED ->
                streamErrorMessage = "Some activity updates were missed. Pull to refresh."
            ActivityStreamEventType.ERROR -> if (environment != null) {
                setSourceFailure(
                    ActivitySourceFailure(
                        environment.id.rawValue,
                        environment.name,
                        event.error ?: "Live activity source unavailable.",
                        ActivityFailureKind.EnvironmentStream,
                    ),
                )
            } else {
                streamErrorMessage = event.error ?: "Live activity updates reported an error."
            }
            ActivityStreamEventType.HEARTBEAT,
            ActivityStreamEventType.UNKNOWN,
            -> Unit
        }
    }

    private fun isCurrentLoad(client: ArcaneClient, clientGeneration: Long, loadGeneration: Long): Boolean =
        this.client === client &&
            this.clientGeneration == clientGeneration &&
            this.loadGeneration == loadGeneration

    private fun isCurrentStream(client: ArcaneClient, clientGeneration: Long, streamGeneration: Long): Boolean =
        this.client === client &&
            this.clientGeneration == clientGeneration &&
            this.streamGeneration == streamGeneration

    private fun replaceSnapshot(snapshot: List<Activity>, environment: ActivityEnvironment) {
        val normalized = snapshot.map { normalize(it, environment) }
        activityBuckets[environment.id.rawValue] = sortActivities(normalized)
        hasMore = activityBuckets.values.any { it.size >= PAGE_SIZE }
        rebuildActivities()
    }

    private fun upsert(activity: Activity) {
        val key = activity.sourceEnvironmentKey
        val bucket = activityBuckets[key].orEmpty().toMutableList()
        val index = bucket.indexOfFirst { it.id == activity.id }
        if (index >= 0) {
            bucket[index] = activity
        } else {
            bucket.add(0, activity)
        }
        activityBuckets[key] = sortActivities(bucket).take(PAGE_SIZE)
        rebuildActivities()
    }

    private fun applyMessage(message: ActivityMessage) {
        for ((key, bucket) in activityBuckets) {
            val index = bucket.indexOfFirst { it.id == message.activityId }
            if (index < 0) continue
            val updated = bucket.toMutableList()
            updated[index] = updated[index].copy(
                latestMessage = message.message,
                updatedAt = message.createdAt,
            )
            activityBuckets[key] = updated
            break
        }
        rebuildActivities()
    }

    private fun rebuildActivities() {
        activities = sortActivities(activityBuckets.values.flatten())
    }

    private fun matchesSearch(activity: Activity, search: String): Boolean {
        fun String?.has() = this?.contains(search, ignoreCase = true) ?: false
        return activity.displayTitle.contains(search, ignoreCase = true) ||
            activity.subtitle.contains(search, ignoreCase = true) ||
            activity.latestMessage.contains(search, ignoreCase = true) ||
            activity.type.wire.contains(search, ignoreCase = true) ||
            activity.status.wire.contains(search, ignoreCase = true) ||
            activity.sourceEnvironmentName.has()
    }

    private fun sortedUnique(values: List<String>): List<String> =
        values.filter { it.isNotEmpty() }.toSortedSet(String.CASE_INSENSITIVE_ORDER).toList()

    private suspend fun resolveEnvironments(client: ArcaneClient): List<ActivityEnvironment> {
        val items = loadCompleteEnvironments { query -> client.environments.list(query) }
        return items.map { environment ->
            ActivityEnvironment(
                id = EnvironmentId(environment.id),
                name = environment.name?.trim()?.takeIf { it.isNotEmpty() } ?: environment.id,
            )
        }
    }

    private fun environmentFor(id: EnvironmentId): ActivityEnvironment =
        ActivityEnvironment(id, environmentNames[id.rawValue] ?: id.rawValue)

    private fun normalize(activity: Activity, environment: ActivityEnvironment): Activity {
        var normalized = activity
        if (normalized.sourceEnvironmentId?.trim().isNullOrEmpty()) {
            normalized = normalized.copy(sourceEnvironmentId = environment.id.rawValue)
        }
        if (normalized.sourceEnvironmentName?.trim().isNullOrEmpty()) {
            normalized = normalized.copy(sourceEnvironmentName = environment.name)
        }
        return normalized
    }

    private fun sortActivities(items: List<Activity>): List<Activity> =
        items.sortedWith(
            compareByDescending<Activity> { it.isCancellable }
                .thenByDescending { it.sortTime }
                .thenBy { it.id },
        )

    private data class ActivityEnvironment(val id: EnvironmentId, val name: String)

    private data class SourceLoadResult(
        val environment: ActivityEnvironment,
        val data: List<Activity>?,
        val failureMessage: String?,
    )

    data class ClearHistoryResult(val deleted: Long, val failed: Int)

    companion object {
        const val PAGE_SIZE = 50
    }
}
