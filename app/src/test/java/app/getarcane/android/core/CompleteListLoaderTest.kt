package app.getarcane.android.core

import app.getarcane.sdk.models.base.PaginationResponse
import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.base.SortOrder
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.pagination.PaginatedResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CompleteListLoaderTest {
    @Test
    fun `environment loader requests all rows and returns more than twenty`() = runBlocking {
        val environments = (0 until 125).map(::environment)
        var capturedQuery: SearchPaginationSort? = null

        val result = loadCompleteEnvironments { query ->
            capturedQuery = query
            page(environments, totalItems = environments.size.toLong())
        }

        assertEquals(environments, result)
        assertEquals(0, capturedQuery?.start)
        assertEquals(COMPLETE_LIST_LIMIT, capturedQuery?.limit)
        assertEquals("id", capturedQuery?.sortBy)
        assertEquals(SortOrder.ASCENDING, capturedQuery?.sortOrder)
    }

    @Test
    fun `duplicates are removed when total counts raw rows`() = runBlocking {
        val result = loadCompletePaginatedCollection("Thing", idOf = { it }) {
            page(listOf("one", "two", "two"), totalItems = 3)
        }

        assertEquals(listOf("one", "two"), result)
    }

    @Test
    fun `duplicates are removed when total counts unique identities`() = runBlocking {
        val result = loadCompletePaginatedCollection("Thing", idOf = { it }) {
            page(listOf("one", "two", "two"), totalItems = 2)
        }

        assertEquals(listOf("one", "two"), result)
    }

    @Test
    fun `empty response is a successful complete collection`() = runBlocking {
        assertEquals(
            emptyList<String>(),
            loadCompletePaginatedCollection<String, String>("Thing", idOf = { it }) {
                page(emptyList(), totalItems = 0)
            },
        )
    }

    @Test
    fun `short show-all response is accepted as the final response`() = runBlocking {
        val result = loadCompletePaginatedCollection("Thing", idOf = { it }) {
            page(listOf("one", "two", "three"), totalItems = 3)
        }

        assertEquals(listOf("one", "two", "three"), result)
    }

    @Test
    fun `count mismatch fails atomically`() {
        val error = assertThrows(IncompleteCollectionException::class.java) {
            runBlocking {
                loadCompletePaginatedCollection("Thing", idOf = { it }) {
                    page(listOf("one"), totalItems = 2)
                }
            }
        }

        assertEquals("Thing response counts: raw=1, unique=1; server reported 2", error.message)
    }

    @Test
    fun `unsuccessful response fails atomically`() {
        val error = assertThrows(IncompleteCollectionException::class.java) {
            normalizeCompleteCollection(
                resourceName = "Thing",
                response = CompleteListResponse(listOf("one"), totalItems = 1, success = false),
                idOf = { it },
            )
        }

        assertEquals("Thing response was unsuccessful", error.message)
    }

    @Test
    fun `load failure is propagated without partial content`() {
        val expected = IOException("failed")

        val actual = assertThrows(IOException::class.java) {
            runBlocking {
                loadCompletePaginatedCollection<String, String>("Thing", idOf = { it }) {
                    throw expected
                }
            }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `cancellation is propagated`() {
        val expected = CancellationException("cancelled")

        val actual = assertThrows(CancellationException::class.java) {
            runBlocking {
                loadCompletePaginatedCollection<String, String>("Thing", idOf = { it }) {
                    throw expected
                }
            }
        }

        assertSame(expected, actual)
    }

    private fun environment(index: Int): Environment =
        Environment(
            id = index.toString(),
            name = "Environment $index",
            apiUrl = "https://environment-$index.example.com",
            status = "online",
        )

    private fun <T> page(items: List<T>, totalItems: Long): PaginatedResponse<T> =
        PaginatedResponse(
            success = true,
            data = items,
            pagination = PaginationResponse(
                totalPages = if (items.isEmpty()) 0 else 1,
                totalItems = totalItems,
                currentPage = if (items.isEmpty()) 0 else 1,
                itemsPerPage = items.size,
            ),
        )
}
