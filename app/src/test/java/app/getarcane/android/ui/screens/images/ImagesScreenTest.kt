package app.getarcane.android.ui.screens.images

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagesScreenTest {
    @Test
    fun initialDestinationSelectsStartRoute() {
        assertEquals("list", ImagesInitialDestination.List.startRoute)
        assertEquals("vulnerabilities", ImagesInitialDestination.Vulnerabilities.startRoute)
    }
}
