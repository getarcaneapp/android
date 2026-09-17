package app.getarcane.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardActivityBadgeTest {
    @Test
    fun failedActivityBadgeTextShowsCountUpToNine() {
        assertEquals("0", failedActivityBadgeText(0))
        assertEquals("1", failedActivityBadgeText(1))
        assertEquals("9", failedActivityBadgeText(9))
    }

    @Test
    fun failedActivityBadgeTextCapsDoubleDigitCounts() {
        assertEquals("9+", failedActivityBadgeText(10))
        assertEquals("9+", failedActivityBadgeText(42))
    }

}
