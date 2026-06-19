package app.getarcane.android.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ArcaneClientManagerTest {
    @Test
    fun requestTimeoutAllowsLargeUpdaterBatches() {
        assertTrue(
            "Long-running updater batches need more than the SDK default 30 second timeout",
            ARCANE_REQUEST_TIMEOUT_MILLIS >= 10 * 60 * 1000L,
        )
    }
}
