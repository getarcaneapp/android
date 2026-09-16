package app.getarcane.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StaleDataBannerTest {
    @Test
    fun `optimistic cache emission does not flash a warning before revalidation`() {
        assertNull(staleDataInfoAfterRefreshFailure(123, null))
    }

    @Test
    fun `failed live refresh exposes cached age and warning`() {
        assertEquals(
            StaleDataInfo(123, "offline"),
            staleDataInfoAfterRefreshFailure(123, "offline"),
        )
    }
}
