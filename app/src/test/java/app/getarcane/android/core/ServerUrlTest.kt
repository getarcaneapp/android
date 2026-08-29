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
    fun normalizeCanonicalizesHostAndDefaultPorts() {
        assertEquals("https://arcane.example.com", ServerUrl.normalize("HTTPS://ARCANE.EXAMPLE.COM.:443/"))
        assertEquals("http://arcane.example.com", ServerUrl.normalize("http://ARCANE.EXAMPLE.COM:80"))
        assertEquals("https://arcane.example.com:8443", ServerUrl.normalize("https://ARCANE.EXAMPLE.COM:8443"))
    }

    @Test
    fun normalizeRejectsUnsupportedSchemesAndEmbeddedCredentials() {
        assertNull(ServerUrl.normalize("ftp://arcane.example.com"))
        assertNull(ServerUrl.normalize("https://user:password@arcane.example.com"))
    }

    @Test
    fun normalizeRejectsBlankOrHostlessUrls() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("https:///dashboard"))
    }
}
