package app.getarcane.android.core

import app.getarcane.sdk.serialization.ArcaneJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardStreamModelsTest {
    @Test
    fun decodesHeartbeatWithLocalEnvironmentFallback() {
        val event = ArcaneJson.default.decodeFromString<DashboardStreamEvent>(
            """{"type":"heartbeat","timestamp":"2026-06-10T17:00:00.123Z"}""",
        )

        assertEquals(DashboardStreamEventType.Heartbeat, event.eventType)
        assertEquals("0", event.resolvedEnvironmentId)
        assertNull(event.snapshot)
    }

    @Test
    fun decodesSnapshotWithNullTablesAndUnknownActionItemValues() {
        val event = ArcaneJson.default.decodeFromString<DashboardStreamEvent>(
            """
            {
              "type": "snapshot",
              "environmentId": "env_1",
              "snapshot": {
                "containers": {
                  "data": null,
                  "counts": {
                    "runningContainers": 7,
                    "stoppedContainers": 2,
                    "totalContainers": 9
                  },
                  "pagination": {
                    "totalPages": 1,
                    "totalItems": 9,
                    "currentPage": 1,
                    "itemsPerPage": 10
                  }
                },
                "images": {
                  "data": null,
                  "pagination": {
                    "totalPages": 1,
                    "totalItems": 14,
                    "currentPage": 1,
                    "itemsPerPage": 10
                  }
                },
                "imageUsageCounts": {
                  "imagesInuse": 9,
                  "imagesUnused": 5,
                  "totalImages": 14,
                  "totalImageSize": 4815162342
                },
                "actionItems": {
                  "items": [
                    { "kind": "stopped_containers", "count": 2, "severity": "warning" },
                    { "kind": "future_kind", "count": 1, "severity": "catastrophic" }
                  ]
                },
                "settings": {},
                "versionInfo": {
                  "currentVersion": "2.0.2",
                  "revision": "28a441461051a7101708ab697b61c7eb50ce988b",
                  "shortRevision": "28a4414",
                  "goVersion": "go1.24.0",
                  "nodeVersion": "22.0.0",
                  "svelteKitVersion": "2.0.0",
                  "displayVersion": "2.0.2",
                  "isSemverVersion": true,
                  "newestVersion": "2.1.0",
                  "updateAvailable": true
                }
              },
              "timestamp": "2026-06-10T17:00:00Z"
            }
            """.trimIndent(),
        )

        val snapshot = event.snapshot!!
        assertEquals(DashboardStreamEventType.Snapshot, event.eventType)
        assertEquals("env_1", event.resolvedEnvironmentId)
        assertEquals(emptyList<Any>(), snapshot.containers.data)
        assertEquals(7, snapshot.containers.counts.runningContainers)
        assertEquals(2, snapshot.containers.counts.stoppedContainers)
        assertEquals(9, snapshot.containers.counts.totalContainers)
        assertEquals(emptyList<Any>(), snapshot.images.data)
        assertEquals(14, snapshot.imageUsageCounts.totalImages)
        assertEquals(DashboardActionItemKind.StoppedContainers, snapshot.actionItems.items.first().itemKind)
        assertEquals(DashboardActionItemKind.Unknown, snapshot.actionItems.items.last().itemKind)
        assertEquals(DashboardActionItemSeverity.Unknown, snapshot.actionItems.items.last().itemSeverity)
        assertEquals(true, snapshot.versionInfo?.updateAvailable)
    }

    @Test
    fun decodesErrorCodeClassification() {
        val incompatible = ArcaneJson.default.decodeFromString<DashboardStreamEvent>(
            """{"type":"error","environmentId":"env_9","error":"missing","errorCode":"agent_incompatible"}""",
        )
        val future = ArcaneJson.default.decodeFromString<DashboardStreamEvent>(
            """{"type":"error","environmentId":"env_9","error":"boom","errorCode":"future_code"}""",
        )
        val unclassified = ArcaneJson.default.decodeFromString<DashboardStreamEvent>(
            """{"type":"error","environmentId":"env_9","error":"boom"}""",
        )

        assertEquals(DashboardStreamEventType.Error, incompatible.eventType)
        assertEquals(DashboardStreamErrorCode.AgentIncompatible, incompatible.streamErrorCode)
        assertEquals(DashboardStreamErrorCode.Unknown, future.streamErrorCode)
        assertNull(unclassified.streamErrorCode)
    }
}
