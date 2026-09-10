package app.getarcane.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyBrowserReturnTrackerTest {
    @Test
    fun `browser dismissal after launch requests cancellation`() {
        val tracker = PasskeyBrowserReturnTracker()
        assertTrue(tracker.start())
        tracker.launched()
        assertTrue(tracker.shouldCancelOnResume())
    }

    @Test
    fun `callback before launch returns cannot be cancelled by resume`() {
        val tracker = PasskeyBrowserReturnTracker()
        assertTrue(tracker.start())
        assertTrue(tracker.callback())
        tracker.launched()
        assertFalse(tracker.shouldCancelOnResume())
        assertFalse(tracker.callback())
    }

    @Test
    fun `reset permits retry and rejects stale callback`() {
        val tracker = PasskeyBrowserReturnTracker()
        assertFalse(tracker.callback())
        assertTrue(tracker.start())
        tracker.launched()
        tracker.reset()
        assertFalse(tracker.callback())
        assertTrue(tracker.start())
    }
}
