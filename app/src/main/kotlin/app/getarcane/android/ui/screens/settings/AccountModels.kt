package app.getarcane.android.ui.screens.settings

import app.getarcane.sdk.models.user.UpdateProfile
import app.getarcane.sdk.models.user.User
import kotlinx.coroutines.CancellationException

internal data class AccountPasswordDraft(
    val current: String = "",
    val new: String = "",
    val confirmation: String = "",
) {
    val hasInput: Boolean get() = current.isNotEmpty() || new.isNotEmpty() || confirmation.isNotEmpty()
}

internal data class AccountSavePlan(
    val profileUpdate: UpdateProfile?,
    val currentPassword: String?,
    val newPassword: String?,
    val emailError: String? = null,
    val passwordError: String? = null,
) {
    val isValid: Boolean get() = emailError == null && passwordError == null
    val hasChanges: Boolean get() = profileUpdate != null || newPassword != null
    val canSave: Boolean get() = isValid && hasChanges
}

internal fun accountSavePlan(
    user: User,
    displayName: String,
    email: String,
    password: AccountPasswordDraft,
): AccountSavePlan {
    val trimmedDisplayName = displayName.trim()
    val trimmedEmail = email.trim()
    val isOidcUser = !user.oidcSubjectId.isNullOrBlank()
    val profileChanged = !isOidcUser &&
        (trimmedDisplayName != user.displayName.orEmpty() || trimmedEmail != user.email.orEmpty())
    val emailError = if (profileChanged && trimmedEmail.isNotEmpty() && !isPlausibleEmail(trimmedEmail)) {
        "Enter a valid email address."
    } else {
        null
    }
    val passwordError = when {
        isOidcUser || !password.hasInput -> null
        password.current.isEmpty() -> "Enter your current password."
        password.new.length < 8 -> "New password must be at least 8 characters."
        password.new != password.confirmation -> "Passwords don't match."
        else -> null
    }
    return AccountSavePlan(
        profileUpdate = if (profileChanged) {
            UpdateProfile(displayName = trimmedDisplayName, email = trimmedEmail)
        } else {
            null
        },
        currentPassword = password.current.takeIf { !isOidcUser && password.hasInput },
        newPassword = password.new.takeIf { !isOidcUser && password.hasInput },
        emailError = emailError,
        passwordError = passwordError,
    )
}

private fun isPlausibleEmail(value: String): Boolean {
    if (value.any(Char::isWhitespace)) return false
    val separator = value.indexOf('@')
    return separator > 0 && separator == value.lastIndexOf('@') && separator < value.lastIndex
}

internal enum class AccountSaveStage { PROFILE, PASSWORD }

internal sealed interface AccountSaveOutcome {
    data class Success(val profileUpdated: Boolean, val passwordChanged: Boolean) : AccountSaveOutcome
    data class Failure(
        val stage: AccountSaveStage,
        val profileUpdated: Boolean,
        val cause: Throwable,
    ) : AccountSaveOutcome
}

/**
 * Applies profile then password changes, matching iOS. A successful profile mutation is published
 * before attempting the password so partial success remains truthful and recoverable.
 */
internal suspend fun saveAccountChanges(
    plan: AccountSavePlan,
    updateProfile: suspend (UpdateProfile) -> User,
    publishProfile: (User) -> Unit,
    changePassword: suspend (current: String, new: String) -> Unit,
): AccountSaveOutcome {
    require(plan.canSave)
    var profileUpdated = false
    plan.profileUpdate?.let { update ->
        val updated = try {
            updateProfile(update)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return AccountSaveOutcome.Failure(AccountSaveStage.PROFILE, false, e)
        }
        publishProfile(updated)
        profileUpdated = true
    }
    plan.newPassword?.let { newPassword ->
        try {
            changePassword(requireNotNull(plan.currentPassword), newPassword)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return AccountSaveOutcome.Failure(AccountSaveStage.PASSWORD, profileUpdated, e)
        }
    }
    return AccountSaveOutcome.Success(
        profileUpdated = profileUpdated,
        passwordChanged = plan.newPassword != null,
    )
}
