package app.getarcane.android.ui.screens.settings

import app.getarcane.sdk.models.user.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountModelsTest {
    private val user = User(
        id = "user-1",
        username = "michael",
        displayName = "Michael",
        email = "michael@example.com",
    )

    @Test
    fun `profile fields are trimmed and empty email is allowed`() {
        val plan = accountSavePlan(
            user,
            displayName = "  Mike  ",
            email = "  ",
            password = AccountPasswordDraft(),
        )

        assertTrue(plan.canSave)
        assertEquals("Mike", plan.profileUpdate?.displayName)
        assertEquals("", plan.profileUpdate?.email)
        assertNull(plan.emailError)
    }

    @Test
    fun `invalid email and incomplete password prevent save`() {
        val plan = accountSavePlan(
            user,
            displayName = "Michael",
            email = "not-an-email",
            password = AccountPasswordDraft(current = "current", new = "short", confirmation = "different"),
        )

        assertFalse(plan.canSave)
        assertEquals("Enter a valid email address.", plan.emailError)
        assertEquals("New password must be at least 8 characters.", plan.passwordError)
    }

    @Test
    fun `oidc identity cannot submit profile or password changes`() {
        val oidcUser = user.copy(oidcSubjectId = "subject")

        val plan = accountSavePlan(
            oidcUser,
            displayName = "Changed",
            email = "changed@example.com",
            password = AccountPasswordDraft("current", "new-password", "new-password"),
        )

        assertFalse(plan.hasChanges)
        assertNull(plan.profileUpdate)
        assertNull(plan.newPassword)
    }

    @Test
    fun `profile success is published before password failure`() = runBlocking {
        val plan = accountSavePlan(
            user,
            displayName = "Mike",
            email = user.email.orEmpty(),
            password = AccountPasswordDraft("wrong", "new-password", "new-password"),
        )
        val published = mutableListOf<User>()

        val outcome = saveAccountChanges(
            plan = plan,
            updateProfile = { user.copy(displayName = it.displayName) },
            publishProfile = published::add,
            changePassword = { _, _ -> error("incorrect current password") },
        )

        assertTrue(outcome is AccountSaveOutcome.Failure)
        outcome as AccountSaveOutcome.Failure
        assertEquals(AccountSaveStage.PASSWORD, outcome.stage)
        assertTrue(outcome.profileUpdated)
        assertEquals("Mike", published.single().displayName)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never converted to a save failure`(): Unit = runBlocking {
        val plan = accountSavePlan(user, "Mike", user.email.orEmpty(), AccountPasswordDraft())
        saveAccountChanges(
            plan = plan,
            updateProfile = { throw CancellationException("cancelled") },
            publishProfile = {},
            changePassword = { _, _ -> },
        )
    }

    @Test
    fun `initials use at most the first two words`() {
        assertEquals("MK", accountInitials("Michael Kaltner Example"))
        assertEquals("?", accountInitials("  "))
    }
}
