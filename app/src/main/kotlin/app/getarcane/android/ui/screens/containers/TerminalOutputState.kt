package app.getarcane.android.ui.screens.containers

internal data class TerminalOutputSnapshot(
    val text: String = "",
    val discardedCharacters: Long = 0,
)

/** Keeps terminal scrollback within a fixed character budget and reports every discarded prefix. */
internal class TerminalOutputBuffer(private val maximumCharacters: Int = 200_000) {
    private val text = StringBuilder()
    private var discardedCharacters = 0L

    fun append(value: String): TerminalOutputSnapshot {
        val budget = maximumCharacters.coerceAtLeast(1)
        if (value.length >= budget) {
            discardedCharacters += text.length.toLong() + value.length - budget
            text.clear()
            text.append(value, value.length - budget, value.length)
            return snapshot()
        }
        text.append(value)
        val overflow = text.length - budget
        if (overflow > 0) {
            text.delete(0, overflow)
            discardedCharacters += overflow
        }
        return snapshot()
    }

    fun clear(): TerminalOutputSnapshot {
        text.clear()
        discardedCharacters = 0
        return snapshot()
    }

    fun snapshot(): TerminalOutputSnapshot = TerminalOutputSnapshot(text.toString(), discardedCharacters)
}
