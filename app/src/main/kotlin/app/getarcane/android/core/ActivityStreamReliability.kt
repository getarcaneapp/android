package app.getarcane.android.core

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration.Companion.milliseconds

internal enum class ActivityFailureKind { InitialLoad, EnvironmentStream, Connection }

internal data class ActivitySourceFailure(
    val sourceId: String,
    val sourceName: String,
    val message: String,
    val kind: ActivityFailureKind,
)

internal class ActivitySourceFailureRegistry {
    private val failures = LinkedHashMap<String, ActivitySourceFailure>()

    fun record(failure: ActivitySourceFailure): List<ActivitySourceFailure> {
        failures[failure.sourceId] = failure
        return snapshot()
    }

    fun recover(sourceId: String): List<ActivitySourceFailure> {
        failures.remove(sourceId)
        return snapshot()
    }

    fun retain(activeEnvironmentIds: Set<String>): List<ActivitySourceFailure> {
        failures.keys
            .filter { it != ACTIVITY_STREAM_SOURCE_ID && it !in activeEnvironmentIds }
            .forEach(failures::remove)
        return snapshot()
    }

    fun clear(): List<ActivitySourceFailure> {
        failures.clear()
        return emptyList()
    }

    fun allEnvironmentSourcesFailed(environmentIds: Collection<String>): Boolean =
        environmentIds.isNotEmpty() && environmentIds.all(failures::containsKey)

    fun isEmpty(): Boolean = failures.isEmpty()

    fun snapshot(): List<ActivitySourceFailure> = failures.values.toList()
}

internal fun activityReconnectDelayMillis(failedAttempt: Int): Long? = when (failedAttempt) {
    0 -> 1_000L
    1 -> 2_000L
    2 -> 4_000L
    else -> null
}

@OptIn(FlowPreview::class)
internal fun <T> Flow<T>.withActivityHeartbeatTimeout(timeoutMillis: Long): Flow<T> =
    timeout(timeoutMillis.milliseconds)

internal const val ACTIVITY_HEARTBEAT_TIMEOUT_MILLIS = 45_000L
internal const val ACTIVITY_STREAM_SOURCE_ID = "__activity_stream__"
