package app.getarcane.android.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Result wrapper for suspending best-effort work that must never consume coroutine cancellation. */
internal suspend inline fun <T> runSuspendCatching(
    crossinline block: suspend () -> T,
): Result<T> =
    try {
        val value = block()
        currentCoroutineContext().ensureActive()
        Result.success(value)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
