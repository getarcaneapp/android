package app.getarcane.android.ui.screens.events

import app.getarcane.sdk.models.event.Event

internal fun mergeEventHistory(
    current: List<Event>,
    incoming: List<Event>,
    limit: Int,
): List<Event> {
    if (limit <= 0) return emptyList()
    val byId = LinkedHashMap<String, Event>()
    current.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }
    return byId.values.sortedWith(
        compareByDescending<Event> { it.timestamp }.thenBy { it.id },
    ).take(limit)
}

internal fun nextEventPollDelayMillis(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 0) return EVENTS_POLL_INTERVAL_MILLIS
    val multiplier = 1L shl consecutiveFailures.coerceAtMost(4)
    return (EVENTS_POLL_INTERVAL_MILLIS * multiplier).coerceAtMost(EVENTS_MAX_BACKOFF_MILLIS)
}

internal const val EVENTS_POLL_INTERVAL_MILLIS = 5_000L
internal const val EVENTS_MAX_BACKOFF_MILLIS = 60_000L
