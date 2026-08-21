package app.getarcane.android.core

import app.getarcane.sdk.models.base.SearchPaginationSort
import app.getarcane.sdk.models.base.SortOrder
import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.pagination.PaginatedResponse

internal const val COMPLETE_LIST_LIMIT = -1

internal data class CompleteListResponse<T>(
    val items: List<T>,
    val totalItems: Long,
    val success: Boolean = true,
)

internal class IncompleteCollectionException(message: String) : IllegalStateException(message)

internal fun completeListQuery(
    sortBy: String? = null,
    sortOrder: SortOrder? = null,
): SearchPaginationSort =
    SearchPaginationSort(
        start = 0,
        limit = COMPLETE_LIST_LIMIT,
        sortBy = sortBy,
        sortOrder = sortOrder,
    )

/**
 * Normalizes one endpoint-supported show-all response without ever publishing a partial result.
 * Duplicate identities are collapsed in server order. The server may count either raw rows or
 * unique identities, but any other reported total means the response is incomplete or malformed.
 */
internal fun <T, K> normalizeCompleteCollection(
    resourceName: String,
    response: CompleteListResponse<T>,
    idOf: (T) -> K,
): List<T> {
    if (!response.success) {
        throw IncompleteCollectionException("$resourceName response was unsuccessful")
    }
    val itemsById = linkedMapOf<K, T>()
    response.items.forEach { item -> itemsById.putIfAbsent(idOf(item), item) }

    val rawItemCount = response.items.size.toLong()
    val uniqueItemCount = itemsById.size.toLong()
    if (response.totalItems != rawItemCount &&
        response.totalItems != uniqueItemCount
    ) {
        throw IncompleteCollectionException(
            "$resourceName response counts: raw=$rawItemCount, unique=$uniqueItemCount; " +
                "server reported ${response.totalItems}",
        )
    }
    return itemsById.values.toList()
}

internal suspend fun <T, K> loadCompleteCollection(
    resourceName: String,
    idOf: (T) -> K,
    loadAll: suspend () -> CompleteListResponse<T>,
): List<T> = normalizeCompleteCollection(resourceName, loadAll(), idOf)

internal suspend fun <T, K> loadCompletePaginatedCollection(
    resourceName: String,
    idOf: (T) -> K,
    loadAll: suspend () -> PaginatedResponse<T>,
): List<T> {
    return loadCompleteCollection(
        resourceName = resourceName,
        idOf = idOf,
        loadAll = {
            val response = loadAll()
            CompleteListResponse(response.data, response.pagination.totalItems, response.success)
        },
    )
}

/** All visible environments, using the server's documented finite show-all request. */
internal suspend fun loadCompleteEnvironments(
    loadAll: suspend (SearchPaginationSort) -> PaginatedResponse<Environment>,
): List<Environment> =
    loadCompletePaginatedCollection(
        resourceName = "Environment",
        idOf = Environment::id,
        loadAll = {
            loadAll(
                completeListQuery(sortBy = "id", sortOrder = SortOrder.ASCENDING),
            )
        },
    )
