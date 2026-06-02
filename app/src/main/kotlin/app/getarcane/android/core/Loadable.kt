package app.getarcane.android.core

/** Simple async UI state for one-shot loads. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Error(val message: String) : Loadable<Nothing>
    data class Success<T>(val value: T) : Loadable<T>
}
