package app.getarcane.android.ui.screens.containers

import app.getarcane.android.core.Loadable
import app.getarcane.android.core.ResourceUpdateFilter
import app.getarcane.android.core.CompleteListResponse
import app.getarcane.android.core.displayName
import app.getarcane.android.core.hasAvailableUpdate
import app.getarcane.android.core.IncompleteCollectionException
import app.getarcane.android.core.isRunning
import app.getarcane.android.core.loadCompleteCollection
import app.getarcane.sdk.models.container.ContainerSummary

internal typealias CompleteContainerResponse<T> = CompleteListResponse<T>
internal typealias IncompleteContainerCollectionException = IncompleteCollectionException

/**
 * Normalizes the server's documented single-response "show all" result. The Arcane container
 * endpoint has no unique sort binding, so offset traversal cannot guarantee a stable collection
 * when containers change or share the same supported sort value.
 */
internal suspend fun <T> loadCompleteContainerCollection(
    idOf: (T) -> String,
    loadAll: suspend () -> CompleteContainerResponse<T>,
): List<T> {
    return loadCompleteCollection("Container", idOf, loadAll)
}

internal data class ContainerListLoadState<T>(
    val content: Loadable<T> = Loadable.Loading,
    val refreshing: Boolean = false,
)

internal fun <T> beginContainerReload(
    state: ContainerListLoadState<T>,
): ContainerListLoadState<T> =
    if (state.content is Loadable.Success) state else state.copy(content = Loadable.Loading)

internal fun <T> beginContainerRefresh(
    state: ContainerListLoadState<T>,
): ContainerListLoadState<T> =
    state.copy(
        content = if (state.content is Loadable.Success) state.content else Loadable.Loading,
        refreshing = true,
    )

internal fun <T> completeContainerLoad(value: T): ContainerListLoadState<T> =
    ContainerListLoadState(content = Loadable.Success(value))

internal fun <T> failContainerLoad(message: String): ContainerListLoadState<T> =
    ContainerListLoadState(content = Loadable.Error(message))

internal enum class ContainerStateFilter { All, Running, Stopped }

internal fun filterAndSortContainers(
    containers: List<ContainerSummary>,
    search: String,
    stateFilter: ContainerStateFilter,
    updateFilter: ResourceUpdateFilter,
    sortAscending: Boolean,
): List<ContainerSummary> {
    val filtered = containers.filter { container ->
        (stateFilter == ContainerStateFilter.All ||
            (stateFilter == ContainerStateFilter.Running && container.isRunning) ||
            (stateFilter == ContainerStateFilter.Stopped && !container.isRunning)) &&
            updateFilter.matches(container.hasAvailableUpdate) &&
            (search.isBlank() ||
                container.names.any { it.contains(search, ignoreCase = true) } ||
                container.image.contains(search, ignoreCase = true))
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

    return if (sortAscending) filtered else filtered.reversed()
}
