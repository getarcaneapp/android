package app.getarcane.android.core

import app.getarcane.sdk.errors.ArcaneError

/** Human-readable message for an [ArcaneError], mirroring iOS `friendlyErrorMessage(_:)`. */
fun friendlyErrorMessage(error: Throwable): String = when (error) {
    is ArcaneError.Unauthorized -> "Invalid credentials or session expired."
    is ArcaneError.Forbidden -> "You don't have permission to do that."
    is ArcaneError.NotFound -> "Not found."
    is ArcaneError.Conflict -> error.detail ?: "That conflicts with the current state."
    is ArcaneError.Validation -> {
        val first = error.fields.entries.firstOrNull()
        if (first != null) "${first.key}: ${first.value.joinToString(", ")}" else "Validation failed."
    }
    is ArcaneError.RateLimited -> "Too many requests. Please try again shortly."
    is ArcaneError.Server -> error.serverMessage.ifBlank { "Server error (${error.code})." }
    is ArcaneError.Transport -> "Couldn't reach the server. Check the URL and your connection."
    is ArcaneError.Decoding -> "The server returned an unexpected response."
    is ArcaneError.Unknown -> "Unexpected error (HTTP ${error.statusCode})."
    else -> error.message ?: "Something went wrong."
}
