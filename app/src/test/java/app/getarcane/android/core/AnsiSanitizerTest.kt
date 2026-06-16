package app.getarcane.android.core

import org.junit.Assert.assertEquals
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
}
