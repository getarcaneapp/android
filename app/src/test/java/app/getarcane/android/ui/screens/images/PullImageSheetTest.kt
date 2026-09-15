package app.getarcane.android.ui.screens.images

import org.junit.Assert.assertEquals
import org.junit.Test

class PullImageSheetTest {
    @Test
    fun `parses registry ports tags and digests without leaking into persistence`() {
        assertEquals("registry.local:5000/team/image" to "tag", parseImageReference("registry.local:5000/team/image:tag"))
        assertEquals("registry.local/team/image" to null, parseImageReference("registry.local/team/image@sha256:abc"))
        assertEquals("nginx" to "latest", parseImageReference("nginx:latest"))
    }
}
