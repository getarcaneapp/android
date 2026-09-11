package app.getarcane.android.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivityStreamReliabilityTest {
    @Test
    fun reconnectBudgetIsFiniteAndExponential() {
        assertEquals(1_000L, activityReconnectDelayMillis(0))
        assertEquals(2_000L, activityReconnectDelayMillis(1))
        assertEquals(4_000L, activityReconnectDelayMillis(2))
        assertNull(activityReconnectDelayMillis(3))
    }

    @Test
    fun oneEnvironmentFailurePreservesHealthySourceAndRecoversIndividually() {
        val registry = ActivitySourceFailureRegistry()
        registry.record(ActivitySourceFailure("edge", "Edge", "offline", ActivityFailureKind.EnvironmentStream))

        assertEquals(listOf("edge"), registry.snapshot().map { it.sourceId })
        assertEquals(false, registry.allEnvironmentSourcesFailed(listOf("0", "edge")))

        assertEquals(emptyList<ActivitySourceFailure>(), registry.recover("edge"))
    }

    @Test
    fun fullEnvironmentFailureAndTerminalConnectionFailureRemainIdentifiable() {
        val registry = ActivitySourceFailureRegistry()
        registry.record(ActivitySourceFailure("0", "Local Docker", "unavailable", ActivityFailureKind.InitialLoad))
        registry.record(ActivitySourceFailure("edge", "Edge", "offline", ActivityFailureKind.InitialLoad))
        assertEquals(true, registry.allEnvironmentSourcesFailed(listOf("0", "edge")))

        registry.record(
            ActivitySourceFailure(
                ACTIVITY_STREAM_SOURCE_ID,
                "Activity stream",
                "reconnect budget exhausted",
                ActivityFailureKind.Connection,
            ),
        )
        assertEquals(ActivityFailureKind.Connection, registry.snapshot().last().kind)
        assertEquals(3, registry.snapshot().size)
    }

    @Test
    fun heartbeatTimeoutTerminatesSilentSource() {
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                flow {
                    emit("snapshot")
                    delay(100)
                    emit("late")
                }.withActivityHeartbeatTimeout(10).toList()
            }
        }
    }

    @Test
    fun regularHeartbeatsKeepSourceAlive() = runBlocking {
        val received = flow {
            emit("snapshot")
            delay(5)
            emit("heartbeat")
            delay(5)
            emit("activity")
        }.withActivityHeartbeatTimeout(20).toList()

        assertEquals(listOf("snapshot", "heartbeat", "activity"), received)
    }

    @Test
    fun ownerCancellationStopsHeartbeatWaitImmediately() = runBlocking {
        val job = launch {
            flow<String> { awaitCancellation() }
                .withActivityHeartbeatTimeout(60_000)
                .toList()
        }
        yield()

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }
}
