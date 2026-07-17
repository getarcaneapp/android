package app.getarcane.android.ui.screens.containers

import app.getarcane.android.core.Loadable
import app.getarcane.android.core.ResourceUpdateFilter
import app.getarcane.sdk.models.container.ContainerHostConfig
import app.getarcane.sdk.models.container.ContainerNetworkSettings
import app.getarcane.sdk.models.container.ContainerSummary
import app.getarcane.sdk.models.image.ImageUpdateInfo
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerPaginationTest {
    @Test
    fun `show-all contract loads more than twenty containers in one finite request`() = runBlocking {
        val source = (0 until 225).map { "container-$it" }
        var requestCount = 0

        val result = loadCompleteContainerCollection(idOf = { it }) {
            requestCount++
            CompleteContainerResponse(source, totalItems = source.size.toLong())
        }

        assertEquals(source, result)
        assertEquals(1, requestCount)
    }

    @Test
    fun `duplicate IDs are removed without additional requests`() = runBlocking {
        var requestCount = 0

        val result = loadCompleteContainerCollection(idOf = { it }) {
            requestCount++
            CompleteContainerResponse(
                items = listOf("container-1", "container-2", "container-2"),
                totalItems = 2,
            )
        }

        assertEquals(listOf("container-1", "container-2"), result)
        assertEquals(1, requestCount)
    }

    @Test
    fun `reported total mismatch fails instead of publishing an incomplete result`() {
        val error = assertThrows(IncompleteContainerCollectionException::class.java) {
            runBlocking {
                loadCompleteContainerCollection(idOf = { it }) {
                    CompleteContainerResponse(listOf("container-1"), totalItems = 2)
                }
            }
        }

        assertEquals(
            "Container response contained 1 unique items, but the server reported 2",
            error.message,
        )
    }

    @Test
    fun `empty complete collection terminates after one request`() = runBlocking {
        var requestCount = 0

        val result = loadCompleteContainerCollection<String>(idOf = { it }) {
            requestCount++
            CompleteContainerResponse(emptyList(), totalItems = 0)
        }

        assertEquals(emptyList<String>(), result)
        assertEquals(1, requestCount)
    }

    @Test
    fun `failure is propagated without a partial result`() {
        val expected = IOException("load failed")

        val actual = assertThrows(IOException::class.java) {
            runBlocking {
                loadCompleteContainerCollection<String>(idOf = { it }) {
                    throw expected
                }
            }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `cancellation is propagated immediately`() {
        val expected = CancellationException("cancelled")

        val actual = assertThrows(CancellationException::class.java) {
            runBlocking {
                loadCompleteContainerCollection<String>(idOf = { it }) {
                    throw expected
                }
            }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `search and status filters see a match outside the former first page`() = runBlocking {
        val containers = (0 until 125).map { index ->
            container(
                id = "container-$index",
                name = if (index == 124) "late-search-match" else "container-$index",
                state = if (index == 124) "running" else "exited",
            )
        }
        val complete = loadCompleteContainerCollection(idOf = ContainerSummary::id) {
            CompleteContainerResponse(containers, totalItems = containers.size.toLong())
        }

        val result = filterAndSortContainers(
            containers = complete,
            search = "late-search",
            stateFilter = ContainerStateFilter.Running,
            updateFilter = ResourceUpdateFilter.ALL,
            sortAscending = true,
        )

        assertEquals(listOf("container-124"), result.map { it.id })
    }

    @Test
    fun `update filter sees a match outside the former first page`() = runBlocking {
        val containers = (0 until 125).map { index ->
            container(
                id = "container-$index",
                name = "container-$index",
                state = "running",
                hasUpdate = index == 124,
            )
        }
        val complete = loadCompleteContainerCollection(idOf = ContainerSummary::id) {
            CompleteContainerResponse(containers, totalItems = containers.size.toLong())
        }

        val result = filterAndSortContainers(
            containers = complete,
            search = "",
            stateFilter = ContainerStateFilter.All,
            updateFilter = ResourceUpdateFilter.HAS_UPDATES,
            sortAscending = true,
        )

        assertEquals(listOf("container-124"), result.map { it.id })
    }

    @Test
    fun `initial state and reload expose loading without refresh`() {
        val initial = ContainerListLoadState<List<String>>()
        val afterError = ContainerListLoadState<List<String>>(
            content = Loadable.Error("old error"),
        )

        assertTrue(initial.content is Loadable.Loading)
        assertFalse(initial.refreshing)
        assertTrue(beginContainerReload(afterError).content is Loadable.Loading)
    }

    @Test
    fun `refresh retains prior complete data while loading`() {
        val complete = completeContainerLoad(listOf("container-1"))

        val refreshing = beginContainerRefresh(complete)

        assertEquals(
            listOf("container-1"),
            (refreshing.content as Loadable.Success).value,
        )
        assertTrue(refreshing.refreshing)
    }

    @Test
    fun `refresh failure replaces prior content and clears refreshing`() {
        val refreshing = beginContainerRefresh(completeContainerLoad(listOf("container-1")))

        val failed = failContainerLoad<List<String>>("refresh failed")

        assertTrue(refreshing.refreshing)
        assertEquals("refresh failed", (failed.content as Loadable.Error).message)
        assertFalse(failed.refreshing)
    }

    @Test
    fun `initial failure exposes error and clears loading indicators`() {
        val failed = failContainerLoad<List<String>>("initial failed")

        assertEquals("initial failed", (failed.content as Loadable.Error).message)
        assertFalse(failed.refreshing)
    }

    @Test
    fun `successful empty load is distinct from loading and failure`() {
        val empty = completeContainerLoad(emptyList<String>())

        assertEquals(emptyList<String>(), (empty.content as Loadable.Success).value)
        assertFalse(empty.refreshing)
    }

    private fun container(
        id: String,
        name: String,
        state: String,
        hasUpdate: Boolean = false,
    ): ContainerSummary =
        ContainerSummary(
            id = id,
            names = listOf(name),
            image = "example/image:latest",
            imageId = "image-$id",
            command = "",
            created = 0,
            ports = emptyList(),
            state = state,
            status = state,
            hostConfig = ContainerHostConfig(),
            networkSettings = ContainerNetworkSettings(),
            mounts = emptyList(),
            updateInfo = if (hasUpdate) updateInfo() else null,
        )

    private fun updateInfo(): ImageUpdateInfo =
        ImageUpdateInfo(
            hasUpdate = true,
            updateType = "digest",
            currentVersion = "1",
            latestVersion = "2",
            currentDigest = "sha256:current",
            latestDigest = "sha256:latest",
            checkTime = Instant.fromEpochMilliseconds(0),
            responseTimeMs = 1,
            error = "",
        )
}
