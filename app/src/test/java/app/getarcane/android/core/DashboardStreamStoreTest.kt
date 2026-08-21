package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError
import app.getarcane.sdk.models.container.ContainerStatusCounts
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.image.ImageUsageCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStreamStoreTest {
    @Test
    fun aggregateWaitsForEveryEnvironmentToSettle() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = DashboardStreamStore(scope)
        try {
            store.reconcile(
                listOf(
                    Environment(id = "0", name = "Local", apiUrl = "", status = "active"),
                    Environment(id = "edge", name = "Edge", apiUrl = "", status = "active"),
                ),
            )

            store.applyForTest(snapshotEvent("0", running = 2, stopped = 1, images = 4))
            assertNull(store.aggregate)

            store.applyForTest(errorEvent("edge"))
            assertNull(store.aggregate)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshotClearsExistingEnvironmentErrorButKeepsLoadedLatch() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = DashboardStreamStore(scope)
        try {
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))

            store.applyForTest(snapshotEvent("edge", running = 1, stopped = 0, images = 2))
            store.applyForTest(errorEvent("edge"))
            assertTrue(store.statesByEnvironmentId.getValue("edge").hasLoaded)
            assertTrue(store.statesByEnvironmentId.getValue("edge").streamError)

            store.applyForTest(snapshotEvent("edge", running = 3, stopped = 2, images = 8))
            val state = store.statesByEnvironmentId.getValue("edge")
            assertTrue(state.hasLoaded)
            assertFalse(state.streamError)
            assertEquals(3, state.snapshot?.containers?.counts?.runningContainers)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun retryClearsPerEnvironmentStreamErrors() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = DashboardStreamStore(scope)
        try {
            store.configure(HangingDashboardStreamClient)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            store.start()
            store.applyForTest(errorEvent("edge"))
            assertTrue(store.statesByEnvironmentId.getValue("edge").streamError)

            store.retry()

            assertFalse(store.statesByEnvironmentId.getValue("edge").streamError)
            assertNull(store.statesByEnvironmentId.getValue("edge").errorMessage)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun configureNewClientKeepsReconciledEnvironmentsButClearsSnapshots() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = DashboardStreamStore(scope)
        try {
            store.configure(HangingDashboardStreamClient)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            store.applyForTest(snapshotEvent("edge", running = 1, stopped = 0, images = 2))

            store.configure(AlternateDashboardStreamClient)

            val state = store.statesByEnvironmentId.getValue("edge")
            assertEquals("Edge", state.name)
            assertTrue(state.loading)
            assertFalse(state.hasLoaded)
            assertFalse(state.streamError)
            assertNull(state.snapshot)

            store.applyForTest(snapshotEvent("edge", running = 3, stopped = 1, images = 5))
            assertEquals(
                DashboardStreamAggregateCounts(
                    runningContainers = 3,
                    stoppedContainers = 1,
                    totalContainers = 4,
                    totalImages = 5,
                ),
                store.aggregate,
            )
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun newerRefreshResultCannotBeOverwrittenByOlderRequest() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = ControlledSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))

            val first = async(start = CoroutineStart.UNDISPATCHED) { store.refresh() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { store.refresh() }
            yield()
            assertEquals(2, client.requests.size)

            client.requests[1].complete(snapshot(running = 9))
            second.await()
            client.requests[0].complete(snapshot(running = 1))
            first.await()

            assertEquals(9, store.statesByEnvironmentId.getValue("edge").snapshot?.containers?.counts?.runningContainers)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun cancelledRefreshPropagatesWithoutPublishingAnError() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = ControlledSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))

            val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.refresh() }
            yield()
            refresh.cancelAndJoin()

            val state = store.statesByEnvironmentId.getValue("edge")
            assertFalse(state.streamError)
            assertNull(state.errorMessage)
            assertFalse(state.hasLoaded)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun removedEnvironmentRejectsAnInFlightSnapshot() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = ControlledSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.refresh() }
            yield()

            store.reconcile(emptyList())
            client.requests.single().complete(snapshot(running = 7))
            refresh.await()

            assertTrue(store.statesByEnvironmentId.isEmpty())
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun replacementClientRejectsAnInFlightSnapshotFromThePriorServer() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val originalClient = ControlledSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(originalClient)
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { store.refresh() }
            yield()

            store.configure(AlternateDashboardStreamClient)
            originalClient.requests.single().complete(snapshot(running = 7))
            refresh.await()

            val state = store.statesByEnvironmentId.getValue("edge")
            assertTrue(state.loading)
            assertFalse(state.hasLoaded)
            assertNull(state.snapshot)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun environmentRemovalCancelsItsStoreOwnedSnapshotJob() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = CountingSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.start()
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            assertEquals(1, client.active)

            store.reconcile(emptyList())
            yield()

            assertEquals(0, client.active)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun clientReplacementCancelsStoreOwnedSnapshotJobs() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = CountingSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.start()
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            assertEquals(1, client.active)

            store.configure(AlternateDashboardStreamClient)
            yield()

            assertEquals(0, client.active)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun liveSnapshotCancelsTheRedundantStoreOwnedFallback() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = CountingSnapshotClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.start()
            store.reconcile(listOf(Environment(id = "edge", name = "Edge", apiUrl = "", status = "active")))
            assertEquals(1, client.active)

            store.applyForTest(snapshotEvent("edge", running = 3, stopped = 0, images = 2))
            yield()

            assertEquals(0, client.active)
            assertEquals(3, store.statesByEnvironmentId.getValue("edge").snapshot?.containers?.counts?.runningContainers)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun reconnectReplacesTheSingleOwnedStreamJob() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = CountingStreamClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(client)
            store.start()
            assertEquals(1, client.started)
            assertEquals(1, client.active)

            store.retry()

            assertEquals(2, client.started)
            assertEquals(1, client.active)
        } finally {
            store.stop()
            scope.cancel()
        }
        assertEquals(0, client.active)
    }

    @Test
    fun unsupportedStreamStopsReconnectAndResetsForAReplacementClient() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val unsupportedClient = UnsupportedStreamClient()
        val store = DashboardStreamStore(scope)
        try {
            store.configure(unsupportedClient)
            store.start()
            yield()

            assertTrue(store.streamUnsupported)
            assertFalse(store.streamFailed)
            assertFalse(store.isStreaming)
            assertEquals(1, unsupportedClient.started)

            store.retry()
            assertEquals(1, unsupportedClient.started)

            store.configure(HangingDashboardStreamClient)
            store.start()
            assertFalse(store.streamUnsupported)
            assertTrue(store.isStreaming)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    @Test
    fun repeatedTransportFailuresEnterBoundedIdleRetry() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = FailingStreamClient()
        val store = DashboardStreamStore(
            scope = scope,
            maxReconnectAttempts = 2,
            maxReconnectDelayMillis = 1,
            idleRetryMillis = 60_000,
        )
        try {
            store.configure(client)
            store.start()
            withTimeout(1_000) {
                while (!store.streamFailed) yield()
            }

            assertEquals(3, client.started)
            assertTrue(store.isStreaming)
            assertFalse(store.connected)
        } finally {
            store.stop()
            scope.cancel()
        }
    }

    private fun snapshotEvent(environmentId: String, running: Int, stopped: Int, images: Int): DashboardStreamEvent =
        DashboardStreamEvent(
            type = "snapshot",
            environmentId = environmentId,
            snapshot = DashboardSnapshot(
                containers = DashboardSnapshotContainers(
                    counts = ContainerStatusCounts(
                        runningContainers = running,
                        stoppedContainers = stopped,
                        totalContainers = running + stopped,
                    ),
                ),
                images = DashboardSnapshotImages(),
                imageUsageCounts = ImageUsageCounts(
                    imagesInuse = images,
                    imagesUnused = 0,
                    totalImages = images,
                    totalImageSize = 0,
                ),
            ),
        )

    private fun errorEvent(environmentId: String): DashboardStreamEvent =
        DashboardStreamEvent(
            type = "error",
            environmentId = environmentId,
            error = "unreachable",
            errorCode = "unreachable",
        )

    private fun snapshot(running: Int): DashboardSnapshot =
        snapshotEvent("edge", running = running, stopped = 0, images = 0).snapshot!!
}

private object HangingDashboardStreamClient : DashboardStreamClient {
    override fun stream(): Flow<DashboardStreamEvent> = flow { kotlinx.coroutines.awaitCancellation() }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}

private object AlternateDashboardStreamClient : DashboardStreamClient {
    override fun stream(): Flow<DashboardStreamEvent> = flow { kotlinx.coroutines.awaitCancellation() }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}

private class ControlledSnapshotClient : DashboardStreamClient {
    val requests = mutableListOf<CompletableDeferred<DashboardSnapshot>>()

    override fun stream(): Flow<DashboardStreamEvent> = flow { awaitCancellation() }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        CompletableDeferred<DashboardSnapshot>().also(requests::add).await()
}

private class CountingStreamClient : DashboardStreamClient {
    var started = 0
    var active = 0

    override fun stream(): Flow<DashboardStreamEvent> = flow {
        started++
        active++
        try {
            awaitCancellation()
        } finally {
            active--
        }
    }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}

private class CountingSnapshotClient : DashboardStreamClient {
    var active = 0

    override fun stream(): Flow<DashboardStreamEvent> = flow { awaitCancellation() }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot {
        active++
        try {
            awaitCancellation()
        } finally {
            active--
        }
    }
}

private class UnsupportedStreamClient : DashboardStreamClient {
    var started = 0

    override fun stream(): Flow<DashboardStreamEvent> = flow {
        started++
        yield()
        throw ArcaneError.NotFound
    }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}

private class FailingStreamClient : DashboardStreamClient {
    var started = 0

    override fun stream(): Flow<DashboardStreamEvent> = flow {
        started++
        throw ArcaneError.Transport("offline")
    }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}
