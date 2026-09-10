package app.getarcane.android.ui.screens.settings.variables

import app.getarcane.sdk.models.variable.EnvironmentSyncStatus
import app.getarcane.sdk.models.variable.VariableSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariablesStoreTest {
    @Test
    fun `newer load rejects stale success and failure`() {
        val loadingOne = reduceVariablesState(VariablesState("session"), VariablesEvent.LoadStarted(1))
        val loadingTwo = reduceVariablesState(loadingOne, VariablesEvent.LoadStarted(2))

        val staleSuccess = reduceVariablesState(
            loadingTwo,
            VariablesEvent.LoadSucceeded(1, listOf(variable("stale")), emptyList(), emptyList()),
        )
        val staleFailure = reduceVariablesState(
            staleSuccess,
            VariablesEvent.LoadFailed(1, "stale failure", unsupported = true),
        )

        assertEquals(loadingTwo, staleFailure)
        assertTrue(staleFailure.isLoading)
        assertFalse(staleFailure.isUnsupported)
    }

    @Test
    fun `mutation invalidates old sync and merges statuses exactly once`() {
        val store = VariablesStore("session")
        val syncGeneration = store.beginSync()
        val mutationGeneration = store.beginMutation()
        store.dispatch(
            VariablesEvent.MutationFinished(
                generation = mutationGeneration,
                variable = variable("new"),
                syncStatuses = listOf(
                    status("env-1", VariableSyncState.PENDING),
                    status("env-1", VariableSyncState.SYNCED),
                    status("env-2", VariableSyncState.ERROR),
                ),
            ),
        )
        store.dispatch(
            VariablesEvent.SyncFinished(
                syncGeneration,
                listOf(status("env-stale", VariableSyncState.SYNCED)),
                timedOut = false,
            ),
        )

        val state = store.state.value
        assertEquals(listOf("new"), state.variables.map(VariableDisplay::id))
        assertEquals(listOf("env-1", "env-2"), state.syncStatuses.map(EnvironmentSyncStatus::environmentId))
        assertEquals(VariableSyncState.SYNCED, state.syncStatuses.first().status)
    }

    @Test
    fun `load sorts variables and normalizes duplicate status ids`() {
        val store = VariablesStore("session")
        val generation = store.beginLoad()

        store.dispatch(
            VariablesEvent.LoadSucceeded(
                generation,
                variables = listOf(variable("z", "Zulu"), variable("a", "alpha")),
                environments = emptyList(),
                syncStatuses = listOf(
                    status("env", VariableSyncState.PENDING),
                    status("env", VariableSyncState.SYNCED),
                ),
            ),
        )

        assertEquals(listOf("a", "z"), store.state.value.variables.map(VariableDisplay::id))
        assertEquals(1, store.state.value.syncStatuses.size)
        assertEquals(VariableSyncState.SYNCED, store.state.value.syncStatuses.single().status)
    }

    @Test
    fun `successful action clears a retained load failure`() {
        val store = VariablesStore("session")
        val loadGeneration = store.beginLoad()
        store.dispatch(VariablesEvent.LoadFailed(loadGeneration, "old load failure", unsupported = false))
        assertEquals("old load failure", store.state.value.errorMessage)

        val syncGeneration = store.beginSync()
        assertNull(store.state.value.errorMessage)
        store.dispatch(
            VariablesEvent.SyncFinished(
                syncGeneration,
                listOf(status("env", VariableSyncState.SYNCED)),
                timedOut = false,
            ),
        )

        assertNull(store.state.value.errorMessage)
        assertNull(store.state.value.actionMessage)
    }

    @Test
    fun `timeout and partial failure messages remain accurate`() {
        val pending = listOf(status("env", VariableSyncState.PENDING))
        val partial = listOf(
            status("one", VariableSyncState.SYNCED),
            status("two", VariableSyncState.ERROR),
        )

        assertEquals(
            "Variable sync is still pending. Check status again shortly.",
            syncCompletionMessage(pending, timedOut = true),
        )
        assertEquals("Some environments failed to sync.", syncCompletionMessage(partial, timedOut = false))
        assertEquals(
            "One or more environments reported an unknown sync status.",
            syncCompletionMessage(
                listOf(
                    status("one", VariableSyncState.SYNCED),
                    status("two", VariableSyncState.UNKNOWN),
                ),
                timedOut = false,
            ),
        )
        assertNull(syncCompletionMessage(listOf(status("env", VariableSyncState.SYNCED)), timedOut = false))
    }

    private fun variable(id: String, key: String = id) = VariableDisplay(
        id = id,
        key = key,
        visibleValue = "value",
        isSecret = false,
        allEnvironments = true,
        environmentIds = emptySet(),
        revision = "revision-$id",
    )

    private fun status(id: String, state: VariableSyncState) = EnvironmentSyncStatus(
        environmentId = id,
        environmentName = id,
        status = state,
    )
}
