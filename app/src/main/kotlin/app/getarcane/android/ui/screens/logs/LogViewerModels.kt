package app.getarcane.android.ui.screens.logs

import app.getarcane.android.core.AnsiSanitizer
import app.getarcane.sdk.streaming.LogLine

internal data class RetainedLogLine(val id: Long, val line: LogLine)

internal sealed interface LogConnectionState {
    data object Idle : LogConnectionState
    data object Connecting : LogConnectionState
    data object Live : LogConnectionState
    data object Ended : LogConnectionState
    data class Failed(val message: String) : LogConnectionState
}

internal data class LogRetentionSnapshot(
    val lines: List<RetainedLogLine> = emptyList(),
    val discardedLineCount: Long = 0,
    val truncatedLineCount: Long = 0,
) {
    val hasDiscardedContent: Boolean get() = discardedLineCount > 0 || truncatedLineCount > 0
}

/** Bounded log retention shared by container and project viewers. */
internal class BoundedLogBuffer(
    private val maximumLines: Int = MAXIMUM_RETAINED_LINES,
    private val maximumCharacters: Int = MAXIMUM_RETAINED_CHARACTERS,
    private val maximumLineCharacters: Int = MAXIMUM_LINE_CHARACTERS,
) {
    private data class Entry(val retained: RetainedLogLine, val sequenceKey: String?)

    private val entries = ArrayList<Entry>()
    private val sequenceKeys = HashSet<String>()
    private var nextId = 0L
    private var discardedLineCount = 0L
    private var truncatedLineCount = 0L
    private var retainedCharacters = 0

    fun append(rawLine: LogLine): Boolean {
        val sequenceKey = rawLine.seq?.let { "${rawLine.service.orEmpty()}:$it" }
        if (sequenceKey != null && sequenceKey in sequenceKeys) return false

        val effectiveLineLimit = minOf(maximumLineCharacters, maximumCharacters)
        val boundedText = if (rawLine.text.length > effectiveLineLimit) {
            truncatedLineCount++
            rawLine.text.takeLast(effectiveLineLimit)
        } else {
            rawLine.text
        }
        val line = rawLine.copy(
            text = boundedText,
            timestamp = rawLine.timestamp?.take(MAXIMUM_METADATA_CHARACTERS),
            service = rawLine.service?.take(MAXIMUM_METADATA_CHARACTERS),
            level = rawLine.level?.take(MAXIMUM_LEVEL_CHARACTERS),
        )
        val entry = Entry(RetainedLogLine(nextId++, line), sequenceKey)
        entries += entry
        retainedCharacters += retainedCharacters(line)
        sequenceKey?.let(sequenceKeys::add)

        while (entries.size > maximumLines || retainedCharacters > maximumCharacters) {
            val removed = entries.removeAt(0)
            retainedCharacters -= retainedCharacters(removed.retained.line)
            removed.sequenceKey?.let(sequenceKeys::remove)
            discardedLineCount++
        }
        return true
    }

    fun clear() {
        entries.clear()
        sequenceKeys.clear()
        nextId = 0
        discardedLineCount = 0
        truncatedLineCount = 0
        retainedCharacters = 0
    }

    fun snapshot(): LogRetentionSnapshot = LogRetentionSnapshot(
        lines = entries.map { it.retained },
        discardedLineCount = discardedLineCount,
        truncatedLineCount = truncatedLineCount,
    )

    private fun retainedCharacters(line: LogLine): Int =
        line.text.length + line.timestamp.orEmpty().length + line.service.orEmpty().length + line.level.orEmpty().length

    companion object {
        const val MAXIMUM_RETAINED_LINES = 5_000
        const val MAXIMUM_RETAINED_CHARACTERS = 300_000
        const val MAXIMUM_LINE_CHARACTERS = 32_000
        private const val MAXIMUM_METADATA_CHARACTERS = 256
        private const val MAXIMUM_LEVEL_CHARACTERS = 64
    }
}

internal data class LogFollowState(
    val isFollowing: Boolean = true,
    val newLinesWhilePaused: Int = 0,
    val isManuallyPaused: Boolean = false,
) {
    fun pause(manually: Boolean = false): LogFollowState = copy(
        isFollowing = false,
        isManuallyPaused = isManuallyPaused || manually,
    )

    fun resume(): LogFollowState = LogFollowState(isFollowing = true)

    fun resumeFromScroll(): LogFollowState = if (isManuallyPaused) this else resume()

    fun receive(acceptedLines: Int): LogFollowState = if (isFollowing || acceptedLines <= 0) {
        this
    } else {
        copy(newLinesWhilePaused = (newLinesWhilePaused + acceptedLines).coerceAtMost(BoundedLogBuffer.MAXIMUM_RETAINED_LINES))
    }
}

internal fun exportLogText(snapshot: LogRetentionSnapshot, showTimestamps: Boolean): String =
    snapshot.lines.joinToString("\n") { entry ->
        val line = entry.line
        buildList {
            if (showTimestamps) line.timestamp?.trim()?.takeIf(String::isNotEmpty)?.let { add("[$it]") }
            line.service?.trim()?.takeIf(String::isNotEmpty)?.let { add("[$it]") }
            normalizedLogLevel(line.level)?.let { add("[$it]") }
            add(AnsiSanitizer.strip(line.text))
        }.joinToString(" ")
    }

internal fun normalizedLogLevel(level: String?): String? {
    val value = level?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when (value.lowercase()) {
        "error", "err", "stderr" -> "ERROR"
        "warn", "warning" -> "WARN"
        "debug" -> "DEBUG"
        else -> value.uppercase()
    }
}

internal fun sanitizedLogFilename(value: String): String {
    val sanitized = value.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .split('-')
        .filter(String::isNotEmpty)
        .joinToString("-")
    return sanitized.ifEmpty { "logs" }
}
