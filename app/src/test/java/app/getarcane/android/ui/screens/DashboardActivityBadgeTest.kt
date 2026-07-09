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

    @Test
    fun activityCenterButtonDescriptionIncludesFailedCountOnlyWhenPresent() {
        assertEquals("Activity Center", activityCenterButtonContentDescription(0))
        assertEquals(
            "Activity Center, 1 failed activity need attention",
            activityCenterButtonContentDescription(1),
        )
        assertEquals(
            "Activity Center, 3 failed activities need attention",
            activityCenterButtonContentDescription(3),
        )
    }
}
