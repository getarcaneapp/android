package app.getarcane.android.core

/**
 * Best-effort plain-text sanitizer for ANSI terminal control sequences.
 *
 * The Android log surfaces do not render ANSI SGR styling yet, so this utility preserves all regular
 * log content exactly while removing common SGR sequences such as ESC[0m, ESC[31m, ESC[32m, and
 * ESC[1;32m. It also drops broader CSI/OSC escape sequences, short two-byte escapes, and BEL so
 * incomplete or unknown terminal control output does not leak raw control bytes into Compose Text.
 */
object AnsiSanitizer {
    fun strip(input: String): String {
        if (!input.contains(ESC_CHAR) && !input.contains(BEL)) return input
        val out = StringBuilder(input.length)
        var i = 0
        val n = input.length
        while (i < n) {
            when (input[i]) {
                BEL -> i++
                ESC_CHAR -> i = consumeEscape(input, i + 1)
                else -> {
                    out.append(input[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Returns the index just past the consumed escape sequence (ESC already skipped at [start]). */
    private fun consumeEscape(s: String, start: Int): Int {
        var i = start
        val n = s.length
        if (i >= n) return i
        when (s[i]) {
            '[' -> {
                i++
                while (i < n) {
                    val value = s[i].code
                    i++
                    if (value in 0x40..0x7E) break
                }
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
            }
            '(', ')', '*', '+', '%', '#' -> i += 2
            else -> i++
        }
        return i
    }

    private const val ESC_CHAR = '\u001b'
    private const val BEL = '\u0007'
}
