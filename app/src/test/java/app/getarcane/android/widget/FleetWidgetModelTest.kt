package app.getarcane.android.widget

import app.getarcane.android.core.SnapshotErrorCode
import app.getarcane.android.core.SnapshotFreshness
import app.getarcane.android.core.StatusEnvironmentSnapshot
import app.getarcane.android.core.StatusSnapshot
import app.getarcane.android.core.StatusSnapshotRead
import app.getarcane.android.nav.AuthenticatedRouteCodec
import app.getarcane.android.nav.RouteDestination
import app.getarcane.android.nav.RouteParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetWidgetModelTest {
    @Test
    fun `fresh snapshot maps only reviewed aggregate fields and server bound route`() {
        val model = FleetWidgetModel.from(
            StatusSnapshotRead.Available(snapshot()),
            nowEpochMs = 120_000,
        )

        assertEquals(FleetWidgetState.FRESH, model.state)
        assertEquals(4, model.runningContainers)
        assertEquals(7, model.totalContainers)
        assertEquals("Updated 1m ago", model.freshnessLabel)
        val parsed = AuthenticatedRouteCodec.parse(model.routeUri) as RouteParseResult.Valid
        assertEquals("c".repeat(64), parsed.route.serverBindingHash)
        assertEquals(RouteDestination.DASHBOARD, parsed.route.destination)
        assertNull(parsed.route.environmentId)
        assertNull(parsed.route.resourceId)
    }

    @Test
    fun `stale unavailable signed out and unconfigured states stay explicit`() {
        assertEquals(
            FleetWidgetState.STALE,
            FleetWidgetModel.from(
                StatusSnapshotRead.Available(snapshot().copy(freshness = SnapshotFreshness.STALE)),
                120_000,
            ).state,
        )
        assertEquals(
            FleetWidgetState.UNAVAILABLE,
            FleetWidgetModel.from(
                StatusSnapshotRead.Available(
                    snapshot().copy(
                        freshness = SnapshotFreshness.ERROR,
                        errorCode = SnapshotErrorCode.NETWORK_UNAVAILABLE,
                    ),
                ),
                120_000,
            ).state,
        )
        val signedOut = FleetWidgetModel.from(
            StatusSnapshotRead.Available(StatusSnapshot.signedOut(42, 10)),
            120_000,
        )
        assertEquals(FleetWidgetState.SIGNED_OUT, signedOut.state)
        assertNull(signedOut.routeUri)
        assertEquals(
            FleetWidgetState.UNCONFIGURED,
            FleetWidgetModel.from(StatusSnapshotRead.Missing, 120_000).state,
        )
        assertEquals(
            FleetWidgetState.UNAVAILABLE,
            FleetWidgetModel.from(StatusSnapshotRead.CorruptOrUnsupported, 120_000).state,
        )
    }

    @Test
    fun `large widget rows remain snapshot bounded`() {
        val model = FleetWidgetModel.from(
            StatusSnapshotRead.Available(
                snapshot().copy(
                    environments = List(10) { index ->
                        environment("${index.toString(16)}".repeat(64).take(64), "Environment $index")
                    },
                ),
            ),
            120_000,
        )

        assertEquals(4, model.environments.size)
        assertTrue(model.environments.all { it.displayName.startsWith("Environment") })
    }

    @Test
    fun `fresh label expires without claiming old launcher data is live`() {
        val stale = FleetWidgetModel.from(
            StatusSnapshotRead.Available(snapshot()),
            nowEpochMs = 120_001,
        )
        val unavailable = FleetWidgetModel.from(
            StatusSnapshotRead.Available(snapshot()),
            nowEpochMs = 24 * 60 * 60_000L + 120_001,
        )

        assertEquals(FleetWidgetState.STALE, stale.state)
        assertEquals(FleetWidgetState.UNAVAILABLE, unavailable.state)
    }

    private fun snapshot() = StatusSnapshot(
        sourceVersion = 42,
        generatedAtEpochMs = 120_000,
        sourceUpdatedAtEpochMs = 60_000,
        freshness = SnapshotFreshness.FRESH,
        serverBindingHash = "c".repeat(64),
        scopeId = "a".repeat(64),
        activeEnvironmentKey = "b".repeat(64),
        totalRunningContainers = 4,
        totalContainers = 7,
        totalImages = 9,
        totalUpdates = 2,
        onlineEnvironments = 1,
        environments = listOf(environment("b".repeat(64), "Local Docker")),
    )

    private fun environment(key: String, name: String) = StatusEnvironmentSnapshot(
        environmentKey = key,
        displayName = name,
        online = true,
        runningContainers = 4,
        totalContainers = 7,
        images = 9,
        updatesAvailable = 2,
    )
}
