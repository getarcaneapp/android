package app.getarcane.android.ui.screens.projects

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectStreamStateTest {
    @Test
    fun `normal completion succeeds`() {
        assertEquals(ProjectStreamOutcome.Success, ProjectStreamOutcome.Running.onCompleted())
    }

    @Test
    fun `error frame remains failed after normal flow completion`() {
        val failed = ProjectStreamOutcome.Running.onServerEvent("pull denied")

        assertEquals(ProjectStreamOutcome.Failure("pull denied"), failed)
        assertEquals(failed, failed.onCompleted())
    }

    @Test
    fun `later exception does not replace authoritative server error`() {
        val failed = ProjectStreamOutcome.Running.onServerEvent("compose failed")

        assertEquals(failed, failed.onException("connection closed"))
    }

    @Test
    fun `transport exception produces failure`() {
        assertEquals(
            ProjectStreamOutcome.Failure("offline"),
            ProjectStreamOutcome.Running.onException("offline"),
        )
    }
}
