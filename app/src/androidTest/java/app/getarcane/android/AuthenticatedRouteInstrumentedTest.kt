package app.getarcane.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.getarcane.android.nav.AuthenticatedRoute
import app.getarcane.android.nav.AuthenticatedRouteCodec
import app.getarcane.android.nav.RouteDestination
import app.getarcane.android.nav.RouteParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticatedRouteInstrumentedTest {
    @Test
    fun serverBoundResourceRouteRoundTripsAndMalformedRouteFailsClosed() {
        val route = AuthenticatedRoute(
            serverBindingHash = "a".repeat(64),
            destination = RouteDestination.CONTAINER,
            environmentId = "environment-1",
            resourceId = "container-1",
        )

        assertEquals(RouteParseResult.Valid(route), AuthenticatedRouteCodec.parse(AuthenticatedRouteCodec.encode(route)))
        assertTrue(AuthenticatedRouteCodec.parse("arcane-mobile://route/v1/current/container/-/-") is RouteParseResult.Invalid)
    }
}
