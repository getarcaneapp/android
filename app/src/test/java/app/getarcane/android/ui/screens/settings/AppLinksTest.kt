package app.getarcane.android.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksTest {
    @Test
    fun appSettingsLinksUseIntentionalAndroidAndProjectDestinations() {
        assertEquals("https://github.com/getarcaneapp/android", AppLinks.ANDROID_SOURCE)
        assertEquals("https://github.com/getarcaneapp/android/issues", AppLinks.ANDROID_ISSUES)
        assertEquals("https://getarcane.app/docs", AppLinks.DOCUMENTATION)
        assertEquals("https://getarcane.app/privacy", AppLinks.PRIVACY_POLICY)
        assertEquals("https://discord.gg/WyXYpdyV3Z", AppLinks.COMMUNITY_SUPPORT)
        assertEquals("https://getarcane.app", AppLinks.PROJECT_HOME)

        val destinations = listOf(
            AppLinks.ANDROID_SOURCE,
            AppLinks.ANDROID_ISSUES,
            AppLinks.DOCUMENTATION,
            AppLinks.PRIVACY_POLICY,
            AppLinks.COMMUNITY_SUPPORT,
            AppLinks.PROJECT_HOME,
        )
        assertTrue(destinations.all { it.startsWith("https://") })
        assertFalse(destinations.any { "/getarcaneapp/ios" in it })
    }
}
