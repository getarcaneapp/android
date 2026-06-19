package app.getarcane.android.ui.screens.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdaterHistoryScreenTest {
    @Test
    fun parsesRestartedHistoryStatus() {
        val history = parseUpdaterHistory(
            """
            {
              "success": true,
              "data": [
                {
                  "id": "rec-1",
                  "resourceId": "container-1",
                  "resourceType": "container",
                  "resourceName": "web",
                  "status": "restarted",
                  "startTime": "2026-06-19T18:00:00Z",
                  "endTime": "2026-06-19T18:01:00Z",
                  "updateAvailable": true,
                  "updateApplied": true,
                  "createdAt": "2026-06-19T18:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, history.size)
        assertEquals("restarted", history.single().status)
    }
}
