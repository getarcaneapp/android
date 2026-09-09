package app.getarcane.android.ui.screens.whatsnew

import app.getarcane.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun currentAndroidVersionHasAnExactAndroidReleaseNote() {
        assertEquals(
            BuildConfig.VERSION_NAME,
            ReleaseNotes.currentForInstalledVersion(BuildConfig.VERSION_NAME)?.version,
        )
        assertEquals(
            listOf(BuildConfig.VERSION_NAME),
            ReleaseNotes.visibleForInstalledVersion(BuildConfig.VERSION_NAME).map { it.version },
        )
        assertTrue(BuildConfig.VERSION_CODE > 260602)
        assertTrue(
            ReleaseNotes.all
                .flatMap { it.new + it.changed + it.fixed }
                .none { bullet ->
                    listOf("iOS", "TestFlight", "VoiceOver", "Liquid Glass", "Swift SDK")
                        .any { copiedClaim -> copiedClaim in bullet.text }
                },
        )
    }

    @Test
    fun futureNotesStayHiddenFromAnOlderInstalledVersion() {
        val notes = listOf(
            ReleaseNote(version = "0.2.0", new = listOf(Bullet("Future"))),
            ReleaseNote(version = "0.1.0", new = listOf(Bullet("Shipped"))),
        )

        assertEquals(listOf("0.1.0"), visibleReleaseNotes("0.1.0", notes).map { it.version })
        assertEquals("0.1.0", currentReleaseNote("0.1.0", notes)?.version)
    }

    @Test
    fun presentationSortsVersionsSemanticallyAndHandlesPrereleases() {
        val notes = listOf(
            ReleaseNote(version = "0.9.0"),
            ReleaseNote(version = "0.10.0-alpha.2"),
            ReleaseNote(version = "0.10.0"),
            ReleaseNote(version = "0.10.0-alpha.10"),
        )

        assertEquals(
            listOf("0.10.0", "0.10.0-alpha.10", "0.10.0-alpha.2", "0.9.0"),
            visibleReleaseNotes("0.10.0", notes).map { it.version },
        )
        assertEquals(
            listOf("0.10.0-alpha.2", "0.9.0"),
            visibleReleaseNotes("0.10.0-alpha.2", notes).map { it.version },
        )
    }

    @Test
    fun invalidInstalledVersionCannotExposeAnyRelease() {
        assertTrue(visibleReleaseNotes("development", ReleaseNotes.all).isEmpty())
        assertNull(currentReleaseNote("development", ReleaseNotes.all))
    }
}
