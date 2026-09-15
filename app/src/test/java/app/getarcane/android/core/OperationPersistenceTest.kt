package app.getarcane.android.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OperationPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `actual DataStore persistence atomically saves reloads and clears the ledger`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = temporaryFolder.newFolder().resolve("arcane_operations.preferences_pb")
        val persistence = DataStoreOperationPersistence(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        )
        try {
            val expected = record()
            persistence.save(listOf(expected))
            assertEquals(listOf(expected), (persistence.load() as OperationLedgerRead.Current).operations)
            persistence.clear()
            assertTrue((persistence.load() as OperationLedgerRead.Current).operations.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    private fun record() = OperationRecord(
        operationId = "persistence_1",
        kind = OperationKind.PROJECT_BUILD,
        state = OperationState.RUNNING,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
        serverBindingHash = "a".repeat(64),
        accountBindingHash = "b".repeat(64),
        credentialOriginHash = "c".repeat(64),
        environmentId = "0",
        targetType = OperationTargetType.PROJECT,
        opaqueTargetId = "project-1",
        duplicateKeyDigest = "d".repeat(64),
        activityBatchId = "persistence_1",
        recoveryMode = OperationRecoveryMode.ACTIVITY,
    )
}
