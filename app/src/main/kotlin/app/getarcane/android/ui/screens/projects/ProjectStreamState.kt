package app.getarcane.android.ui.screens.projects

internal sealed interface ProjectStreamOutcome {
    data object Running : ProjectStreamOutcome
    data object Success : ProjectStreamOutcome
    data class Failure(val message: String) : ProjectStreamOutcome
}

internal fun ProjectStreamOutcome.onServerEvent(error: String?): ProjectStreamOutcome =
    when {
        this !is ProjectStreamOutcome.Running -> this
        error.isNullOrBlank() -> this
        else -> ProjectStreamOutcome.Failure(error)
    }

internal fun ProjectStreamOutcome.onCompleted(): ProjectStreamOutcome =
    if (this is ProjectStreamOutcome.Running) ProjectStreamOutcome.Success else this

internal fun ProjectStreamOutcome.onException(message: String): ProjectStreamOutcome =
    if (this is ProjectStreamOutcome.Failure) this else ProjectStreamOutcome.Failure(message)
