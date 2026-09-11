package app.getarcane.android.ui.screens.containers

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalOutputStateTest {
    @Test
    fun retentionKeepsLatestCharactersAndReportsDiscardedPrefix() {
        val buffer = TerminalOutputBuffer(maximumCharacters = 5)
        buffer.append("abc")

        val snapshot = buffer.append("defg")

        assertEquals("cdefg", snapshot.text)
        assertEquals(2L, snapshot.discardedCharacters)
    }

    @Test
    fun clearResetsContentAndRetentionEvidence() {
        val buffer = TerminalOutputBuffer(maximumCharacters = 2)
        val bounded = buffer.append("secret")

        assertEquals("et", bounded.text)
        assertEquals(4L, bounded.discardedCharacters)

        assertEquals(TerminalOutputSnapshot(), buffer.clear())
    }
}
