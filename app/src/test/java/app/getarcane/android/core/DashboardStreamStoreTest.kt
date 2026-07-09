package app.getarcane.android.core

import app.getarcane.sdk.models.container.ContainerStatusCounts
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.image.ImageUsageCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
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
            assertEquals(
                DashboardStreamAggregateCounts(
                    runningContainers = 2,
                    stoppedContainers = 1,
                    totalContainers = 3,
                    totalImages = 4,
                ),
                store.aggregate,
            )
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
}

private object HangingDashboardStreamClient : DashboardStreamClient {
    override fun stream(): Flow<DashboardStreamEvent> = flow { kotlinx.coroutines.awaitCancellation() }

    override suspend fun snapshot(environmentId: String): DashboardSnapshot =
        error("snapshot should not be called")
}
