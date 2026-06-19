package app.getarcane.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class AnsiSanitizerTest {
    @Test
    fun strip_returnsSameInstanceWhenNoAnsiControlCharactersArePresent() {
        val plain = "2026-06-16T10:00:00Z  app  ready\n  indented"

        assertSame(plain, AnsiSanitizer.strip(plain))
    }

    @Test
    fun strip_removesCommonSgrSequencesWhilePreservingContent() {
        val input = "before \u001b[31mred\u001b[0m and \u001b[32mgreen\u001b[0m after"

        assertEquals("before red and green after", AnsiSanitizer.strip(input))
    }

    @Test
    fun strip_removesSgrSequencesWithMultipleNumericParameters() {
        val input = "status=\u001b[1;32mrunning\u001b[0m"

        assertEquals("status=running", AnsiSanitizer.strip(input))
    }

    @Test
    fun strip_preservesTimestampsWhitespaceAndLineBreaksExactly() {
        val input = "2026-06-16T10:00:00Z  \u001b[31mERR\u001b[0m\n\tsecond  line  "

        assertEquals("2026-06-16T10:00:00Z  ERR\n\tsecond  line  ", AnsiSanitizer.strip(input))
    }

    @Test
    fun strip_dropsIncompleteAndUnknownEscapeSequencesGracefully() {
        val input = "alpha\u001b[?999zbeta\u001b]2;title\u0007gamma\u001bXdelta incomplete\u001b[31"

        assertEquals("alphabetagammadelta incomplete", AnsiSanitizer.strip(input))
    }

    @Test
    fun strip_keepsScanningOscUntilBelOrStringTerminator() {
        val input = "before\u001b]2;foo\u001b[31mbar\u0007after \u001b]2;title\u001b\\done"

        assertEquals("beforeafter done", AnsiSanitizer.strip(input))
    }

    @Test
    fun parseSgr_returnsStyledSegmentsForCommonForegroundColorsAndReset() {
        val segments = AnsiSanitizer.parseSgr("plain \u001b[31mred\u001b[0m \u001b[32mgreen")

        assertEquals(
            listOf(
                AnsiSanitizer.StyledText("plain "),
                AnsiSanitizer.StyledText("red", foreground = AnsiSanitizer.Foreground.Red),
                AnsiSanitizer.StyledText(" "),
                AnsiSanitizer.StyledText("green", foreground = AnsiSanitizer.Foreground.Green),
            ),
            segments,
        )
    }

    @Test
    fun parseSgr_supportsBoldAndMultipleNumericParameters() {
        val segments = AnsiSanitizer.parseSgr("status=\u001b[1;33mWARN\u001b[22;39m ok")

        assertEquals(
            listOf(
                AnsiSanitizer.StyledText("status="),
                AnsiSanitizer.StyledText("WARN", foreground = AnsiSanitizer.Foreground.Yellow, bold = true),
                AnsiSanitizer.StyledText(" ok"),
            ),
            segments,
        )
    }

    @Test
    fun parseSgr_treatsEmptyParametersAsReset() {
        val segments = AnsiSanitizer.parseSgr("\u001b[31;mplain")

        assertEquals(listOf(AnsiSanitizer.StyledText("plain")), segments)
    }

    @Test
    fun parseSgr_supportsRemainingReadableForegroundColorsAndDefault() {
        val segments = AnsiSanitizer.parseSgr("\u001b[35mmag\u001b[36mcyan\u001b[37mwhite\u001b[39mdefault")

        assertEquals(
            listOf(
                AnsiSanitizer.StyledText("mag", foreground = AnsiSanitizer.Foreground.Magenta),
                AnsiSanitizer.StyledText("cyan", foreground = AnsiSanitizer.Foreground.Cyan),
                AnsiSanitizer.StyledText("white", foreground = AnsiSanitizer.Foreground.White),
                AnsiSanitizer.StyledText("default"),
            ),
            segments,
        )
    }

    @Test
    fun parseSgr_dropsUnsupportedAndIncompleteSequencesWithoutRawEscapes() {
        val segments = AnsiSanitizer.parseSgr("a\u001b[34mblue\u001b[?25hb\u001b]2;title\u0007c\u001b[31")

        assertEquals(
            listOf(
                AnsiSanitizer.StyledText("a"),
                AnsiSanitizer.StyledText("bluebc", foreground = AnsiSanitizer.Foreground.Blue),
            ),
            segments,
        )
        assertFalse(segments.joinToString(separator = "") { it.text }.contains('\u001b'))
    }
}
