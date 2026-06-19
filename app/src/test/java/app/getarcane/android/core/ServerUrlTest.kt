package app.getarcane.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test
    fun normalizeAddsHttpsAndTrimsTrailingSlash() {
        assertEquals("https://arcane.example.com", ServerUrl.normalize(" arcane.example.com/ "))
    }

    @Test
    fun normalizeStripsPastedWebRoutesFromRootDeployments() {
        assertEquals("https://arcane.example.com", ServerUrl.normalize("https://arcane.example.com/dashboard"))
        assertEquals("https://arcane.example.com", ServerUrl.normalize("https://arcane.example.com/login"))
        assertEquals("https://arcane.example.com", ServerUrl.normalize("https://arcane.example.com/api"))
    }

    @Test
    fun normalizeStripsPastedWebRoutesAfterDeploymentSubpath() {
        assertEquals("https://example.com/arcane", ServerUrl.normalize("https://example.com/arcane/dashboard"))
        assertEquals("https://example.com/arcane", ServerUrl.normalize("https://example.com/arcane/login"))
        assertEquals("https://example.com/arcane", ServerUrl.normalize("https://example.com/arcane/api"))
    }

    @Test
    fun normalizePreservesRealDeploymentSubpaths() {
        assertEquals("https://example.com/arcane", ServerUrl.normalize("https://example.com/arcane"))
        assertEquals("https://example.com/arcane/v2", ServerUrl.normalize("https://example.com/arcane/v2/"))
    }

    @Test
    fun normalizeDropsQueryAndFragmentFromPastedBrowserRoutes() {
        assertEquals(
            "https://arcane.example.com",
            ServerUrl.normalize("https://arcane.example.com/dashboard?tab=updates#images"),
        )
    }

    @Test
    fun normalizeRejectsBlankOrHostlessUrls() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("https:///dashboard"))
    }
}
