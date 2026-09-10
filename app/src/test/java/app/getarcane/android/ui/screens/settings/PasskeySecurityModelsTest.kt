package app.getarcane.android.ui.screens.settings

import kotlinx.coroutines.CancellationException
import app.getarcane.sdk.android.passkey.PasskeyCeremonyCancelledException
import app.getarcane.sdk.models.auth.StepUpGrant
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeySecurityModelsTest {
    @Test fun `first enrollment may use the active interactive session`() {
        assertTrue(
            canBeginPasskeyEnrollment(
                0,
                canEnrollWithActiveSession = true,
                requiresStepUp = false,
                hasStepUpGrant = false,
            ),
        )
        assertFalse(
            canBeginPasskeyEnrollment(
                0,
                canEnrollWithActiveSession = true,
                requiresStepUp = true,
                hasStepUpGrant = false,
            ),
        )
        assertFalse(
            canBeginPasskeyEnrollment(
                1,
                canEnrollWithActiveSession = true,
                requiresStepUp = false,
                hasStepUpGrant = false,
            ),
        )
        assertTrue(
            canBeginPasskeyEnrollment(
                2,
                canEnrollWithActiveSession = false,
                requiresStepUp = true,
                hasStepUpGrant = true,
            ),
        )
    }

    @Test fun `last passkey deletion follows the authoritative capability`() {
        assertFalse(canDeletePasskey(1, canDeleteLastPasskey = false, hasStepUpGrant = true))
        assertTrue(canDeletePasskey(1, canDeleteLastPasskey = true, hasStepUpGrant = true))
        assertTrue(canDeletePasskey(2, canDeleteLastPasskey = false, hasStepUpGrant = true))
        assertFalse(canDeletePasskey(2, canDeleteLastPasskey = true, hasStepUpGrant = false))
    }

    @Test fun `step-up grants expire at their typed server deadline`() {
        val grant = StepUpGrant(
            token = "not-a-real-token",
            expiresAt = Instant.parse("2026-09-09T12:01:00Z"),
        )

        assertSame(grant, validStepUpGrant(grant, Instant.parse("2026-09-09T12:00:00Z")))
        assertNull(validStepUpGrant(grant, Instant.parse("2026-09-09T12:01:00Z")))
        assertEquals(60_000L, stepUpExpiryDelayMillis(grant, Instant.parse("2026-09-09T12:00:00Z")))
        assertEquals(0L, stepUpExpiryDelayMillis(grant, Instant.parse("2026-09-09T12:02:00Z")))
    }

    @Test fun `background or replacement invalidates a pending sensitive operation`() {
        val generation = SensitiveOperationGeneration()
        val first = generation.begin()
        assertTrue(generation.isCurrent(first))

        generation.invalidate()
        assertFalse(generation.isCurrent(first))

        val replacement = generation.begin()
        assertTrue(generation.isCurrent(replacement))
        assertFalse(generation.isCurrent(first))
    }

    @Test fun `failure is recoverable without exposing its inputs`() = runBlocking {
        val failure = IllegalStateException("provider unavailable")
        val result = runSensitiveMutation { throw failure }
        assertTrue(result is SensitiveMutationResult.Failure)
        assertSame(failure, (result as SensitiveMutationResult.Failure).cause)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`(): Unit = runBlocking {
        runSensitiveMutation { throw CancellationException("cancelled") }
    }

    @Test fun `credential provider cancellation is a normal recoverable terminal state`() = runBlocking {
        assertSame(
            SensitiveMutationResult.Cancelled,
            runSensitiveMutation { throw PasskeyCeremonyCancelledException() },
        )
    }

    @Test fun `successful sensitive results publish only through the success value`() = runBlocking {
        val result = runSensitiveMutation { "safe-result" }
        assertEquals("safe-result", (result as SensitiveMutationResult.Success).value)
    }

    @Test fun `disabling mfa clears newly generated recovery codes`() {
        val codes = listOf("one-time-a", "one-time-b")
        assertEquals(codes, recoveryCodesAfterMfaState(codes, enabled = true))
        assertTrue(recoveryCodesAfterMfaState(codes, enabled = false).isEmpty())
    }

    @Test fun `recovery code submission matches iOS whitespace handling`() {
        assertEquals("one-time-code", normalizedRecoveryCode("  one-time-code\n"))
    }
}
