package app.getarcane.android.ui.screens.logs

import app.getarcane.sdk.streaming.LogLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogViewerModelsTest {
    @Test
    fun retentionBoundsLinesCharactersAndReportsDiscardedScope() {
        val buffer = BoundedLogBuffer(maximumLines = 3, maximumCharacters = 9, maximumLineCharacters = 8)

        buffer.append(LogLine("1111", seq = 1u))
        buffer.append(LogLine("2222", seq = 2u))
        buffer.append(LogLine("3333", seq = 3u))

        val snapshot = buffer.snapshot()
        assertEquals(listOf("2222", "3333"), snapshot.lines.map { it.line.text })
        assertEquals(1L, snapshot.discardedLineCount)
        assertTrue(snapshot.hasDiscardedContent)
    }

    @Test
    fun oversizedLineIsBoundedAndReported() {
        val buffer = BoundedLogBuffer(maximumLines = 10, maximumCharacters = 10, maximumLineCharacters = 4)

        buffer.append(LogLine("secret-value"))

        assertEquals("alue", buffer.snapshot().lines.single().line.text)
        assertEquals(1L, buffer.snapshot().truncatedLineCount)
    }

    @Test
    fun sequenceDeduplicatesReconnectTailWithoutCollapsingUnsequencedLines() {
        val buffer = BoundedLogBuffer(maximumLines = 10, maximumCharacters = 100, maximumLineCharacters = 100)

        assertTrue(buffer.append(LogLine("once", seq = 7u, service = "api")))
        assertFalse(buffer.append(LogLine("once", seq = 7u, service = "api")))
        assertTrue(buffer.append(LogLine("repeat")))
        assertTrue(buffer.append(LogLine("repeat")))
        assertEquals(3, buffer.snapshot().lines.size)
    }

    @Test
    fun exportUsesRequestedMetadataAndStripsAnsi() {
        val buffer = BoundedLogBuffer()
        buffer.append(LogLine("\u001b[31mfailed\u001b[0m", level = "stderr", service = "web", timestamp = "2026-09-11T12:00:00Z"))

        assertEquals(
            "[2026-09-11T12:00:00Z] [web] [ERROR] failed",
            exportLogText(buffer.snapshot(), showTimestamps = true),
        )
        assertEquals("[web] [ERROR] failed", exportLogText(buffer.snapshot(), showTimestamps = false))
    }

    @Test
    fun pauseCountsAcceptedLinesAndResumeClearsCount() {
        val paused = LogFollowState().pause().receive(3)
        assertEquals(3, paused.newLinesWhilePaused)
        assertTrue(paused.resume().isFollowing)
        assertEquals(0, paused.resume().newLinesWhilePaused)
    }

    @Test
    fun manualPauseIsNotUndoneByRemainingAtBottom() {
        val paused = LogFollowState().pause(manually = true).resumeFromScroll().receive(2)

        assertFalse(paused.isFollowing)
        assertTrue(paused.isManuallyPaused)
        assertEquals(2, paused.newLinesWhilePaused)
    }

    @Test
    fun scrollPauseResumesWhenReturningToBottom() {
        val resumed = LogFollowState().pause().receive(2).resumeFromScroll()

        assertTrue(resumed.isFollowing)
        assertFalse(resumed.isManuallyPaused)
        assertEquals(0, resumed.newLinesWhilePaused)
    }

    @Test
    fun filenameIsSafeAndDeterministic() {
        assertEquals("project-prod-logs", sanitizedLogFilename("project / prod logs"))
        assertEquals("logs", sanitizedLogFilename("///"))
    }
}
