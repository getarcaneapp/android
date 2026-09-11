package app.getarcane.android.ui.screens.events

import app.getarcane.sdk.models.event.Event
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EventLiveStateTest {
    @Test
    fun pollMergeDeduplicatesUpdatesSortsAndRetainsLoadedWindow() {
        val current = listOf(event("a", "old", 10), event("b", "second", 20))
        val incoming = listOf(event("a", "updated", 30), event("c", "third", 15))

        val merged = mergeEventHistory(current, incoming, limit = 3)

        assertEquals(listOf("a", "b", "c"), merged.map(Event::id))
        assertEquals("updated", merged.first().title)
    }

    @Test
    fun equalTimestampsUseStableIdOrdering() {
        assertEquals(
            listOf("a", "b"),
            mergeEventHistory(emptyList(), listOf(event("b", "B", 10), event("a", "A", 10)), 10).map(Event::id),
        )
    }

    @Test
    fun pollBackoffIsBoundedAndRecoversToBaseInterval() {
        assertEquals(5_000L, nextEventPollDelayMillis(0))
        assertEquals(10_000L, nextEventPollDelayMillis(1))
        assertEquals(60_000L, nextEventPollDelayMillis(20))
        assertEquals(5_000L, nextEventPollDelayMillis(0))
    }

    private fun event(id: String, title: String, seconds: Long) = Event(
        id = id,
        type = "test",
        severity = "info",
        title = title,
        timestamp = Instant.fromEpochSeconds(seconds),
        createdAt = Instant.fromEpochSeconds(seconds),
    )
}
