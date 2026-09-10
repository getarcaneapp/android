package app.getarcane.android.ui.screens.settings.variables

import app.getarcane.sdk.models.variable.EnvironmentSyncStatus
import app.getarcane.sdk.models.variable.VariableSyncState
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class VariablesState(
    val sessionKey: String,
    val generation: Long = 0,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isUnsupported: Boolean = false,
    val variables: List<VariableDisplay> = emptyList(),
    val environments: List<EnvironmentChoice> = emptyList(),
    val syncStatuses: List<EnvironmentSyncStatus> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null,
)

internal sealed interface VariablesEvent {
    val generation: Long

    data class LoadStarted(override val generation: Long) : VariablesEvent

    data class LoadSucceeded(
        override val generation: Long,
        val variables: List<VariableDisplay>,
        val environments: List<EnvironmentChoice>,
        val syncStatuses: List<EnvironmentSyncStatus>,
    ) : VariablesEvent

    data class LoadFailed(
        override val generation: Long,
        val message: String,
        val unsupported: Boolean,
    ) : VariablesEvent

    data class SyncStarted(override val generation: Long) : VariablesEvent

    data class SyncFinished(
        override val generation: Long,
        val syncStatuses: List<EnvironmentSyncStatus>,
        val timedOut: Boolean,
    ) : VariablesEvent

    data class MutationFinished(
        override val generation: Long,
        val variable: VariableDisplay? = null,
        val deletedId: String? = null,
        val syncStatuses: List<EnvironmentSyncStatus> = emptyList(),
        val timedOut: Boolean = false,
    ) : VariablesEvent

    data class ActionFailed(
        override val generation: Long,
        val message: String,
    ) : VariablesEvent
}

internal fun reduceVariablesState(state: VariablesState, event: VariablesEvent): VariablesState {
    if (event is VariablesEvent.LoadStarted) {
        if (event.generation <= state.generation) return state
        return state.copy(
            generation = event.generation,
            isLoading = true,
            isSyncing = false,
            isUnsupported = false,
            errorMessage = null,
            actionMessage = null,
        )
    }
    if (event is VariablesEvent.SyncStarted) {
        if (event.generation <= state.generation) return state
        return state.copy(
            generation = event.generation,
            isLoading = false,
            isSyncing = true,
            errorMessage = null,
            actionMessage = null,
        )
    }
    if (event.generation != state.generation) return state

    return when (event) {
        is VariablesEvent.LoadStarted,
        is VariablesEvent.SyncStarted -> state

        is VariablesEvent.LoadSucceeded -> state.copy(
            isLoading = false,
            isUnsupported = false,
            variables = event.variables.sortedByKey(),
            environments = event.environments,
            syncStatuses = normalizeSyncStatuses(event.syncStatuses),
            errorMessage = null,
        )

        is VariablesEvent.LoadFailed -> state.copy(
            isLoading = false,
            isUnsupported = event.unsupported,
            variables = if (event.unsupported) emptyList() else state.variables,
            syncStatuses = if (event.unsupported) emptyList() else state.syncStatuses,
            errorMessage = event.message,
        )

        is VariablesEvent.SyncFinished -> state.copy(
            isLoading = false,
            isSyncing = false,
            syncStatuses = normalizeSyncStatuses(event.syncStatuses),
            errorMessage = null,
            actionMessage = syncCompletionMessage(event.syncStatuses, event.timedOut),
        )

        is VariablesEvent.MutationFinished -> {
            val variables = state.variables
                .filterNot { it.id == event.deletedId || it.id == event.variable?.id }
                .let { existing -> event.variable?.let(existing::plus) ?: existing }
                .sortedByKey()
            state.copy(
                isLoading = false,
                variables = variables,
                syncStatuses = mergeSyncStatuses(state.syncStatuses, event.syncStatuses),
                errorMessage = null,
                actionMessage = syncCompletionMessage(event.syncStatuses, event.timedOut),
            )
        }

        is VariablesEvent.ActionFailed -> state.copy(
            isLoading = false,
            isSyncing = false,
            actionMessage = event.message,
        )
    }
}

internal class VariablesStore(sessionKey: String) {
    private val mutableState = MutableStateFlow(VariablesState(sessionKey = sessionKey))
    val state: StateFlow<VariablesState> = mutableState.asStateFlow()

    @Synchronized
    fun beginLoad(): Long = begin { generation -> VariablesEvent.LoadStarted(generation) }

    @Synchronized
    fun beginSync(): Long = begin { generation -> VariablesEvent.SyncStarted(generation) }

    /** Mutations invalidate every older load, mutation, sync, and polling result. */
    @Synchronized
    fun beginMutation(): Long {
        val generation = mutableState.value.generation + 1
        mutableState.value = mutableState.value.copy(
            generation = generation,
            isLoading = false,
            isSyncing = false,
            errorMessage = null,
            actionMessage = null,
        )
        return generation
    }

    @Synchronized
    fun dispatch(event: VariablesEvent) {
        mutableState.value = reduceVariablesState(mutableState.value, event)
    }

    private fun begin(event: (Long) -> VariablesEvent): Long {
        val generation = mutableState.value.generation + 1
        mutableState.value = reduceVariablesState(mutableState.value, event(generation))
        return generation
    }
}

internal fun normalizeSyncStatuses(statuses: List<EnvironmentSyncStatus>): List<EnvironmentSyncStatus> =
    statuses
        .associateBy(EnvironmentSyncStatus::environmentId)
        .values
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { status: EnvironmentSyncStatus ->
                status.environmentName?.trim().orEmpty().ifEmpty { status.environmentId }
            },
        )

internal fun mergeSyncStatuses(
    current: List<EnvironmentSyncStatus>,
    updates: List<EnvironmentSyncStatus>,
): List<EnvironmentSyncStatus> {
    if (updates.isEmpty()) return normalizeSyncStatuses(current)
    val merged = current.associateByTo(linkedMapOf(), EnvironmentSyncStatus::environmentId)
    updates.forEach { merged[it.environmentId] = it }
    return normalizeSyncStatuses(merged.values.toList())
}

internal fun syncCompletionMessage(statuses: List<EnvironmentSyncStatus>, timedOut: Boolean): String? {
    if (statuses.isEmpty()) return null
    val errors = statuses.count { it.status == VariableSyncState.ERROR }
    return when {
        timedOut && statuses.any { it.status == VariableSyncState.PENDING } ->
            "Variable sync is still pending. Check status again shortly."
        errors == statuses.size -> "Variable sync failed for every reported environment."
        errors > 0 -> "Some environments failed to sync."
        statuses.any { it.status == VariableSyncState.UNKNOWN } ->
            "One or more environments reported an unknown sync status."
        statuses.any { it.status == VariableSyncState.PENDING } ->
            "Variable sync is pending."
        else -> null
    }
}

private fun List<VariableDisplay>.sortedByKey(): List<VariableDisplay> = sortedWith(
    compareBy<VariableDisplay> { it.key.lowercase(Locale.ROOT) }.thenBy(VariableDisplay::id),
)
