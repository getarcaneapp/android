package app.getarcane.android.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import app.getarcane.sdk.errors.ArcaneError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStorePolicyTest {
    @Test
    fun `state machine permits recovery and cancellation but rejects terminal resurrection`() {
        assertTrue(OperationStateMachine.canTransition(OperationState.STARTING, OperationState.RUNNING))
        assertTrue(OperationStateMachine.canTransition(OperationState.RUNNING, OperationState.RECONNECTING))
        assertTrue(OperationStateMachine.canTransition(OperationState.RECONNECTING, OperationState.CANCEL_REQUESTED))
        assertTrue(OperationStateMachine.canTransition(OperationState.CANCEL_REQUESTED, OperationState.CANCELLED))
        assertTrue(OperationStateMachine.canTransition(OperationState.CANCEL_REQUESTED, OperationState.RECONNECTING))
        assertFalse(OperationStateMachine.canTransition(OperationState.SUCCESS, OperationState.RUNNING))
        assertTrue(OperationStateMachine.canTransition(OperationState.UNKNOWN, OperationState.SUCCESS))
    }

    @Test
    fun `ledger round trip keeps only recovery descriptor and bounded presentation scalar`() {
        val raw = OperationLedgerCodec.encode(listOf(record().copy(
            targetName = "registry.example/private/image:secret",
            environmentName = "Sensitive environment",
            lines = listOf(OperationLine("secret output", false)),
            phases = listOf("secret phase"),
            detailMessage = "secret failure",
            progressPercent = 140,
        )))
        val json = Json.parseToJsonElement(raw).jsonObject.toString()
        assertFalse(json.contains("secret", ignoreCase = true))

        val decoded = OperationLedgerCodec.decode(raw) as OperationLedgerRead.Current
        assertEquals(100, decoded.operations.single().progressPercent)
        assertTrue(decoded.operations.single().lines.isEmpty())
        assertTrue(decoded.operations.single().phases.isEmpty())
        assertNull(decoded.operations.single().detailMessage)
    }

    @Test
    fun `image pull never persists an opaque target`() {
        val raw = OperationLedgerCodec.encode(listOf(record().copy(
            kind = OperationKind.IMAGE_PULL,
            targetType = OperationTargetType.IMAGE,
            opaqueTargetId = "private.example/team/image:tag",
        )))
        assertFalse(raw.contains("private.example"))
        val decoded = OperationLedgerCodec.decode(raw) as OperationLedgerRead.Current
        assertNull(decoded.operations.single().opaqueTargetId)
    }

    @Test
    fun `future and corrupt schemas fail closed without interpreting operations`() {
        assertEquals(OperationLedgerRead.FutureSchema, OperationLedgerCodec.decode("""{"schemaVersion":999,"operations":[]}"""))
        assertEquals(OperationLedgerRead.Corrupt, OperationLedgerCodec.decode("""{"schemaVersion":1,"operations":[{}]}"""))
    }

    @Test
    fun `schema one fixture fills additive defaults without replayable command data`() {
        val fixture = """{"schemaVersion":1,"operations":[{"operationId":"legacy_1","createdAtEpochMs":1,"updatedAtEpochMs":2,"serverBindingHash":"${"a".repeat(64)}","accountBindingHash":"${"b".repeat(64)}","credentialOriginHash":"${"c".repeat(64)}","environmentId":"0","duplicateKeyDigest":"${"d".repeat(64)}","activityBatchId":"legacy_1"}]}"""
        val decoded = OperationLedgerCodec.decode(fixture) as OperationLedgerRead.Current
        assertEquals(OperationKind.UNKNOWN, decoded.operations.single().kind)
        assertEquals(OperationState.UNKNOWN, decoded.operations.single().state)
        assertEquals(OperationRecoveryMode.NONE, decoded.operations.single().recoveryMode)
    }

    @Test
    fun `notification IDs are stable unique and avoid summary collision`() {
        val first = stableOperationNotificationIds(listOf("Aa", "BB", "operation-3"))
        val second = stableOperationNotificationIds(listOf("operation-3", "BB", "Aa"))
        assertEquals(first, second)
        assertEquals(3, first.values.toSet().size)
        assertFalse(OPERATION_NOTIFICATION_SUMMARY_ID in first.values)
        assertNotEquals(first.getValue("Aa"), first.getValue("BB"))
    }

    @Test
    fun `notification text is closed vocabulary and omits targets errors and server URLs`() {
        val sensitive = record().copy(
            kind = OperationKind.IMAGE_PULL,
            state = OperationState.FAILURE,
            targetName = "private.registry/team/secret:tag",
            detailMessage = "https://server.example token=secret",
            presentationCode = OperationPresentationCode.COMPLETED_WITH_ISSUES,
        )
        val projected = sensitive.kind.notificationTitle() + " " + sensitive.notificationStateText()
        assertEquals("Image pull Completed with issues", projected)
        assertFalse(projected.contains("private.registry"))
        assertFalse(projected.contains("server.example"))
        assertFalse(projected.contains("secret"))
    }

    @Test
    fun `operation notification routes accept only exact bounded contexts`() {
        assertEquals(OperationRoute.Center, OperationRoute.parse("arcane-mobile://operations"))
        assertEquals(OperationRoute.Detail("operation_1"), OperationRoute.parse("arcane-mobile://operations/operation_1"))
        assertNull(OperationRoute.parse("https://operations/operation_1"))
        assertNull(OperationRoute.parse("arcane-mobile://operations/one/cancel"))
        assertNull(OperationRoute.parse("arcane-mobile://operations/contains%20spaces"))
        assertEquals("operation_1", parseOperationCancelRoute("arcane-mobile://operations/operation_1/cancel"))
        assertNull(parseOperationCancelRoute("arcane-mobile://operations/contains%20spaces/cancel"))
        assertNull(parseOperationCancelRoute("arcane-mobile://operations/operation_1/delete"))
    }

    @Test
    fun `cancellation is denied for stale binding terminal state or missing permission`() {
        val binding = OperationBinding("a".repeat(64), "b".repeat(64), "c".repeat(64))
        val active = record()
        assertTrue(canCancelOperation(active, hasCancelPermission = true, currentBinding = binding))
        assertFalse(canCancelOperation(active, hasCancelPermission = false, currentBinding = binding))
        assertFalse(canCancelOperation(active, hasCancelPermission = true, currentBinding = binding.copy(accountHash = "e".repeat(64))))
        assertFalse(canCancelOperation(active.copy(state = OperationState.SUCCESS), true, binding))
        assertFalse(canCancelOperation(active.copy(recoveryMode = OperationRecoveryMode.NONE), true, binding))
    }

    @Test
    fun `duplicate and project deployment family submissions are rejected precisely`() {
        val existing = record()
        assertTrue(operationConflicts(existing, OperationKind.PROJECT_BUILD, OperationTargetType.PROJECT, "project-1", "other"))
        assertTrue(operationConflicts(existing.copy(kind = OperationKind.PROJECT_BUILD), OperationKind.PROJECT_REDEPLOY, OperationTargetType.PROJECT, "project-1", "other"))
        assertFalse(operationConflicts(existing.copy(kind = OperationKind.PROJECT_BUILD), OperationKind.PROJECT_PULL, OperationTargetType.PROJECT, "project-1", "other"))
        assertTrue(operationConflicts(existing.copy(kind = OperationKind.PROJECT_BUILD), OperationKind.PROJECT_PULL, OperationTargetType.PROJECT, "project-1", existing.duplicateKeyDigest))
        assertFalse(operationConflicts(existing, OperationKind.PROJECT_BUILD, OperationTargetType.PROJECT, "project-2", "other"))
    }

    @Test
    fun `uncertain outcome can retry reconciliation without becoming success implicitly`() {
        assertTrue(OperationStateMachine.canTransition(OperationState.INTERRUPTED, OperationState.UNKNOWN))
        assertTrue(OperationStateMachine.canTransition(OperationState.UNKNOWN, OperationState.RECONNECTING))
        assertFalse(OperationStateMachine.canTransition(OperationState.INTERRUPTED, OperationState.SUCCESS))
    }

    @Test
    fun `high volume presentation output remains bounded and phases deduplicate`() {
        var lines = emptyList<OperationLine>()
        repeat(10_000) { lines = appendBoundedOperationLine(lines, OperationLine("line-$it", false)) }
        assertTrue(lines.size <= 400)
        assertEquals("line-9999", lines.last().text)

        var phases = emptyList<String>()
        repeat(1_000) { phases = appendBoundedOperationPhase(phases, "phase-${it % 100}") }
        assertEquals(64, phases.size)
        assertEquals(phases.size, phases.distinct().size)
    }

    @Test
    fun `terminal retention expires success sooner and never evicts active work`() {
        val day = 24L * 60 * 60 * 1_000
        val active = record().copy(operationId = "active", activityBatchId = "active", createdAtEpochMs = 1)
        val expiredSuccess = record().copy(
            operationId = "success", activityBatchId = "success", state = OperationState.SUCCESS,
            terminalAtEpochMs = 1, updatedAtEpochMs = 1,
        )
        val retainedUnknown = record().copy(
            operationId = "unknown", activityBatchId = "unknown", state = OperationState.UNKNOWN,
            terminalAtEpochMs = 1, updatedAtEpochMs = 1,
        )
        val result = retainOperations(listOf(active, expiredSuccess, retainedUnknown), timestamp = day * 2)
        assertTrue(result.any { it.operationId == "active" })
        assertFalse(result.any { it.operationId == "success" })
        assertTrue(result.any { it.operationId == "unknown" })
    }

    @Test
    fun `saved side effect route never resubmits after recreation`() {
        assertTrue(shouldSubmitSavedOperation(null))
        assertFalse(shouldSubmitSavedOperation("terminal-operation"))
    }

    @Test
    fun `restored activity work is conservatively transport invoked for server cancellation`() {
        val active = record()
        val terminal = record().copy(operationId = "terminal", activityBatchId = "terminal", state = OperationState.SUCCESS)
        val requestOnly = record().copy(operationId = "request", activityBatchId = "request", recoveryMode = OperationRecoveryMode.NONE)
        assertEquals(setOf(active.operationId), restoredActivityTransportIds(listOf(active, terminal, requestOnly)))
    }

    @Test
    fun `row cap evicts only the oldest terminal row needed for insertion`() {
        val rows = (0 until MAX_OPERATION_ROWS).map { index ->
            record().copy(
                operationId = "operation_$index",
                activityBatchId = "operation_$index",
                state = if (index < 2) OperationState.RUNNING else OperationState.SUCCESS,
                createdAtEpochMs = index.toLong(),
            )
        }
        val trimmed = makeRoomForOperation(rows)
        assertEquals(MAX_OPERATION_ROWS - 1, trimmed.size)
        assertTrue(trimmed.any { it.operationId == "operation_0" })
        assertTrue(trimmed.any { it.operationId == "operation_1" })
        assertFalse(trimmed.any { it.operationId == "operation_2" })
        assertTrue(trimmed.any { it.operationId == "operation_3" })
    }

    @Test
    fun `older fleet endpoint failures are explicit unsupported failures`() {
        assertTrue(isUnsupportedFleetError(ArcaneError.NotFound))
        assertTrue(isUnsupportedFleetError(ArcaneError.Decoding("old response")))
        assertFalse(isUnsupportedFleetError(ArcaneError.Transport("offline")))
    }

    private fun record(): OperationRecord = OperationRecord(
        operationId = "operation_1",
        kind = OperationKind.PROJECT_DEPLOY,
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
        activityBatchId = "operation_1",
        recoveryMode = OperationRecoveryMode.ACTIVITY,
    )
}
