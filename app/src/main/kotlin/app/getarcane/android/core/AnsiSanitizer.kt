package app.getarcane.android.core

/**
 * Best-effort ANSI terminal control handling for Android log and terminal surfaces.
 *
 * [strip] preserves all regular log content exactly while removing common terminal control sequences.
 * [parseSgr] additionally returns small style runs for common SGR foreground colors and bold so
 * Compose log rows can render readable colored output without leaking raw escape bytes.
 */
object AnsiSanitizer {
    enum class Foreground { Red, Green, Yellow, Blue, Magenta, Cyan, White, Default }

    data class StyledText(
        val text: String,
        val foreground: Foreground? = null,
        val bold: Boolean = false,
    )

    fun strip(input: String): String {
        if (!input.contains(ESC_CHAR) && !input.contains(BEL)) return input
        return parseSgr(input).joinToString(separator = "") { it.text }
    }

    fun parseSgr(input: String): List<StyledText> {
        if (!input.contains(ESC_CHAR) && !input.contains(BEL)) return listOf(StyledText(input))

        val segments = mutableListOf<StyledText>()
        val text = StringBuilder(input.length)
        var style = TextStyle()
        var i = 0
        val n = input.length

        fun flush() {
            if (text.isEmpty()) return
            segments.add(StyledText(text.toString(), foreground = style.foreground, bold = style.bold))
            text.clear()
        }

        while (i < n) {
            when (input[i]) {
                BEL -> i++
                ESC_CHAR -> {
                    val consumed = consumeEscape(input, i + 1)
                    if (consumed.sgrParameters != null) {
                        flush()
                        style = style.applySgr(consumed.sgrParameters)
                    }
                    i = consumed.nextIndex
                }
                else -> {
                    text.append(input[i])
                    i++
                }
            }
        }
        flush()
        return segments
    }

    /** Returns the index just past the consumed escape sequence (ESC already skipped at [start]). */
    private fun consumeEscape(s: String, start: Int): EscapeResult {
        var i = start
        val n = s.length
        if (i >= n) return EscapeResult(i)
        return when (s[i]) {
            '[' -> {
                val parameterStart = i + 1
                i++
                while (i < n) {
                    val value = s[i].code
                    i++
                    if (value in 0x40..0x7E) {
                        val command = s[i - 1]
                        val parameters = s.substring(parameterStart, i - 1)
                        return EscapeResult(
                            nextIndex = i,
                            sgrParameters = parameters.takeIf { command == 'm' }?.toSgrParameters(),
                        )
                    }
                }
                EscapeResult(i)
            }
            ']' -> {
                i++
                while (i < n) {
                    if (s[i] == BEL) {
                        i++
                        break
                    }
                    if (s[i] == ESC_CHAR) {
                        i += 2
                        break
                    }
                    i++
                }
                EscapeResult(i.coerceAtMost(n))
            }
            '(', ')', '*', '+', '%', '#' -> EscapeResult((i + 2).coerceAtMost(n))
            else -> EscapeResult((i + 1).coerceAtMost(n))
        }
    }

    private data class EscapeResult(
        val nextIndex: Int,
        val sgrParameters: List<Int>? = null,
    )

    private data class TextStyle(
        val foreground: Foreground? = null,
        val bold: Boolean = false,
    ) {
        fun applySgr(parameters: List<Int>): TextStyle {
            val codes = if (parameters.isEmpty()) listOf(0) else parameters
            var next = this
            var index = 0
            while (index < codes.size) {
                when (codes[index]) {
                    0 -> next = TextStyle()
                    1 -> next = next.copy(bold = true)
                    22 -> next = next.copy(bold = false)
                    30 -> next = next.copy(foreground = Foreground.Default)
                    31 -> next = next.copy(foreground = Foreground.Red)
                    32 -> next = next.copy(foreground = Foreground.Green)
                    33 -> next = next.copy(foreground = Foreground.Yellow)
                    34 -> next = next.copy(foreground = Foreground.Blue)
                    35 -> next = next.copy(foreground = Foreground.Magenta)
                    36 -> next = next.copy(foreground = Foreground.Cyan)
                    37 -> next = next.copy(foreground = Foreground.White)
                    39 -> next = next.copy(foreground = null)
                    90 -> next = next.copy(foreground = Foreground.Default)
                    91 -> next = next.copy(foreground = Foreground.Red)
                    92 -> next = next.copy(foreground = Foreground.Green)
                    93 -> next = next.copy(foreground = Foreground.Yellow)
                    94 -> next = next.copy(foreground = Foreground.Blue)
                    95 -> next = next.copy(foreground = Foreground.Magenta)
                    96 -> next = next.copy(foreground = Foreground.Cyan)
                    97 -> next = next.copy(foreground = Foreground.White)
                }
                index++
            }
            return next
        }
    }

    private fun String.toSgrParameters(): List<Int> =
        if (isBlank()) {
            emptyList()
        } else {
            split(';').map { it.toIntOrNull() ?: 0 }
        }

    private const val ESC_CHAR = '\u001b'
    private const val BEL = '\u0007'
}
