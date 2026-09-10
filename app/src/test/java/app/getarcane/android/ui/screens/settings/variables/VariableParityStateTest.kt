package app.getarcane.android.ui.screens.settings.variables

import app.getarcane.sdk.models.environment.Environment
import app.getarcane.sdk.models.user.User
import app.getarcane.sdk.models.variable.GlobalVariable
import app.getarcane.sdk.models.variable.VariableSyncState
import app.getarcane.sdk.models.variable.EnvironmentSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VariableParityStateTest {
    @Test
    fun `permissions are independently gated from the global bucket`() {
        val user = User(
            id = "user",
            username = "operator",
            permissionsByEnv = mapOf(
                User.GLOBAL_PERMISSIONS_KEY to listOf("variables:read", "variables:sync"),
                "environment-1" to listOf("variables:update"),
            ),
        )

        val policy = variablePermissionPolicy(user)

        assertTrue(policy.canRead)
        assertTrue(policy.canSync)
        assertFalse(policy.canCreate)
        assertFalse(policy.canUpdate)
        assertFalse(policy.canDelete)
    }

    @Test
    fun `secret values are discarded before display search and accessibility`() {
        val secret = sdkVariable(key = "TOKEN", value = "needle-secret", secret = true).toVariableDisplay()

        assertNull(secret.visibleValue)
        assertTrue(filterVariables(listOf(secret), "TOKEN").contains(secret))
        assertTrue(filterVariables(listOf(secret), "env-1").contains(secret))
        assertTrue(filterVariables(listOf(secret), "needle-secret").isEmpty())
        assertFalse(variableAccessibilityLabel(secret).contains("needle-secret"))
    }

    @Test
    fun `plain value participates in search and concurrent revision`() {
        val original = sdkVariable(value = "old").toVariableDisplay()
        val changed = sdkVariable(value = "new").toVariableDisplay()
        val draft = original.toEditorDraft()

        assertEquals(listOf(original), filterVariables(listOf(original), "old"))
        assertTrue(concurrentVariableEdit(draft, listOf(changed)) is VariableEditConflict.Changed)
    }

    @Test
    fun `deleted edit is a distinct recoverable conflict`() {
        val draft = sdkVariable().toVariableDisplay().toEditorDraft()

        assertSame(VariableEditConflict.Deleted, concurrentVariableEdit(draft, emptyList()))
    }

    @Test
    fun `create supports more than twenty deduplicated selected environments`() {
        val selected = (0..30).map { "env-$it" } + listOf("env-4", "env-20")
        val draft = VariableEditorDraft(
            key = " DATABASE_URL ",
            allEnvironments = false,
            environmentIds = selected.toSet(),
        )

        val write = variableWritePlan(draft, "postgres://database").getOrThrow() as VariableWritePlan.Create

        assertEquals("DATABASE_URL", write.request.key)
        assertEquals(31, write.request.environmentIds.size)
        assertEquals(write.request.environmentIds.distinct(), write.request.environmentIds)
        assertFalse(write.request.allEnvironments)
    }

    @Test
    fun `all environment create clears stale selected ids`() {
        val write = variableWritePlan(
            VariableEditorDraft(key = "KEY", allEnvironments = true, environmentIds = setOf("old")),
            "value",
        ).getOrThrow() as VariableWritePlan.Create

        assertTrue(write.request.allEnvironments)
        assertTrue(write.request.environmentIds.isEmpty())
    }

    @Test
    fun `unchanged secret preserves write only value but conversion requires replacement`() {
        val variable = sdkVariable(secret = true, value = "server-must-be-discarded").toVariableDisplay()
        val preserved = variableWritePlan(variable.toEditorDraft(), "").getOrThrow() as VariableWritePlan.Update

        assertNull(preserved.request.value)
        assertThrows(IllegalArgumentException::class.java) {
            variableWritePlan(variable.toEditorDraft().copy(isSecret = false), "").getOrThrow()
        }
    }

    @Test
    fun `secret input is scrubbed on scope failure close and session change`() {
        val opened = reduceSensitiveEditor(
            SensitiveEditorState("server-a#user-a"),
            SensitiveEditorEvent.Open(VariableEditorDraft(key = "TOKEN", isSecret = true), "secret"),
        )
        val scopeChanged = reduceSensitiveEditor(
            opened,
            SensitiveEditorEvent.DraftChanged(opened.editor!!.copy(allEnvironments = false)),
        )
        val typedAgain = reduceSensitiveEditor(scopeChanged, SensitiveEditorEvent.ValueChanged("secret-2"))

        assertEquals("", scopeChanged.value)
        assertEquals("", reduceSensitiveEditor(typedAgain, SensitiveEditorEvent.Failed).value)
        assertEquals("", reduceSensitiveEditor(typedAgain, SensitiveEditorEvent.Closed).value)
        assertEquals(
            SensitiveEditorState("server-b#user-b"),
            reduceSensitiveEditor(typedAgain, SensitiveEditorEvent.SessionChanged("server-b#user-b")),
        )
    }

    @Test
    fun `environment choices retain unknown scope and deduplicate more than twenty rows`() {
        val environments = (0..30).map(::environment) + environment(4)

        val choices = environmentChoices(environments, setOf("removed-environment"))

        assertEquals(32, choices.size)
        assertEquals(1, choices.count { it.id == "env-4" })
        assertTrue(choices.any { it.id == "removed-environment" && it.name == "removed-environment" })
    }

    @Test
    fun `current environment names replace retained id fallbacks in an editor`() {
        val retained = listOf(
            EnvironmentChoice("env-1", "env-1"),
            EnvironmentChoice("removed", "removed"),
        )

        val merged = mergeEnvironmentChoices(
            authoritative = listOf(EnvironmentChoice("env-1", "Production")),
            retained = retained,
        )

        assertEquals("Production", merged.single { it.id == "env-1" }.name)
        assertEquals("removed", merged.single { it.id == "removed" }.name)
    }

    @Test
    fun `polling is bounded and reports pending timeout`() = runBlocking {
        var loads = 0
        val pending = listOf(status("env", VariableSyncState.PENDING))

        val result = pollVariableSync(
            initial = pending,
            maxAttempts = 3,
            pause = {},
            loadStatus = { loads++; pending },
        )

        assertEquals(3, loads)
        assertEquals(SyncSummary.TIMED_OUT, result.summary)
    }

    @Test
    fun `transient poll errors consume bounded attempts without hiding last status`() = runBlocking {
        var loads = 0
        val pending = listOf(status("env", VariableSyncState.PENDING))

        val result = pollVariableSync(
            initial = pending,
            maxAttempts = 2,
            pause = {},
            loadStatus = { loads++; error("temporary") },
        )

        assertEquals(2, loads)
        assertEquals(pending, result.statuses)
        assertEquals(SyncSummary.TIMED_OUT, result.summary)
    }

    @Test
    fun `polling stops before duplicate work after generation becomes stale`() = runBlocking {
        var current = true
        var loads = 0
        val pending = listOf(status("env", VariableSyncState.PENDING))

        pollVariableSync(
            initial = pending,
            maxAttempts = 30,
            isCurrent = { current },
            pause = { current = false },
            loadStatus = { loads++; pending },
        )

        assertEquals(0, loads)
    }

    @Test
    fun `poll cancellation is never converted to timeout`() {
        val cancelled = CancellationException("scope changed")

        val actual = assertThrows(CancellationException::class.java) {
            runBlocking {
                pollVariableSync(
                    initial = listOf(status("env", VariableSyncState.PENDING)),
                    pause = { throw cancelled },
                    loadStatus = { emptyList() },
                )
            }
        }

        assertSame(cancelled, actual)
    }

    @Test
    fun `mixed errors unknown and successes are partial`() {
        assertEquals(
            SyncSummary.PARTIAL,
            summarizeSync(
                listOf(
                    status("one", VariableSyncState.SYNCED),
                    status("two", VariableSyncState.ERROR),
                    status("three", VariableSyncState.UNKNOWN),
                ),
            ),
        )
    }

    private fun sdkVariable(
        key: String = "KEY",
        value: String = "value",
        secret: Boolean = false,
    ) = GlobalVariable(
        id = "variable-1",
        key = key,
        value = value,
        isSecret = secret,
        allEnvironments = false,
        environmentIds = listOf("env-1"),
        createdAt = Instant.parse("2026-09-09T00:00:00Z"),
    )

    private fun environment(index: Int) = Environment(
        id = "env-$index",
        name = "Environment $index",
        apiUrl = "https://environment-$index.example.com",
        status = "online",
    )

    private fun status(id: String, state: VariableSyncState) = EnvironmentSyncStatus(
        environmentId = id,
        environmentName = id,
        status = state,
    )
}
